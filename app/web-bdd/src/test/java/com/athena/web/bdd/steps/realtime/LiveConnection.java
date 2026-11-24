package com.athena.web.bdd.steps.realtime;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.util.function.BooleanSupplier;

public class LiveConnection {

    static final double CONNECT_TIMEOUT_MS = 15_000;

    static final double ALERT_TIMEOUT_MS = 10_000;

    private final WebWorld world;

    private final ShellFrame shell;

    public LiveConnection(WebWorld world) {
        this.world = world;
        this.shell = new ShellFrame(world);
    }

    public void cutAndBlockReconnect() {
        Page page = world.page();
        waitUntilStreamInUse(page, "切斷之前，畫面一直沒有連上事件串流，或後端的 init 沒有送到");
        world.network().disconnect(page, Network.STAFF_STREAM);
        world.network().cutLiveStream(page);
    }

    public void allowReconnect() {
        Page page = world.page();
        Network network = world.network();
        network.releaseLiveSignals(page);
        network.passThrough(page, Network.STAFF_STREAM);
        waitUntilStreamInUse(page, "放行重連之後，畫面沒有重新連上事件串流，或後端的 init 沒有送到");
        sendSignal(page, "heartbeat", "{\"stream_pos\":0,\"degraded\":false}");
    }

    public void cutAndAllowFirstReconnect() {
        Page page = world.page();
        Network network = world.network();
        waitUntilStreamInUse(page, "切斷之前，畫面一直沒有連上事件串流，或後端的 init 沒有送到");
        network.passThrough(page, Network.STAFF_STREAM);
        network.cutLiveStream(page);
        waitUntilStreamInUse(page, "切斷之後，畫面沒有重新連上事件串流，或後端的 init 沒有送到");
        sendSignal(page, "heartbeat", "{\"stream_pos\":0,\"degraded\":false}");
    }

    private void waitUntilStreamInUse(Page page, String message) {
        Network network = world.network();
        waitFor(page, () -> network.liveStreamCount(page) > 0 && network.liveChunksDelivered(page) > 0,
                CONNECT_TIMEOUT_MS, message);
    }

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

    public boolean alertSeenSinceWatching() {
        return Boolean.TRUE.equals(world.page().evaluate("() => window.__webBddAlertSeen === true"));
    }

    public void waitUntilConnected() {
        Page page = world.page();
        waitFor(page, () -> world.network().liveStreamCount(page) > 0, CONNECT_TIMEOUT_MS,
                "畫面一直沒有連上事件串流");
    }

    public void waitUntilResyncReceived() {
        Page page = world.page();
        Network network = world.network();
        waitFor(page, () -> network.liveStreamCount(page) > 0 && resyncReceived(page), CONNECT_TIMEOUT_MS,
                "畫面一直沒有連上事件串流，或後端的 init、resync 沒有送到");
    }

    public boolean resyncReceived(Page page) {
        return world.network().liveEventsFromBackend(page).stream().anyMatch(event -> "resync".equals(event.event()));
    }

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

    static void waitFor(Page page, BooleanSupplier condition, double timeoutMs, String message) {
        try {
            page.waitForCondition(condition, new Page.WaitForConditionOptions().setTimeout(timeoutMs));
        } catch (TimeoutError e) {
            throw new AssertionError(message + "（等了 " + (long) (timeoutMs / 1000) + " 秒）", e);
        }
    }
}
