package com.athena.tms.relayfairness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.athena.tms.SharedContainers;
import com.athena.tms.common.TenantContext;
import com.athena.tms.config.TenantMetricsConfig;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.service.TaskEngine;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.zh_tw.假設;
import io.cucumber.java.zh_tw.當;
import io.cucumber.java.zh_tw.那麼;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class RelayFairnessSteps {

    private static final Map<Long, UUID> ROOM_BY_TENANT = Map.of(
            1L, UUID.fromString("00000000-0000-7000-8000-000000000301"),
            2L, UUID.fromString("00000000-0000-7000-8000-000000020101"));
    private static final Duration KAFKA_WAIT = Duration.ofSeconds(30);

    @Autowired
    private TaskEngine taskEngine;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${tms.outbox.relay.poll-interval}")
    private String pollInterval;

    @Value("${tms.outbox.relay.workers}")
    private int workers;

    private final String runId = "fairness-" + UUID.randomUUID();
    private int backlogSize;
    private UUID hotelTwoTask;
    private long hotelTwoCommittedAt;
    private long hotelOnePendingWhenHotelTwoArrived;
    private Connection lockHolder;
    private boolean parkedByLock;
    private double hotelOneTurnsBefore;
    private double hotelTwoTurnsBefore;

    @Before
    public void clearUnsentOutbox() {
        jdbcTemplate.update("DELETE FROM outbox WHERE status <> 'sent'");
    }

    @After
    public void releaseLocks() throws SQLException {
        releaseLock();
    }

    @假設("relay 的定時輪詢間隔為 {string}，只有 {int} 個工作執行緒")
    public void relaySettings(String expectedPollInterval, int expectedWorkers) {
        assertEquals(expectedPollInterval, pollInterval, "定時補漏要拉長，才分得出訊息是被提交喚醒送出的");
        assertEquals(expectedWorkers, workers, "只開 1 個工作執行緒，才證明是輪流制在保護其他飯店");
    }

    @假設("飯店 {long} 的待送匣有 {int} 筆任務事件，分在 {int} 個隊伍")
    public void backlog(long tenantId, int rows, int lanes) {
        backlogSize = rows;
        jdbcTemplate.update("""
                INSERT INTO outbox (tenant_id, aggregate_key, kind, payload, idempotency_key, status, attempts, created_at)
                SELECT ?, ? || ':lane-' || (g % ?), 'task_event', jsonb_build_object('n', g), ? || ':' || g, 'pending', 0, now()
                  FROM generate_series(1, ?) g
                """, tenantId, runId, lanes, runId, rows);
    }

    @假設("飯店 {long} 積的待送被另一個交易鎖住不放")
    public void lockBacklog(long tenantId) throws SQLException {
        var postgres = SharedContainers.postgres();
        lockHolder = DriverManager.getConnection(SharedContainers.database("relayfairness"), postgres.getUsername(), postgres.getPassword());
        lockHolder.setAutoCommit(false);
        try (Statement statement = lockHolder.createStatement()) {
            statement.execute("SELECT id FROM outbox WHERE tenant_id = " + tenantId
                    + " AND aggregate_key LIKE '" + runId + ":%' FOR UPDATE");
        }
    }

    @假設("飯店 {long} 的隊伍 {string} 最前面是一筆超過 Kafka 大小上限的訊息，後面還有 {int} 筆")
    public void oversizedHead(long tenantId, String lane, int behind) {
        insertRow(tenantId, lane, 0, "x".repeat(100_000));
        for (int i = 1; i <= behind; i++) {
            insertRow(tenantId, lane, i, null);
        }
    }

    @假設("飯店 {long} 的隊伍 {string} 有 {int} 筆")
    public void laneRows(long tenantId, String lane, int rows) {
        for (int i = 0; i < rows; i++) {
            insertRow(tenantId, lane, i, null);
        }
    }

    @當("飯店 {long} 提交一張任務，喚醒它的 relay")
    public void wakeHotel(long tenantId) {
        createTaskAndCommit(tenantId, "喚醒 " + runId);
    }

    @當("{int} 毫秒後飯店 {long} 在獨立交易裡建立一張任務並提交")
    public void laterCommit(int delayMillis, long tenantId) throws InterruptedException {
        Thread.sleep(delayMillis);
        commitHotelTwo(tenantId);
    }

    @當("飯店 {long} 在獨立交易裡建立一張任務並提交")
    public void commit(long tenantId) {
        hotelOneTurnsBefore = turns(1L);
        hotelTwoTurnsBefore = turns(2L);
        commitHotelTwo(tenantId);
    }

    @當("鎖住飯店 {long} 待送的交易結束")
    public void unlock(long tenantId) throws SQLException {
        releaseLock();
        parkedByLock = true;
    }

    @那麼("飯店 {long} 這張任務的 {string} 事件在提交後 {int} 毫秒內送上 Kafka")
    public void publishedWithin(long tenantId, String eventType, int maxMillis) {
        ConsumerRecord<String, String> record = awaitTaskEvent(hotelTwoTask, eventType);
        hotelOnePendingWhenHotelTwoArrived = backlogPending();
        long latency = record.timestamp() - hotelTwoCommittedAt;
        System.out.printf("飯店 %d 提交到送上 Kafka：%d 毫秒；那時飯店 1 積的待送還有 %d 筆%n",
                tenantId, latency, hotelOnePendingWhenHotelTwoArrived);
        assertTrue(latency <= maxMillis, "飯店 " + tenantId + " 要在提交後 " + maxMillis + " 毫秒內送出，實際 " + latency + " 毫秒");
    }

    @那麼("那時飯店 {long} 積的待送還沒送完")
    public void backlogStillPending(long tenantId) {
        assertTrue(hotelOnePendingWhenHotelTwoArrived > 0,
                "飯店 2 送出時飯店 1 積的待送應該還沒送完，否則證明不了飯店 2 沒有排在後面等");
    }

    @那麼("營運看得到飯店 {long} 自己的送出延遲指標")
    public void perHotelLagRecorded(long tenantId) {
        long recorded = meterRegistry.find("tms.outbox.relay.lag").tag(TenantMetricsConfig.TENANT_TAG, Long.toString(tenantId))
                .timers().stream().mapToLong(Timer::count).sum();
        assertTrue(recorded > 0, "飯店 " + tenantId + " 的訊息送出後，依飯店分開的送出延遲指標應該有紀錄");
    }

    @那麼("飯店 {long} 積的待送最後都送上 Kafka，每個隊伍照寫入順序、每筆剛好一次")
    public void backlogDeliveredInLaneOrder(long tenantId) {
        // 被鎖住而暫停的租戶，暫停結束後要等下一次喚醒；定時補漏拉長了，只能靠提交喚醒。
        awaitUntil(() -> backlogPending() == 0, () -> {
            if (parkedByLock) {
                createTaskAndCommit(tenantId, "再喚醒 " + runId);
            }
        });
        Map<String, List<Long>> byLane = new HashMap<>();
        for (ConsumerRecord<String, String> record : readTopic(r -> r.key() != null && r.key().startsWith(runId), backlogSize)) {
            byLane.computeIfAbsent(record.key(), k -> new ArrayList<>()).add(objectMapper.readTree(record.value()).get("n").asLong());
        }
        int total = byLane.values().stream().mapToInt(List::size).sum();
        assertEquals(backlogSize, total, "積的 " + backlogSize + " 筆應各送一次");
        byLane.forEach((lane, sequence) -> {
            for (int i = 1; i < sequence.size(); i++) {
                assertTrue(sequence.get(i) > sequence.get(i - 1), "隊伍 " + lane + " 在 Kafka 上的順序和寫入順序不同：" + sequence);
            }
        });
    }

    @那麼("飯店 {long} 隊伍 {string} 的訊息送上 Kafka")
    public void laneDelivered(long tenantId, String lane) {
        String key = runId + ":" + lane;
        readTopic(r -> key.equals(r.key()), 1);
    }

    @那麼("飯店 {long} 隊伍 {string} 最前面那筆試過但沒送成，後面那筆一次都還沒送")
    public void laneStopsAtFailingHead(long tenantId, String lane) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status, attempts FROM outbox WHERE tenant_id = ? AND aggregate_key = ? ORDER BY id",
                tenantId, runId + ":" + lane);
        assertEquals("pending", rows.get(0).get("status"));
        assertTrue((Integer) rows.get(0).get("attempts") >= 1, "最前面那筆應該至少試送過一次：" + rows);
        assertEquals("pending", rows.get(1).get("status"));
        assertEquals(0, rows.get(1).get("attempts"), "排在送不出去那筆後面的不能插隊送出：" + rows);
    }

    @那麼("{int} 毫秒內飯店 {long} 的 relay 輪數增加、飯店 {long} 的 relay 輪數不變")
    public void onlyCommittingHotelWoken(int millis, long woken, long untouched) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofMillis(millis).toNanos();
        while (turns(woken) == hotelTwoTurnsBefore && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(turns(woken) > hotelTwoTurnsBefore, "飯店 " + woken + " 提交後 " + millis + " 毫秒內應該被喚醒");
        Thread.sleep(Math.max(0, (deadline - System.nanoTime()) / 1_000_000));
        assertEquals(hotelOneTurnsBefore, turns(untouched), "飯店 " + woken + " 的提交不該喚醒飯店 " + untouched);
    }

    private void commitHotelTwo(long tenantId) {
        hotelTwoTask = createTaskAndCommit(tenantId, "準時 " + runId);
        hotelTwoCommittedAt = System.currentTimeMillis();
    }

    private UUID createTaskAndCommit(long tenantId, String title) {
        TenantContext.set(tenantId);
        try {
            return taskEngine.create(new TaskCreateCommand(tenantId, "other", "desk", ROOM_BY_TENANT.get(tenantId), null,
                    "frontdesk", "triage", null, title, null, null, null, null, Actor.system())).getId();
        } finally {
            TenantContext.clear();
        }
    }

    private void insertRow(long tenantId, String lane, int n, String padding) {
        backlogSize++;
        jdbcTemplate.update("""
                INSERT INTO outbox (tenant_id, aggregate_key, kind, payload, idempotency_key, status, attempts, created_at)
                VALUES (?, ?, 'task_event', jsonb_build_object('n', ?::int, 'padding', ?::text), ?, 'pending', 0, now())
                """, tenantId, runId + ":" + lane, n, padding, runId + ":" + lane + ":" + n);
    }

    private long backlogPending() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox WHERE aggregate_key LIKE ? AND status = 'pending'", Long.class, runId + ":%");
    }

    private double turns(long tenantId) {
        return meterRegistry.find("tms.outbox.relay.turn").tag(TenantMetricsConfig.TENANT_TAG, Long.toString(tenantId)).timers().stream()
                .mapToDouble(Timer::count).sum();
    }

    private void releaseLock() throws SQLException {
        if (lockHolder != null) {
            lockHolder.rollback();
            lockHolder.close();
            lockHolder = null;
        }
    }

    private void awaitUntil(BooleanSupplier done, Runnable nudge) {
        long deadline = System.nanoTime() + KAFKA_WAIT.toNanos();
        while (!done.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "等 " + KAFKA_WAIT.toSeconds() + " 秒後飯店積的待送仍沒送完，還有 " + backlogPending() + " 筆");
            nudge.run();
            sleep(300);
        }
    }

    private ConsumerRecord<String, String> awaitTaskEvent(UUID taskId, String eventType) {
        return readTopic(r -> {
            if (r.key() == null || !r.key().startsWith("task:")) {
                return false;
            }
            JsonNode envelope = objectMapper.readTree(r.value());
            return taskId.toString().equals(envelope.path("task_id").asText()) && eventType.equals(envelope.path("type").asText());
        }, 1).getFirst();
    }

    private List<ConsumerRecord<String, String>> readTopic(java.util.function.Predicate<ConsumerRecord<String, String>> wanted, int expected) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                "bootstrap.servers", SharedContainers.kafka().getBootstrapServers(),
                "group.id", "relay-fairness-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest",
                "key.deserializer", StringDeserializer.class,
                "value.deserializer", StringDeserializer.class))) {
            consumer.subscribe(List.of(RelayFairnessSpringConfiguration.TASK_EVENTS_TOPIC));
            long deadline = System.nanoTime() + KAFKA_WAIT.toNanos();
            while (found.size() < expected && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    if (wanted.test(record)) {
                        found.add(record);
                    }
                }
            }
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (wanted.test(record)) {
                    found.add(record);
                }
            }
        }
        assertTrue(found.size() >= expected, "等 " + KAFKA_WAIT.toSeconds() + " 秒後 Kafka 上只有 " + found.size() + " 筆，預期 " + expected);
        return found;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
