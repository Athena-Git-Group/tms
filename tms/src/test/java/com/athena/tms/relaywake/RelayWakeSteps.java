package com.athena.tms.relaywake;

import com.athena.tms.SharedContainers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.athena.tms.common.TenantContext;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.service.TaskEngine;
import io.cucumber.java.zh_tw.假設;
import io.cucumber.java.zh_tw.當;
import io.cucumber.java.zh_tw.那麼;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

public class RelayWakeSteps {

    private static final long TENANT_ID = 1L;
    private static final UUID ROOM_301 = UUID.fromString("00000000-0000-7000-8000-000000000301");
    private static final Duration KAFKA_WAIT = Duration.ofSeconds(15);

    @Autowired
    private TaskEngine taskEngine;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${tms.outbox.relay.poll-interval}")
    private String pollInterval;

    private final Map<UUID, Long> commitMillisByTask = new LinkedHashMap<>();
    private UUID rolledBackTaskId;
    private double passesBefore;

    @假設("relay 的定時輪詢間隔為 {string}")
    public void relayPollIntervalIs(String expected) {
        assertEquals(expected, pollInterval, "這個套件要靠拉長的定時輪詢，才分得出訊息是被提交喚醒送出的");
    }

    @當("依序 {int} 次各在獨立交易裡建立一張任務並提交")
    public void createTasksSequentially(int count) throws InterruptedException {
        for (int i = 0; i < count; i++) {
            UUID taskId = createTaskAndCommit("依序建立 " + i);
            commitMillisByTask.put(taskId, System.currentTimeMillis());
            Thread.sleep(100);
        }
    }

    @當("在交易裡建立一張任務後回滾")
    public void createTaskThenRollback() {
        passesBefore = relayPasses();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        rolledBackTaskId = asTenant(() -> transaction.execute(status -> {
            UUID taskId = taskEngine.create(command("回滾")).getId();
            status.setRollbackOnly();
            return taskId;
        }));
    }

    @當("由 {int} 條執行緒同時送出 {int} 筆各自獨立的任務建立交易")
    public void createTasksConcurrently(int threads, int count) throws Exception {
        passesBefore = relayPasses();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<UUID>> created = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String title = "同時建立 " + i;
                created.add(pool.submit(() -> {
                    go.await();
                    return createTaskAndCommit(title);
                }));
            }
            go.countDown();
            for (Future<UUID> future : created) {
                commitMillisByTask.put(future.get(), System.currentTimeMillis());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @那麼("每張任務的 {string} 事件都在提交後 {int} 毫秒內送上 Kafka")
    public void everyEventPublishedWithin(String eventType, int maxMillis) {
        Map<UUID, ConsumerRecord<String, String>> records = awaitEvents(commitMillisByTask.keySet(), eventType);
        List<Long> latencies = new ArrayList<>();
        commitMillisByTask.forEach((taskId, committedAt) -> latencies.add(records.get(taskId).timestamp() - committedAt));
        List<Long> sorted = latencies.stream().sorted().toList();
        System.out.printf("提交到送上 Kafka 的延遲（毫秒）：n=%d 中位數=%d 最大=%d 全部=%s%n",
                sorted.size(), sorted.get(sorted.size() / 2), sorted.get(sorted.size() - 1), latencies);
        assertTrue(sorted.get(sorted.size() - 1) <= maxMillis,
                "每筆都要在提交後 " + maxMillis + " 毫秒內送出，實際延遲：" + latencies);
    }

    @那麼("每張任務的 {string} 事件都送上 Kafka")
    public void everyEventPublished(String eventType) {
        awaitEvents(commitMillisByTask.keySet(), eventType);
    }

    @那麼("{int} 毫秒內 relay 跑完的輪數不變")
    public void relayPassesUnchangedFor(int millis) throws InterruptedException {
        Thread.sleep(millis);
        assertEquals(passesBefore, relayPasses(), "回滾的交易不該喚醒 relay");
    }

    @那麼("Kafka 上沒有這張任務的事件")
    public void noEventForRolledBackTask() {
        List<ConsumerRecord<String, String>> all = readTaskEvents(Duration.ofSeconds(2));
        assertTrue(all.stream().noneMatch(record -> ("task:" + rolledBackTaskId).equals(record.key())),
                "回滾的任務不該有事件送上 Kafka");
    }

    @那麼("relay 為這批提交跑完的輪數少於 {int}")
    public void relayPassesForBurstBelow(int limit) throws InterruptedException {
        Thread.sleep(500);
        double passes = relayPasses() - passesBefore;
        System.out.printf("%d 筆提交讓 relay 跑了 %.0f 輪%n", commitMillisByTask.size(), passes);
        assertTrue(passes < limit, commitMillisByTask.size() + " 筆提交應合併成少於 " + limit + " 輪，實際 " + passes + " 輪");
    }

    private UUID createTaskAndCommit(String title) {
        return asTenant(() -> taskEngine.create(command(title)).getId());
    }

    private static TaskCreateCommand command(String title) {
        return new TaskCreateCommand(TENANT_ID, "other", "desk", ROOM_301, null, "frontdesk", "triage", null,
                title, null, null, null, null, Actor.system());
    }

    private static <T> T asTenant(java.util.function.Supplier<T> work) {
        TenantContext.set(TENANT_ID);
        try {
            return work.get();
        } finally {
            TenantContext.clear();
        }
    }

    private double relayPasses() {
        return meterRegistry.get("tms.outbox.relay.passes").counter().count();
    }

    private Map<UUID, ConsumerRecord<String, String>> awaitEvents(Collection<UUID> taskIds, String eventType) {
        Map<UUID, ConsumerRecord<String, String>> found = new HashMap<>();
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(List.of(RelayWakeSpringConfiguration.TASK_EVENTS_TOPIC));
            long deadline = System.nanoTime() + KAFKA_WAIT.toNanos();
            while (found.size() < taskIds.size() && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    UUID taskId = UUID.fromString(objectMapper.readTree(record.value()).get("task_id").asText());
                    String type = objectMapper.readTree(record.value()).get("type").asText();
                    if (taskIds.contains(taskId) && eventType.equals(type)) {
                        found.put(taskId, record);
                    }
                }
            }
        }
        assertEquals(taskIds.size(), found.size(), "等 " + KAFKA_WAIT.toSeconds() + " 秒後仍有任務的 " + eventType + " 事件沒送上 Kafka");
        return found;
    }

    private List<ConsumerRecord<String, String>> readTaskEvents(Duration window) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(List.of(RelayWakeSpringConfiguration.TASK_EVENTS_TOPIC));
            long deadline = System.nanoTime() + window.toNanos();
            while (System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
        }
        return records;
    }

    private static KafkaConsumer<String, String> newConsumer() {
        return new KafkaConsumer<>(Map.of(
                "bootstrap.servers", SharedContainers.kafka().getBootstrapServers(),
                "group.id", "relay-wake-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest",
                "key.deserializer", StringDeserializer.class,
                "value.deserializer", StringDeserializer.class));
    }
}
