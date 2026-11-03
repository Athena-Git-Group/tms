package com.athena.tms.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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
 * 班末掃描的週期工作：員工班次結束時，把名下還沒開始的單退回待接池交給下一班，
 * 不讓任何一間房因為下班而無聲消失。
 *
 * <p>名下任務依狀態分三路（規則見 data-model.dbml Table tasks 的 Note）：
 * 待開始（accepted）與本人延後（deferred 且 deferred_by_self）的單退回待接池，
 * 來源標示班末退池（pool_source=shift_end_return）並記 returned_to_pool 事件；
 * 進行中（doing）的單留給本人完成；已中斷（interrupted）的單維持中斷、負責人不變，
 * 改推播主管請他決定是否附交代退池（tasksRequeue）。退了幾單會推播告知員工本人。
 *
 * <p>班次結束時刻從 plan_rooms 連到 cleaning_plans.shift 取得。shift 的值域由各租戶
 * 的 shift_times 設定決定、每家飯店不同，所以同時接受 HH:mm 與班別名稱兩種寫法；
 * 解析不出結束時刻的員工不退池——寧可這輪漏掃，也不能在猜錯時把還在上班的人的單退走。
 *
 * <p>退池用條件式 UPDATE（WHERE 帶負責人與狀態），兩次掃描重疊時第二次一列也改
 * 不到；推播以「事件＋日期＋收件人」組冪等鍵，重跑不會重複插入。時間一律用注入的
 * {@link Clock}，測試能以可調時鐘直接把「現在」設到班次結束時刻。
 */
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
            LocalTime shiftEnd = resolveShiftEnd(String.valueOf(row.get("shift")), shiftTimes);
            if (shiftEnd == null || now.isBefore(shiftEnd)) {
                continue;
            }
            UUID staffId = (UUID) row.get("staff_id");
            String dept = String.valueOf(row.get("dept"));
            requeueNotStarted(tenantId, staffId, dept);
            notifyManagersOfInterrupted(tenantId, staffId);
        }
    }

    /**
     * 待開始與本人延後的單退回待接池：條件式 UPDATE 整批轉 pool、清負責人與停住資訊，
     * 每一筆記一則 returned_to_pool 事件；退了幾單推播告知員工本人（0 單就不吵人）。
     */
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
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("recipientStaffId", staffId.toString());
        payload.put("event", "shift_end_returned");
        payload.put("title", returned.size() + " 單還沒開始，已退回待接池交給下一班；進行中的單仍由您完成");
        ObjectNode target = payload.putObject("target");
        target.put("kind", "pool");
        target.put("dept", dept);
        pushOutboxWriter.enqueue(tenantId, "staff:" + staffId, payload,
                "shift-end-return:" + LocalDate.ofInstant(clock.instant(), clock.getZone()) + ":" + staffId);
    }

    /** 已中斷的單不動（負責人、狀態都保留），逐單推播主管請他決定是否附交代退池。 */
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
        List<UUID> managers = jdbcTemplate.queryForList(
                "SELECT id FROM staff_accounts WHERE tenant_id = ? AND role = 'manager' AND active = true",
                UUID.class, tenantId);
        LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());
        for (Map<String, Object> row : interrupted) {
            UUID taskId = (UUID) row.get("id");
            Object roomNo = row.get("room_no");
            String label = roomNo != null && !String.valueOf(roomNo).isBlank()
                    ? String.valueOf(roomNo)
                    : String.valueOf(row.get("title"));
            for (UUID manager : managers) {
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("recipientStaffId", manager.toString());
                payload.put("event", "shift_end_interrupted");
                payload.put("title", label + " 班末仍中斷，需要附交代退池");
                ObjectNode target = payload.putObject("target");
                target.put("kind", "task");
                target.put("taskId", taskId.toString());
                pushOutboxWriter.enqueue(tenantId, "task:" + taskId, payload,
                        "shift-end-interrupted:" + taskId + ":" + today + ":" + manager);
            }
        }
    }

    /**
     * 回 null 代表這位員工這輪不掃——寧可漏掃一輪，也不能在解析不出班次結束時刻時
     * 猜一個，把還在上班的人的單退走。
     */
    private LocalTime resolveShiftEnd(String shift, JsonNode shiftTimes) {
        try {
            return LocalTime.parse(shift);
        } catch (DateTimeParseException notHHmm) {
            if (shiftTimes == null || !shiftTimes.isObject()) {
                return null;
            }
            JsonNode window = shiftTimes.get(shift);
            if (window == null || !window.isObject() || !window.path("end").isTextual()) {
                return null;
            }
            try {
                return LocalTime.parse(window.get("end").asText());
            } catch (DateTimeParseException badConfig) {
                return null;
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
