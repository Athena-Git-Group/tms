package com.athena.tms.service;

import com.athena.tms.common.OutboxKafkaHeaders;
import com.athena.tms.config.KafkaTopicsProperties;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
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

/**
 * Outbox relay：定期把 outbox 資料表裡 pending 的訊息送上 Kafka。
 *
 * <h2>這個類別要保證什麼</h2>
 * <ol>
 *   <li><b>同一個 aggregate_key 的訊息，送達 Kafka 的順序和寫入順序一致。</b>
 *       前一筆還沒確認送達，後面的一律不送。前一筆送失敗、正在等退避重試、
 *       或被別的 instance 認領中，都算還沒送達。順序顛倒是不可逆的，延遲可以等，
 *       所以一律寧可慢、不可亂。</li>
 *   <li><b>一個租戶或一個鍵出問題，不拖慢其他租戶和其他鍵。</b>
 *       某個租戶這輪拋例外只跳過它自己，某個鍵送失敗只停那個鍵。</li>
 *   <li><b>多個 instance 同時跑，同一筆訊息不會被兩邊同時送。</b></li>
 * </ol>
 *
 * <h2>怎麼做到</h2>
 *
 * <h3>1. 每輪每個租戶撈固定筆數，由舊到新</h3>
 * 依 id 排序，等最久的訊息先送，各個鍵輪流分到名額。筆數上限讓積壓很深時一輪的工作量有上限，
 * 剩下的下一輪接著送。撈的時候就排除送不出去的鍵：有一筆 failed 的鍵，以及前面有一筆
 * 還在等重試或租約的鍵。不排除的話，這些列會佔滿筆數上限，能送的鍵反而分不到名額。
 *
 * <h3>2. 分波送出，每一波每個鍵只送一筆</h3>
 * 第 k 波送每個鍵的第 k 筆。一波之內，先用一句 UPDATE 認領全部，再全部送出、一起等 Kafka 確認；
 * 成功的用一句 UPDATE 標 sent，失敗的各自排退避，並退出後面的波次。同一個鍵的下一筆
 * 一定在前一筆確認送達後才送，所以順序保證成立。
 * <p>
 * 為什麼合適：逐筆送出時每一筆都要等一次 Kafka 確認，吞吐量被一次往返時間卡死，
 * 而且所有租戶共用這一條隊伍。分波之後，同一波裡不同鍵的訊息共用同一次等待。
 *
 * <h3>3. 租戶之間平行</h3>
 * 每一輪把各租戶分給固定大小的執行緒池，全部做完這一輪才結束，所以輪與輪不會重疊。
 * 一個租戶的訊息再多、Kafka 對它再慢，也只佔一條執行緒。
 *
 * <h3>4. 認領就是租約</h3>
 * 認領時 attempts 加一、next_retry_at 設成租約到期時間，租約內別的 instance 認領不到；
 * 前提條件寫在 UPDATE 的 WHERE 裡，條件不符就一列也不改。送失敗改成退避時間，
 * 次數用盡標 failed，整個鍵停送等人工處理。
 * <p>
 * 租約必須撐過一整波最久可能花的時間，否則活著的 instance 會被當成已當掉，
 * 同一筆被別的 instance 搶去重送。送出階段開始後超過 max.block.ms 就不再送新的，
 * 剩下的當作送失敗排重試；最後送出的那一筆本身可能再卡 max.block.ms，之後最多等 send-timeout。
 * 所以要求租約大於 2 × max.block.ms + send-timeout。
 */
@Component
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final RowMapper<PendingMessage> PENDING_MAPPER = (rs, rowNum) -> new PendingMessage(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("aggregate_key"), rs.getString("kind"), rs.getString("payload"), rs.getString("idempotency_key"));
    private static final RowMapper<Claim> CLAIM_MAPPER = (rs, rowNum) -> new Claim(rs.getLong("id"), rs.getInt("attempts"), "failed".equals(rs.getString("status")));
    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicsProperties topics;
    private final TenantScopedExecutor tenantScopedExecutor;
    private final int maxAttempts;
    private final Duration backoffBase;
    private final Duration sendTimeout;
    private final Duration claimLease;
    private final Duration producerMaxBlock;
    private final int batchSize;
    private final ExecutorService tenantPool;

    public OutboxRelay(JdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate, KafkaTopicsProperties topics, TenantScopedExecutor tenantScopedExecutor, @Value(value="${tms.outbox.relay.max-attempts:8}") int maxAttempts, @Value(value="${tms.outbox.relay.backoff-base:1s}") String backoffBase, @Value(value="${tms.outbox.relay.send-timeout:10s}") String sendTimeout, @Value(value="${tms.outbox.relay.claim-lease:60s}") String claimLease, @Value(value="${tms.outbox.relay.batch-size:500}") int batchSize, @Value(value="${tms.outbox.relay.tenant-parallelism:8}") int tenantParallelism, @Value(value="${spring.kafka.producer.properties.max.block.ms:5000}") long producerMaxBlockMs, @Value(value="${spring.kafka.producer.properties.delivery.timeout.ms:120000}") long producerDeliveryTimeoutMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.tenantScopedExecutor = tenantScopedExecutor;
        this.maxAttempts = maxAttempts;
        this.backoffBase = DurationStyle.detectAndParse((String)backoffBase);
        this.sendTimeout = DurationStyle.detectAndParse((String)sendTimeout);
        this.claimLease = DurationStyle.detectAndParse((String)claimLease);
        this.producerMaxBlock = Duration.ofMillis(producerMaxBlockMs);
        this.batchSize = batchSize;
        if (this.sendTimeout.toMillis() < producerDeliveryTimeoutMs) {
            throw new IllegalStateException("tms.outbox.relay.send-timeout（" + String.valueOf(this.sendTimeout) + "）必須大於等於 producer 的 delivery.timeout.ms（" + producerDeliveryTimeoutMs + "ms）。send-timeout 比它短，我們會在 producer 真正放棄之前就先認輸、把訊息排去重試，但 producer 可能之後還是把它送到了 Kafka，留下一筆我們自己不知道的重複送出。");
        }
        Duration worstCaseWave = this.producerMaxBlock.multipliedBy(2L).plus(this.sendTimeout);
        if (this.claimLease.compareTo(worstCaseWave) <= 0) {
            throw new IllegalStateException("tms.outbox.relay.claim-lease（" + String.valueOf(this.claimLease) + "）必須大於 2 × producer max.block.ms + relay send-timeout（目前是 " + String.valueOf(worstCaseWave) + "）。租約撐不過一波訊息最長可能卡住的時間，活著的 instance 會被誤判成當掉、同一筆被別的 instance 搶走重送，破壞同 aggregate_key 的送出順序。");
        }
        this.tenantPool = Executors.newFixedThreadPool(tenantParallelism, Thread.ofPlatform().name("outbox-relay-", 0L).factory());
    }

    @Scheduled(fixedDelayString="${tms.outbox.relay.poll-interval:2s}")
    public void poll() {
        Map<Long, Future<?>> rounds = new LinkedHashMap<>();
        for (long tenantId : this.tenantScopedExecutor.tenantIds()) {
            rounds.put(tenantId, this.tenantPool.submit(() -> this.relayTenant(tenantId)));
        }
        for (Map.Entry<Long, Future<?>> round : rounds.entrySet()) {
            try {
                round.getValue().get();
            }
            catch (ExecutionException e) {
                log.error("租戶 {} 這輪 relay 出錯，跳過、下一輪再試: {}", new Object[]{round.getKey(), e.getCause().getMessage(), e.getCause()});
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @PreDestroy
    void shutdown() {
        this.tenantPool.shutdownNow();
    }

    private void relayTenant(long tenantId) {
        List<Deque<PendingMessage>> liveKeys = OutboxRelay.groupByKeyKeepingOrder(this.fetchPending(tenantId));
        while (!liveKeys.isEmpty()) {
            liveKeys = this.relayWave(tenantId, liveKeys);
        }
    }

    /** 送出每個鍵排在最前面的那一筆，回傳下一波還能繼續送的鍵。 */
    private List<Deque<PendingMessage>> relayWave(long tenantId, List<Deque<PendingMessage>> liveKeys) {
        Map<Long, Claim> claims = this.claim(tenantId, liveKeys.stream().map(key -> key.peekFirst().id()).toList());
        List<InFlight> inFlight = new ArrayList<>();
        long sendPhaseStarted = System.nanoTime();
        for (Deque<PendingMessage> key : liveKeys) {
            PendingMessage message = key.peekFirst();
            Claim claim = claims.get(message.id());
            if (claim == null) {
                // 沒認領到：別的 instance 正在送，或租約、退避還沒到期。整鍵等下一輪。
                continue;
            }
            if (claim.exhausted()) {
                log.error("outbox {} 重試次數用盡，標 failed 等人工處理（aggregate_key={}）", (Object)message.id(), (Object)message.aggregateKey());
                continue;
            }
            boolean withinBlockBudget = System.nanoTime() - sendPhaseStarted < this.producerMaxBlock.toNanos();
            CompletableFuture<?> ack = withinBlockBudget ? this.send(message) : CompletableFuture.failedFuture(new TimeoutException("這一波的送出階段已超過 max.block.ms，未送出"));
            inFlight.add(new InFlight(key, message, claim.attempts(), ack));
        }
        long ackDeadline = System.nanoTime() + this.sendTimeout.toNanos();
        List<Long> sentIds = new ArrayList<>();
        List<Deque<PendingMessage>> nextWave = new ArrayList<>();
        for (InFlight sending : inFlight) {
            try {
                sending.ack().get(Math.max(0L, ackDeadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            }
            catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                this.scheduleRetry(tenantId, sending.message(), sending.attempts(), e);
                continue;
            }
            sentIds.add(sending.message().id());
            sending.key().pollFirst();
            if (!sending.key().isEmpty()) {
                nextWave.add(sending.key());
            }
        }
        // 標記失敗時這一波的訊息仍是 pending，租約到期後會重送；若繼續送同鍵的下一筆，
        // 重送的那一筆就會排到它後面，所以這一輪到此為止。
        return this.markSent(tenantId, sentIds) ? nextWave : List.of();
    }

    private List<PendingMessage> fetchPending(long tenantId) {
        return this.tenantScopedExecutor.callAsTenant(tenantId, () -> this.jdbcTemplate.query("SELECT o.id, o.tenant_id, o.aggregate_key, o.kind, o.payload, o.idempotency_key\n  FROM outbox o\n WHERE o.status = 'pending'\n   AND NOT EXISTS (\n       SELECT 1 FROM outbox f\n        WHERE f.aggregate_key = o.aggregate_key AND f.status = 'failed'\n   )\n   AND NOT EXISTS (\n       SELECT 1 FROM outbox w\n        WHERE w.aggregate_key = o.aggregate_key AND w.status = 'pending'\n          AND w.id <= o.id AND w.next_retry_at > now()\n   )\n ORDER BY o.id\n LIMIT ?\n", PENDING_MAPPER, new Object[]{this.batchSize}));
    }

    private Map<Long, Claim> claim(long tenantId, List<Long> ids) {
        double leaseSeconds = (double)this.claimLease.toMillis() / 1000.0;
        List<Claim> rows = this.tenantScopedExecutor.callAsTenant(tenantId, () -> this.jdbcTemplate.query("UPDATE outbox\n   SET attempts = CASE WHEN attempts >= ? THEN attempts ELSE attempts + 1 END,\n       next_retry_at = CASE WHEN attempts >= ? THEN next_retry_at\n                            ELSE now() + make_interval(secs => ?) END,\n       status = CASE WHEN attempts >= ? THEN 'failed' ELSE status END\n WHERE id = ANY(?::bigint[])\n   AND status = 'pending'\n   AND (next_retry_at IS NULL OR next_retry_at <= now())\nRETURNING id, attempts, status\n", CLAIM_MAPPER, new Object[]{this.maxAttempts, this.maxAttempts, leaseSeconds, this.maxAttempts, ids.toArray(Long[]::new)}));
        return rows.stream().collect(Collectors.toMap(Claim::id, Function.identity()));
    }

    private CompletableFuture<?> send(PendingMessage message) {
        try {
            return this.kafkaTemplate.send(this.toProducerRecord(message));
        }
        catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void scheduleRetry(long tenantId, PendingMessage message, int attempts, Exception cause) {
        Duration retryDelay = this.backoff(attempts);
        double delaySeconds = (double)retryDelay.toMillis() / 1000.0;
        this.tenantScopedExecutor.runAsTenant(tenantId, () -> this.jdbcTemplate.update("UPDATE outbox SET next_retry_at = now() + make_interval(secs => ?) WHERE id = ?", new Object[]{delaySeconds, message.id()}));
        log.warn("outbox {} 送出失敗（第 {} 次），{} 後重試: {}", new Object[]{message.id(), attempts, retryDelay, cause.getMessage()});
    }

    private boolean markSent(long tenantId, List<Long> ids) {
        if (ids.isEmpty()) {
            return true;
        }
        try {
            this.tenantScopedExecutor.runAsTenant(tenantId, () -> this.jdbcTemplate.update("UPDATE outbox SET status = 'sent', sent_at = now() WHERE id = ANY(?::bigint[])", new Object[]{ids.toArray(Long[]::new)}));
            return true;
        }
        catch (Exception e) {
            log.error("outbox {} 已經送達 Kafka，但標記 sent 失敗；維持 pending，租約到期後會自動重送、由下游去重收尾: {}", new Object[]{ids, e.getMessage(), e});
            return false;
        }
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

    private static List<Deque<PendingMessage>> groupByKeyKeepingOrder(List<PendingMessage> pending) {
        LinkedHashMap<String, Deque<PendingMessage>> byKey = new LinkedHashMap<>();
        for (PendingMessage message : pending) {
            byKey.computeIfAbsent(message.aggregateKey(), key -> new ArrayDeque<>()).add(message);
        }
        return new ArrayList<>(byKey.values());
    }

    private record PendingMessage(long id, long tenantId, String aggregateKey, String kind, String payload, String idempotencyKey) {
    }

    private record Claim(long id, int attempts, boolean exhausted) {
    }

    private record InFlight(Deque<PendingMessage> key, PendingMessage message, int attempts, CompletableFuture<?> ack) {
    }
}
