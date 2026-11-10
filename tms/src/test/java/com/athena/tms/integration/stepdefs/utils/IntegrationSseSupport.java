package com.athena.tms.integration.stepdefs.utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class IntegrationSseSupport {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private IntegrationSseSupport() {
    }

    public static IntegrationSseClient open(int port, String path, String apiKey) throws Exception {
        return new IntegrationSseClient(port, path, apiKey);
    }

    public static final class IntegrationSseClient
    implements AutoCloseable {
        private final BlockingQueue<Event> pending = new LinkedBlockingQueue<Event>();
        private final List<Event> everything = new CopyOnWriteArrayList<Event>();
        private final Stream<String> body;
        private final Thread reader;
        private volatile boolean closed;
        private volatile Throwable readerFailure;

        IntegrationSseClient(int port, String path, String apiKey) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("X-API-Key", apiKey).header("Accept", "text/event-stream").GET().build();
            HttpResponse<Stream<String>> response = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).get(10L, TimeUnit.SECONDS);
            Assertions.assertEquals((int)200, (int)response.statusCode(), (String)("\u6574\u5408 SSE \u7aef\u9ede\u61c9\u4ee5 200 \u958b\u6d41\uff08" + path + "\uff09"));
            this.body = response.body();
            this.reader = new Thread(this::readLoop, "integration-sse-bdd-reader");
            this.reader.setDaemon(true);
            this.reader.start();
        }

        private void readLoop() {
            String name = null;
            String id = null;
            StringBuilder data = new StringBuilder();
            try {
                Iterator lines = this.body.iterator();
                while (lines.hasNext()) {
                    String line = (String)lines.next();
                    if (line.isEmpty()) {
                        if (name != null && !data.isEmpty()) {
                            Event event = new Event(name, id, JSON.readTree(data.toString()));
                            this.everything.add(event);
                            this.pending.add(event);
                        }
                        name = null;
                        id = null;
                        data.setLength(0);
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        name = line.substring("event:".length()).trim();
                        continue;
                    }
                    if (line.startsWith("id:")) {
                        id = line.substring("id:".length()).trim();
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

        public Event awaitNamed(String name, Duration timeout) throws InterruptedException {
            return this.await(timeout, event -> event.name().equals(name), "\u540d\u70ba '" + name + "' \u7684\u4e8b\u4ef6");
        }

        public Event awaitRoomStatus(String roomNo, Duration timeout) throws InterruptedException {
            return this.await(timeout, event -> event.name().equals("room_status") && roomNo.equals(event.data().get("room_no").asText()), "room_no=" + roomNo + " \u7684 room_status \u4e8b\u4ef6");
        }

        private Event await(Duration timeout, Predicate<Event> match, String what) throws InterruptedException {
            long remaining;
            Event event;
            long deadline = System.nanoTime() + timeout.toNanos();
            do {
                this.failIfReaderDied();
                if ((remaining = deadline - System.nanoTime()) > 0L) continue;
                Assertions.fail((String)("\u6574\u5408\u9023\u7dda\u7b49\u4e0d\u5230" + what + "\uff1b\u5df2\u6536\u5230: " + this.summary()));
            } while ((event = this.pending.poll(remaining, TimeUnit.NANOSECONDS)) == null || !match.test(event));
            return event;
        }

        public List<Event> roomStatusEvents() {
            this.failIfReaderDied();
            return this.everything.stream().filter(e -> e.name().equals("room_status")).toList();
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

        public record Event(String name, String id, JsonNode data) {
        }
    }
}
