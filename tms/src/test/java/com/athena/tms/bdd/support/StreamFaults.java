package com.athena.tms.bdd.support;

import com.athena.tms.config.KafkaTopicsProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;

public class StreamFaults {

    private static final String STREAM_LISTENER_ID = "sse-task-events";

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaListenerEndpointRegistry listenerRegistry;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTopicsProperties topics;
    private final KafkaGroupAwait kafkaGroupAwait;
    private final TenantFixtures tenantFixtures;

    private boolean lagReported;
    private boolean eventSourceDisconnected;

    public StreamFaults(
            KafkaListenerEndpointRegistry listenerRegistry,
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaTopicsProperties topics,
            KafkaGroupAwait kafkaGroupAwait,
            TenantFixtures tenantFixtures) {
        this.listenerRegistry = listenerRegistry;
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
        this.kafkaGroupAwait = kafkaGroupAwait;
        this.tenantFixtures = tenantFixtures;
    }

    public void reportLag(Duration lag) {
        lagReported = true;
        sendMarkerEvent(System.currentTimeMillis() - lag.toMillis());
    }

    public void reportCaughtUp() {
        sendMarkerEvent(System.currentTimeMillis());
    }

    public void awaitProcessed() {
        MessageListenerContainer listener = streamListener();
        if (!listener.isRunning()) {
            throw new IllegalStateException("串流的事件消費者停著，等不到它處理事件；先呼叫 reconnectEventSource()");
        }
        boolean caughtUp = kafkaGroupAwait.awaitCaughtUp(listener.getGroupId(), topics.getTaskEvents(), PROCESS_TIMEOUT);
        if (!caughtUp) {
            throw new IllegalStateException(
                    "串流的事件消費者 " + PROCESS_TIMEOUT.toSeconds() + " 秒內沒有把送進 Kafka 的事件處理完");
        }
    }

    public void disconnectEventSource() {
        eventSourceDisconnected = true;
        streamListener().stop();
    }

    public void reconnectEventSource() {
        MessageListenerContainer listener = streamListener();
        if (!listener.isRunning()) {
            listener.start();
        }
        long deadline = System.nanoTime() + RECONNECT_TIMEOUT.toNanos();
        while (!hasAssignedPartitions(listener)) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException(
                        "串流的事件消費者 " + RECONNECT_TIMEOUT.toSeconds() + " 秒內沒有重新接上 Kafka");
            }
            sleepBriefly();
        }
        eventSourceDisconnected = false;
    }

    /**
     * 任何一步失敗都會往標準錯誤輸出印出原因並丟例外。
     * Spring 在銷毀物件的階段只會把例外記成一行警告，場景本身不會因此失敗，
     * 所以要另外印出來，讓看測試輸出的人知道後面場景的結果可能不可信。
     */
    public void restore() {
        if (!lagReported && !eventSourceDisconnected) {
            return;
        }
        List<String> failures = new ArrayList<>();
        try {
            reconnectEventSource();
        } catch (RuntimeException e) {
            failures.add("事件消費者沒有接回去：" + e.getMessage());
        }
        try {
            reportCaughtUp();
            awaitProcessed();
            lagReported = false;
        } catch (RuntimeException e) {
            failures.add("後端的降級狀態沒有確定歸零：" + e.getMessage());
        }
        if (!failures.isEmpty()) {
            String message = "[StreamFaults] 場景結束時還原串流狀態失敗，後面場景的結果可能受影響。" + String.join("；", failures);
            System.err.println(message);
            throw new IllegalStateException(message);
        }
    }

    private void sendMarkerEvent(long timestampMillis) {
        UUID noSuchTask = UUID.randomUUID();
        String envelope = "{\"tenant_id\": " + tenantFixtures.testTenantId()
                + ", \"task_id\": \"" + noSuchTask + "\", \"seq\": 1, \"type\": \"created\"}";
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topics.getTaskEvents(), null, timestampMillis, "task:" + noSuchTask, envelope);
        try {
            kafkaTemplate.send(record).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("送出測試用的串流事件時被中斷", e);
        } catch (Exception e) {
            throw new IllegalStateException("送出測試用的串流事件失敗", e);
        }
    }

    private MessageListenerContainer streamListener() {
        MessageListenerContainer listener = listenerRegistry.getListenerContainer(STREAM_LISTENER_ID);
        if (listener == null) {
            throw new IllegalStateException("找不到串流的事件消費者，listener id=" + STREAM_LISTENER_ID);
        }
        return listener;
    }

    private static boolean hasAssignedPartitions(MessageListenerContainer listener) {
        Collection<TopicPartition> assigned = listener.getAssignedPartitions();
        return assigned != null && !assigned.isEmpty();
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等串流的事件消費者接上 Kafka 時被中斷", e);
        }
    }
}
