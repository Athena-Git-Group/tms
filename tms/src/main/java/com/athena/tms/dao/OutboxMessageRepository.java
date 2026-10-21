package com.athena.tms.dao;

import com.athena.tms.entity.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    /**
     * relay 輪詢：idx_outbox_pending_by_key（partial WHERE status='pending'）。
     * 刻意不帶 tenant——relay 是跨租戶的背景搬運工，串行邊界是 aggregate_key
     * 不是租戶；排序 (aggregate_key, id) 讓同鍵事件天然相鄰、依序送出。
     */
    List<OutboxMessage> findByStatusOrderByAggregateKeyAscIdAsc(String status);

    /**
     * uq_outbox_idempotency 撞鍵時 DB 只會丟 23505；relay/補償邏輯要能先問
     * 「這封已經在匣子裡了嗎」而不是靠接例外分流。
     */
    Optional<OutboxMessage> findByTenantIdAndKindAndIdempotencyKey(
            Long tenantId, String kind, String idempotencyKey);

    /** 同鍵串行判定：同 aggregate_key 還有更早未送出的列就不准送後續。 */
    boolean existsByAggregateKeyAndStatusAndIdLessThan(String aggregateKey, String status, Long id);
}
