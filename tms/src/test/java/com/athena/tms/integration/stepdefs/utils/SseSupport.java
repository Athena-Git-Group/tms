package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.integration.stepdefs.CucumberSpringConfiguration;
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
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Assertions;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class SseSupport {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private final Environment environment;

    public SseSupport(Environment environment) {
        this.environment = environment;
    }

    public SseStreamClient open(String path, String bearerToken) throws Exception {
        SseStreamClient client = new SseStreamClient((Integer)this.environment.getRequiredProperty("local.server.port", Integer.class), path, bearerToken);
        client.awaitNamed("init", Duration.ofSeconds(10L));
        return client;
    }

    public void produceRaw(long timestamp, String key, String value) throws Exception {
        Map<String, Object> props = Map.of("bootstrap.servers", CucumberSpringConfiguration.KAFKA.getBootstrapServers(), "key.serializer", StringSerializer.class, "value.serializer", StringSerializer.class);
        try (KafkaProducer producer = new KafkaProducer(props);){
            producer.send(new ProducerRecord("tms.task-events", null, Long.valueOf(timestamp), (Object)key, (Object)value)).get(10L, TimeUnit.SECONDS);
        }
    }

    public static String envelope(long tenantId, UUID taskId, int seq, String type) {
        return "{\"tenant_id\": " + tenantId + ", \"task_id\": \"" + String.valueOf(taskId) + "\", \"seq\": " + seq + ", \"type\": \"" + type + "\"}";
    }

    public static final class SseStreamClient
    implements AutoCloseable {
        private final BlockingQueue<Event> pending = new LinkedBlockingQueue<Event>();
        private final List<Event> everything = new CopyOnWriteArrayList<Event>();
        private final Stream<String> body;
        private final Thread reader;
        private volatile boolean closed;
        private volatile Throwable readerFailure;

        SseStreamClient(int port, String path, String bearerToken) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Authorization", "Bearer " + bearerToken).header("Accept", "text/event-stream").GET().build();
            HttpResponse<Stream<String>> response = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).get(10L, TimeUnit.SECONDS);
            Assertions.assertEquals((int)200, (int)response.statusCode(), (String)("SSE 端點應以 200 開流（" + path + "）"));
            this.body = response.body();
            this.reader = new Thread(this::readLoop, "sse-bdd-reader");
            this.reader.setDaemon(true);
            this.reader.start();
        }

        private void readLoop() {
            String name = null;
            StringBuilder data = new StringBuilder();
            try {
                Iterator lines = this.body.iterator();
                while (lines.hasNext()) {
                    String line = (String)lines.next();
                    if (line.isEmpty()) {
                        if (name != null && !data.isEmpty()) {
                            Event event = new Event(name, JSON.readTree(data.toString()));
                            this.everything.add(event);
                            this.pending.add(event);
                        }
                        name = null;
                        data.setLength(0);
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        name = line.substring("event:".length()).trim();
                        continue;
                    }
                    if (!line.startsWith("data:")) continue;
                    data.append(line.substring("data:".length()).trim());
                }
            }
            catch (Exception exception) {
                if (!this.closed) {
                    this.readerFailure = exception;
                }
            }
        }

        public JsonNode awaitNamed(String name, Duration timeout) throws InterruptedException {
            long remaining;
            Event event;
            long deadline = System.nanoTime() + timeout.toNanos();
            do {
                this.failIfReaderDied();
                if ((remaining = deadline - System.nanoTime()) > 0L) continue;
                Assertions.fail((String)("等不到 SSE 事件 '" + name + "'；已收到: " + this.summary()));
            } while ((event = this.pending.poll(remaining, TimeUnit.NANOSECONDS)) == null || !event.name().equals(name));
            return event.data();
        }

        public JsonNode awaitTask(UUID taskId, String type, Duration timeout) throws InterruptedException {
            long remaining;
            Event event;
            long deadline = System.nanoTime() + timeout.toNanos();
            do {
                this.failIfReaderDied();
                if ((remaining = deadline - System.nanoTime()) > 0L) continue;
                Assertions.fail((String)("等不到任務 " + String.valueOf(taskId) + " 的 '" + type + "' 事件；已收到: " + this.summary()));
            } while ((event = this.pending.poll(remaining, TimeUnit.NANOSECONDS)) == null || !event.name().equals("task") || !taskId.toString().equals(event.data().get("task_id").asText()) || !type.equals(event.data().get("type").asText()));
            return event.data();
        }

        public List<JsonNode> taskEventsFor(UUID taskId) {
            this.failIfReaderDied();
            return this.everything.stream().filter(e -> e.name().equals("task")).map(Event::data).filter(d -> taskId.toString().equals(d.get("task_id").asText())).toList();
        }

        public List<String> eventNames() {
            this.failIfReaderDied();
            return this.everything.stream().map(Event::name).toList();
        }

        public JsonNode resyncData() {
            this.failIfReaderDied();
            return this.everything.stream().filter(e -> e.name().equals("resync")).map(Event::data).findFirst().orElseThrow(() -> new IllegalStateException("這條連線還沒收到 resync：" + this.summary()));
        }

        /** 讀取執行緒因例外中止（不是測試主動關流）時，讓接下來讀流的斷言直接失敗並帶出原因。 */
        private void failIfReaderDied() {
            Throwable failure = this.readerFailure;
            if (failure != null) {
                throw new AssertionError("SSE 讀取執行緒已因例外中止；已收到: " + this.summary(), failure);
            }
        }

        private String summary() {
            return this.everything.stream().map(e -> e.name() + ":" + e.data().toString()).toList().toString();
        }

        @Override
        public void close() {
            this.closed = true;
            this.body.close();
            this.reader.interrupt();
        }

        public record Event(String name, JsonNode data) {
        }
    }
}
