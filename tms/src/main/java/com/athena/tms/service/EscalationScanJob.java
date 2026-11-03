package com.athena.tms.service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 逾時升級的週期掃描：進行中的任務超過時限仍沒完成時，逐層通知該負責的人，
 * 避免一間房卡住整天都沒人知道。
 *
 * <p>逾時判斷：任務帶到期時間（due_at）時以到期時間為準，否則以建立時間加上
 * 該類別的 SLA 時限（tenant_config 的 sla_minutes_cleaning 等鍵）；兩套不疊加。
 * 超過時限先升 L1 通知負責人，之後每多過一個層距（escalation_step_minutes）
 * 再升一層：L2 通知主管、L3 通知值班經理，最多到 L3。
 *
 * <p>掃待接池（pool）、待開始（accepted）與進行中（doing）的任務；已延後與
 * 已中斷的單是人主動停住的，照樣升級只會對已知狀況重複吵人，恢復進行後自然
 * 重新納入掃描。進行中的單最多升到 L1：有人正在房裡做，升到主管與值班經理
 * 只會對處理中的工作疊加催促；沒人接手的單（pool 與 accepted）才逐層往上催。
 * 每層只升一次——升級用條件式更新（WHERE 帶前一層的 esc_level），兩次掃描
 * 重疊時第二次一列也改不到；推播以「任務＋層級＋收件人」當冪等鍵，重跑不會
 * 重複插入。
 *
 * <p>時間一律用注入的 {@link Clock}，測試能以可調時鐘直接把「現在」推到
 * 時限之後，不必真等。
 */
@Component
public class EscalationScanJob {

    private static final int MAX_LEVEL = 3;

    private final JdbcTemplate jdbcTemplate;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final PushOutboxWriter pushOutboxWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EscalationScanJob(
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
        Integer stepMinutes = configMinutes(tenantId, "escalation_step_minutes");
        if (stepMinutes == null || stepMinutes <= 0) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT t.id, t.type, t.dept, t.assignee_id, t.esc_level, t.due_at, t.created_at,
                       t.state, t.title, r.room_no
                  FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? AND t.state IN ('pool', 'accepted', 'doing')
                """,
                tenantId);
        for (Map<String, Object> row : rows) {
            OffsetDateTime deadline = deadlineOf(tenantId, row);
            if (deadline == null) {
                continue;
            }
            long overdueMinutes = Duration.between(deadline.toInstant(), now.toInstant()).toMinutes();
            if (overdueMinutes <= 0) {
                continue;
            }
            int maxLevel = "doing".equals(row.get("state")) ? 1 : MAX_LEVEL;
            int targetLevel = (int) Math.min(maxLevel, 1 + (overdueMinutes - 1) / stepMinutes);
            int currentLevel = row.get("esc_level") instanceof Number n ? n.intValue() : 0;
            UUID taskId = (UUID) row.get("id");
            UUID assigneeId = (UUID) row.get("assignee_id");
            for (int level = currentLevel + 1; level <= targetLevel; level++) {
                if (!escalateOneLevel(tenantId, taskId, level)) {
                    break;
                }
                notifyLevel(tenantId, taskId, level, assigneeId, String.valueOf(row.get("dept")),
                        labelOf(row));
            }
        }
    }

    /** 到期時間優先；沒有到期時間就用建立時間加該類別 SLA 時限，查不到時限就不升級。 */
    private OffsetDateTime deadlineOf(long tenantId, Map<String, Object> row) {
        OffsetDateTime dueAt = toOffsetDateTime(row.get("due_at"));
        if (dueAt != null) {
            return dueAt;
        }
        String slaKey = switch (String.valueOf(row.get("type"))) {
            case "cleaning" -> "sla_minutes_cleaning";
            case "amenity" -> "sla_minutes_amenity";
            case "other" -> "sla_minutes_other";
            default -> null;
        };
        if (slaKey == null) {
            return null;
        }
        Integer slaMinutes = configMinutes(tenantId, slaKey);
        OffsetDateTime createdAt = toOffsetDateTime(row.get("created_at"));
        if (slaMinutes == null || slaMinutes <= 0 || createdAt == null) {
            return null;
        }
        return createdAt.plusMinutes(slaMinutes);
    }

    /**
     * 把任務升到指定層級並記一筆 escalated 事件。前一層的 esc_level 直接寫在
     * UPDATE 的 WHERE 裡，另一次掃描搶先升過這層時這裡一列也改不到，直接放棄，
     * 同一層不會升第二次。
     */
    private boolean escalateOneLevel(long tenantId, UUID taskId, int level) {
        List<Integer> seqs = jdbcTemplate.query(
                """
                UPDATE tasks
                   SET esc_level = ?, event_seq = event_seq + 1, updated_at = now()
                 WHERE tenant_id = ? AND id = ? AND state IN ('pool', 'accepted', 'doing') AND COALESCE(esc_level, 0) = ?
                RETURNING event_seq
                """,
                (rs, i) -> rs.getInt(1),
                level, tenantId, taskId, level - 1);
        if (seqs.isEmpty()) {
            return false;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("level", level);
        jdbcTemplate.update(
                """
                INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
                VALUES (?, ?, ?, 'escalated', 'system', NULL, ?::jsonb, ?)
                """,
                tenantId, taskId, seqs.get(0), payload.toString(), OffsetDateTime.now(clock));
        return true;
    }

    /** L1 通知負責人（沒有負責人時通知該部門在職成員）、L2 通知主管、L3 通知值班經理。 */
    private void notifyLevel(
            long tenantId, UUID taskId, int level, UUID assigneeId, String dept, String label) {
        List<UUID> recipients = switch (level) {
            case 1 -> assigneeId != null
                    ? List.of(assigneeId)
                    : jdbcTemplate.queryForList(
                            "SELECT id FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND active = true",
                            UUID.class, tenantId, dept);
            case 2 -> jdbcTemplate.queryForList(
                    "SELECT id FROM staff_accounts WHERE tenant_id = ? AND role = 'manager' AND active = true",
                    UUID.class, tenantId);
            default -> jdbcTemplate.queryForList(
                    "SELECT id FROM staff_accounts WHERE tenant_id = ? AND role = 'duty_manager' AND active = true",
                    UUID.class, tenantId);
        };
        String title = level == 1 ? label + " 已逾時 · 升級 L1" : label + " 已升級 L" + level;
        for (UUID recipient : recipients) {
            ObjectNode payload = PushOutboxWriter.payload(objectMapper, recipient, "escalated", title);
            if (assigneeId != null) {
                PushOutboxWriter.targetTask(payload, taskId);
            } else {
                PushOutboxWriter.targetPool(payload, dept);
            }
            pushOutboxWriter.enqueue(tenantId, "task:" + taskId, payload,
                    "escalate:" + taskId + ":L" + level + ":" + recipient);
        }
    }

    private Integer configMinutes(long tenantId, String key) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT value #>> '{}' FROM tenant_config WHERE tenant_id = ? AND key = ?",
                String.class, tenantId, key);
        if (values.isEmpty() || values.get(0) == null) {
            return null;
        }
        try {
            return Integer.parseInt(values.get(0).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String labelOf(Map<String, Object> row) {
        Object roomNo = row.get("room_no");
        return roomNo != null && !String.valueOf(roomNo).isBlank()
                ? String.valueOf(roomNo)
                : String.valueOf(row.get("title"));
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
