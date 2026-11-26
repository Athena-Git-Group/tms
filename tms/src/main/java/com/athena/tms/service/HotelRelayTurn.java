package com.athena.tms.service;

import com.athena.tms.common.OutboxKafkaHeaders;
import com.athena.tms.config.KafkaTopicsProperties;
import com.athena.tms.service.HotelRunQueue.TurnOutcome;
import com.athena.tms.service.TenantScopedExecutor.TxLimits;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 替一家飯店把 outbox 裡 pending 的訊息送上 Kafka，送一輪就交還執行緒。
 *
 * <h2>這個類別要保證什麼</h2>
 *
 * <ol>
 *   <li><b>同一個隊伍的訊息照寫入順序送上 Kafka。</b>隊伍是 kind 加 aggregate_key。
 *       例如同一間房先後兩筆房況回寫若顛倒，PMS 會被舊狀態蓋掉，而且無法自動修正。</li>
 *   <li><b>一家飯店一輪佔用執行緒的時間有上限。</b>這樣其他飯店最多只等這一輪。</li>
 *   <li><b>多個 instance 同時替同一家飯店送也不會亂序或重複認領。</b></li>
 * </ol>
 *
 * <h2>怎麼做到</h2>
 *
 * <h3>1. 每回合只認領每個隊伍最前面那一筆</h3>
 * 一條 SQL 在同一個語句裡找出可送的隊伍頭並認領。可送的意思是：隊伍裡沒有重試用完的列，
 * 隊伍頭也沒有正在送或在等重試。認領把 next_retry_at 推到租約到期時間，這個隊伍就被自己擋住，
 * 別的執行緒或 instance 在租約內都認領不到它的下一筆。同一隊伍第 k+1 筆要等第 k 筆標成 sent 之後的回合才會被選到，
 * 順序因此只靠資料庫決定，不靠記憶體裡的狀態。
 * <p>
 * 為什麼合適：兩個認領者搶同一個隊伍頭時，後到的 UPDATE 會等前者提交，再重新檢查條件，
 * 條件已不符就一列也不改。不需要另外的跨 instance 協調。
 *
 * <h3>2. 掃描範圍與等待時間都有上限</h3>
 * 認領只看這家飯店最舊的 scan-window 筆可送列。被擋住的隊伍在限制筆數之前就整隊排除，
 * 所以一個重試用完的隊伍後面堆再多筆，也不會把範圍塞滿。認領與標記結果的交易都設了
 * lock_timeout 與 statement_timeout，一家飯店的列被別的交易鎖住時，這一輪會拋例外結束，交給排班表暫停這家飯店。
 *
 * <h3>3. 一回合的訊息一起送出，再一起等確認</h3>
 * 每回合各隊伍最多一筆，所以並行送出不影響隊伍內順序。單筆 send() 可能卡住最多 max.block.ms，
 * 送出階段超過這段時間就不再送剩下的列，把它們退回成沒認領過，不算一次嘗試。
 * 租約必須撐過最壞情況的一回合，建構時會檢查。
 *
 * <h2>已知但刻意不修的殘餘風險</h2>
 *
 * 訊息已送上 Kafka 但標記 sent 失敗時，那筆會在租約到期後重送一次。下游以冪等鍵去重，
 * 而且同隊伍的下一筆要等它標成 sent 才會送，所以重複的那筆一定排在下一筆之前。
 */
@Component
class HotelRelayTurn {

    private static final Logger log = LoggerFactory.getLogger(HotelRelayTurn.class);

    private static final String CLAIM_LANE_HEADS = """
            WITH blocked AS MATERIALIZED (
                SELECT kind, aggregate_key FROM outbox
                 WHERE tenant_id = ? AND status = 'failed'
                UNION
                SELECT kind, aggregate_key FROM outbox
                 WHERE tenant_id = ? AND status = 'pending' AND next_retry_at IS NOT NULL AND next_retry_at > now()
            ),
            scan AS (
                SELECT o.id, o.kind, o.aggregate_key
                  FROM outbox o
                 WHERE o.tenant_id = ? AND o.status = 'pending'
                   AND NOT EXISTS (SELECT 1 FROM blocked b WHERE b.kind = o.kind AND b.aggregate_key = o.aggregate_key)
                 ORDER BY o.id
                 LIMIT ?
            ),
            heads AS (
                SELECT DISTINCT ON (kind, aggregate_key) id FROM scan ORDER BY kind, aggregate_key, id
            ),
            picked AS (
                SELECT id FROM heads ORDER BY id LIMIT ?
            )
            UPDATE outbox u
               SET attempts = CASE WHEN u.attempts >= ? THEN u.attempts ELSE u.attempts + 1 END,
                   next_retry_at = CASE WHEN u.attempts >= ? THEN u.next_retry_at
                                        ELSE now() + make_interval(secs => ?) END,
                   status = CASE WHEN u.attempts >= ? THEN 'failed' ELSE u.status END
              FROM picked p
             WHERE u.id = p.id
               AND u.status = 'pending'
               AND (u.next_retry_at IS NULL OR u.next_retry_at <= now())
            RETURNING u.id, u.kind, u.aggregate_key, u.payload, u.idempotency_key, u.attempts, u.status, u.created_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicsProperties topics;
    private final TenantScopedExecutor tenants;
    private final OutboxRelayMetrics metrics;
    private final RelaySettings settings;
    private final Duration producerMaxBlock;
    private final TxLimits txLimits;

    HotelRelayTurn(JdbcTemplate jdbcTemplate, KafkaTemplate<String, String> kafkaTemplate, KafkaTopicsProperties topics,
                   TenantScopedExecutor tenants, OutboxRelayMetrics metrics, RelaySettings settings,
                   @Value("${spring.kafka.producer.properties.max.block.ms:5000}") long producerMaxBlockMs,
                   @Value("${spring.kafka.producer.properties.delivery.timeout.ms:120000}") long producerDeliveryTimeoutMs) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.tenants = tenants;
        this.metrics = metrics;
        this.settings = settings;
        this.producerMaxBlock = Duration.ofMillis(producerMaxBlockMs);
        this.txLimits = new TxLimits(settings.lockTimeout(), settings.statementTimeout());
        if (settings.sendTimeout().toMillis() < producerDeliveryTimeoutMs) {
            throw new IllegalStateException("tms.outbox.relay.send-timeout（" + settings.sendTimeout() + "）必須大於等於 producer 的 delivery.timeout.ms（" + producerDeliveryTimeoutMs + "ms）。send-timeout 比它短，我們會在 producer 真正放棄之前就先認輸、把訊息排去重試，但 producer 可能之後還是把它送到了 Kafka，留下一筆我們自己不知道的重複送出。");
        }
        Duration worstCaseRound = producerMaxBlock.multipliedBy(2).plus(settings.sendTimeout()).plus(settings.statementTimeout());
        if (settings.claimLease().compareTo(worstCaseRound) <= 0) {
            throw new IllegalStateException("tms.outbox.relay.claim-lease（" + settings.claimLease() + "）必須大於 2 倍 producer max.block.ms、relay send-timeout 與 statement-timeout 的總和（目前是 " + worstCaseRound + "）。租約撐不過一回合最長可能卡住的時間，活著的 instance 會被誤判成當掉、同一筆被別的 instance 搶走重送，破壞同隊伍的送出順序。");
        }
    }

    TurnOutcome run(long tenantId, BooleanSupplier keepGoing) {
        long deadline = System.nanoTime() + settings.turnBudget().toNanos();
        int claimedForSending = 0;
        while (keepGoing.getAsBoolean()) {
            List<Claimed> claimed = claimLaneHeads(tenantId, settings.batchSize() - claimedForSending);
            if (claimed.isEmpty()) {
                return TurnOutcome.DRAINED;
            }
            List<Claimed> toSend = new ArrayList<>();
            for (Claimed row : claimed) {
                if (row.exhausted()) {
                    log.error("outbox {} 重試次數用盡，標 failed 等人工處理（飯店 {}，aggregate_key={}）", row.id(), tenantId, row.aggregateKey());
                    metrics.exhausted(tenantId, row.kind());
                } else {
                    toSend.add(row);
                }
            }
            if (!toSend.isEmpty()) {
                record(tenantId, send(tenantId, toSend));
                claimedForSending += toSend.size();
            }
            if (claimedForSending >= settings.batchSize() || System.nanoTime() >= deadline) {
                return TurnOutcome.MORE;
            }
        }
        return TurnOutcome.MORE;
    }

    private List<Claimed> claimLaneHeads(long tenantId, int maxHeads) {
        double leaseSeconds = settings.claimLease().toMillis() / 1000.0;
        int maxAttempts = settings.maxAttempts();
        List<Claimed> claimed = tenants.callAsTenant(tenantId, txLimits, () -> jdbcTemplate.query(CLAIM_LANE_HEADS, CLAIMED,
                tenantId, tenantId, tenantId, settings.scanWindow(), maxHeads, maxAttempts, maxAttempts, leaseSeconds, maxAttempts));
        return claimed.stream().sorted(Comparator.comparingLong(Claimed::id)).toList();
    }

    private Round send(long tenantId, List<Claimed> rows) {
        long fireDeadline = System.nanoTime() + producerMaxBlock.toNanos();
        Map<Claimed, CompletableFuture<?>> inFlight = new LinkedHashMap<>();
        List<Claimed> neverSent = new ArrayList<>();
        for (Claimed row : rows) {
            if (System.nanoTime() > fireDeadline) {
                neverSent.add(row);
            } else {
                inFlight.put(row, fire(tenantId, row));
            }
        }
        long ackDeadline = System.nanoTime() + settings.sendTimeout().toNanos();
        List<Claimed> acked = new ArrayList<>();
        List<Claimed> failed = new ArrayList<>();
        for (Map.Entry<Claimed, CompletableFuture<?>> entry : inFlight.entrySet()) {
            Claimed row = entry.getKey();
            try {
                entry.getValue().get(Math.max(0, ackDeadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                acked.add(row);
                metrics.sent(tenantId, row.kind(), Duration.between(row.createdAt(), OffsetDateTime.now()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed.add(row);
            } catch (ExecutionException | TimeoutException e) {
                Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                log.warn("outbox {} 送出失敗（第 {} 次），{} 後重試: {}", row.id(), row.attempts(), backoff(row.attempts()), cause.getMessage());
                metrics.sendFailed(tenantId, row.kind());
                failed.add(row);
            }
        }
        return new Round(acked, failed, neverSent);
    }

    private CompletableFuture<?> fire(long tenantId, Claimed row) {
        try {
            return kafkaTemplate.send(toProducerRecord(tenantId, row));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void record(long tenantId, Round round) {
        tenants.callAsTenant(tenantId, txLimits, () -> {
            if (!round.acked().isEmpty()) {
                jdbcTemplate.update("UPDATE outbox SET status = 'sent', sent_at = now() WHERE id = ANY(?)",
                        ps -> ps.setArray(1, ids(ps.getConnection(), round.acked())));
            }
            for (Claimed row : round.failed()) {
                double delaySeconds = backoff(row.attempts()).toMillis() / 1000.0;
                jdbcTemplate.update("UPDATE outbox SET next_retry_at = now() + make_interval(secs => ?) WHERE id = ?",
                        delaySeconds, row.id());
            }
            if (!round.neverSent().isEmpty()) {
                jdbcTemplate.update("UPDATE outbox SET attempts = attempts - 1, next_retry_at = NULL WHERE id = ANY(?) AND status = 'pending'",
                        ps -> ps.setArray(1, ids(ps.getConnection(), round.neverSent())));
            }
            return null;
        });
    }

    private static Array ids(java.sql.Connection connection, List<Claimed> rows) throws java.sql.SQLException {
        return connection.createArrayOf("bigint", rows.stream().map(Claimed::id).toArray());
    }

    private ProducerRecord<String, String> toProducerRecord(long tenantId, Claimed row) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topics.topicFor(row.kind()), row.aggregateKey(), row.payload());
        record.headers().add(new RecordHeader(OutboxKafkaHeaders.TENANT_ID, Long.toString(tenantId).getBytes(StandardCharsets.UTF_8)));
        if (row.idempotencyKey() != null) {
            record.headers().add(new RecordHeader(OutboxKafkaHeaders.IDEMPOTENCY_KEY, row.idempotencyKey().getBytes(StandardCharsets.UTF_8)));
        }
        return record;
    }

    private Duration backoff(int attempts) {
        return settings.backoffBase().multipliedBy(1L << Math.min(attempts - 1, 10));
    }

    private static final RowMapper<Claimed> CLAIMED = (rs, rowNum) -> new Claimed(
            rs.getLong("id"), rs.getString("kind"), rs.getString("aggregate_key"), rs.getString("payload"),
            rs.getString("idempotency_key"), rs.getInt("attempts"), "failed".equals(rs.getString("status")),
            rs.getObject("created_at", OffsetDateTime.class));

    private record Claimed(long id, String kind, String aggregateKey, String payload, String idempotencyKey,
                           int attempts, boolean exhausted, OffsetDateTime createdAt) {
    }

    private record Round(List<Claimed> acked, List<Claimed> failed, List<Claimed> neverSent) {
    }
}
