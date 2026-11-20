package com.athena.web.bdd.steps.guest.realtime;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 住客這支手機的即時連線，以及「幾秒內」從哪一刻起算。
 *
 * <p>串流操作和櫃檯的 {@code steps.realtime.LiveConnection} 同一種做法，但對的是住客的串流
 * {@link Network#GUEST_STREAM}：已經連上的串流經 {@link Network} 包住 {@code fetch} 的那一段切斷，路由攔截只拿來擋之後的重連。
 *
 * <p>規格的「幾秒內」從 When 送出的那一刻起算，不從結果句開始檢查時起算，所以 When 要先以 {@link #markWhen()} 記下那一刻。
 */
public class GuestLive {

    /** 畫面連上或重新連上住客串流最久等這麼久；產品重連的間隔是秒級，後端在本機。 */
    static final double CONNECT_TIMEOUT_MS = 15_000;

    /** 串流斷掉之後等畫面開始定期重讀最久等這麼久：重讀每 4 秒一次，多留一個間隔給忙碌的機器。 */
    static final double FIRST_POLL_TIMEOUT_MS = 10_000;

    private final WebWorld world;

    private Instant whenAt;

    private int requestsAtWhen = -1;

    private Page pageAtWhen;

    private List<String> headingsAtWhen = List.of();

    public GuestLive(WebWorld world) {
        this.world = world;
    }

    /** 記下 When 送出的這一刻、到這時為止送出過幾個請求、畫面上的標題；同一個場景再記一次就以後來的為準。 */
    public void markWhen() {
        markWhen(world.page());
    }

    /** 同上，但結果句要看的是指定的分頁，例如動作在另一支手機上做、結果要看「住客」那一支的時候。 */
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

    /** When 那一刻目前的分頁從那時起送出過的這支端點的請求有幾個。 */
    public long requestsSinceWhen(Network.Api api) {
        requireWhen();
        return world.network().sentRequestsSince(requestsAtWhen).stream()
                .filter(request -> request.page() == pageAtWhen && request.is(api))
                .count();
    }

    /** When 那一刻畫面上的標題（{@code h1}、{@code h2}），由上到下。 */
    public List<String> headingsAtWhen() {
        requireWhen();
        return headingsAtWhen;
    }

    /** 這個分頁現在看得到的標題（角色 {@code heading}，任何層級），由上到下。 */
    public static List<String> headings(Page page) {
        Locator headings = page.getByRole(AriaRole.HEADING).filter(new Locator.FilterOptions().setVisible(true));
        return headings.all().stream().map(heading -> ShellFrame.normalizeSpaces(heading.innerText())).toList();
    }

    /** 畫面上看得到的提示浮層（有字的，載入中的轉圈不算）、提示框或對話框一共幾個。 */
    public static int visibleNoticeLayers(Page page, GuestFrame frame) {
        int count = frame.toasts().count();
        for (AriaRole role : List.of(AriaRole.ALERT, AriaRole.DIALOG, AriaRole.ALERTDIALOG)) {
            count += page.getByRole(role).filter(new Locator.FilterOptions().setVisible(true)).count();
        }
        return count;
    }

    /**
     * 切斷這個分頁正在用的住客串流，之後畫面再怎麼重連都連不上，其他住客介面照常讀得到。
     * 切斷只切得到後端已經回應的串流，所以先等這條連線收過後端的資料；先擋重連再切斷，以免畫面在兩步之間就重新連上。
     */
    public void cutAndBlockReconnect() {
        Page page = world.page();
        waitUntilStreamInUse(page, "切斷之前，畫面一直沒有連上住客串流，或後端的 init 沒有送到");
        world.network().disconnect(page, Network.GUEST_STREAM);
        world.network().cutLiveStream(page);
    }

    /** 放行重連（之前扣著的訊號也一併放掉），等畫面重新連上、後端在新連線送來的 {@code init} 交給畫面。 */
    public void allowReconnectAndWaitForInit() {
        Page page = world.page();
        Network network = world.network();
        long initsBefore = initsReceived(page);
        network.releaseLiveSignals(page);
        network.passThrough(page, Network.GUEST_STREAM);
        waitFor(page, () -> network.liveStreamCount(page) > 0 && initsReceived(page) > initsBefore,
                CONNECT_TIMEOUT_MS, "放行重連之後，畫面沒有重新連上住客串流，或後端的 init 沒有送到");
    }

    /** 等到畫面連著住客串流，而且後端已經交了資料給這條連線。 */
    public void waitUntilStreamInUse(Page page, String message) {
        Network network = world.network();
        waitFor(page, () -> network.liveStreamCount(page) > 0 && network.liveChunksDelivered(page) > 0,
                CONNECT_TIMEOUT_MS, message);
    }

    /** 代替後端在目前分頁的串流裡送一則訊號；畫面沒有連著串流時直接失敗，不讓這一則無聲地落空。 */
    public void sendSignal(String event, String dataJson) {
        Page page = world.page();
        waitUntilStreamInUse(page, "畫面一直沒有連上住客串流，送不出「" + event + "」訊號");
        if (world.network().sendLiveSignal(page, event, dataJson) == 0) {
            throw new AssertionError("畫面沒有連著住客串流，送不出「" + event + "」訊號");
        }
    }

    /** 從現在起等這個分頁送出一個這支端點的請求。 */
    public void waitForNextRequest(Network.Api api, String message) {
        Page page = world.page();
        Network network = world.network();
        int before = network.sentRequestCount();
        waitFor(page, () -> network.sentRequestsSince(before).stream()
                .anyMatch(request -> request.page() == page && request.is(api)), FIRST_POLL_TIMEOUT_MS, message);
    }

    /** 分幾段推這個分頁的瀏覽器時鐘：一次推完時，週期性的計時器只會觸發一次。 */
    public void fastForwardBrowser(Duration total, Duration step) {
        Page page = world.page();
        for (long left = total.toMillis(); left > 0; left -= step.toMillis()) {
            page.clock().fastForward(Math.min(left, step.toMillis()));
        }
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
