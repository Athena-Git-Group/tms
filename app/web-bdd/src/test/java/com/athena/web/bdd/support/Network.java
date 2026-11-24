package com.athena.web.bdd.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Route;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class Network {

    public record Api(String method, String path) {

        boolean matches(String method, String url) {
            return this.method.equals(method) && matchesPath(url);
        }

        boolean matchesPath(String url) {
            String regex = Pattern.quote(path).replace("*", "\\E[^/]+\\Q");
            return Pattern.matches(regex, pathOf(url));
        }
    }

    public static final Api DESK_BOARD = new Api("GET", "/api/v1/desk/board");

    public static final Api DESK_TASK = new Api("GET", "/api/v1/desk/tasks/*");

    public static final Api DESK_QUICK_REPLIES = new Api("GET", "/api/v1/desk/quick-replies");

    public static final Api DESK_ASSIGN = new Api("POST", "/api/v1/desk/tasks/*/assign");

    public static final Api DESK_UNDO_ASSIGN = new Api("POST", "/api/v1/desk/tasks/*/assign/undo");

    public static final Api DESK_ANSWER = new Api("POST", "/api/v1/desk/tasks/*/answer");

    public static final Api DESK_CREATE_REQUESTS = new Api("POST", "/api/v1/desk/requests");

    public static final Api DESK_ROOMS = new Api("GET", "/api/v1/desk/rooms");

    public static final Api DESK_SETTINGS_UPDATE = new Api("PATCH", "/api/v1/desk/settings");

    public static final List<Api> DESK_WRITES = List.of(DESK_ASSIGN, DESK_UNDO_ASSIGN, DESK_ANSWER, DESK_SETTINGS_UPDATE);

    public static final Api STAFF_STREAM = new Api("GET", "/staff/stream");

    public static final Api LOGIN = new Api("POST", "/api/v1/staff-accounts/login");

    public static final Api GUEST_SCAN = new Api("POST", "/guest/scan");

    public static final Api GUEST_HOME = new Api("GET", "/guest/home");

    public static final Api GUEST_REQUESTS = new Api("GET", "/guest/requests");

    public static final Api GUEST_CREATE = new Api("POST", "/guest/requests");

    public static final Api GUEST_CANCEL = new Api("POST", "/guest/requests/*/cancel");

    public static final Api GUEST_RATE = new Api("POST", "/guest/requests/*/rating");

    public static final Api GUEST_STREAM = new Api("GET", "/guest/stream");

    private static final List<Api> LIVE_STREAMS = List.of(STAFF_STREAM, GUEST_STREAM);

    public record SentRequest(Page page, String method, String path, boolean documentLoad) {

        public boolean is(Api api) {
            return api.matches(method, path);
        }

        public boolean isWrite() {
            return !"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method);
        }
    }

    private record Key(Page page, Api api) {
    }

    private record Interception(Predicate<String> url, Consumer<Route> handler, List<Route> held) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Supplier<Page> currentPage;

    private final Map<Key, Interception> interceptions = new LinkedHashMap<>();

    private final List<SentRequest> sent = new ArrayList<>();

    private final Map<Page, Response> lastDeskBoard = new HashMap<>();

    private final Map<Page, Integer> deskBoardsReceived = new HashMap<>();

    private final Map<Page, Interception> backendCutOff = new HashMap<>();

    private final Map<Key, String> lostResponseKeys = new HashMap<>();

    Network(Supplier<Page> currentPage) {
        this.currentPage = currentPage;
    }

    public void respondWithError(Page page, Api api, int status, String code, String message,
            Map<String, Object> context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        if (!context.isEmpty()) {
            body.put("context", context);
        }
        String json = toJson(body);
        intercept(page, api, (route, held) -> route.fulfill(new Route.FulfillOptions()
                .setStatus(status)
                .setContentType("application/json")
                .setBody(json)));
    }

    public void respondWithError(Api api, int status, String code, String message) {
        respondWithError(currentPage.get(), api, status, code, message, Map.of());
    }

    public void disconnect(Page page, Api api) {
        intercept(page, api, (route, held) -> route.abort("connectionfailed"));
    }

    public void disconnect(Api api) {
        disconnect(currentPage.get(), api);
    }

    public void hold(Page page, Api api) {
        intercept(page, api, (route, held) -> held.add(route));
    }

    public void hold(Api api) {
        hold(currentPage.get(), api);
    }

    public void handle(Page page, Api api, Consumer<Route> handler) {
        intercept(page, api, (route, held) -> handler.accept(route));
    }

    public void deliverThenLoseResponse(Page page, Api api) {
        Key key = new Key(page, api);
        lostResponseKeys.remove(key);
        intercept(page, api, (route, held) -> {
            if (lostResponseKeys.containsKey(key)) {
                route.resume();
                return;
            }
            lostResponseKeys.put(key, route.request().headers().getOrDefault("idempotency-key", ""));
            route.fetch();
            route.abort("failed");
        });
    }

    public void deliverThenLoseResponse(Api api) {
        deliverThenLoseResponse(currentPage.get(), api);
    }

    public Optional<String> lostResponseKey(Page page, Api api) {
        return Optional.ofNullable(lostResponseKeys.get(new Key(page, api)));
    }

    public Optional<String> lostResponseKey(Api api) {
        return lostResponseKey(currentPage.get(), api);
    }

    public int heldCount(Page page, Api api) {
        Interception interception = interceptions.get(new Key(page, api));
        return interception == null ? 0 : interception.held().size();
    }

    public int heldCount(Api api) {
        return heldCount(currentPage.get(), api);
    }

    /**
     * 等到畫面真的發出請求並且被扣住，逾時就失敗。用在要確定「畫面正在等這支端點」之後才往下檢查的地方。
     *
     * <p>等的時候一定要經過這個方法或 Playwright 自己的等待，不能用 {@code Thread.sleep} 迴圈去看
     * {@link #heldCount(Page, Api)}：Playwright 只有在測試呼叫它的時候才處理瀏覽器送來的事件，
     * 測試自己睡著的那段時間，被扣住的請求與請求紀錄都不會增加。
     */
    public void waitUntilHeld(Page page, Api api) {
        page.waitForCondition(() -> heldCount(page, api) > 0);
    }

    public void waitUntilHeld(Api api) {
        waitUntilHeld(currentPage.get(), api);
    }

    public void passThrough(Page page, Api api) {
        Interception interception = interceptions.remove(new Key(page, api));
        if (interception == null) {
            return;
        }
        if (!page.isClosed()) {
            page.unroute(interception.url(), interception.handler());
        }
        for (Route route : interception.held()) {
            try {
                route.resume();
            } catch (RuntimeException alreadyGone) {
            }
        }
    }

    public void passThrough(Api api) {
        passThrough(currentPage.get(), api);
    }

    public void cutOffBackend(Page page) {
        restoreBackend(page);
        Predicate<String> url = Network::isBackendUrl;
        Consumer<Route> handler = route -> route.abort("connectionfailed");
        page.route(url, handler);
        backendCutOff.put(page, new Interception(url, handler, List.of()));
    }

    public void restoreBackend(Page page) {
        Interception cut = backendCutOff.remove(page);
        if (cut != null && !page.isClosed()) {
            page.unroute(cut.url(), cut.handler());
        }
    }

    private static boolean isBackendUrl(String url) {
        String path = pathOf(url);
        return path.startsWith("/api/") || path.startsWith("/guest/") || path.startsWith("/staff/");
    }

    public void pauseLiveUpdates(Page page) {
        hold(page, STAFF_STREAM);
        hold(page, DESK_BOARD);
        cutLiveStream(page);
    }

    public void pauseLiveUpdates() {
        pauseLiveUpdates(currentPage.get());
    }

    public void resumeLiveUpdates(Page page) {
        passThrough(page, STAFF_STREAM);
        passThrough(page, DESK_BOARD);
    }

    public void resumeLiveUpdates() {
        resumeLiveUpdates(currentPage.get());
    }

    public void cutLiveStream(Page page) {
        page.evaluate("() => { if (window.__webBddCutLiveStreams) window.__webBddCutLiveStreams(); }");
    }

    public void cutLiveStream() {
        cutLiveStream(currentPage.get());
    }

    public int sendLiveSignal(Page page, String event, String dataJson) {
        String text = "event:" + event + "\ndata:" + dataJson + "\n\n";
        Object sentTo = page.evaluate("text => window.__webBddSendLiveSignal ? window.__webBddSendLiveSignal(text) : 0",
                text);
        return ((Number) sentTo).intValue();
    }

    public void holdLiveSignals(Page page) {
        page.evaluate("() => { if (window.__webBddHoldLiveSignals) window.__webBddHoldLiveSignals(true); }");
    }

    public void releaseLiveSignals(Page page) {
        page.evaluate("() => { if (window.__webBddHoldLiveSignals) window.__webBddHoldLiveSignals(false); }");
    }

    public int liveStreamCount(Page page) {
        Object count = page.evaluate("() => window.__webBddLiveStreamCount ? window.__webBddLiveStreamCount() : 0");
        return ((Number) count).intValue();
    }

    public int liveChunksDelivered(Page page) {
        Object count = page.evaluate(
                "() => window.__webBddLiveChunksDelivered ? window.__webBddLiveChunksDelivered() : 0");
        return ((Number) count).intValue();
    }

    public record LiveEvent(String event, String data) {
    }

    public List<LiveEvent> liveEventsFromBackend(Page page) {
        Object text = page.evaluate("() => window.__webBddLiveReceived || ''");
        List<LiveEvent> events = new ArrayList<>();
        for (String block : String.valueOf(text).replace("\r\n", "\n").split("\n\n")) {
            String event = "message";
            StringBuilder data = new StringBuilder();
            for (String line : block.split("\n")) {
                if (line.startsWith("event:")) {
                    event = line.substring("event:".length()).strip();
                } else if (line.startsWith("data:")) {
                    data.append(data.isEmpty() ? "" : "\n").append(line.substring("data:".length()).strip());
                }
            }
            if (!data.isEmpty()) {
                events.add(new LiveEvent(event, data.toString()));
            }
        }
        return events;
    }

    public Optional<String> lastDeskBoardBody(Page page) {
        Response response = lastDeskBoard.get(page);
        return response == null ? Optional.empty() : Optional.of(response.text());
    }

    public int deskBoardsReceived(Page page) {
        return deskBoardsReceived.getOrDefault(page, 0);
    }

    public List<SentRequest> sentRequests() {
        return List.copyOf(sent);
    }

    public int sentRequestCount() {
        return sent.size();
    }

    public List<SentRequest> sentRequestsSince(int count) {
        return List.copyOf(sent.subList(Math.min(count, sent.size()), sent.size()));
    }

    /**
     * 每個頁面載入前先包住 {@code fetch}，測試才有辦法切斷已經連上的事件串流。
     *
     * <p>瀏覽器的請求攔截與離線模擬都只影響之後才發出的請求，切不斷已經在收資料的串流。
     * 所以事件串流的回應改由這段程式轉手：資料照原樣一塊一塊交給畫面，測試要切斷時讓畫面那一頭讀到連線錯誤，
     * 同時把真正的連線關掉。其他端點的 {@code fetch} 原樣通過。
     * 同一段程式也讓測試能代替後端送一則訊號進去，或把後端送來的訊號先扣住不交給畫面。
     *
     * <p>切斷時已經送出、但回應還沒回來的串流請求也算在切斷之內：它的回應回來時畫面直接讀到連線錯誤。
     * 這種請求已經過了請求攔截，攔截扣不住它；放它連上的話，畫面會在「暫停跟上」之後照樣收到後端推來的卡片。
     */
    private static final String LIVE_STREAM_CUT_SCRIPT = """
            (() => {
              const realFetch = window.fetch;
              const live = new Set();
              let cutCount = 0;
              window.__webBddCutLiveStreams = () => {
                cutCount++;
                for (const cut of Array.from(live)) cut();
              };
              const taps = new Set();
              const encoder = new TextEncoder();
              const decoder = new TextDecoder();
              window.__webBddLiveReceived = '';
              const received = (value) => {
                window.__webBddLiveReceived += decoder.decode(value, { stream: true });
              };
              let held = false;
              window.__webBddSendLiveSignal = (text) => {
                for (const tap of Array.from(taps)) tap.send(text);
                return taps.size;
              };
              window.__webBddHoldLiveSignals = (on) => {
                held = on;
                if (!on) for (const tap of Array.from(taps)) tap.flush();
              };
              window.__webBddLiveStreamCount = () => taps.size;
              window.__webBddLiveChunksDelivered = () =>
                Array.from(taps).reduce((most, tap) => Math.max(most, tap.delivered), 0);
              window.fetch = async function (input, init) {
                const cutsBefore = cutCount;
                const response = await realFetch.call(this, input, init);
                let path = '';
                try {
                  const url = typeof input === 'string' ? input : (input && input.url) || String(input);
                  path = new URL(url, location.href).pathname;
                } catch (e) {
                  return response;
                }
                if (!%s.includes(path) || !response.body) return response;
                if (cutCount !== cutsBefore) {
                  response.body.cancel().catch(() => {});
                  throw new TypeError('network error');
                }
                const reader = response.body.getReader();
                let cut = null;
                let tap = null;
                let finished = false;
                const pending = [];
                const finish = () => { finished = true; live.delete(cut); taps.delete(tap); };
                const body = new ReadableStream({
                  start(controller) {
                    cut = () => {
                      if (finished) return;
                      finish();
                      controller.error(new TypeError('network error'));
                      reader.cancel().catch(() => {});
                    };
                    live.add(cut);
                    tap = {
                      delivered: 0,
                      send: (text) => { if (!finished) controller.enqueue(encoder.encode(text)); },
                      flush: () => {
                        if (finished) return;
                        for (const value of pending.splice(0)) { controller.enqueue(value); received(value); }
                      },
                    };
                    taps.add(tap);
                  },
                  async pull(controller) {
                    try {
                      for (;;) {
                        const { done, value } = await reader.read();
                        if (finished) return;
                        if (done) { finish(); controller.close(); return; }
                        if (held) { pending.push(value); continue; }
                        controller.enqueue(value);
                        received(value);
                        tap.delivered++;
                        return;
                      }
                    } catch (e) {
                      if (finished) return;
                      finish();
                      controller.error(e);
                    }
                  },
                  cancel(reason) {
                    finish();
                    return reader.cancel(reason);
                  },
                });
                return new Response(body, {
                  status: response.status,
                  statusText: response.statusText,
                  headers: response.headers,
                });
              };
            })();
            """.formatted(toJson(LIVE_STREAMS.stream().map(Api::path).toList()));

    void watch(BrowserContext context) {
        context.onRequest(this::record);
        context.onResponse(this::recordDeskBoard);
        context.addInitScript(LIVE_STREAM_CUT_SCRIPT);
    }

    private void record(Request request) {
        String path = pathOf(request.url());
        if (SessionInjector.STORAGE_PAGE_PATH.equals(path)) {
            return;
        }
        Page page = null;
        try {
            page = request.frame().page();
        } catch (RuntimeException noFrame) {
        }
        sent.add(new SentRequest(page, request.method(), path, request.isNavigationRequest()));
    }

    private void recordDeskBoard(Response response) {
        if (!response.ok() || !DESK_BOARD.matches(response.request().method(), response.url())) {
            return;
        }
        Page page;
        try {
            page = response.frame().page();
        } catch (RuntimeException noFrame) {
            return;
        }
        lastDeskBoard.put(page, response);
        deskBoardsReceived.merge(page, 1, Integer::sum);
    }

    private interface Behaviour {
        void handle(Route route, List<Route> held);
    }

    private void intercept(Page page, Api api, Behaviour behaviour) {
        passThrough(page, api);
        List<Route> held = new ArrayList<>();
        Predicate<String> url = api::matchesPath;
        Consumer<Route> handler = route -> {
            if (api.method().equals(route.request().method())) {
                behaviour.handle(route, held);
            } else {
                route.fallback();
            }
        };
        page.route(url, handler);
        interceptions.put(new Key(page, api), new Interception(url, handler, held));
    }

    private static String pathOf(String url) {
        if (url.startsWith("/")) {
            int query = url.indexOf('?');
            return query < 0 ? url : url.substring(0, query);
        }
        try {
            String path = URI.create(url).getPath();
            return path == null ? "" : path;
        } catch (IllegalArgumentException notAUrl) {
            return "";
        }
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("錯誤回應轉不成 JSON：" + value, e);
        }
    }
}
