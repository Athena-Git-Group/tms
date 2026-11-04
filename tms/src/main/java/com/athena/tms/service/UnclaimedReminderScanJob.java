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

/**
 * 待認領未接提醒的週期掃描（裁決 B，取代已廢除的 L1/L2/L3 逾時升級鏈）：一間房進了
 * 部門待認領池卻一直沒人接，先提醒同仁、再通知主管，避免房務／工務同仁沒注意到
 * 池子裡有新單。
 *
 * <p>只掃 {@code state=pool} 的任務：待開始、進行中的單已經有人在處理，不屬於
 * 「沒人接」的情境；認領、改派或撤回後任務自然離開 pool，掃描query 本身就排除掉，
 * 不需要額外清空階段。
 *
 * <p>門檻分鐘數讀 {@code tenant_config} 鍵 {@code unclaimed_reminder_minutes}
 * （見 V12__unclaimed_reminder_thresholds.sql），一般單與急件（{@code tasks.urgent}）
 * 各自獨立的「提醒同仁」「通知主管」分鐘數。從任務進池的時間算起（最近一次
 * {@code created} 或 {@code returned_to_pool} 事件；查不到事件時退回
 * {@code tasks.created_at}）：超過提醒同仁門檻先進 {@code staff_reminded}，超過通知
 * 主管門檻再進 {@code manager_notified}（見 V13__unclaimed_reminder_stage.sql）。
 * 兩段各自只推播一次——用條件式更新（WHERE 帶前一階段）推進階段，兩次掃描重疊時
 * 第二次一列也改不到；推播以「任務＋階段＋收件人」當冪等鍵，重跑不會重複插入。
 * 每段都只記「目前到哪個階段」，不另開新表或新 {@code task_events} 型別。
 *
 * <p>時間一律用注入的 {@link Clock}，測試能以可調時鐘直接把「現在」推到門檻之後，
 * 不必真等。
 */
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

    /**
     * 把任務的未接提醒階段推進一段。前一階段直接寫在 UPDATE 的 WHERE 裡（用
     * {@code IS NOT DISTINCT FROM} 相容 NULL），另一次掃描搶先推進過這階段時這裡
     * 一列也改不到，直接放棄，同一階段不會推進第二次。只更新 state=pool 的任務——
     * 認領、改派或撤回後任務已經不在池中，這裡自然不會再動它。
     */
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

    /**
     * 提醒同仁推播同部門在職成員；通知主管推播全體在職主管。去處都是部門待認領。
     * 收件人查詢共用 {@link PushOutboxWriter#activeDeptStaffIds}／{@link PushOutboxWriter#activeManagerIds}
     * ——與 {@link PushNotifier} 的部門推播、其他通知全體主管的事件同一套認定，不在這裡另開一份。
     */
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

    /**
     * 房務／其他部門單用「房號 內容」；工務單用「單號 類別」（報修人與房務看慣的叫法不同）。
     * 類別中文對照共用 {@link RepairService#CATEGORY_LABELS}，不維護第二份工務單類別名稱表。
     */
    private static String labelOf(Map<String, Object> row) {
        Object ticketNo = row.get("ticket_no");
        if (ticketNo != null) {
            String category = String.valueOf(row.get("category"));
            return ticketNo + " " + RepairService.CATEGORY_LABELS.getOrDefault(category, category);
        }
        return row.get("room_no") + " " + row.get("title");
    }

    /** 讀 {@code unclaimed_reminder_minutes} 鍵；沒設定時回 null，這輪不掃。 */
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
