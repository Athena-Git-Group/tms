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

/**
 * 句型要驗後端在降級與恢復時送出什麼訊號，得先有辦法把後端推進降級、再拉回來；這一支就是那個入口。
 * 契約列了兩種降級原因：後端處理事件落後超過 10 秒，或後端連不上事件來源。
 * 兩種都能在這裡製造與解除，句型不必真的等 10 秒，也不必把整個 Kafka 關掉。
 *
 * <p>後端的降級狀態與事件消費者是整個測試套件共用的，一個場景把它弄成降級而沒有復原，
 * 後面每個場景都會從降級開始。所以每個場景一份，記住這個場景動過什麼，
 * 場景結束時由 {@link #restore()} 還原，句型不必自己收尾。
 *
 * <p>這裡只負責把後端推到那個狀況，不檢查後端有沒有因此送出降級或恢復的訊號。
 */
public class StreamFaults {

    /** 產品碼的 {@code TaskEventsSseConsumer} 在 {@code @KafkaListener} 上替串流事件消費者取的 id。 */
    private static final String STREAM_LISTENER_ID = "sse-task-events";

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    /** 消費者重新接上 Kafka 要重新分配分區，本機通常幾秒內完成；超過這個時間就當成測試環境壞了。 */
    private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(30);

    /** 消費者在本機處理一則事件只要幾十毫秒；超過這個時間還沒處理完就當成測試環境壞了。 */
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

    /**
     * 讓後端的事件消費者看到一則「已經發生了 {@code lag} 這麼久」的事件。
     * 後端是拿事件的時間和現在比來算落後多久，超過 10 秒就進入降級；
     * 所以不必真的等，送一則時間印在過去的事件就夠了。
     * 這則事件指向一張不存在的單，不會變成任何連線上的 {@code task} 訊號。
     *
     * <p>這個方法只等到 Kafka 收下這則事件就回來，後端這時可能還沒處理到它。
     * 接下來的步驟若要以「後端已經進入降級」為前提，先呼叫 {@link #awaitProcessed()}。
     */
    public void reportLag(Duration lag) {
        lagReported = true;
        sendMarkerEvent(System.currentTimeMillis() - lag.toMillis());
    }

    /**
     * 讓後端的事件消費者看到一則時間是現在的事件，後端算出來的落後就回到零。
     * 和 {@link #reportLag} 一樣只等到 Kafka 收下，要確定後端已經處理到就接著呼叫 {@link #awaitProcessed()}。
     */
    public void reportCaughtUp() {
        sendMarkerEvent(System.currentTimeMillis());
    }

    /**
     * 等到後端的事件消費者把目前為止送進 Kafka 的事件全部處理完才回來。
     * 沒有這一步，句型在 {@link #reportLag} 之後馬上連線或檢查，有時搶在後端進入降級之前、有時之後，
     * 同一個場景就會時過時不過。
     *
     * <p>等待有上限。超過上限還沒處理完，或事件消費者正被 {@link #disconnectEventSource()} 停著，
     * 都丟 {@link IllegalStateException}，不回傳布林值，呼叫的句型不會不小心忽略逾時。
     */
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

    /** 停掉串流的事件消費者，後端從這一刻起收不到任何事件。Kafka 本身與推播的消費者不受影響。 */
    public void disconnectEventSource() {
        eventSourceDisconnected = true;
        streamListener().stop();
    }

    /**
     * 把停掉的事件消費者接回去，等到它重新分到分區才回來。
     * 沒等到就開始製造事件的話，那些事件要等消費者接上才會被處理，句型會誤以為訊號沒送出。
     */
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
     * 保證下一個場景開始時，事件消費者是接著的、後端不在降級。場景結束時由 Spring 呼叫。
     *
     * <p>做法是兩步：把消費者接回去；送一則讓落後歸零的事件並等後端處理完。
     * 兩步各做各的，接回失敗時仍然會送歸零的事件，因為只要其中一步沒做，後面的場景就會從錯的狀態開始。
     * 這個場景沒有動過任何狀況時什麼都不做，其他場景不必為此多等。
     *
     * <p>任何一步失敗都會往標準錯誤輸出印出原因並丟例外。
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
