package com.athena.tms.integration.stepdefs.utils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * PMS 整合 SSE 連線（/integration/pms/stream）的測試客戶端。
 *
 * <p>與 {@link SseSupport} 的住客／員工客戶端分開實作，因為協定不同：
 * 整合連線以 X-API-Key 認證（不是 Authorization bearer）、建線不送 init
 * （補齊機制是 cursor 回放，不是重拉快照，等 init 會白等到逾時），且事件
 * 多了 {@code id:} 行——那是斷線重連要帶的 cursor，斷言「id 等於資料中的
 * event_id」必須留下它，而 SseSupport 的解析器會把這行丟掉。
 *
 * <p>同樣保存整條連線收過的所有事件：要證明「沒收到某事件」只能自己留底。
 */
public final class IntegrationSseSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private IntegrationSseSupport() {
    }

    public static IntegrationSseClient open(int port, String path, String apiKey) throws Exception {
        return new IntegrationSseClient(port, path, apiKey);
    }

    public static final class IntegrationSseClient implements AutoCloseable {

        /** 一筆 SSE 事件：名稱、{@code id:} 行原文（可為 null）、data 的 JSON。 */
        public record Event(String name, String id, JsonNode data) {
        }

        private final BlockingQueue<Event> pending = new LinkedBlockingQueue<>();
        private final List<Event> everything = new CopyOnWriteArrayList<>();
        private final Stream<String> body;
        private final Thread reader;

        IntegrationSseClient(int port, String path, String apiKey) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("X-API-Key", apiKey)
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();
            HttpResponse<Stream<String>> response = HTTP
                    .sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .get(10, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode(), "整合 SSE 端點應以 200 開流（" + path + "）");
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
                for (Iterator<String> lines = body.iterator(); lines.hasNext(); ) {
                    String line = lines.next();
                    if (line.isEmpty()) {
                        // SSE 以空行代表一筆事件結束
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
            } catch (Exception ignored) {
                // close() 關流時讀取迴圈以例外結束，是預期行為
            }
        }

        /** 等下一筆指定名稱的事件（等待間收到的其他事件仍留在歷史裡）。 */
        public Event awaitNamed(String name, Duration timeout) throws InterruptedException {
            return await(timeout, event -> event.name().equals(name),
                    "名為 '" + name + "' 的事件");
        }

        /** 等指定房號的 room_status 事件（回放與即時推送共用這個斷言）。 */
        public Event awaitRoomStatus(String roomNo, Duration timeout) throws InterruptedException {
            return await(timeout, event -> event.name().equals("room_status")
                            && roomNo.equals(event.data().get("room_no").asText()),
                    "room_no=" + roomNo + " 的 room_status 事件");
        }

        private Event await(Duration timeout, java.util.function.Predicate<Event> match,
                            String what) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    fail("整合連線等不到" + what + "；已收到: " + summary());
                }
                Event event = pending.poll(remaining, TimeUnit.NANOSECONDS);
                if (event != null && match.test(event)) {
                    return event;
                }
            }
        }

        /** 整條連線收過的 room_status 事件（含已被 await 取走的）：驗「一筆都沒有」用。 */
        public List<Event> roomStatusEvents() {
            return everything.stream().filter(e -> e.name().equals("room_status")).toList();
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
