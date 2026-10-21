package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.integration.stepdefs.CucumberSpringConfiguration;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 即時事件推送（SSE）場景的測試工具：開啟真實的 SSE 連線，以及直接把訊息寫進 Kafka。
 *
 * <p>SSE 客戶端走真 HTTP 連線，並保存這條連線收過的所有事件。
 * 保存全部是為了驗證「不應該收到某事件」的場景：要證明沒收到什麼，只能自己留底。
 *
 * <p>Kafka 直送繞過 outbox relay（平時負責把 outbox 資料表的訊息送進 Kafka 的背景元件）。
 * 這是測試中唯一能自訂 record 時間戳與製造重複投遞的方法，
 * 供模擬消費延遲與重複訊息的場景使用。
 */
public class SseSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final Environment environment;

    public SseSupport(Environment environment) {
        this.environment = environment;
    }

    /**
     * 回傳前先等到伺服器的第一筆 init 事件，確保連線真的建立完成。
     * 不等的話，後續的事件等待會把建線延遲誤判成掉事件，造成偶發紅燈。
     */
    public SseStreamClient open(String path, String bearerToken) throws Exception {
        SseStreamClient client = new SseStreamClient(
                environment.getRequiredProperty("local.server.port", Integer.class),
                path, bearerToken);
        client.awaitNamed("init", Duration.ofSeconds(10));
        return client;
    }

    /** 以自訂時間戳把訊息直接寫進 Kafka，不經過 outbox relay。 */
    public void produceRaw(long timestamp, String key, String value) throws Exception {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                CucumberSpringConfiguration.KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("tms.task-events", null, timestamp, key, value))
                    .get(10, TimeUnit.SECONDS);
        }
    }

    public static String envelope(long tenantId, UUID taskId, int seq, String type) {
        return "{\"tenant_id\": " + tenantId + ", \"task_id\": \"" + taskId
                + "\", \"seq\": " + seq + ", \"type\": \"" + type + "\"}";
    }

    /**
     * 最小 SSE 客戶端，以 JDK HttpClient 自行解析事件流。
     *
     * <p>不用現成的 SSE 客戶端程式庫，是因為這裡要保存整條連線收過的所有事件，
     * 才驗得了「不應該收到某事件」的斷言。一般程式庫只回呼新事件，不留歷史。
     */
    public static final class SseStreamClient implements AutoCloseable {

        public record Event(String name, JsonNode data) {
        }

        private final BlockingQueue<Event> pending = new LinkedBlockingQueue<>();
        private final List<Event> everything = new CopyOnWriteArrayList<>();
        private final Stream<String> body;
        private final Thread reader;

        SseStreamClient(int port, String path, String bearerToken) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Authorization", "Bearer " + bearerToken)
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();
            HttpResponse<Stream<String>> response = HTTP
                    .sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .get(10, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode(), "SSE 端點應以 200 開流（" + path + "）");
            this.body = response.body();
            this.reader = new Thread(this::readLoop, "sse-bdd-reader");
            this.reader.setDaemon(true);
            this.reader.start();
        }

        private void readLoop() {
            String name = null;
            StringBuilder data = new StringBuilder();
            try {
                for (Iterator<String> lines = body.iterator(); lines.hasNext(); ) {
                    String line = lines.next();
                    if (line.isEmpty()) {
                        // SSE 的格式以空行代表一筆事件結束。
                        if (name != null && !data.isEmpty()) {
                            Event event = new Event(name, JSON.readTree(data.toString()));
                            everything.add(event);
                            pending.add(event);
                        }
                        name = null;
                        data.setLength(0);
                    } else if (line.startsWith("event:")) {
                        name = line.substring("event:".length()).trim();
                    } else if (line.startsWith("data:")) {
                        data.append(line.substring("data:".length()).trim());
                    }
                }
            } catch (Exception ignored) {
                // close() 關閉串流時，讀取迴圈會以例外結束。這是預期行為，不是錯誤。
            }
        }

        /**
         * 等待下一筆指定名稱的事件。
         * 等待期間收到的其他事件仍會留在事件歷史裡，不會因為不是目標就遺失。
         */
        public JsonNode awaitNamed(String name, Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    fail("等不到 SSE 事件 '" + name + "'；已收到: " + summary());
                }
                Event event = pending.poll(remaining, TimeUnit.NANOSECONDS);
                if (event != null && event.name().equals(name)) {
                    return event.data();
                }
            }
        }

        public JsonNode awaitTask(UUID taskId, String type, Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    fail("等不到任務 " + taskId + " 的 '" + type + "' 事件；已收到: " + summary());
                }
                Event event = pending.poll(remaining, TimeUnit.NANOSECONDS);
                if (event != null && event.name().equals("task")
                        && taskId.toString().equals(event.data().get("task_id").asText())
                        && type.equals(event.data().get("type").asText())) {
                    return event.data();
                }
            }
        }

        /** 查的是整條連線的事件歷史：已被 await 方法取走的事件也包含在內，適合驗總量與順序。 */
        public List<JsonNode> taskEventsFor(UUID taskId) {
            return everything.stream()
                    .filter(e -> e.name().equals("task"))
                    .map(Event::data)
                    .filter(d -> taskId.toString().equals(d.get("task_id").asText()))
                    .toList();
        }

        /** 這條連線目前收過的事件名稱，依到達順序——驗「連線一開必先 init 再 resync」用這個。 */
        public List<String> eventNames() {
            return everything.stream().map(Event::name).toList();
        }

        /**
         * resync 只在連線剛建立時送一次，可能已經被 awaitNamed 從待消費佇列取走——
         * 要再拿它的內容得查永久保留的歷史（everything），不能再 await 一次
         * （佇列裡已經沒有，會白等到逾時）。
         */
        public JsonNode resyncData() {
            return everything.stream()
                    .filter(e -> e.name().equals("resync"))
                    .map(Event::data)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("這條連線還沒收到 resync：" + summary()));
        }

        private String summary() {
            return everything.stream()
                    .map(e -> e.name() + ":" + e.data().toString())
                    .toList()
                    .toString();
        }

        @Override
        public void close() {
            body.close();
            reader.interrupt();
        }
    }
}
