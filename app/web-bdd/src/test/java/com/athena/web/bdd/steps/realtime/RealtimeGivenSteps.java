package com.athena.web.bdd.steps.realtime;

import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import io.cucumber.java.zh_tw.假設;
import java.time.Instant;

/** 「即時更新」模組的 Given 句型。 */
public class RealtimeGivenSteps {

    private final WebWorld world;

    private final LiveConnection live;

    private final RealtimeScenario scenario;

    public RealtimeGivenSteps(WebWorld world, LiveConnection live, RealtimeScenario scenario) {
        this.world = world;
        this.live = live;
        this.scenario = scenario;
    }

    /** 切斷這個分頁的串流並擋住重連，看板資料照常讀得到；等提醒列出現。 */
    @假設("即時連線已經中斷，提醒列出現了")
    public void connectionLostWithAlert() {
        live.cutAndBlockReconnect();
        live.waitForAlertShown();
    }

    /**
     * 先切斷串流、等提醒列出現，再放行重連，等提醒列消失。
     * 從提醒列消失的那一刻起記下這個分頁送出的每一個看板請求，給「沒有每 5 秒定時讀取」比對。
     */
    @假設("即時連線中斷後已經恢復，提醒列消失了")
    public void connectionRestoredAlertGone() {
        live.cutAndBlockReconnect();
        live.waitForAlertShown();
        live.allowReconnect();
        live.waitForAlertGone();

        Page page = world.page();
        scenario.markBoardRequestsFrom(world.network().sentRequestCount());
        page.onRequest(request -> {
            String path = request.url().split("[?#]")[0];
            if (Network.DESK_BOARD.method().equals(request.method()) && path.endsWith(Network.DESK_BOARD.path())) {
                scenario.recordBoardRequest(Instant.now());
            }
        });
    }
}
