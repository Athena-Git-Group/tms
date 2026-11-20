package com.athena.web.bdd.steps.guest.supply;

import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.athena.web.bdd.support.WhenStart;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 「備品補充」的句子之間要傳的東西，以及只有這個模組用的網路手段：第一張建立之後斷線，
 * 和從 When 開始每一次建立需求請求的送出與結束先後。一個場景一份。回應慢 3 秒由 {@code GuestRequestLimitStub} 做。
 *
 * <p>建立需求的請求都要先經過 {@code GuestRequestLimitStub}（它記下送出的冪等鍵，「後端多了以下需求」靠這份記錄比對），
 * 所以這裡的攔截要放行時一律交給下一個攔截（{@code fallback}），不直接送往後端。
 */
public final class SupplyScenario {

    /** 建立需求請求的一次送出或結束。 */
    public record CreateEvent(boolean started, Request request) {
    }

    private static final String SUPPLY_FEATURES = "specs/truth/features/guest-web/備品補充/";

    private final WebWorld world;

    private final GuestScenario scenario;

    private final WhenStart whenStart;

    private final List<CreateEvent> createEvents = new ArrayList<>();

    private Page cutPage;

    private Predicate<String> cutUrl;

    private Consumer<Route> cutHandler;

    private Boolean disabledAtSecondPress;

    public SupplyScenario(WebWorld world, GuestScenario scenario, WhenStart whenStart) {
        this.world = world;
        this.scenario = scenario;
        this.whenStart = whenStart;
    }

    @Before(order = 3)
    public void recordCreatesFromWhen(Scenario cucumberScenario) {
        if (cucumberScenario.getUri().getPath().contains(SUPPLY_FEATURES)) {
            whenStart.alsoCapture(this::listenToCreates);
        }
    }

    /** 從 When 開始建立需求請求的送出（{@code started}）與結束（回覆或失敗），依瀏覽器發生的先後。 */
    public List<CreateEvent> createEvents() {
        return List.copyOf(createEvents);
    }

    /**
     * 這個分頁的第一次建立需求請求照常送到後端，從它之後這個分頁對後端的每一個請求都在送出時斷線，
     * 直到 {@link #reconnect()}。
     */
    public void disconnectAfterFirstCreate(Page page) {
        boolean[] firstCreateSent = {false};
        cutPage = page;
        cutUrl = SupplyScenario::isBackendUrl;
        cutHandler = route -> {
            if (firstCreateSent[0]) {
                route.abort("connectionfailed");
                return;
            }
            Request request = route.request();
            if (Network.GUEST_CREATE.method().equals(request.method())
                    && Network.GUEST_CREATE.path().equals(URI.create(request.url()).getPath())) {
                firstCreateSent[0] = true;
            }
            route.fallback();
        };
        page.route(cutUrl, cutHandler);
    }

    /** 拿掉 {@link #disconnectAfterFirstCreate(Page)} 的斷線。 */
    public void reconnect() {
        if (cutPage != null && !cutPage.isClosed()) {
            cutPage.unroute(cutUrl, cutHandler);
        }
        cutPage = null;
    }

    public void rememberDisabledAtSecondPress(boolean disabled) {
        disabledAtSecondPress = disabled;
    }

    /** 「連按兩下」的第二下按下時，按鈕是不是停用的；沒有連按過時是空的。 */
    public java.util.Optional<Boolean> disabledAtSecondPress() {
        return java.util.Optional.ofNullable(disabledAtSecondPress);
    }

    private void listenToCreates() {
        if (!scenario.active()) {
            return;
        }
        BrowserContext context = world.page().context();
        context.onRequest(request -> {
            if (isCreate(request)) {
                createEvents.add(new CreateEvent(true, request));
            }
        });
        Consumer<Request> ended = request -> {
            if (isCreate(request)) {
                createEvents.add(new CreateEvent(false, request));
            }
        };
        context.onRequestFinished(ended);
        context.onRequestFailed(ended);
    }

    private static boolean isCreate(Request request) {
        return Network.GUEST_CREATE.method().equals(request.method())
                && Network.GUEST_CREATE.path().equals(URI.create(request.url()).getPath());
    }

    private static boolean isBackendUrl(String url) {
        String path = URI.create(url).getPath();
        return path != null && (path.startsWith("/api/") || path.startsWith("/guest/") || path.startsWith("/staff/"));
    }
}
