package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.LeaveRepository;
import com.athena.tms.dto.Actor;
import com.athena.tms.entity.Leave;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class LeaveService {

    private final LeaveRepository leaveRepository;
    private final TaskEngine taskEngine;
    private final JdbcTemplate jdbcTemplate;
    private final PushOutboxWriter pushOutboxWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LeaveService(
            LeaveRepository leaveRepository,
            TaskEngine taskEngine,
            JdbcTemplate jdbcTemplate,
            PushOutboxWriter pushOutboxWriter,
            ObjectMapper objectMapper,
            Clock clock) {
        this.leaveRepository = leaveRepository;
        this.taskEngine = taskEngine;
        this.jdbcTemplate = jdbcTemplate;
        this.pushOutboxWriter = pushOutboxWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public Leave create(UUID staffId, OffsetDateTime fromAt, OffsetDateTime toAt) {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        if (staffId == null || fromAt == null || toAt == null) {
            throw new UnprocessableEntityException("staffId、fromAt、toAt 皆必填");
        }

        Leave leave = new Leave();
        leave.setId(UuidV7.generate());
        leave.setTenantId(tenantId);
        leave.setStaffId(staffId);
        leave.setFromAt(fromAt);
        leave.setToAt(toAt);
        leave.setCreatedBy(actor.staffId());
        leave.setCreatedAt(OffsetDateTime.now(clock));
        leave = leaveRepository.save(leave);

        List<Map<String, Object>> returned = unfinishedTasksOf(tenantId, staffId);
        Actor engineActor = Actor.staff(actor.role(), actor.staffId());
        for (Map<String, Object> task : returned) {
            taskEngine.returnToPoolForLeave(tenantId, (UUID) task.get("id"), engineActor);
        }
        notifySameFloorTakeover(tenantId, leave.getId(), staffId, returned);
        return leave;
    }

    private List<Map<String, Object>> unfinishedTasksOf(long tenantId, UUID staffId) {
        return jdbcTemplate.queryForList(
                """
                SELECT t.id, t.state, t.dept, r.floor
                  FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? AND t.assignee_id = ?
                   AND t.state IN ('accepted', 'doing', 'interrupted', 'deferred')
                 ORDER BY t.created_at
                """,
                tenantId, staffId);
    }

    private void notifySameFloorTakeover(
            long tenantId, UUID leaveId, UUID staffOnLeave, List<Map<String, Object>> returned) {
        Map<Integer, List<Map<String, Object>>> byFloor = new LinkedHashMap<>();
        for (Map<String, Object> task : returned) {
            Integer floor = task.get("floor") instanceof Number n ? n.intValue() : null;
            if (floor == null) {
                continue;
            }
            byFloor.computeIfAbsent(floor, k -> new ArrayList<>()).add(task);
        }
        for (Map.Entry<Integer, List<Map<String, Object>>> entry : byFloor.entrySet()) {
            int floor = entry.getKey();
            List<Map<String, Object>> tasks = entry.getValue();
            UUID highlight = tasks.stream()
                    .filter(t -> "doing".equals(t.get("state")))
                    .map(t -> (UUID) t.get("id"))
                    .findFirst()
                    .orElse((UUID) tasks.get(0).get("id"));
            String dept = String.valueOf(tasks.get(0).get("dept"));
            String title = floor + " 樓有 " + tasks.size() + " 單需要接手";
            for (UUID recipient : sameFloorActiveStaff(tenantId, floor, dept, staffOnLeave)) {
                ObjectNode payload = PushOutboxWriter.payload(objectMapper, recipient, "leave_takeover", title);
                PushOutboxWriter.targetPool(payload, dept, highlight);
                pushOutboxWriter.enqueue(tenantId, "leave:" + leaveId, payload,
                        "leave_takeover:" + leaveId + ":floor:" + floor + ":" + recipient);
            }
        }
    }

    private List<UUID> sameFloorActiveStaff(long tenantId, int floor, String dept, UUID excludedStaffId) {
        return jdbcTemplate.queryForList(
                """
                SELECT DISTINCT sa.id
                  FROM plan_rooms pr
                  JOIN rooms pl ON pl.tenant_id = pr.tenant_id AND pl.id = pr.room_id
                  JOIN staff_accounts sa ON sa.tenant_id = pr.tenant_id AND sa.id = pr.staff_id
                 WHERE pr.tenant_id = ? AND pl.floor = ?
                   AND sa.active = true AND sa.dept = ? AND sa.id <> ?
                """,
                UUID.class, tenantId, floor, dept, excludedStaffId);
    }
}
