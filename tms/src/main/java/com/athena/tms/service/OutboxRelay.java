package com.athena.tms.service;

import com.athena.tms.common.OutboxKafkaHeaders;
import com.athena.tms.config.KafkaTopicsProperties;
import com.athena.tms.service.TenantScopedExecutor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 把 outbox 裡 pending 的訊息送上 Kafka，並保證寫入方的交易一提交就開始送，不必等下一次輪詢。
 *
 * <p>送出全部由一條專屬執行緒負責。寫入 outbox 的交易提交後，{@link #onEnqueued} 只往容量為一的 {@link #wakeSignal}
 * 放一個訊號就返回，請求執行緒不碰資料庫也不碰 Kafka。執行緒被叫醒後跑一輪 {@link #poll}，
 * 一輪會把當下所有 pending 的訊息送完，所以送出期間湧進來的多次提交只在佇列裡留下一個訊號，合併成下一輪。
 * 如果改成一次提交跑一輪，尖峰時一百筆提交就是一百輪重複的查詢。交易回滾不會呼叫 {@link #onEnqueued}，也就不會白跑一輪。
 *
 * <p>沒有訊號時，執行緒每隔 tms.outbox.relay.poll-interval 自己醒來跑一輪。這一輪負責撿回不經 {@link OutboxWriter} 寫入的列，
 * 以及退避時間到期、該重試的訊息；少了它，這兩種訊息會一直留在 pending。
 *
 * <p>{@link #poll} 也可以從外部直接呼叫，和這條執行緒同時跑也安全。每筆訊息送出前都要先搶租約：用一句只在該列仍是 pending 且沒有別人持有租約時才成立的 UPDATE，把下次可重試時間往後推，
 * 搶不到的一方會跳過整個 aggregate_key，同鍵順序不會亂。這和多個 instance 同時跑靠的是同一套機制。
 */
@Component
public class OutboxRelay implements SmartLifecycle {
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
    private final Duration pollInterval;
    private final Counter passes;
    private final BlockingQueue<Boolean> wakeSignal = new ArrayBlockingQueue<>(1);
    private volatile Thread runner;

    public OutboxRelay(JdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate, KafkaTopicsProperties topics, TenantScopedExecutor tenantScopedExecutor, MeterRegistry meterRegistry, @Value(value="${tms.outbox.relay.poll-interval:2s}") String pollInterval, @Value(value="${tms.outbox.relay.max-attempts:8}") int maxAttempts, @Value(value="${tms.outbox.relay.backoff-base:1s}") String backoffBase, @Value(value="${tms.outbox.relay.send-timeout:10s}") String sendTimeout, @Value(value="${tms.outbox.relay.claim-lease:60s}") String claimLease, @Value(value="${spring.kafka.producer.properties.max.block.ms:5000}") long producerMaxBlockMs, @Value(value="${spring.kafka.producer.properties.delivery.timeout.ms:120000}") long producerDeliveryTimeoutMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.maxAttempts = maxAttempts;
        this.backoffBase = DurationStyle.detectAndParse((String)backoffBase);
        this.sendTimeout = DurationStyle.detectAndParse((String)sendTimeout);
        this.claimLease = DurationStyle.detectAndParse((String)claimLease);
        this.pollInterval = DurationStyle.detectAndParse(pollInterval);
        this.passes = Counter.builder("tms.outbox.relay.passes").description("relay 跑完的輪數").register(meterRegistry);
        if (this.sendTimeout.toMillis() < producerDeliveryTimeoutMs) {
            throw new IllegalStateException("tms.outbox.relay.send-timeout（" + String.valueOf(this.sendTimeout) + "）必須大於等於 producer 的 delivery.timeout.ms（" + producerDeliveryTimeoutMs + "ms）。send-timeout 比它短，我們會在 producer 真正放棄之前就先認輸、把訊息排去重試，但 producer 可能之後還是把它送到了 Kafka，留下一筆我們自己不知道的重複送出。");
        }
        Duration worstCaseSend = Duration.ofMillis(producerMaxBlockMs).plus(this.sendTimeout);
        if (this.claimLease.compareTo(worstCaseSend) <= 0) {
            throw new IllegalStateException("tms.outbox.relay.claim-lease（" + String.valueOf(this.claimLease) + "）必須大於 producer max.block.ms + relay send-timeout 的總和（目前是 " + String.valueOf(worstCaseSend) + "）。租約撐不過單筆訊息最長可能卡住的時間，活著的 instance 會被誤判成當掉、同一筆被別的 instance 搶走重送，破壞同 aggregate_key 的送出順序。");
        }
    }

    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT, fallbackExecution=true)
    public void onEnqueued(OutboxEnqueued event) {
        this.wakeSignal.offer(Boolean.TRUE);
    }

    @Override
    public void start() {
        Thread thread = new Thread(this::runUntilStopped, "outbox-relay");
        thread.setDaemon(true);
        this.runner = thread;
        thread.start();
    }

    @Override
    public void stop() {
        Thread thread = this.runner;
        this.runner = null;
        if (thread == null) {
            return;
        }
        this.wakeSignal.offer(Boolean.TRUE);
        try {
            thread.join(this.sendTimeout.toMillis());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return this.runner != null;
    }

    private void runUntilStopped() {
        while (this.runner == Thread.currentThread()) {
            try {
                this.wakeSignal.poll(this.pollInterval.toMillis(), TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e) {
                return;
            }
            if (this.runner != Thread.currentThread()) {
                return;
            }
            try {
                this.poll();
            }
            // 接住所有 Throwable：送出與輪詢補漏都在這條執行緒上，讓 Error 結束它，之後就再也沒有訊息送出，isRunning() 卻仍回 true。
            catch (Throwable e) {
                log.error("relay 這輪出錯，等下一輪再試: {}", (Object)e.getMessage(), (Object)e);
            }
        }
    }

    public void poll() {
        for (long tenantId : this.tenantScopedExecutor.tenantIds()) {
            try {
                this.relayTenant(tenantId);
            }
            catch (Exception e) {
                log.error("租戶 {} 這輪 relay 出錯，跳過、下一輪再試: {}", new Object[]{tenantId, e.getMessage(), e});
            }
        }
        this.passes.increment();
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
