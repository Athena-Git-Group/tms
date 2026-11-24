package com.athena.web.bdd.steps.guest.realtime;

import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import io.cucumber.java.zh_tw.假設;
import java.time.Duration;

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

    public void noSignalFor(int seconds) {
        Page page = world.page();
        live.waitUntilStreamInUse(page, "畫面一直沒有連上住客串流，或後端的 init 沒有送到");
        world.network().holdLiveSignals(page);
        world.clocks().elapse(page, Duration.ofSeconds(seconds + 1));
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
