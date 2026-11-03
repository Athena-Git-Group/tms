/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 *  org.springframework.beans.factory.annotation.Value
 *  org.springframework.boot.convert.DurationStyle
 *  org.springframework.jdbc.core.JdbcTemplate
 *  org.springframework.jdbc.core.RowMapper
 *  org.springframework.kafka.core.KafkaTemplate
 *  org.springframework.scheduling.annotation.Scheduled
 *  org.springframework.stereotype.Component
 */
package com.athena.tms.service;

import com.athena.tms.config.KafkaTopicsProperties;
import com.athena.tms.service.TenantScopedExecutor;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
    private static final RowMapper<PendingMessage> PENDING_MAPPER = (rs, rowNum) -> new PendingMessage(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("aggregate_key"), rs.getString("kind"), rs.getString("payload"), rs.getInt("attempts"), rs.getObject("next_retry_at", OffsetDateTime.class));
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
            throw new IllegalStateException("tms.outbox.relay.send-timeout\uff08" + String.valueOf(this.sendTimeout) + "\uff09\u5fc5\u9808\u5927\u65bc\u7b49\u65bc producer \u7684 delivery.timeout.ms\uff08" + producerDeliveryTimeoutMs + "ms\uff09\u3002send-timeout \u6bd4\u5b83\u77ed\uff0c\u6211\u5011\u6703\u5728 producer \u771f\u6b63\u653e\u68c4\u4e4b\u524d\u5c31\u5148\u8a8d\u8f38\u3001\u628a\u8a0a\u606f\u6392\u53bb\u91cd\u8a66\uff0c\u4f46 producer \u53ef\u80fd\u4e4b\u5f8c\u9084\u662f\u628a\u5b83\u9001\u5230\u4e86 Kafka\uff0c\u7559\u4e0b\u4e00\u7b46\u6211\u5011\u81ea\u5df1\u4e0d\u77e5\u9053\u7684\u91cd\u8907\u9001\u51fa\u3002");
        }
        Duration worstCaseSend = Duration.ofMillis(producerMaxBlockMs).plus(this.sendTimeout);
        if (this.claimLease.compareTo(worstCaseSend) <= 0) {
            throw new IllegalStateException("tms.outbox.relay.claim-lease\uff08" + String.valueOf(this.claimLease) + "\uff09\u5fc5\u9808\u5927\u65bc producer max.block.ms + relay send-timeout \u7684\u7e3d\u548c\uff08\u76ee\u524d\u662f " + String.valueOf(worstCaseSend) + "\uff09\u3002\u79df\u7d04\u6490\u4e0d\u904e\u55ae\u7b46\u8a0a\u606f\u6700\u9577\u53ef\u80fd\u5361\u4f4f\u7684\u6642\u9593\uff0c\u6d3b\u8457\u7684 instance \u6703\u88ab\u8aa4\u5224\u6210\u7576\u6389\u3001\u540c\u4e00\u7b46\u88ab\u5225\u7684 instance \u6436\u8d70\u91cd\u9001\uff0c\u7834\u58de\u540c aggregate_key \u7684\u9001\u51fa\u9806\u5e8f\u3002");
        }
    }

    @Scheduled(fixedDelayString="${tms.outbox.relay.poll-interval:2s}")
    public void poll() {
        for (long tenantId : this.tenantScopedExecutor.tenantIds()) {
            try {
                this.relayTenant(tenantId);
            }
            catch (Exception e) {
                log.error("\u79df\u6236 {} \u9019\u8f2a relay \u51fa\u932f\uff0c\u8df3\u904e\u3001\u4e0b\u4e00\u8f2a\u518d\u8a66: {}", new Object[]{tenantId, e.getMessage(), e});
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
                    log.error("outbox {} \u91cd\u8a66\u6b21\u6578\u7528\u76e1\uff0c\u6a19 failed \u7b49\u4eba\u5de5\u8655\u7406\uff08aggregate_key={}\uff09", (Object)message.id(), (Object)message.aggregateKey());
                    continue block0;
                }
                if (claim.claimed() && this.sendOne(tenantId, message, claim.attempts())) continue;
                continue block0;
            }
        }
    }

    private List<PendingMessage> fetchPending(long tenantId) {
        return this.tenantScopedExecutor.callAsTenant(tenantId, () -> this.jdbcTemplate.query("SELECT o.id, o.tenant_id, o.aggregate_key, o.kind, o.payload, o.attempts, o.next_retry_at\n  FROM outbox o\n WHERE o.status = 'pending'\n   AND NOT EXISTS (\n       SELECT 1 FROM outbox f\n        WHERE f.aggregate_key = o.aggregate_key AND f.status = 'failed'\n   )\n ORDER BY o.aggregate_key, o.id\n", PENDING_MAPPER));
    }

    private ClaimResult claim(long tenantId, PendingMessage message) {
        double leaseSeconds = (double)this.claimLease.toMillis() / 1000.0;
        List rows = this.tenantScopedExecutor.callAsTenant(tenantId, () -> this.jdbcTemplate.query("UPDATE outbox\n   SET attempts = CASE WHEN attempts >= ? THEN attempts ELSE attempts + 1 END,\n       next_retry_at = CASE WHEN attempts >= ? THEN next_retry_at\n                            ELSE now() + make_interval(secs => ?) END,\n       status = CASE WHEN attempts >= ? THEN 'failed' ELSE status END\n WHERE id = ?\n   AND status = 'pending'\n   AND (next_retry_at IS NULL OR next_retry_at <= now())\nRETURNING attempts, status\n", CLAIM_MAPPER, new Object[]{this.maxAttempts, this.maxAttempts, leaseSeconds, this.maxAttempts, message.id()}));
        return rows.isEmpty() ? ClaimResult.notClaimed() : (ClaimResult)rows.get(0);
    }

    private boolean sendOne(long tenantId, PendingMessage message, int attempts) {
        try {
            this.kafkaTemplate.send(this.topics.topicFor(message.kind()), message.aggregateKey(), message.payload()).get(this.sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Duration retryDelay = this.backoff(attempts);
            double delaySeconds = (double)retryDelay.toMillis() / 1000.0;
            this.tenantScopedExecutor.runAsTenant(tenantId, () -> this.jdbcTemplate.update("UPDATE outbox SET next_retry_at = now() + make_interval(secs => ?) WHERE id = ?", new Object[]{delaySeconds, message.id()}));
            log.warn("outbox {} \u9001\u51fa\u5931\u6557\uff08\u7b2c {} \u6b21\uff09\uff0c{} \u5f8c\u91cd\u8a66: {}", new Object[]{message.id(), attempts, retryDelay, e.getMessage()});
            return false;
        }
        try {
            this.tenantScopedExecutor.runAsTenant(tenantId, () -> this.jdbcTemplate.update("UPDATE outbox SET status = 'sent', sent_at = now() WHERE id = ?", new Object[]{message.id()}));
        }
        catch (Exception e) {
            log.error("outbox {} \u5df2\u7d93\u9001\u9054 Kafka\uff0c\u4f46\u6a19\u8a18 sent \u5931\u6557\uff1b\u7dad\u6301 pending\uff0c\u79df\u7d04\u5230\u671f\u5f8c\u6703\u81ea\u52d5\u91cd\u9001\u3001\u7531\u4e0b\u6e38\u53bb\u91cd\u6536\u5c3e: {}", new Object[]{message.id(), e.getMessage(), e});
        }
        return true;
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

    private record PendingMessage(long id, long tenantId, String aggregateKey, String kind, String payload, int attempts, OffsetDateTime nextRetryAt) {
    }

    private record ClaimResult(boolean claimed, boolean exhausted, int attempts) {
        static ClaimResult notClaimed() {
            return new ClaimResult(false, false, -1);
        }
    }
}
