package com.athena.web.bdd.steps.guest.realtime;

import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import io.cucumber.java.zh_tw.假設;

/**
 * 「即時更新」模組的 Given 句型。
 *
 * <p>「超過 {秒數} 秒沒有收到後端的任何訊號」和櫃檯 Web 的 When 一字不差，同一個 glue 只能有一個定義，
 * 由櫃檯的 {@code steps.realtime.RealtimeWhenSteps} 在住客場景交給這裡的 {@link #noSignalFor(int)}。
 */
public class RealtimeGivenSteps {

    private final WebWorld world;

    private final GuestLive live;

    public RealtimeGivenSteps(WebWorld world, GuestLive live) {
        this.world = world;
        this.live = live;
    }

    @假設("即時連線已經中斷")
    public void connectionLost() {
        live.cutAndBlockReconnect();
        live.waitForNextRequest(Network.GUEST_REQUESTS, "即時連線切斷之後，畫面沒有開始定期重新讀取需求清單");
    }

    /**
     * 連線維持著，但後端送來的訊號（含心跳）全部扣住，再把瀏覽器的時鐘往後推該秒數再多 1 秒。
     * 只推瀏覽器、不經過 {@code Clocks}：畫面判斷「太久沒有訊號」看的是瀏覽器的時鐘，和後端的時刻無關。
     */
    public void noSignalFor(int seconds) {
        Page page = world.page();
        live.waitUntilStreamInUse(page, "畫面一直沒有連上住客串流，或後端的 init 沒有送到");
        world.network().holdLiveSignals(page);
        page.clock().fastForward((seconds + 1) * 1000L);
    }

    @假設("後端已宣告即時訊號可能延遲")
    public void backendDegraded() {
        live.sendSignal("degraded", "{\"stream_pos\":0,\"reason\":\"kafka_consumer_lag\"}");
    }

    @假設("即時連線中斷後已經恢復")
    public void connectionLostThenRestored() {
        connectionLost();
        live.allowReconnectAndWaitForInit();
    }
}
