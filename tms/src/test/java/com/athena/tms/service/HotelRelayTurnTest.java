package com.athena.tms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.athena.tms.PostgresSpringTest;
import com.athena.tms.SharedContainers;
import com.athena.tms.service.HotelRunQueue.TurnOutcome;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * 直接跑一家飯店的一輪送出，驗證認領 SQL 的隊伍規則。應用帳號連線，RLS 生效。
 * 定時補漏拉長到 1 小時，背景 relay 不會插手這裡用 SQL 直接寫入的列。
 */
@SpringBootTest(properties = {"tms.outbox.relay.poll-interval=1h", "tms.outbox.relay.scan-window=100"})
@ActiveProfiles("test")
class HotelRelayTurnTest {

    private static final String TASK_EVENTS_TOPIC = "relayturn.tms.task-events";
    private static final String PUSH_TOPIC = "relayturn.tms.push";

    static {
        SharedContainers.createTopics(TASK_EVENTS_TOPIC, PUSH_TOPIC, PUSH_TOPIC + ".DLT");
    }

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        PostgresSpringTest.wireDatabase(registry, "relayturn");
        registry.add("spring.kafka.bootstrap-servers", SharedContainers.kafka()::getBootstrapServers);
        registry.add("tms.kafka.topic.task-events", () -> TASK_EVENTS_TOPIC);
        registry.add("tms.kafka.topic.push", () -> PUSH_TOPIC);
    }

    @Autowired
    private HotelRelayTurn turn;

    @Autowired
    private ObjectMapper objectMapper;

    private final JdbcTemplate admin = adminJdbc();
    private final String runId = "turn-" + UUID.randomUUID();

    @BeforeEach
    void clearUnsentOutbox() {
        admin.update("DELETE FROM outbox WHERE status <> 'sent'");
    }

    @Test
    void twoRelaysDrainingTheSameHotelSendEveryRowOnceInLaneOrder() throws Exception {
        seed(1L, "lane", 300, 3);

        ExecutorService relays = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> running = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                running.add(relays.submit(() -> {
                    while (turn.run(1L, () -> true) != TurnOutcome.DRAINED) {
                        Thread.onSpinWait();
                    }
                }));
            }
            for (Future<?> relay : running) {
                relay.get();
            }
        } finally {
            relays.shutdownNow();
        }

        assertThat(admin.queryForObject("SELECT count(*) FROM outbox WHERE aggregate_key LIKE ? AND status <> 'sent'",
                Long.class, runId + ":%")).isZero();
        assertThat(admin.queryForObject("SELECT max(attempts) FROM outbox WHERE aggregate_key LIKE ?",
                Integer.class, runId + ":%")).as("沒有一筆被兩個 relay 重複認領").isEqualTo(1);
        Map<String, List<Long>> byLane = readByLane(300);
        assertThat(byLane.values().stream().mapToInt(List::size).sum()).isEqualTo(300);
        byLane.forEach((lane, sequence) -> assertThat(sequence).as("隊伍 %s 的順序", lane).isSorted().doesNotHaveDuplicates());
    }

    @Test
    void aRowOutOfAttemptsIsMarkedFailedAndStopsOnlyItsOwnLane() {
        insert(1L, "stuck", 0, 8);
        insert(1L, "stuck", 1, 0);
        insert(1L, "healthy", 0, 0);

        assertThat(turn.run(1L, () -> true)).isEqualTo(TurnOutcome.DRAINED);

        assertThat(statusAndAttempts("stuck")).containsExactly("failed/8", "pending/0");
        assertThat(statusAndAttempts("healthy")).containsExactly("sent/1");
    }

    @Test
    void aLongStoppedLaneDoesNotHideNewerRowsBeyondTheScanWindow() {
        insert(1L, "stopped-lane-0", 0, 8);
        admin.update("UPDATE outbox SET status = 'failed' WHERE aggregate_key = ?", runId + ":stopped-lane-0");
        seed(1L, "stopped", 3000, 1);
        insert(1L, "fresh", 0, 0);

        turn.run(1L, () -> true);

        assertThat(statusAndAttempts("fresh")).containsExactly("sent/1");
        assertThat(admin.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate_key = ? AND status = 'pending' AND attempts = 0",
                Long.class, runId + ":stopped-lane-0")).isEqualTo(3000);
    }

    @Test
    void aTurnNeverTouchesAnotherHotelsRows() {
        insert(1L, "mine", 0, 0);
        insert(2L, "theirs", 0, 0);

        turn.run(1L, () -> true);

        assertThat(statusAndAttempts("mine")).containsExactly("sent/1");
        assertThat(statusAndAttempts("theirs")).containsExactly("pending/0");
    }

    @Test
    void oneTurnSendsAtMostOneBatchAndReportsThatMoreIsLeft() {
        seed(1L, "wide", 500, 50);

        TurnOutcome outcome = turn.run(1L, () -> true);

        long sent = admin.queryForObject("SELECT count(*) FROM outbox WHERE aggregate_key LIKE ? AND status = 'sent'",
                Long.class, runId + ":%");
        assertThat(outcome).isEqualTo(TurnOutcome.MORE);
        assertThat(sent).isPositive().isLessThanOrEqualTo(100);
    }

    private void seed(long tenantId, String prefix, int rows, int lanes) {
        admin.update("""
                INSERT INTO outbox (tenant_id, aggregate_key, kind, payload, idempotency_key, status, attempts, created_at)
                SELECT ?, ? || ':' || ? || '-lane-' || (g % ?), 'task_event', jsonb_build_object('n', g),
                       ? || ':' || ? || ':' || g, 'pending', 0, now()
                  FROM generate_series(1, ?) g
                """, tenantId, runId, prefix, lanes, runId, prefix, rows);
    }

    private void insert(long tenantId, String lane, int n, int attempts) {
        admin.update("""
                INSERT INTO outbox (tenant_id, aggregate_key, kind, payload, idempotency_key, status, attempts, created_at)
                VALUES (?, ?, 'task_event', jsonb_build_object('n', ?::int), ?, 'pending', ?, now())
                """, tenantId, runId + ":" + lane, n, runId + ":" + lane + ":" + n, attempts);
    }

    private List<String> statusAndAttempts(String lane) {
        return admin.queryForList("SELECT status || '/' || attempts FROM outbox WHERE aggregate_key = ? ORDER BY id",
                String.class, runId + ":" + lane);
    }

    private Map<String, List<Long>> readByLane(int expected) {
        Map<String, List<Long>> byLane = new HashMap<>();
        int found = 0;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                "bootstrap.servers", SharedContainers.kafka().getBootstrapServers(),
                "group.id", "relay-turn-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest",
                "key.deserializer", StringDeserializer.class,
                "value.deserializer", StringDeserializer.class))) {
            consumer.subscribe(List.of(TASK_EVENTS_TOPIC));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (System.nanoTime() < deadline && found < expected) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    if (record.key() != null && record.key().startsWith(runId)) {
                        byLane.computeIfAbsent(record.key(), k -> new ArrayList<>())
                                .add(objectMapper.readTree(record.value()).get("n").asLong());
                        found++;
                    }
                }
            }
            consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                if (record.key() != null && record.key().startsWith(runId)) {
                    byLane.computeIfAbsent(record.key(), k -> new ArrayList<>())
                            .add(objectMapper.readTree(record.value()).get("n").asLong());
                }
            });
        }
        return byLane;
    }

    private static JdbcTemplate adminJdbc() {
        var postgres = SharedContainers.postgres();
        return new JdbcTemplate(new DriverManagerDataSource(
                SharedContainers.database("relayturn"), postgres.getUsername(), postgres.getPassword()));
    }
}
