package com.athena.web.bdd.steps.realtime;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.util.function.BooleanSupplier;

/**
 * 「即時更新」的 Given 與 When 共用的串流操作：切斷目前分頁的事件串流並擋住重連、放行重連、等提醒列出現或消失。
 *
 * <p>已經連上的串流只能經過 {@link Network} 包住 {@code fetch} 的那一段切斷，一般的路由攔截切不斷；
 * 路由攔截只拿來擋之後的重連。所有等待都有上限，畫面還沒做出這些反應時步驟在這裡失敗，訊息說明卡在哪裡。
 */
public class LiveConnection {

    /** 畫面連上或重新連上事件串流最久等這麼久；產品重連的間隔是秒級，後端在本機。 */
    static final double CONNECT_TIMEOUT_MS = 15_000;

    /**
     * 提醒列出現或消失最久等這麼久。
     * 用過的連線被切斷時，畫面照產品的第一次重連間隔隔 1 秒重連，這次被路由攔截當場擋掉，提醒列就出現；
     * 降級與恢復則是收到訊號當下就變。10 秒在這 1 秒之外，還多留了一整個 5 秒的後續重連間隔給忙碌的機器。
     */
    static final double ALERT_TIMEOUT_MS = 10_000;

    private final WebWorld world;

    private final ShellFrame shell;

    public LiveConnection(WebWorld world) {
        this.world = world;
        this.shell = new ShellFrame(world);
    }

    /**
     * 切斷這個分頁正在用的事件串流，之後畫面再怎麼重連都連不上，看板資料照常讀得到。
     *
     * <p>切斷前先等這條連線收過後端的 {@code init}。路由攔截只擋得到之後才送出的請求，
     * 切斷也只切得到後端已經回應的串流。剛開頁的第一次連線若請求已經送出、後端還沒回應，
     * 兩邊都碰不到它，它照樣連上，畫面一直是即時更新，提醒列就不會出現。
     * 先擋重連再切斷：反過來的話，畫面可能在兩步之間就重新連上了。
     */
    public void cutAndBlockReconnect() {
        Page page = world.page();
        waitUntilStreamInUse(page, "切斷之前，畫面一直沒有連上事件串流，或後端的 init 沒有送到");
        world.network().disconnect(page, Network.STAFF_STREAM);
        world.network().cutLiveStream(page);
    }

    /**
     * 放行重連，等畫面重新連上、後端的 {@code init} 與 {@code resync} 先到，再代替後端送一則 {@code degraded}
     * 為 false 的心跳。後端的心跳是全部連線共用的 15 秒節拍，不送的話畫面要等到下一拍才知道恢復即時，
     * 「幾秒內提醒列消失」就要看運氣。之前若扣著訊號也一併放掉。
     */
    public void allowReconnect() {
        Page page = world.page();
        Network network = world.network();
        network.releaseLiveSignals(page);
        network.passThrough(page, Network.STAFF_STREAM);
        waitUntilStreamInUse(page, "放行重連之後，畫面沒有重新連上事件串流，或後端的 init 沒有送到");
        sendSignal(page, "heartbeat", "{\"stream_pos\":0,\"degraded\":false}");
    }

    /**
     * 只切斷這個分頁已經連上的事件串流，不擋重連，所以畫面的第一次重連就連得上。
     * 等畫面重新連上、後端的 {@code init} 與 {@code resync} 先到，再代替後端送一則 {@code degraded} 為 false 的心跳，
     * 和 {@link #allowReconnect()} 重連後的樣子相同。
     */
    public void cutAndAllowFirstReconnect() {
        Page page = world.page();
        Network network = world.network();
        // 連線還沒用過就斷，對畫面來說是連線失敗，不是「用得好好的連線斷了一下」。
        waitUntilStreamInUse(page, "切斷之前，畫面一直沒有連上事件串流，或後端的 init 沒有送到");
        network.passThrough(page, Network.STAFF_STREAM);
        network.cutLiveStream(page);
        waitUntilStreamInUse(page, "切斷之後，畫面沒有重新連上事件串流，或後端的 init 沒有送到");
        sendSignal(page, "heartbeat", "{\"stream_pos\":0,\"degraded\":false}");
    }

    /** 等到畫面連著事件串流，而且後端已經交了資料給這條連線。 */
    private void waitUntilStreamInUse(Page page, String message) {
        Network network = world.network();
        waitFor(page, () -> network.liveStreamCount(page) > 0 && network.liveChunksDelivered(page) > 0,
                CONNECT_TIMEOUT_MS, message);
    }

    /**
     * 給「提醒列一次也沒有出現」用：提醒列可能只閃一下就消失，測試這一頭輪詢會漏掉兩次讀取之間的閃動，
     * 所以在瀏覽器裡用 {@code MutationObserver} 看每一個加進畫面的元素，看到過一次就記住。
     * 認提醒列的方式要和 {@link ShellFrame#connectionAlertBar()} 一致，否則兩邊對「提醒列在不在」會說法不同。
     */
    public void startWatchingAlert() {
        world.page().evaluate("""
                title => {
                  const isAlert = el => el.matches('[role="status"]')
                    && [...el.querySelectorAll('*')].some(child => child.textContent.trim() === title);
                  const hasAlert = root => root.nodeType === 1
                    && (isAlert(root) || [...root.querySelectorAll('[role="status"]')].some(isAlert));
                  if (window.__webBddAlertWatcher) window.__webBddAlertWatcher.disconnect();
                  window.__webBddAlertSeen = hasAlert(document.body);
                  window.__webBddAlertWatcher = new MutationObserver(records => {
                    if (window.__webBddAlertSeen) return;
                    window.__webBddAlertSeen = records.some(record => [...record.addedNodes].some(hasAlert))
                      || hasAlert(document.body);
                  });
                  window.__webBddAlertWatcher.observe(document.body,
                    { childList: true, subtree: true, characterData: true });
                }
                """, ShellFrame.CONNECTION_ALERT_TITLE);
    }

    /** 從 {@link #startWatchingAlert()} 到現在，提醒列有沒有出現過。 */
    public boolean alertSeenSinceWatching() {
        return Boolean.TRUE.equals(world.page().evaluate("() => window.__webBddAlertSeen === true"));
    }

    /** 等到畫面已經連著事件串流；剛開頁時串流可能還在連，訊號送不進去。 */
    public void waitUntilConnected() {
        Page page = world.page();
        waitFor(page, () -> world.network().liveStreamCount(page) > 0, CONNECT_TIMEOUT_MS,
                "畫面一直沒有連上事件串流");
    }

    /**
     * 等到畫面已經連著事件串流，而且後端連上時送的 {@code init} 與 {@code resync} 都已經交給畫面。
     * 測試要代替後端送訊號時先等這一步：{@code resync} 若晚於測試送的訊號才到，
     * 畫面會因為它重讀整份看板，案例要看的「只換一張、不重讀」就分不出來了。
     */
    public void waitUntilResyncReceived() {
        Page page = world.page();
        Network network = world.network();
        waitFor(page, () -> network.liveStreamCount(page) > 0 && resyncReceived(page), CONNECT_TIMEOUT_MS,
                "畫面一直沒有連上事件串流，或後端的 init、resync 沒有送到");
    }

    /** 後端從連上到現在有沒有把 {@code resync} 交給這個分頁。 */
    public boolean resyncReceived(Page page) {
        return world.network().liveEventsFromBackend(page).stream().anyMatch(event -> "resync".equals(event.event()));
    }

    /** 代替後端在目前分頁的串流裡送一則訊號；畫面沒有連著串流時直接失敗，不讓這一則無聲地落空。 */
    public void sendSignal(Page page, String event, String dataJson) {
        if (world.network().sendLiveSignal(page, event, dataJson) == 0) {
            throw new AssertionError("畫面沒有連著事件串流，送不出「" + event + "」訊號");
        }
    }

    public void waitForAlertShown() {
        waitForAlert(WaitForSelectorState.VISIBLE, "提醒列「" + ShellFrame.CONNECTION_ALERT_TITLE + "」沒有出現");
    }

    public void waitForAlertGone() {
        waitForAlert(WaitForSelectorState.HIDDEN, "提醒列「" + ShellFrame.CONNECTION_ALERT_TITLE + "」沒有消失");
    }

    private void waitForAlert(WaitForSelectorState state, String message) {
        try {
            shell.connectionAlertBar().first().waitFor(
                    new Locator.WaitForOptions().setState(state).setTimeout(ALERT_TIMEOUT_MS));
        } catch (TimeoutError e) {
            throw new AssertionError(message + "（等了 " + (long) (ALERT_TIMEOUT_MS / 1000) + " 秒）", e);
        }
    }

    /** 等到條件成立，逾時就以這段說明失敗。 */
    static void waitFor(Page page, BooleanSupplier condition, double timeoutMs, String message) {
        try {
            page.waitForCondition(condition, new Page.WaitForConditionOptions().setTimeout(timeoutMs));
        } catch (TimeoutError e) {
            throw new AssertionError(message + "（等了 " + (long) (timeoutMs / 1000) + " 秒）", e);
        }
    }
}
