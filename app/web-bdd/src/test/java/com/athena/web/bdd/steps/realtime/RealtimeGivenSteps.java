package com.athena.web.bdd.steps.realtime;

import com.athena.bddsupport.TaskSituation;
import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.cucumber.java.zh_tw.假設;
import java.time.Instant;
import java.util.UUID;

/** 「即時更新」模組的 Given 句型。 */
public class RealtimeGivenSteps {

    /** 觸發重讀之後，等畫面讀回改過步數的看板最久等這麼久；畫面收到訊號就該馬上重讀。 */
    private static final double REREAD_TIMEOUT_MS = 10_000;

    private final WebWorld world;

    private final LiveConnection live;

    private final RealtimeScenario scenario;

    private final BoardResponses boardResponses;

    public RealtimeGivenSteps(WebWorld world, LiveConnection live, RealtimeScenario scenario,
            BoardResponses boardResponses) {
        this.world = world;
        this.live = live;
        this.scenario = scenario;
        this.boardResponses = boardResponses;
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
        recordBoardRequestsFromNow();
    }

    /**
     * 步數是後端照事件數出來的，做不出指定的數字，所以改看板資料：之後讀回來的看板裡這張卡片一律是這一步。
     * 改好之後送一則沒有帶卡片的訊號讓畫面整份重讀，畫面記下的步數才會是這一步。
     * 這一次重讀是前提的一部分，所以從重讀完之後才開始記看板請求。
     */
    @假設("畫面上 {string} 的卡片是第 {int} 步")
    public void cardIsAtStep(String roomNo, int step) {
        live.waitUntilResyncReceived();
        UUID taskId = world.tasks().idOf(roomNo);
        boardResponses.overrideStep(taskId, step);
        Page page = world.page();
        live.sendSignal(page, "task", CardSignals.withoutCard(taskId, step));
        boardResponses.awaitBoard(card -> card.path("seq").asInt() == step, taskId, REREAD_TIMEOUT_MS,
                "送了沒有帶卡片的訊號之後，畫面沒有讀回「" + roomNo + "」是第 " + step + " 步的看板");
        new ShellFrame(world).settleAfter(() -> { });
        new BoardPage(world).card(roomNo).first().waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        recordBoardRequestsFromNow();
    }

    /**
     * 讀回來的這份看板要是「舊的」：這張卡片仍是這一步、這個狀況。
     * 取回後端的看板後只改這張卡片，其他卡片照後端；扣到「扣住的看板資料讀回來了」才交給畫面。
     */
    @假設("下一次讀取看板會被扣住，讀回來的 {string} 仍是第 {int} 步、狀況是 {string}")
    public void nextBoardReadHeld(String roomNo, int step, String situationTerm) {
        UUID taskId = world.tasks().idOf(roomNo);
        TaskSituation situation = TaskSituation.ofTerm(situationTerm.strip());
        boardResponses.holdNext(board -> {
            ArrayNode tasks = (ArrayNode) board.path("tasks");
            for (int i = 0; i < tasks.size(); i++) {
                JsonNode task = tasks.get(i);
                if (taskId.toString().equals(task.path("id").asText())) {
                    tasks.set(i, CardSignals.situated((ObjectNode) task.deepCopy(), situation, step,
                            world.clocks().now(), RealtimeWhenSteps.assigneeOf(world)));
                    return board;
                }
            }
            throw new IllegalStateException("扣住的看板資料裡沒有「" + roomNo + "」的卡片，後端的看板沒有這張單");
        });
    }

    /** 從這一刻起記下這個分頁送出的每一個看板請求。 */
    private void recordBoardRequestsFromNow() {
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
