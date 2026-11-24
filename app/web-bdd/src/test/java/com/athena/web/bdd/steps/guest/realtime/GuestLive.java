package com.athena.web.bdd.steps.guest.realtime;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

public class GuestLive {

    static final double CONNECT_TIMEOUT_MS = 15_000;

    static final double FIRST_POLL_TIMEOUT_MS = 10_000;

    private final WebWorld world;

    private Instant whenAt;

    private int requestsAtWhen = -1;

    private Page pageAtWhen;

    private List<String> headingsAtWhen = List.of();

    public GuestLive(WebWorld world) {
        this.world = world;
    }

    public void markWhen() {
        markWhen(world.page());
    }

    public void markWhen(Page guestPage) {
        pageAtWhen = guestPage;
        headingsAtWhen = headings(pageAtWhen);
        requestsAtWhen = world.network().sentRequestCount();
        whenAt = Instant.now();
    }

    public Instant whenAt() {
        requireWhen();
        return whenAt;
    }

    public long requestsSinceWhen(Network.Api api) {
        requireWhen();
        return world.network().sentRequestsSince(requestsAtWhen).stream()
                .filter(request -> request.page() == pageAtWhen && request.is(api))
                .count();
    }

    public List<String> headingsAtWhen() {
        requireWhen();
        return headingsAtWhen;
    }

    public static List<String> headings(Page page) {
        Locator headings = page.getByRole(AriaRole.HEADING).filter(new Locator.FilterOptions().setVisible(true));
        return headings.all().stream().map(heading -> ShellFrame.normalizeSpaces(heading.innerText())).toList();
    }

    public static int visibleNoticeLayers(Page page, GuestFrame frame) {
        int count = frame.toasts().count();
        for (AriaRole role : List.of(AriaRole.ALERT, AriaRole.DIALOG, AriaRole.ALERTDIALOG)) {
            count += page.getByRole(role).filter(new Locator.FilterOptions().setVisible(true)).count();
        }
        return count;
    }

    public void cutAndBlockReconnect() {
        Page page = world.page();
        waitUntilStreamInUse(page, "切斷之前，畫面一直沒有連上住客串流，或後端的 init 沒有送到");
        world.network().disconnect(page, Network.GUEST_STREAM);
        world.network().cutLiveStream(page);
    }

    public void allowReconnectAndWaitForInit() {
        Page page = world.page();
        Network network = world.network();
        long initsBefore = initsReceived(page);
        network.releaseLiveSignals(page);
        network.passThrough(page, Network.GUEST_STREAM);
        waitFor(page, () -> network.liveStreamCount(page) > 0 && initsReceived(page) > initsBefore,
                CONNECT_TIMEOUT_MS, "放行重連之後，畫面沒有重新連上住客串流，或後端的 init 沒有送到");
    }

    public void waitUntilStreamInUse(Page page, String message) {
        Network network = world.network();
        waitFor(page, () -> network.liveStreamCount(page) > 0 && network.liveChunksDelivered(page) > 0,
                CONNECT_TIMEOUT_MS, message);
    }

    public void sendSignal(String event, String dataJson) {
        Page page = world.page();
        waitUntilStreamInUse(page, "畫面一直沒有連上住客串流，送不出「" + event + "」訊號");
        if (world.network().sendLiveSignal(page, event, dataJson) == 0) {
            throw new AssertionError("畫面沒有連著住客串流，送不出「" + event + "」訊號");
        }
    }

    public void waitForNextRequest(Network.Api api, String message) {
        Page page = world.page();
        Network network = world.network();
        int before = network.sentRequestCount();
        waitFor(page, () -> network.sentRequestsSince(before).stream()
                .anyMatch(request -> request.page() == page && request.is(api)), FIRST_POLL_TIMEOUT_MS, message);
    }

    private long initsReceived(Page page) {
        return world.network().liveEventsFromBackend(page).stream().filter(event -> "init".equals(event.event())).count();
    }

    private void requireWhen() {
        if (whenAt == null) {
            throw new IllegalStateException("這個場景的 When 沒有記下送出的時刻：「幾秒內」與「這段期間」要接在即時更新模組的 When 之後");
        }
    }

    static void waitFor(Page page, BooleanSupplier condition, double timeoutMs, String message) {
        try {
            page.waitForCondition(condition, new Page.WaitForConditionOptions().setTimeout(timeoutMs));
        } catch (TimeoutError e) {
            throw new AssertionError(message + "（等了 " + (long) (timeoutMs / 1000) + " 秒）", e);
        }
    }
}
