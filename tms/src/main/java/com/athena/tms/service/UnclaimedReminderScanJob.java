package com.athena.tms.service;

import com.athena.tms.repair.RepairService;
import java.time.Clock;
import java.time.Duration;
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
public class UnclaimedReminderScanJob {

    private final JdbcTemplate jdbcTemplate;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final PushOutboxWriter pushOutboxWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public UnclaimedReminderScanJob(
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
            fixedDelayString = "${tms.escalation.scan-interval:1m}",
            initialDelayString = "${tms.escalation.scan-initial-delay:1m}")
    public void scan() {
        tenantScopedExecutor.forEachTenant(this::scanTenant);
    }

    private void scanTenant(long tenantId) {
        JsonNode thresholds = loadThresholds(tenantId);
        if (thresholds == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT t.id, t.dept, t.urgent, t.title, t.reminder_stage, t.created_at,
                       r.room_no, rt.ticket_no, rt.category,
                       (SELECT MAX(te.created_at) FROM task_events te
                         WHERE te.tenant_id = t.tenant_id AND te.task_id = t.id
                           AND te.type IN ('created', 'returned_to_pool')) AS pool_entered_at
                  FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                  LEFT JOIN repair_tickets rt ON rt.tenant_id = t.tenant_id AND rt.task_id = t.id
                 WHERE t.tenant_id = ? AND t.state = 'pool'
                """,
                tenantId);
        for (Map<String, Object> row : rows) {
            boolean urgent = Boolean.TRUE.equals(row.get("urgent"));
            JsonNode bucket = thresholds.path(urgent ? "urgent" : "cleaning");
            int staffMinutes = bucket.path("staffReminded").asInt(-1);
            int managerMinutes = bucket.path("managerNotified").asInt(-1);
            if (staffMinutes <= 0 || managerMinutes <= 0) {
                continue;
            }
            OffsetDateTime poolSince = toOffsetDateTime(row.get("pool_entered_at"));
            if (poolSince == null) {
                poolSince = toOffsetDateTime(row.get("created_at"));
            }
            if (poolSince == null) {
                continue;
            }
            long elapsedMinutes = Duration.between(poolSince.toInstant(), now.toInstant()).toMinutes();
            int targetLevel = elapsedMinutes >= managerMinutes ? 2 : (elapsedMinutes >= staffMinutes ? 1 : 0);
            if (targetLevel == 0) {
                continue;
            }
            String currentStage = row.get("reminder_stage") == null ? null : String.valueOf(row.get("reminder_stage"));
            int currentLevel = currentStage == null ? 0 : ("staff_reminded".equals(currentStage) ? 1 : 2);
            UUID taskId = (UUID) row.get("id");
            String dept = String.valueOf(row.get("dept"));
            String label = labelOf(row);
            for (int level = currentLevel + 1; level <= targetLevel; level++) {
                String stage = level == 1 ? "staff_reminded" : "manager_notified";
                String fromStage = level == 1 ? null : "staff_reminded";
                if (!advanceStage(tenantId, taskId, stage, fromStage)) {
                    break;
                }
                notifyStage(tenantId, taskId, stage, dept, label, staffMinutes);
            }
        }
    }

    private boolean advanceStage(long tenantId, UUID taskId, String stage, String fromStage) {
        int updated = jdbcTemplate.update(
                """
                UPDATE tasks
                   SET reminder_stage = ?, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state = 'pool'
                   AND reminder_stage IS NOT DISTINCT FROM ?
                """,
                stage, tenantId, taskId, fromStage);
        return updated > 0;
    }

    private void notifyStage(long tenantId, UUID taskId, String stage, String dept, String label, int staffMinutes) {
        boolean isStaffStage = "staff_reminded".equals(stage);
        String pushEvent = isStaffStage ? "unclaimed_reminder_staff" : "unclaimed_reminder_manager";
        String title = isStaffStage ? label + " 已等 " + staffMinutes + " 分，還沒人接" : label + " 已通知主管";
        List<UUID> recipients = isStaffStage
                ? pushOutboxWriter.activeDeptStaffIds(tenantId, dept)
                : pushOutboxWriter.activeManagerIds(tenantId);
        for (UUID recipient : recipients) {
            ObjectNode payload = PushOutboxWriter.payload(objectMapper, recipient, pushEvent, title);
            PushOutboxWriter.targetPool(payload, dept);
            pushOutboxWriter.enqueue(tenantId, "task:" + taskId, payload,
                    "unclaimed_reminder:" + taskId + ":" + stage + ":" + recipient);
        }
    }

    private static String labelOf(Map<String, Object> row) {
        Object ticketNo = row.get("ticket_no");
        if (ticketNo != null) {
            String category = String.valueOf(row.get("category"));
            return ticketNo + " " + RepairService.CATEGORY_LABELS.getOrDefault(category, category);
        }
        return row.get("room_no") + " " + row.get("title");
    }

    private JsonNode loadThresholds(long tenantId) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT value #>> '{}' FROM tenant_config WHERE tenant_id = ? AND key = 'unclaimed_reminder_minutes'",
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

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        return null;
    }
}
