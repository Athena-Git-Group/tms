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

    public static final class IntegrationSseClient implements AutoCloseable {

        private final BlockingQueue<Event> pending = new LinkedBlockingQueue<>();
        private final List<Event> everything = new CopyOnWriteArrayList<>();
        private final Stream<String> body;
        private final Thread reader;
        private volatile boolean closed;
        private volatile Throwable readerFailure;

        IntegrationSseClient(int port, String path, String apiKey) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("X-API-Key", apiKey)
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();
            HttpResponse<Stream<String>> response = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .get(10, TimeUnit.SECONDS);
            Assertions.assertEquals(200, response.statusCode(), "整合 SSE 端點應以 200 開流（" + path + "）");
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
                Iterator<String> lines = body.iterator();
                while (lines.hasNext()) {
                    String line = lines.next();
                    if (line.isEmpty()) {
                        if (name != null && !data.isEmpty()) {
                            Event event = new Event(name, id, JSON.readTree(data.toString()));
                            everything.add(event);
                            pending.add(event);
                        }
                        name = null;
                        id = null;
                        data.setLength(0);
                    } else if (line.startsWith("event:")) {
                        name = line.substring("event:".length()).trim();
                    } else if (line.startsWith("id:")) {
                        id = line.substring("id:".length()).trim();
                    } else if (line.startsWith("data:")) {
                        data.append(line.substring("data:".length()).trim());
                    }
                }
            } catch (Exception exception) {
                if (!closed) {
                    readerFailure = exception;
                }
            }
        }

        public Event awaitNamed(String name, Duration timeout) throws InterruptedException {
            return await(timeout, event -> event.name().equals(name), "名為 '" + name + "' 的事件");
        }

        public Event awaitRoomStatus(String roomNo, Duration timeout) throws InterruptedException {
            return await(timeout,
                    event -> event.name().equals("room_status") && roomNo.equals(event.data().get("room_no").asText()),
                    "room_no=" + roomNo + " 的 room_status 事件");
        }

        private Event await(Duration timeout, Predicate<Event> match, String what) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                failIfReaderDied();
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    Assertions.fail("整合連線等不到" + what + "；已收到: " + summary());
                }
                Event event = pending.poll(remaining, TimeUnit.NANOSECONDS);
                if (event != null && match.test(event)) {
                    return event;
                }
            }
        }

        public List<Event> roomStatusEvents() {
            failIfReaderDied();
            return everything.stream().filter(e -> e.name().equals("room_status")).toList();
        }

        /** 讀取執行緒因例外中止（不是測試主動關流）時，讓接下來讀流的斷言直接失敗並帶出原因。 */
        private void failIfReaderDied() {
            Throwable failure = readerFailure;
            if (failure != null) {
                throw new AssertionError("SSE 讀取執行緒已因例外中止；已收到: " + summary(), failure);
            }
        }

        private String summary() {
            return everything.stream().map(e -> e.name() + ":" + e.data().toString()).toList().toString();
        }

        @Override
        public void close() {
            closed = true;
            body.close();
            reader.interrupt();
        }

        public record Event(String name, String id, JsonNode data) {
        }
    }
}
