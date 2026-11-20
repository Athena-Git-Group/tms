package com.athena.web.bdd.steps.guest.entry;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.athena.web.bdd.support.WhenStart;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 從 When 開始，住客的畫面出現過什麼、分頁向後端送過哪些請求。給「過程中沒有…」「沒有輸入或按過任何東西」
 * 「請求沒有帶員工的登入憑證」這三句結果句用：它們問的是整段過程，不只是最後的畫面。
 *
 * <p>畫面每變一次（DOM 有變動），頁面裡的程式就把網址路徑、看得到的全部字與看得到的輸入欄數量交回測試記下來；
 * 整頁重新載入、關掉分頁再開新分頁都照樣記，因為程式是掛在整個瀏覽器環境上的。
 * 只在「進入層」的場景掛上，其他模組的場景不多花這個成本。
 */
public final class ScreenWatch {

    /** 畫面上的一個樣子。 */
    public record Seen(Page page, String path, List<String> lines, int inputs) {
    }

    private static final String ENTRY_FEATURES = "specs/truth/features/guest-web/進入層/";

    private static final String BINDING = "__webBddGuestEntrySeen";

    private static final String OBSERVER = """
            (() => {
              if (window.__webBddGuestEntryWatching) return;
              window.__webBddGuestEntryWatching = true;
              const report = () => {
                if (!document.body || typeof window.%1$s !== 'function') return;
                const inputs = Array.from(document.querySelectorAll(
                    'input:not([type=hidden]), textarea, select, [contenteditable=""], [contenteditable="true"]'))
                  .filter(el => { const r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0; }).length;
                window.%1$s(location.pathname, document.body.innerText, inputs);
              };
              const start = () => new MutationObserver(report).observe(document,
                  { subtree: true, childList: true, characterData: true, attributes: true });
              start();
              if (document.readyState !== 'loading') report();
              else document.addEventListener('DOMContentLoaded', report);
            })();
            """.formatted(BINDING);

    private final WebWorld world;

    private final GuestScenario scenario;

    private final WhenStart whenStart;

    private final List<Seen> seen = new ArrayList<>();

    private final List<Request> backendRequests = new ArrayList<>();

    private final Set<BrowserContext> watched = new HashSet<>();

    private boolean entryScenario;

    private boolean started;

    private String pathAtStart = "";

    private List<String> linesAtStart = List.of();

    public ScreenWatch(WebWorld world, GuestScenario scenario, WhenStart whenStart) {
        this.world = world;
        this.scenario = scenario;
        this.whenStart = whenStart;
    }

    @Before(order = 3)
    public void watchFromWhen(Scenario cucumberScenario) {
        entryScenario = cucumberScenario.getUri().getPath().contains(ENTRY_FEATURES);
        if (entryScenario) {
            whenStart.alsoCapture(this::start);
        }
    }

    /** 從 When 開始畫面出現過的每一個樣子，依先後；第一筆可能就是 When 開始那一刻的畫面（{@link #linesAtStart()}）。 */
    public List<Seen> seen() {
        requireStarted();
        return List.copyOf(seen);
    }

    /** When 開始那一刻目前的分頁停在哪個路徑；還沒開過分頁時是空字串。 */
    public String pathAtStart() {
        requireStarted();
        return pathAtStart;
    }

    /** When 開始那一刻畫面上看得到的每一行字；還沒開過分頁時是空的。 */
    public List<String> linesAtStart() {
        requireStarted();
        return linesAtStart;
    }

    /** 從 When 開始，這個分頁對後端（{@code /guest/}、{@code /api/}、{@code /staff/}）送出的請求，依先後。 */
    public List<Request> backendRequests(Page page) {
        requireStarted();
        return backendRequests.stream().filter(request -> pageOf(request) == page).toList();
    }

    private void start() {
        if (!scenario.active()) {
            return;
        }
        Page page = world.page();
        if (page.url().startsWith(WebApp.baseUrl() + "/")) {
            pathAtStart = URI.create(page.url()).getPath();
            linesAtStart = ShellFrame.linesOf(page.locator("body").innerText());
        }
        watch(page);
        started = true;
    }

    private void watch(Page page) {
        BrowserContext context = page.context();
        if (!watched.add(context)) {
            return;
        }
        context.exposeBinding(BINDING, (source, args) -> {
            seen.add(new Seen(source.page(), String.valueOf(args[0]), ShellFrame.linesOf(String.valueOf(args[1])),
                    ((Number) args[2]).intValue()));
            return null;
        });
        context.addInitScript(OBSERVER);
        context.onRequest(this::record);
        try {
            page.evaluate(OBSERVER);
        } catch (PlaywrightException notReady) {
            // 正在換頁的分頁由上面的 addInitScript 在新頁面掛上。
        }
    }

    private void record(Request request) {
        String path = URI.create(request.url()).getPath();
        if (path != null && (path.startsWith("/guest/") || path.startsWith("/api/") || path.startsWith("/staff/"))) {
            backendRequests.add(request);
        }
    }

    private static Page pageOf(Request request) {
        try {
            return request.frame().page();
        } catch (RuntimeException noFrame) {
            return null;
        }
    }

    private void requireStarted() {
        if (!started) {
            throw new AssertionError("這個場景沒有從 When 開始記錄畫面：它不在「進入層」底下，或前置之後沒有任何「當」步驟");
        }
    }
}
