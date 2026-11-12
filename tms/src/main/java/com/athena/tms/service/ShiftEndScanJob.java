package com.athena.tms.service;

import com.athena.tms.dto.ShiftTimes;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class ShiftEndScanJob {

    private final JdbcTemplate jdbcTemplate;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final PushOutboxWriter pushOutboxWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ShiftEndScanJob(
            JdbcTemplate jdbcTemplate,
            TenantScopedExecutor tenantScopedExecutor,
            PushOutboxWriter pushOutboxWriter,
            ObjectMapper objectMapper,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.pushOutboxWriter = pushOutboxWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${tms.shift-end.scan-interval:1m}",
            initialDelayString = "${tms.shift-end.scan-initial-delay:1m}")
    public void scan() {
        tenantScopedExecutor.forEachTenant(this::scanTenant);
    }

    private void scanTenant(long tenantId) {
        LocalTime now = LocalTime.ofInstant(clock.instant(), clock.getZone());
        JsonNode shiftTimes = loadShiftTimes(tenantId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT pr.staff_id, cp.shift, sa.dept
                  FROM plan_rooms pr
                  JOIN cleaning_plans cp ON cp.tenant_id = pr.tenant_id AND cp.id = pr.plan_id
                  JOIN staff_accounts sa ON sa.tenant_id = pr.tenant_id AND sa.id = pr.staff_id
                 WHERE pr.tenant_id = ? AND pr.staff_id IS NOT NULL AND sa.active = true
                """,
                tenantId);
        for (Map<String, Object> row : rows) {
            LocalTime shiftEnd = ShiftTimes.endOf(String.valueOf(row.get("shift")), shiftTimes);
            if (shiftEnd == null || now.isBefore(shiftEnd)) {
                continue;
            }
            UUID staffId = (UUID) row.get("staff_id");
            String dept = String.valueOf(row.get("dept"));
            requeueNotStarted(tenantId, staffId, dept);
            notifyManagersOfInterrupted(tenantId, staffId);
        }
    }

    private void requeueNotStarted(long tenantId, UUID staffId, String dept) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Map.Entry<UUID, Integer>> returned = jdbcTemplate.query(
                """
                UPDATE tasks
                   SET state = 'pool', assignee_id = NULL, pool_source = 'shift_end_return',
                       return_reason = '班末退池 · 交給下一班', deferred_by_self = false,
                       hold_reason = NULL, event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND assignee_id = ?
                   AND (state = 'accepted' OR (state = 'deferred' AND deferred_by_self))
                RETURNING id, event_seq
                """,
                (rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getInt(2)),
                tenantId, staffId);
        for (Map.Entry<UUID, Integer> entry : returned) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("reason", "shift_end_return");
            jdbcTemplate.update(
                    """
                    INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
                    VALUES (?, ?, ?, 'returned_to_pool', 'system', NULL, ?::jsonb, ?)
                    """,
                    tenantId, entry.getKey(), entry.getValue(), payload.toString(), now);
        }
        if (returned.isEmpty()) {
            return;
        }
        ObjectNode payload = PushOutboxWriter.payload(objectMapper, staffId, "shift_end_returned",
                returned.size() + " 單還沒開始，已退回待認領交給下一班；進行中的單仍由您完成");
        ObjectNode target = payload.putObject("target");
        target.put("kind", "pool");
        target.put("dept", dept);
        pushOutboxWriter.enqueue(tenantId, "staff:" + staffId, payload,
                "shift-end-return:" + LocalDate.ofInstant(clock.instant(), clock.getZone()) + ":" + staffId);
    }

    private void notifyManagersOfInterrupted(long tenantId, UUID staffId) {
        List<Map<String, Object>> interrupted = jdbcTemplate.queryForList(
                """
                SELECT t.id, r.room_no, t.title
                  FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? AND t.assignee_id = ? AND t.state = 'interrupted'
                """,
                tenantId, staffId);
        if (interrupted.isEmpty()) {
            return;
        }
        List<UUID> managers = pushOutboxWriter.activeManagerIds(tenantId);
        LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());
        for (Map<String, Object> row : interrupted) {
            UUID taskId = (UUID) row.get("id");
            Object roomNo = row.get("room_no");
            String label = roomNo != null && !String.valueOf(roomNo).isBlank()
                    ? String.valueOf(roomNo)
                    : String.valueOf(row.get("title"));
            for (UUID manager : managers) {
                ObjectNode payload = PushOutboxWriter.payload(
                        objectMapper, manager, "shift_end_still_interrupted", label + " 班末仍中斷，需要附交代退池");
                ObjectNode target = payload.putObject("target");
                target.put("kind", "task");
                target.put("taskId", taskId.toString());
                pushOutboxWriter.enqueue(tenantId, "task:" + taskId, payload,
                        "shift-end-interrupted:" + taskId + ":" + today + ":" + manager);
            }
        }
    }

    private JsonNode loadShiftTimes(long tenantId) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT value::text FROM tenant_config WHERE tenant_id = ? AND key = 'shift_times'",
                String.class, tenantId);
        if (values.isEmpty() || values.get(0) == null) {
            return null;
        }
        try {
            return objectMapper.readTree(values.get(0));
        } catch (JacksonException e) {
            return null;
        }
    }
}
