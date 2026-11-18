package com.athena.web.bdd.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 控制某一個分頁打後端的請求：讓它收到指定的錯誤、讓它斷線、把回應扣住不放，或恢復正常。
 *
 * <p>只影響指定的那一個分頁，而且只影響設定之後才送出的請求；同一個場景的其他分頁、
 * 測試自己用 {@code BackendChannel} 打後端的請求都不受影響。場景結束時分頁關掉，設定跟著消失。
 *
 * <p>同一個分頁對同一支端點只有一種設定：再設一次會換掉前一種，被扣住的請求先放行。
 *
 * <p>另外記下這個場景所有分頁送出過的請求，給「這段期間沒有送出任何寫入」這類檢查用。
 */
public final class Network {

    /**
     * 一支後端端點：HTTP 方法加路徑。路徑裡的 {@code *} 代表剛好一段，例如單的 id。
     *
     * @param method HTTP 方法，大寫
     * @param path 以 {@code /} 開頭的路徑，不含查詢參數
     */
    public record Api(String method, String path) {

        boolean matches(String method, String url) {
            return this.method.equals(method) && matchesPath(url);
        }

        boolean matchesPath(String url) {
            String regex = Pattern.quote(path).replace("*", "\\E[^/]+\\Q");
            return Pattern.matches(regex, pathOf(url));
        }
    }

    /** 看板資料，總覽看板與待派單頁共用（契約的 {@code getDeskBoard}）。 */
    public static final Api DESK_BOARD = new Api("GET", "/api/v1/desk/board");

    /** 單筆詳情（{@code readDeskTask}）。 */
    public static final Api DESK_TASK = new Api("GET", "/api/v1/desk/tasks/*");

    /** 常用回覆（{@code listDeskQuickReplies}）。 */
    public static final Api DESK_QUICK_REPLIES = new Api("GET", "/api/v1/desk/quick-replies");

    /** 派單（{@code deskAssignTask}）。 */
    public static final Api DESK_ASSIGN = new Api("POST", "/api/v1/desk/tasks/*/assign");

    /** 派出後復原（{@code deskUndoAssign}）。 */
    public static final Api DESK_UNDO_ASSIGN = new Api("POST", "/api/v1/desk/tasks/*/assign/undo");

    /** 當場回答（{@code deskAnswerTask}）。 */
    public static final Api DESK_ANSWER = new Api("POST", "/api/v1/desk/tasks/*/answer");

    /** 櫃檯的三種寫入：派單、復原、當場回答。 */
    public static final List<Api> DESK_WRITES = List.of(DESK_ASSIGN, DESK_UNDO_ASSIGN, DESK_ANSWER);

    /** 即時訊號的事件串流（{@code staffStream}）。 */
    public static final Api STAFF_STREAM = new Api("GET", "/staff/stream");

    /** 登入。 */
    public static final Api LOGIN = new Api("POST", "/api/v1/staff-accounts/login");

    /**
     * 分頁送出過的一個請求。
     *
     * @param page 送出它的分頁；不是由分頁送出的請求是 {@code null}
     * @param method HTTP 方法，大寫
     * @param path 路徑，不含查詢參數
     * @param documentLoad 是不是整頁載入（開網址、重新整理），不是畫面自己打的 API
     */
    public record SentRequest(Page page, String method, String path, boolean documentLoad) {

        public boolean is(Api api) {
            return api.matches(method, path);
        }

        /** 會改後端資料的請求。 */
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

    Network(Supplier<Page> currentPage) {
        this.currentPage = currentPage;
    }

    /**
     * 讓這支端點之後的每一次請求都收到後端的錯誤回應，內容是契約 {@code Error} 的形狀。請求不會到達後端。
     *
     * @param code 契約的錯誤碼，例如 {@code task_not_in_triage}
     * @param context 錯誤附帶的資料，沒有就給空的 Map
     */
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

    /** 讓這支端點之後的每一次請求在送出時斷線：請求沒有到達後端，畫面收到的是連線失敗而不是錯誤回應。 */
    public void disconnect(Page page, Api api) {
        intercept(page, api, (route, held) -> route.abort("connectionfailed"));
    }

    public void disconnect(Api api) {
        disconnect(currentPage.get(), api);
    }

    /**
     * 把這支端點之後的請求扣住：請求不送往後端，畫面一直等不到回應。
     * 用 {@link #passThrough(Page, Api)} 放行後，被扣住的請求才照常送出；一直不放就扣到場景結束。
     */
    public void hold(Page page, Api api) {
        intercept(page, api, (route, held) -> held.add(route));
    }

    public void hold(Api api) {
        hold(currentPage.get(), api);
    }

    /** 這支端點目前被扣住、還沒放行的請求有幾個。 */
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

    /** 拿掉這支端點的設定，恢復成照常打後端；被扣住的請求這時送出。沒有設定過就什麼都不做。 */
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
                // 畫面已經自己放棄這個請求（例如換頁），沒有東西可以放行。
            }
        }
    }

    public void passThrough(Api api) {
        passThrough(currentPage.get(), api);
    }

    /**
     * 讓這個分頁的畫面停止跟上後端的變化，寫入不受影響。之後後端的單怎麼變，畫面都停在暫停當下的樣子。
     *
     * <p>做三件事：事件串流與看板資料之後的請求都扣住，再把已經連上的事件串流切斷。
     * 只扣請求不夠，因為攔截只管得到之後才發出的請求，已經連上的那一條串流會繼續把訊號送進畫面。
     * 切斷之後畫面會照自己的規則重連與重抓，而那些請求都被扣住，所以什麼也拿不到。
     */
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

    /**
     * 把這個分頁已經連上的事件串流從中切斷。畫面看到的和後端行程突然消失時一樣：
     * 正在讀的串流丟出連線錯誤。後端本身不受影響，畫面重連就連得上。
     */
    public void cutLiveStream(Page page) {
        page.evaluate("() => { if (window.__webBddCutLiveStreams) window.__webBddCutLiveStreams(); }");
    }

    public void cutLiveStream() {
        cutLiveStream(currentPage.get());
    }

    /**
     * 代替後端在這個分頁已經連上的事件串流裡送一則訊號，給「後端宣告降級」這類測試沒辦法叫真的後端做的情況用。
     * 畫面還沒連上串流時這一則沒有地方送，所以回傳送進了幾條連線，讓步驟可以當場失敗而不是讓斷言去猜。
     *
     * @param event 訊號名稱，例如 {@code degraded}、{@code heartbeat}、{@code task}
     * @param dataJson 訊號內容，一行 JSON，形狀照契約對應的 {@code StaffStream*}
     */
    public int sendLiveSignal(Page page, String event, String dataJson) {
        // 照後端送的格式寫：畫面若只認後端那種寫法，測試送的也要認得出來。
        String text = "event:" + event + "\ndata:" + dataJson + "\n\n";
        Object sentTo = page.evaluate("text => window.__webBddSendLiveSignal ? window.__webBddSendLiveSignal(text) : 0",
                text);
        return ((Number) sentTo).intValue();
    }

    /**
     * 讓這個分頁的事件串流連線維持著，但後端送來的訊號（含心跳）一則都不交給畫面，先扣在測試這一頭。
     * 用在「連線還在、卻太久沒有訊號」這種切斷連線模擬不出來的情況；之後重連的串流也一樣扣住，
     * 直到呼叫 {@link #releaseLiveSignals(Page)}。
     */
    public void holdLiveSignals(Page page) {
        page.evaluate("() => { if (window.__webBddHoldLiveSignals) window.__webBddHoldLiveSignals(true); }");
    }

    /** 不再扣住訊號，扣著的那些照原來的順序交給畫面。 */
    public void releaseLiveSignals(Page page) {
        page.evaluate("() => { if (window.__webBddHoldLiveSignals) window.__webBddHoldLiveSignals(false); }");
    }

    /** 這個分頁現在有幾條連著的事件串流，給要等畫面重新連上之後才往下做的步驟用。 */
    public int liveStreamCount(Page page) {
        Object count = page.evaluate("() => window.__webBddLiveStreamCount ? window.__webBddLiveStreamCount() : 0");
        return ((Number) count).intValue();
    }

    /**
     * 連著的事件串流裡，後端已經交給畫面幾塊資料（取最多的那一條）。
     * 剛重連時用它等後端的 {@code init}、{@code resync} 先到，測試再送訊號，畫面收到的先後才和真的後端一樣。
     */
    public int liveChunksDelivered(Page page) {
        Object count = page.evaluate(
                "() => window.__webBddLiveChunksDelivered ? window.__webBddLiveChunksDelivered() : 0");
        return ((Number) count).intValue();
    }

    /** 這個場景到目前為止所有分頁送出過的請求，依送出的先後。回傳的是當下的副本。 */
    public List<SentRequest> sentRequests() {
        return List.copyOf(sent);
    }

    /** 到目前為止送出過幾個請求。先記下這個數字，之後用 {@link #sentRequestsSince(int)} 取這之後的。 */
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
     * <p>畫面拿到的串流回應是重新包過的，狀態碼與標頭相同，但 {@code url} 與 {@code redirected} 是空的。
     */
    private static final String LIVE_STREAM_CUT_SCRIPT = """
            (() => {
              const realFetch = window.fetch;
              const live = new Set();
              window.__webBddCutLiveStreams = () => {
                for (const cut of Array.from(live)) cut();
              };
              const taps = new Set();
              const encoder = new TextEncoder();
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
                const response = await realFetch.call(this, input, init);
                let path = '';
                try {
                  const url = typeof input === 'string' ? input : (input && input.url) || String(input);
                  path = new URL(url, location.href).pathname;
                } catch (e) {
                  return response;
                }
                if (path !== '%s' || !response.body) return response;
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
                      flush: () => { if (!finished) for (const value of pending.splice(0)) controller.enqueue(value); },
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
            """.formatted(STAFF_STREAM.path());

    /** 新開的 context 接上請求紀錄，並讓它之後載入的每個頁面都能被切斷事件串流。 */
    void watch(BrowserContext context) {
        context.onRequest(this::record);
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
            // 不是由分頁送出的請求沒有所屬的分頁。
        }
        sent.add(new SentRequest(page, request.method(), path, request.isNavigationRequest()));
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
