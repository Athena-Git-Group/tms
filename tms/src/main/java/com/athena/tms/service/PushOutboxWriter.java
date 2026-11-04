package com.athena.tms.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 推播訊息寫進 outbox（kind=push）的共用入口，保證同一把冪等鍵的推播最多
 * 只寫入一列。寫入帶 ON CONFLICT DO NOTHING，冪等鍵撞到既有列時整句略過；
 * 沒有這層，週期掃描重跑或請求重試會對同一個收件人重複插入、重複推播。
 *
 * <p>payload 由呼叫端組好（形狀對應 contracts shared.yaml 的 PushPayload），
 * 這裡原樣寫入，不另外包一層信封。呼叫端若在交易內，寫入也在同一個交易裡，
 * 業務改動成立但推播沒寫進去的狀態不會出現。時間用注入的 {@link Clock}，
 * 和各呼叫端共用同一個時間來源。
 */
@Component
public class PushOutboxWriter {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public PushOutboxWriter(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public void enqueue(long tenantId, String aggregateKey, ObjectNode payload, String idempotencyKey) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox (tenant_id, aggregate_key, kind, payload, idempotency_key, status, attempts, created_at)
                VALUES (?, ?, 'push', ?::jsonb, ?, 'pending', 0, ?)
                ON CONFLICT (tenant_id, kind, idempotency_key) DO NOTHING
                """,
                tenantId, aggregateKey, payload.toString(), idempotencyKey, OffsetDateTime.now(clock));
    }

    /**
     * 給各通知呼叫端組 PushPayload 的共用開頭：收件人、事件、標題三個欄位，
     * 欄位順序照 contracts shared.yaml 的 PushPayload 定義。body 因通知而異，
     * 由呼叫端自行補上；target 用 {@link #targetTask}、{@link #targetPool}、
     * {@link #targetHome} 三個共用組裝補上。
     */
    public static ObjectNode payload(ObjectMapper objectMapper, UUID recipientStaffId, String event, String title) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("recipientStaffId", recipientStaffId.toString());
        payload.put("event", event);
        payload.put("title", title);
        return payload;
    }

    /** 在 payload 補上「點開直達這張任務」的去處（target.kind=task）。 */
    public static void targetTask(ObjectNode payload, UUID taskId) {
        ObjectNode target = payload.putObject("target");
        target.put("kind", "task");
        target.put("taskId", taskId.toString());
    }

    /** 在 payload 補上「點開到部門待接池」的去處（target.kind=pool）。 */
    public static void targetPool(ObjectNode payload, String dept) {
        ObjectNode target = payload.putObject("target");
        target.put("kind", "pool");
        target.put("dept", dept);
    }

    /** 同 {@link #targetPool(ObjectNode, String)}，另外標亮指定的那張單。 */
    public static void targetPool(ObjectNode payload, String dept, UUID highlightTaskId) {
        ObjectNode target = payload.putObject("target");
        target.put("kind", "pool");
        target.put("dept", dept);
        target.put("highlightTaskId", highlightTaskId.toString());
    }

    /** 在 payload 補上「點開只回首頁」的去處（target.kind=home）。 */
    public static void targetHome(ObjectNode payload) {
        payload.putObject("target").put("kind", "home");
    }

    /**
     * 給「通知全體主管」類推播查收件人：回傳該租戶所有在職主管。這類通知不只
     * 一處在送，收件人的認定集中在這裡，之後若改變「誰算主管」的規則（例如
     * 改成只通知同部門主管），改一處就能讓所有通知一起生效，不會出現同一件事
     * 有的主管收得到、有的收不到。
     */
    public List<UUID> activeManagerIds(long tenantId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM staff_accounts WHERE tenant_id = ? AND role = 'manager' AND active = true",
                UUID.class, tenantId);
    }

    /**
     * 給「通知整個部門」類推播查收件人：回傳該部門所有在職成員。同 {@link #activeManagerIds}，
     * 收件人的認定集中在這裡，不同事件（新需求進池、住客撤回、未接提醒）各自呼叫這裡，
     * 不會各自維護一份查詢而彼此跑掉。
     */
    public List<UUID> activeDeptStaffIds(long tenantId, String dept) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND active = true",
                UUID.class, tenantId, dept);
    }
}
