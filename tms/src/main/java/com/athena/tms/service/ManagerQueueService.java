package com.athena.tms.service;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.ManagerKind;
import com.athena.tms.common.SqlTimestamps;
import com.athena.tms.common.error.ForbiddenException;
import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.ManagerQueues;
import com.athena.tms.dto.ManagerQueues.InspectionQueueItem;
import com.athena.tms.dto.ManagerQueues.VerifyQueueItem;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManagerQueueService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ManagerQueueService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ManagerQueues queues() {
        long tenantId = TenantContext.require();
        CurrentActor actor = CurrentActor.require();
        ManagerKind kind = ManagerKind.of(actor)
                .orElseThrow(() -> new ForbiddenException("只有主管可以查看主管待辦"));
        LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());

        List<InspectionQueueItem> inspection =
                kind.inspects() ? inspectionQueue(tenantId, actor.staffId(), today) : null;
        return ManagerQueues.of(inspection, verifyQueue(tenantId, today));
    }

    private List<InspectionQueueItem> inspectionQueue(long tenantId, UUID callerStaffId, LocalDate today) {
        return jdbcTemplate.queryForList(
                """
                SELECT t.id, t.title, t.done_at AS waiting_since, t.redo_count, r.room_no,
                       s.name AS cleaner_name
                  FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                  JOIN staff_accounts s ON s.tenant_id = t.tenant_id AND s.id = t.assignee_id
                 WHERE t.tenant_id = ? AND t.type = 'cleaning' AND t.state = 'done'
                   AND t.assignee_id IS DISTINCT FROM ?
                 ORDER BY t.done_at, r.room_no, t.id
                """,
                tenantId, callerStaffId).stream()
                .map(row -> {
                    Base base = base(row, today);
                    return new InspectionQueueItem(
                            base.taskId(), base.roomNo(), base.publicArea(), base.title(),
                            base.waitingSince(), base.today(),
                            (String) row.get("cleaner_name"),
                            ((Number) row.get("redo_count")).intValue());
                })
                .toList();
    }

    private List<VerifyQueueItem> verifyQueue(long tenantId, LocalDate today) {
        return jdbcTemplate.queryForList(
                """
                SELECT t.id, t.title, t.source, t.done_at AS waiting_since, r.room_no,
                       rt.ticket_no, rt.category, rt.description, rt.resolution_type,
                       rt.last_verify_failed_at IS NOT NULL AS was_verification_failed,
                       rep.name AS reporter_name, asg.name AS assignee_name
                  FROM tasks t
                  JOIN repair_tickets rt ON rt.tenant_id = t.tenant_id AND rt.task_id = t.id
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                  LEFT JOIN staff_accounts rep ON rep.tenant_id = rt.tenant_id AND rep.id = rt.reporter_id
                  LEFT JOIN staff_accounts asg ON asg.tenant_id = t.tenant_id AND asg.id = t.assignee_id
                 WHERE t.tenant_id = ? AND t.type = 'repair' AND t.state = 'done'
                   AND rt.resolution_type IS DISTINCT FROM 'no_action_needed'
                 ORDER BY t.done_at, NULLIF(r.room_no, ?) NULLS LAST, rt.ticket_no
                """,
                tenantId, AutomationRuleService.PUBLIC_AREA_ROOM_NO).stream()
                .map(row -> {
                    Base base = base(row, today);
                    return new VerifyQueueItem(
                            base.taskId(), base.roomNo(), base.publicArea(), base.title(),
                            base.waitingSince(), base.today(),
                            (String) row.get("ticket_no"),
                            (String) row.get("category"),
                            (String) row.get("description"),
                            (String) row.get("reporter_name"),
                            (String) row.get("assignee_name"),
                            "system".equals(row.get("source")) ? "scheduled" : "reported",
                            (String) row.get("resolution_type"),
                            (Boolean) row.get("was_verification_failed"));
                })
                .toList();
    }

    private record Base(
            UUID taskId,
            String roomNo,
            boolean publicArea,
            String title,
            OffsetDateTime waitingSince,
            boolean today) {}

    private Base base(Map<String, Object> row, LocalDate today) {
        boolean publicArea = AutomationRuleService.isPublicAreaRoomNo(row.get("room_no"));
        OffsetDateTime raw = SqlTimestamps.toOffsetDateTime(row.get("waiting_since"));
        OffsetDateTime waitingSince = raw == null ? null : raw.atZoneSameInstant(clock.getZone()).toOffsetDateTime();
        return new Base(
                (UUID) row.get("id"),
                publicArea ? null : (String) row.get("room_no"),
                publicArea,
                (String) row.get("title"),
                waitingSince,
                waitingSince != null && waitingSince.toLocalDate().equals(today));
    }
}
