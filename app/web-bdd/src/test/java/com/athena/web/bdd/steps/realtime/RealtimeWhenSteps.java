package com.athena.web.bdd.steps.realtime;

import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.ScenarioTasks.Entry;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import io.cucumber.java.zh_tw.當;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 「即時更新」模組的 When 句型。
 *
 * <p>現場有人動了某張單的句子，一律經過後端的入口做，不直接寫資料庫：直接寫的單不會發即時訊號，
 * 畫面就沒有東西可以跟上。送出之前先記下時刻，結果句的「幾秒內」從這一刻起算。
 */
public class RealtimeWhenSteps {

    private static final Map<String, String> DEPT = Map.of("房務", "housekeeping", "工務", "engineering");

    /** 送一則 task 訊號之後，等畫面送出看板請求最久等這麼久；畫面收到訊號就該馬上重讀。 */
    private static final double REFETCH_TIMEOUT_MS = 10_000;

    private final WebWorld world;

    private final LiveConnection live;

    private final RealtimeScenario scenario;

    public RealtimeWhenSteps(WebWorld world, LiveConnection live, RealtimeScenario scenario) {
        this.world = world;
        this.live = live;
        this.scenario = scenario;
    }

    /**
     * 以這間房住客的身分送出這段文字。文字應該不命中任何分類規則，所以這張單要進待派單；
     * 後端把它分到部門時代表案例的前提不成立，當場失敗，不讓後面的結果句錯得莫名其妙。
     * 送出的單登記成這間房的單，標題是住客的原話。
     */
    @當("{string} 的住客送出一張猜不準部門的打字需求 {string}")
    public void guestSubmitsUnclassifiable(String roomNo, String text) {
        scenario.markSignalSent(Instant.now());
        UUID taskId = world.backend().guestSubmitRequest(roomNo, text, null);
        Object state = world.backend().taskRow(taskId).get("state");
        if (!"triage".equals(state)) {
            throw new IllegalStateException("「" + text + "」被後端分到部門了（狀態是 " + state
                    + "），不是猜不準部門的需求，案例的前提不成立");
        }
        world.tasks().add(new Entry(roomNo, text, taskId, "待派單"));
    }

    /** 以同事的身分在後端呼叫派單，不經過這個分頁，所以這個分頁只能靠即時訊號知道。 */
    @當("{string} 在另一台櫃檯把 {string} 派給 {string}")
    public void colleagueAssigns(String colleague, String roomNo, String dept) {
        String deptCode = DEPT.get(dept.strip());
        if (deptCode == null) {
            throw new IllegalArgumentException("派單的部門只接受房務、工務，收到「" + dept + "」");
        }
        UUID taskId = world.tasks().idOf(roomNo);
        scenario.markSignalSent(Instant.now());
        world.backend().deskAssign(taskId, world.staff(colleague), deptCode, null);
    }

    /** 以這間房住客的身分收回這間房的那張單。 */
    @當("{string} 的住客自己收回需求")
    public void guestWithdraws(String roomNo) {
        UUID taskId = world.tasks().idOf(roomNo);
        scenario.markSignalSent(Instant.now());
        world.backend().guestWithdrawRequest(roomNo, taskId);
    }

    @當("即時連線中斷")
    public void connectionLost() {
        live.cutAndBlockReconnect();
    }

    /**
     * 後端例行收掉連線時畫面會重連一次，這一次就連上的話櫃檯不該看到任何提醒。
     * 提醒列可能只閃一下，所以切斷之前就開始監看，重連成功之後才收下監看結果。
     */
    @當("即時連線中斷，第一次重連就成功")
    public void connectionLostThenFirstReconnectSucceeds() {
        live.startWatchingAlert();
        live.cutAndAllowFirstReconnect();
        scenario.markAlertSeenDuringReconnect(live.alertSeenSinceWatching());
    }

    @當("後端宣告即時訊號可能延遲")
    public void backendDegraded() {
        live.waitUntilConnected();
        live.sendSignal(world.page(), "degraded", "{\"reason\":\"kafka_consumer_lag\"}");
    }

    /**
     * 連線維持著，但後端送來的訊號（含心跳）全部扣住，再把瀏覽器的時鐘往後推該秒數再多 1 秒。
     * 只推瀏覽器、不經過 {@code Clocks}：{@code Clocks} 會把兩邊對齊到同一刻，而瀏覽器已經比後端快了場景跑過的秒數，
     * 實際推進的會比要求的少，畫面的「太久沒有訊號」計時就可能還沒到期。
     */
    @當("超過 {int} 秒沒有收到後端的任何訊號")
    public void noSignalFor(int seconds) {
        live.waitUntilConnected();
        Page page = world.page();
        world.network().holdLiveSignals(page);
        page.clock().fastForward((seconds + 1) * 1000L);
    }

    /** 放行重連；重連後後端送來 init 與 resync，測試代替後端補一則 degraded 為 false 的心跳。 */
    @當("即時連線恢復")
    public void connectionRestored() {
        live.allowReconnect();
    }

    /**
     * 讓看板資料與串流都斷線，並觸發一次重讀。重讀用一則即時訊號觸發，所以要趁串流還連著時送：
     * 先擋看板資料，再送一張已登記的單的 task 訊號，等畫面真的送出了看板請求，最後切斷串流並擋住重連。
     */
    @當("後端連不上，重新讀取看板失敗")
    public void backendUnreachable() {
        live.waitUntilConnected();
        Page page = world.page();
        Network network = world.network();
        network.disconnect(page, Network.DESK_BOARD);
        Entry any = world.tasks().all().stream().findFirst().orElseThrow(() -> new IllegalStateException(
                "要送即時訊號觸發重讀，案例裡至少要有一張單"));
        int before = network.sentRequestCount();
        live.sendSignal(page, "task", "{\"stream_pos\":1,\"task_id\":\"" + any.taskId()
                + "\",\"seq\":1,\"type\":\"created\",\"occurred_at\":\"" + world.clocks().now() + "\"}");
        LiveConnection.waitFor(page,
                () -> network.sentRequestsSince(before).stream().anyMatch(request -> request.is(Network.DESK_BOARD)),
                REFETCH_TIMEOUT_MS, "送了即時訊號之後，畫面沒有重新讀取看板");
        live.cutAndBlockReconnect();
    }
}
