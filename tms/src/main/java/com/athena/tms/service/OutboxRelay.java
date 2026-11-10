package com.athena.tms.service;

import com.athena.tms.common.OutboxKafkaHeaders;
import com.athena.tms.config.KafkaTopicsProperties;
import com.athena.tms.service.TenantScopedExecutor;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int MAX_BACKOFF_SHIFT = 10;
    private static final RowMapper<PendingMessage> PENDING_MAPPER = (rs, rowNum) -> new PendingMessage(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("aggregate_key"), rs.getString("kind"), rs.getString("payload"), rs.getString("idempotency_key"), rs.getInt("attempts"), rs.getObject("next_retry_at", OffsetDateTime.class));
    private static final RowMapper<ClaimResult> CLAIM_MAPPER = (rs, rowNum) -> {
        boolean exhausted = "failed".equals(rs.getString("status"));
        return new ClaimResult(!exhausted, exhausted, rs.getInt("attempts"));
    };
    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicsProperties topics;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final int maxAttempts;
    private final Duration backoffBase;
    private final Duration sendTimeout;
    private final Duration claimLease;

    public OutboxRelay(JdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate, KafkaTopicsProperties topics, TenantScopedExecutor tenantScopedExecutor, @Value(value="${tms.outbox.relay.max-attempts:8}") int maxAttempts, @Value(value="${tms.outbox.relay.backoff-base:1s}") String backoffBase, @Value(value="${tms.outbox.relay.send-timeout:10s}") String sendTimeout, @Value(value="${tms.outbox.relay.claim-lease:60s}") String claimLease, @Value(value="${spring.kafka.producer.properties.max.block.ms:5000}") long producerMaxBlockMs, @Value(value="${spring.kafka.producer.properties.delivery.timeout.ms:120000}") long producerDeliveryTimeoutMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.maxAttempts = maxAttempts;
        this.backoffBase = DurationStyle.detectAndParse((String)backoffBase);
        this.sendTimeout = DurationStyle.detectAndParse((String)sendTimeout);
        this.claimLease = DurationStyle.detectAndParse((String)claimLease);
        if (this.sendTimeout.toMillis() < producerDeliveryTimeoutMs) {
            throw new IllegalStateException("tms.outbox.relay.send-timeout（" + String.valueOf(this.sendTimeout) + "）必須大於等於 producer 的 delivery.timeout.ms（" + producerDeliveryTimeoutMs + "ms）。send-timeout 比它短，我們會在 producer 真正放棄之前就先認輸、把訊息排去重試，但 producer 可能之後還是把它送到了 Kafka，留下一筆我們自己不知道的重複送出。");
        }
        Duration worstCaseSend = Duration.ofMillis(producerMaxBlockMs).plus(this.sendTimeout);
        if (this.claimLease.compareTo(worstCaseSend) <= 0) {
            throw new IllegalStateException("tms.outbox.relay.claim-lease（" + String.valueOf(this.claimLease) + "）必須大於 producer max.block.ms + relay send-timeout 的總和（目前是 " + String.valueOf(worstCaseSend) + "）。租約撐不過單筆訊息最長可能卡住的時間，活著的 instance 會被誤判成當掉、同一筆被別的 instance 搶走重送，破壞同 aggregate_key 的送出順序。");
        }
    }

    @Scheduled(fixedDelayString="${tms.outbox.relay.poll-interval:2s}")
    public void poll() {
        for (long tenantId : this.tenantScopedExecutor.tenantIds()) {
            try {
                this.relayTenant(tenantId);
            }
            catch (Exception e) {
                log.error("租戶 {} 這輪 relay 出錯，跳過、下一輪再試: {}", new Object[]{tenantId, e.getMessage(), e});
            }
        }
    }

    private void relayTenant(long tenantId) {
        List<PendingMessage> pending = this.fetchPending(tenantId);
        if (pending.isEmpty()) {
            return;
        }
        block0: for (List<PendingMessage> sameKey : OutboxRelay.groupByKeyKeepingOrder(pending).values()) {
            for (PendingMessage message : sameKey) {
                OffsetDateTime now = OffsetDateTime.now();
                if (message.nextRetryAt() != null && message.nextRetryAt().isAfter(now)) continue block0;
                ClaimResult claim = this.claim(tenantId, message);
                if (claim.exhausted()) {
                    log.error("outbox {} 重試次數用盡，標 failed 等人工處理（aggregate_key={}）", (Object)message.id(), (Object)message.aggregateKey());
                    continue block0;
                }
                if (claim.claimed() && this.sendOne(tenantId, message, claim.attempts())) continue;
                continue block0;
            }
        }
    }

    private List<PendingMessage> fetchPending(long tenantId) {
        return this.tenantScopedExecutor.callAsTenant(tenantId, () -> this.jdbcTemplate.query("SELECT o.id, o.tenant_id, o.aggregate_key, o.kind, o.payload, o.idempotency_key, o.attempts, o.next_retry_at\n  FROM outbox o\n WHERE o.status = 'pending'\n   AND NOT EXISTS (\n       SELECT 1 FROM outbox f\n        WHERE f.aggregate_key = o.aggregate_key AND f.status = 'failed'\n   )\n ORDER BY o.aggregate_key, o.id\n", PENDING_MAPPER));
    }

    private ClaimResult claim(long tenantId, PendingMessage message) {
        double leaseSeconds = (double)this.claimLease.toMillis() / 1000.0;
        List rows = this.tenantScopedExecutor.callAsTenant(tenantId, () -> this.jdbcTemplate.query("UPDATE outbox\n   SET attempts = CASE WHEN attempts >= ? THEN attempts ELSE attempts + 1 END,\n       next_retry_at = CASE WHEN attempts >= ? THEN next_retry_at\n                            ELSE now() + make_interval(secs => ?) END,\n       status = CASE WHEN attempts >= ? THEN 'failed' ELSE status END\n WHERE id = ?\n   AND status = 'pending'\n   AND (next_retry_at IS NULL OR next_retry_at <= now())\nRETURNING attempts, status\n", CLAIM_MAPPER, new Object[]{this.maxAttempts, this.maxAttempts, leaseSeconds, this.maxAttempts, message.id()}));
        return rows.isEmpty() ? ClaimResult.notClaimed() : (ClaimResult)rows.get(0);
    }

    private boolean sendOne(long tenantId, PendingMessage message, int attempts) {
        try {
            this.kafkaTemplate.send(this.toProducerRecord(message)).get(this.sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Duration retryDelay = this.backoff(attempts);
            double delaySeconds = (double)retryDelay.toMillis() / 1000.0;
            this.tenantScopedExecutor.runAsTenant(tenantId, () -> this.jdbcTemplate.update("UPDATE outbox SET next_retry_at = now() + make_interval(secs => ?) WHERE id = ?", new Object[]{delaySeconds, message.id()}));
            log.warn("outbox {} 送出失敗（第 {} 次），{} 後重試: {}", new Object[]{message.id(), attempts, retryDelay, e.getMessage()});
            return false;
        }
        try {
            this.tenantScopedExecutor.runAsTenant(tenantId, () -> this.jdbcTemplate.update("UPDATE outbox SET status = 'sent', sent_at = now() WHERE id = ?", new Object[]{message.id()}));
        }
        catch (Exception e) {
            log.error("outbox {} 已經送達 Kafka，但標記 sent 失敗；維持 pending，租約到期後會自動重送、由下游去重收尾: {}", new Object[]{message.id(), e.getMessage(), e});
        }
        return true;
    }

    /**
     * key 用 aggregate_key 讓同鍵訊息落在同一個 partition；租戶 id 與冪等鍵放 header
     * （名稱見 {@link OutboxKafkaHeaders}），消費者靠它們去重與套用租戶範圍。
     * 沒有冪等鍵的訊息就不附那個 header，消費者自行決定如何處理。
     */
    private ProducerRecord<String, String> toProducerRecord(PendingMessage message) {
        ProducerRecord<String, String> record = new ProducerRecord<>(this.topics.topicFor(message.kind()), message.aggregateKey(), message.payload());
        record.headers().add(new RecordHeader(OutboxKafkaHeaders.TENANT_ID, Long.toString(message.tenantId()).getBytes(StandardCharsets.UTF_8)));
        if (message.idempotencyKey() != null) {
            record.headers().add(new RecordHeader(OutboxKafkaHeaders.IDEMPOTENCY_KEY, message.idempotencyKey().getBytes(StandardCharsets.UTF_8)));
        }
        return record;
    }

    private Duration backoff(int attempts) {
        return this.backoffBase.multipliedBy(1L << Math.min(attempts - 1, 10));
    }

    private static Map<String, List<PendingMessage>> groupByKeyKeepingOrder(List<PendingMessage> pending) {
        LinkedHashMap<String, List<PendingMessage>> byKey = new LinkedHashMap<String, List<PendingMessage>>();
        for (PendingMessage message : pending) {
            byKey.computeIfAbsent(message.aggregateKey(), key -> new ArrayList()).add(message);
        }
        return byKey;
    }

    private record PendingMessage(long id, long tenantId, String aggregateKey, String kind, String payload, String idempotencyKey, int attempts, OffsetDateTime nextRetryAt) {
    }

    private record ClaimResult(boolean claimed, boolean exhausted, int attempts) {
        static ClaimResult notClaimed() {
            return new ClaimResult(false, false, -1);
        }
    }
}
