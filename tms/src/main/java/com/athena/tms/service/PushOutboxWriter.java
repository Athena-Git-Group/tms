package com.athena.tms.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
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
}
