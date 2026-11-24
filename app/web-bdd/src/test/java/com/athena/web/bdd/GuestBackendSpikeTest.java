package com.athena.web.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.athena.bddsupport.BackendChannel;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.bddsupport.BackendChannel.FrontdeskDefaults;
import com.athena.bddsupport.BackendChannel.GuestScan;
import com.athena.bddsupport.BackendChannel.GuestScanRejected;
import com.athena.bddsupport.BackendChannel.RotatedQr;
import com.athena.bddsupport.GuestSeed;
import com.athena.web.bdd.support.WebApp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class GuestBackendSpikeTest {

    private static final String ROOM = "311";
    private static final String GUEST_SESSION_HEADER = "X-Guest-Session";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final int CLAIM_SAMPLES = 3;
    private static final long REALTIME_LIMIT_MS = 5_000;
    private static final long HEARTBEAT_WAIT_MS = 16_000;
    private static final long PAGE_LIMIT_MS = 5_000;

    private final BackendChannel channel = new BackendChannel();
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final String backend = System.getProperty("backend.base.url", "http://127.0.0.1:8080");

    private record SseEvent(String name, JsonNode data, long receivedNanos) {}

    @Test
    void 住客串流_冪等鍵回放_測試資料方法_對真後端實跑() throws Exception {
        channel.resetBusinessData();
        FrontdeskDefaults defaults = channel.ensureFrontdeskDefaults();
        channel.ensureGuestDefaults();

        String sessionToken = scanAndRotate();
        List<UUID> seeded = seedHousekeepingPool();
        assertSeededVisibleAsGuestRequests(sessionToken, seeded);

        streamAndClaim(sessionToken, seeded, defaults.staff("阿美"));

        boolean replayed = submitTwiceWithSameKey(sessionToken);

        String newToken = channel.roomQrToken(ROOM);
        openGuestHome(newToken);

        assertTrue(replayed, "同一把 Idempotency-Key 第二次送出沒有回放，照 research 的退路做");
    }

    private String scanAndRotate() {
        String oldToken = channel.roomQrToken(ROOM);
        GuestScan before = channel.guestScan(oldToken);
        assertFalse(before.sessionToken().isBlank(), "舊代碼換得到住客 session");
        System.out.printf("[spike] ① 舊代碼掃碼 200，需求選單 %d 項%n", before.menu().size());

        RotatedQr rotated = channel.rotateRoomQr(ROOM);
        assertEquals(oldToken, rotated.oldToken(), "換 QR 前讀到的就是剛才掃的代碼");
        try {
            channel.guestScan(rotated.oldToken());
            fail("換 QR 之後舊代碼不該再換得到 session");
        } catch (GuestScanRejected rejected) {
            System.out.printf("[spike] ① 換 QR 後舊代碼 -> %d %s%n", rejected.status(), rejected.code());
            assertEquals(409, rejected.status(), "舊代碼回 409");
            assertEquals("room_vacant", rejected.code(), "舊代碼的錯誤代碼是 room_vacant");
        }
        GuestScan after = channel.guestScan(rotated.newToken());
        assertFalse(after.sessionToken().isBlank(), "新代碼換得到住客 session");
        System.out.println("[spike] ① 換 QR 後新代碼掃碼 200");
        return after.sessionToken();
    }

    private List<UUID> seedHousekeepingPool() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 1; i <= CLAIM_SAMPLES; i++) {
            ids.add(channel.seedGuestRequest(ROOM, new GuestSeed(GuestSeed.Situation.HOUSEKEEPING_POOL,
                    "spike 備品 " + i, "amenity", channel.backendNow().minusMinutes(CLAIM_SAMPLES + 1 - i),
                    null, null, null, null)));
        }
        return ids;
    }

    private void assertSeededVisibleAsGuestRequests(String sessionToken, List<UUID> seeded) throws Exception {
        JsonNode list = guestRequests(sessionToken);
        for (UUID id : seeded) {
            JsonNode row = findById(list, id);
            assertNotNull(row, "GET /guest/requests 列得到種好的 " + id);
            assertEquals("pool", row.path("state").asText(), "種好的需求在待認領");
            assertEquals("housekeeping", row.path("dept").asText(), "種好的需求在房務");
            assertEquals("guest", taskSource(id), "種好的需求來源是 guest");
        }
        System.out.printf("[spike] ① 種 %d 張房務待認領，GET /guest/requests 列出 %d 張，欄位 %s，資料表 source 都是 guest%n",
                seeded.size(), list.size(), fieldNames(list.get(0)));
    }

    private void streamAndClaim(String sessionToken, List<UUID> seeded, CreatedStaff amei) throws Exception {
        channel.jwt(amei);
        BlockingQueue<SseEvent> events = new LinkedBlockingQueue<>();
        long connectedNanos = System.nanoTime();
        HttpResponse<Stream<String>> response = http.send(
                HttpRequest.newBuilder(URI.create(backend + "/guest/stream"))
                        .header(GUEST_SESSION_HEADER, sessionToken)
                        .header("Accept", "text/event-stream")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofLines());
        assertEquals(200, response.statusCode(), "GET /guest/stream 帶住客 session 回 200");
        Thread reader = Thread.ofVirtual().start(() -> readEvents(response.body(), events));
        try {
            SseEvent first = nextEvent(events, 5_000);
            SseEvent second = nextEvent(events, 5_000);
            System.out.printf("[spike] ② 連上後依序 %s %s、%s %s%n", first.name(), first.data(), second.name(), second.data());
            assertEquals("init", first.name(), "第一則是 init");
            assertEquals("resync", second.name(), "第二則是 resync");
            assertEquals("/guest/home", second.data().path("refetch").asText(), "resync 要住客重讀 /guest/home");

            List<SseEvent> heartbeats = new ArrayList<>();
            List<Long> eventMs = new ArrayList<>();
            List<Long> refetchMs = new ArrayList<>();
            for (UUID taskId : seeded) {
                long actedNanos = System.nanoTime();
                channel.postTaskAction(taskId, "claim", amei);
                SseEvent task = awaitTaskEvent(events, taskId, heartbeats);
                long toEvent = (task.receivedNanos() - actedNanos) / 1_000_000;
                guestRequests(sessionToken);
                long toRefetch = (System.nanoTime() - actedNanos) / 1_000_000;
                eventMs.add(toEvent);
                refetchMs.add(toRefetch);
                System.out.printf("[spike] ② 阿美認領 %s：收到 task %s 花 %d ms，重讀清單完成 %d ms%n",
                        taskId, task.data(), toEvent, toRefetch);
            }
            long worst = refetchMs.stream().mapToLong(Long::longValue).max().orElseThrow();
            System.out.printf("[spike] ② 動作到收到 task %s ms，到重讀完成 %s ms，最慢 %d ms，門檻 %d ms%n",
                    eventMs, refetchMs, worst, REALTIME_LIMIT_MS);
            assertTrue(worst < REALTIME_LIMIT_MS, "認領到住客重讀完成最慢 " + worst + " ms");

            long waitUntil = connectedNanos + TimeUnit.MILLISECONDS.toNanos(HEARTBEAT_WAIT_MS);
            while (System.nanoTime() < waitUntil) {
                SseEvent event = events.poll(waitUntil - System.nanoTime(), TimeUnit.NANOSECONDS);
                if (event != null) {
                    assertEquals("heartbeat", event.name(), "認領完之後只該再收到心跳，收到 " + event);
                    heartbeats.add(event);
                }
            }
            for (SseEvent heartbeat : heartbeats) {
                System.out.printf("[spike] ② 連上後 %d ms 收到 heartbeat %s%n",
                        (heartbeat.receivedNanos() - connectedNanos) / 1_000_000, heartbeat.data());
                assertFalse(heartbeat.data().has("degraded"), "住客的 heartbeat 沒有 degraded 欄位");
            }
            assertFalse(heartbeats.isEmpty(), "連上 16 秒內收到 heartbeat");
        } finally {
            response.body().close();
            reader.interrupt();
        }
    }

    private SseEvent awaitTaskEvent(BlockingQueue<SseEvent> events, UUID taskId, List<SseEvent> heartbeats)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REALTIME_LIMIT_MS * 2);
        while (true) {
            SseEvent event = nextEvent(events, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
            if (event.name().equals("heartbeat")) {
                heartbeats.add(event);
                continue;
            }
            assertEquals("task", event.name(), "認領之後收到的是 task，收到 " + event);
            if (event.data().path("task_id").asText().equals(taskId.toString())) {
                return event;
            }
            System.out.printf("[spike] ② 等 %s 時先收到別張的 task %s%n", taskId, event.data());
        }
    }

    private SseEvent nextEvent(BlockingQueue<SseEvent> events, long timeoutMs) throws InterruptedException {
        SseEvent event = events.poll(Math.max(timeoutMs, 0), TimeUnit.MILLISECONDS);
        assertNotNull(event, "串流 " + timeoutMs + " ms 內沒有下一則事件");
        return event;
    }

    private void readEvents(Stream<String> lines, BlockingQueue<SseEvent> events) {
        String[] name = {null};
        StringBuilder data = new StringBuilder();
        try {
            lines.forEach(line -> {
                if (line.isEmpty()) {
                    if (name[0] != null) {
                        events.add(new SseEvent(name[0], parse(data.toString()), System.nanoTime()));
                    }
                    name[0] = null;
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    name[0] = line.substring("event:".length()).strip();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring("data:".length()).strip());
                }
            });
        } catch (RuntimeException closed) {
        }
    }

    private boolean submitTwiceWithSameKey(String sessionToken) throws Exception {
        int before = guestRequests(sessionToken).size();
        String key = UUID.randomUUID().toString();
        String body = json.writeValueAsString(Map.of("text", "spike 冪等鍵回放：請送一條毛巾"));
        HttpResponse<String> first = postGuestRequest(sessionToken, key, body);
        HttpResponse<String> second = postGuestRequest(sessionToken, key, body);
        int after = guestRequests(sessionToken).size();
        String replayedHeader = second.headers().firstValue("Idempotency-Replayed").orElse("（沒有）");
        System.out.printf("[spike] ③ 第一次 %d %s%n", first.statusCode(), first.body());
        System.out.printf("[spike] ③ 第二次 %d Idempotency-Replayed=%s %s%n",
                second.statusCode(), replayedHeader, second.body());
        System.out.printf("[spike] ③ 清單 %d 張 -> %d 張%n", before, after);
        assertTrue(first.statusCode() >= 200 && first.statusCode() < 300, "第一次送出成功，回應 " + first.body());
        boolean replayed = "true".equals(replayedHeader)
                && second.statusCode() == first.statusCode()
                && second.body().equals(first.body())
                && after == before + 1;
        System.out.printf("[spike] ③ %s%n", replayed ? "第二次是回放，清單只多一張" : "第二次沒有回放");
        return replayed;
    }

    private HttpResponse<String> postGuestRequest(String sessionToken, String key, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(backend + "/guest/requests"))
                        .header(GUEST_SESSION_HEADER, sessionToken)
                        .header(IDEMPOTENCY_KEY_HEADER, key)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .timeout(Duration.ofSeconds(10))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void openGuestHome(String qrToken) {
        WebProfile profile = WebProfile.current();
        try (Playwright playwright = Playwright.create();
                Browser browser = profile.launch(playwright);
                BrowserContext context = profile.newContext(browser)) {
            Page page = context.newPage();
            List<String> scans = new ArrayList<>();
            page.onResponse(response -> {
                if (response.url().endsWith("/guest/scan")) {
                    scans.add(response.status() + "");
                }
            });
            long started = System.nanoTime();
            page.navigate(WebApp.baseUrl() + "/g/" + qrToken);
            page.getByRole(AriaRole.MAIN).waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.ATTACHED).setTimeout(PAGE_LIMIT_MS * 2));
            page.getByRole(AriaRole.STATUS).waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.DETACHED).setTimeout(PAGE_LIMIT_MS * 2));
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            System.out.printf("[spike] ④ web.profile=%s 引擎=%s 打開 /g/{新代碼} 到首頁容器 %d ms，/guest/scan 回 %s%n",
                    profile.id(), browser.browserType().name(), elapsedMs, scans);
            assertEquals(List.of("200"), scans, "畫面用新代碼換 session 一次、回 200");
            assertTrue(elapsedMs < PAGE_LIMIT_MS, "走到首頁容器花 " + elapsedMs + " ms");
        }
    }

    private JsonNode guestRequests(String sessionToken) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(backend + "/guest/requests"))
                        .header(GUEST_SESSION_HEADER, sessionToken)
                        .timeout(Duration.ofSeconds(10))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode(), "GET /guest/requests 回 200，回應 " + response.body());
        return parse(response.body());
    }

    private String taskSource(UUID taskId) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        System.getProperty("backend.db.url", "jdbc:postgresql://127.0.0.1:5433/tms"),
                        System.getProperty("backend.db.user", "tms"),
                        System.getProperty("backend.db.password", "tms"));
                PreparedStatement statement = connection.prepareStatement("SELECT source FROM tasks WHERE id = ?")) {
            statement.setObject(1, taskId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), "tasks 找得到 " + taskId);
                return rows.getString(1);
            }
        }
    }

    private static JsonNode findById(JsonNode list, UUID id) {
        for (JsonNode row : list) {
            if (row.path("id").asText().equals(id.toString())) {
                return row;
            }
        }
        return null;
    }

    private static List<String> fieldNames(JsonNode row) {
        List<String> names = new ArrayList<>();
        row.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private JsonNode parse(String text) {
        try {
            return json.readTree(text.isEmpty() ? "{}" : text);
        } catch (Exception e) {
            throw new IllegalStateException("解析不了 JSON：" + text, e);
        }
    }
}
