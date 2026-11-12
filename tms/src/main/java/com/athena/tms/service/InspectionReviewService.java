package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.error.NotFoundException;
import com.athena.tms.common.error.UnprocessableEntityException;
import com.athena.tms.dao.InspectionRepository;
import com.athena.tms.dao.TaskRepository;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.InspectionRequest;
import com.athena.tms.entity.Inspection;
import com.athena.tms.entity.Task;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class InspectionReviewService {

    private final TaskRepository taskRepository;
    private final InspectionRepository inspectionRepository;
    private final TaskEngine taskEngine;
    private final PushOutboxWriter pushOutboxWriter;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InspectionReviewService(
            TaskRepository taskRepository,
            InspectionRepository inspectionRepository,
            TaskEngine taskEngine,
            PushOutboxWriter pushOutboxWriter,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.inspectionRepository = inspectionRepository;
        this.taskEngine = taskEngine;
        this.pushOutboxWriter = pushOutboxWriter;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public record PassRate(int passed, int failed, Double rate) {}

    @Transactional
    public Inspection spotCheck(UUID taskId, List<InspectionRequest.Item> items) {
        long tenantId = TenantContext.require();
        CurrentActor inspector = CurrentActor.require();
        Task task = taskRepository
                .findByTenantIdAndId(tenantId, taskId)
                .orElseThrow(() -> new NotFoundException("查無此任務"));
        if (task.getRoomId() == null) {
            throw new UnprocessableEntityException("此任務未綁房間，無法抽查");
        }
        if (items == null || items.isEmpty()) {
            throw new UnprocessableEntityException("items 必填：至少抽查一個項目");
        }
        for (InspectionRequest.Item item : items) {
            if (item.key() == null || item.pass() == null) {
                throw new UnprocessableEntityException("抽查項目必須同時有 key 與 pass 判定");
            }
        }

        List<String> failedKeys = items.stream()
                .filter(item -> !item.pass())
                .map(InspectionRequest.Item::key)
                .toList();
        if (!failedKeys.isEmpty()) {
            String failedKeysText = String.join("、", failedKeys);
            Actor actor = Actor.staff(inspector.role(), inspector.staffId());
            int seq = taskEngine.failSpotCheck(tenantId, taskId, failedKeysText, actor);
            jdbcTemplate.update(
                    "DELETE FROM task_sop_checks WHERE tenant_id = ? AND task_id = ?", tenantId, taskId);
            String roomNo = markRoomDirty(tenantId, task.getRoomId());
            notifyAssignee(tenantId, task, seq, roomNo, failedKeysText);
        }
        return recordSpotCheck(tenantId, taskId, inspector.staffId(), failedKeys.isEmpty(), items);
    }

    public PassRate passRate(LocalDate date) {
        long tenantId = TenantContext.require();
        LocalDate workDate = date != null ? date : LocalDate.ofInstant(clock.instant(), clock.getZone());
        int passed = countInspection(tenantId, workDate, "passed");
        int failed = countInspection(tenantId, workDate, "failed");
        int total = passed + failed;
        return new PassRate(passed, failed, total == 0 ? null : (double) passed / total);
    }

    private int countInspection(long tenantId, LocalDate workDate, String inspection) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM tasks
                 WHERE tenant_id = ? AND type = 'cleaning' AND work_date = ? AND inspection = ?
                """,
                Integer.class, tenantId, workDate, inspection);
        return count == null ? 0 : count;
    }

    private String markRoomDirty(long tenantId, UUID roomId) {
        return jdbcTemplate.queryForObject(
                "UPDATE rooms SET hk_state = 'dirty', updated_at = now() WHERE tenant_id = ? AND id = ? RETURNING room_no",
                String.class, tenantId, roomId);
    }

    private void notifyAssignee(long tenantId, Task task, int seq, String roomNo, String failedKeysText) {
        if (task.getAssigneeId() == null) {
            return;
        }
        ObjectNode payload = PushOutboxWriter.payload(
                objectMapper, task.getAssigneeId(), "inspection_failed",
                roomNo + " 查房退回 · " + failedKeysText + "不合格");
        PushOutboxWriter.targetOwnedTask(payload, task.getType(), task.getId());
        pushOutboxWriter.enqueue(
                tenantId,
                "task:" + task.getId(),
                payload,
                "spot_check_failed:" + task.getId() + ":seq:" + seq);
    }

    private Inspection recordSpotCheck(
            long tenantId, UUID taskId, UUID inspectorId, boolean allPass, List<InspectionRequest.Item> items) {
        ArrayNode itemsJson = objectMapper.createArrayNode();
        for (InspectionRequest.Item item : items) {
            ObjectNode node = itemsJson.addObject();
            node.put("key", item.key());
            node.put("pass", item.pass());
        }
        Inspection inspection = new Inspection();
        inspection.setId(UuidV7.generate());
        inspection.setTenantId(tenantId);
        inspection.setTaskId(taskId);
        inspection.setInspectorId(inspectorId);
        inspection.setResult(allPass ? "passed" : "failed");
        inspection.setItems(itemsJson.toString());
        inspection.setCreatedAt(OffsetDateTime.now(clock));
        return inspectionRepository.save(inspection);
    }
}
