package com.athena.web.bdd.steps.realtime;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.bddsupport.TaskSituation;
import com.athena.web.bdd.steps.common.TaskTableRows;
import com.athena.web.bdd.support.Clocks;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.ScenarioTasks.Entry;
import com.athena.web.bdd.support.WebWorld;
import com.athena.web.bdd.support.WhenStart;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Page;
import io.cucumber.java.zh_tw.當;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 「即時更新」模組的 When 句型。
 *
 * <p>現場有人動了某張單的句子，一律經過後端的入口做，不直接寫資料庫：直接寫的單不會發即時訊號，
 * 畫面就沒有東西可以跟上。送出之前先記下時刻，結果句的「幾秒內」從這一刻起算。
 */
public class RealtimeWhenSteps {

    private static final Map<String, String> DEPT = Map.of("房務", "housekeeping", "工務", "engineering");

    /** 代替後端送的卡片要有負責人、畫面上那張又沒有時，照部門給的同仁；和建單表格沒寫負責人時的第一順位相同。 */
    private static final Map<String, String> DEFAULT_ASSIGNEE = Map.of("housekeeping", "小美", "engineering", "阿強");

    /** 推進瀏覽器時鐘時每一步推這麼多；比畫面「太久沒有訊號」的 35 秒短得多，每步之後補一則心跳就不會誤觸提醒列。 */
    private static final Duration CLOCK_STEP = Duration.ofSeconds(10);

    /** 放行扣住的看板資料之後，等畫面收到它最久等這麼久。 */
    private static final double RELEASE_TIMEOUT_MS = 10_000;

    /** 測試代替後端時送的心跳，{@code degraded} 為 false，和後端平常的心跳一樣。 */
    private static final String HEARTBEAT = "{\"stream_pos\":0,\"degraded\":false}";

    /** 送一則 task 訊號之後，等畫面送出看板請求最久等這麼久；畫面收到訊號就該馬上重讀。 */
    private static final double REFETCH_TIMEOUT_MS = 10_000;

    private final WebWorld world;

    private final LiveConnection live;

    private final RealtimeScenario scenario;

    private final BoardResponses boardResponses;

    private final LiveUpdateTiming timing;

    public RealtimeWhenSteps(WebWorld world, LiveConnection live, RealtimeScenario scenario,
            BoardResponses boardResponses, LiveUpdateTiming timing) {
        this.world = world;
        this.live = live;
        this.scenario = scenario;
        this.boardResponses = boardResponses;
        this.timing = timing;
    }

    /** 給部門代碼、回代替後端送的卡片上要掛的負責人。 */
    static Function<String, CreatedStaff> assigneeOf(WebWorld world) {
        return dept -> {
            String name = DEFAULT_ASSIGNEE.get(dept);
            if (name == null) {
                throw new IllegalStateException("「" + dept + "」部門沒有預設的負責人，卡片的狀況要有人做時只能是房務或工務的單");
            }
            return world.staff(name);
        };
    }

    /**
     * 畫面上這張卡片改成這個狀況、這一步之後送出。
     * 比畫面上那張新的，之後讀回來的看板也照這張；一樣或較舊的是重複或晚到的訊號，後端的單沒有變，看板照舊。
     */
    @當("後端送來 {string} 第 {int} 步的卡片，狀況是 {string}")
    public void backendPushesCard(String roomNo, int step, String situationTerm) {
        live.waitUntilResyncReceived();
        UUID taskId = world.tasks().idOf(roomNo);
        ObjectNode onScreen = boardResponses.cardOnScreen(taskId);
        boolean newer = step > onScreen.path("seq").asInt();
        ObjectNode card = CardSignals.situated(onScreen, TaskSituation.ofTerm(situationTerm.strip()), step,
                world.clocks().now(), assigneeOf(world));
        if (newer) {
            boardResponses.rememberPushed(taskId, card);
        }
        timing.markSent();
        live.sendSignal(world.page(), "task", CardSignals.withCard(taskId, step, boardResponses.businessDate(), card));
    }

    @當("後端送來 {string} 第 {int} 步，說它已經不在看板上")
    public void backendPushesRemoval(String roomNo, int step) {
        live.waitUntilResyncReceived();
        UUID taskId = world.tasks().idOf(roomNo);
        boardResponses.rememberPushed(taskId, null);
        timing.markSent();
        live.sendSignal(world.page(), "task", CardSignals.withCard(taskId, step, boardResponses.businessDate(), null));
    }

    /**
     * 先在後端直接建好這張單（直接寫的單不會發訊號），再代替後端送它的卡片。
     * 單要真的存在：側欄徽章的結果句會核對後端的待派單張數，之後的整份重讀也要讀得到它。
     * 卡片取自後端的單筆詳情，只留看板卡片也有的欄位，步數照句型定成第 1 步。
     */
    @當("後端送來畫面上沒有的 {string} 卡片，狀況是 {string}")
    public void backendPushesNewCard(String roomNo, String situationTerm) {
        live.waitUntilResyncReceived();
        JsonNode template = boardResponses.lastBoard().path("tasks").path(0);
        if (!template.isObject()) {
            throw new IllegalStateException("畫面上的看板沒有任何卡片，無從得知卡片有哪些欄位");
        }
        new TaskTableRows(world).createAll(List.of(Map.of("房號", roomNo, "類型", "其他",
                "住客原話", "需要多一條毛巾", "狀況", situationTerm.strip())));
        UUID taskId = world.tasks().idOf(roomNo);
        JsonNode detail = world.backend().getJson("/desk/tasks/" + taskId, world.staff(WhenStart.BACKEND_READER));
        ObjectNode card = JsonNodeFactory.instance.objectNode();
        for (Iterator<String> fields = template.fieldNames(); fields.hasNext(); ) {
            String field = fields.next();
            card.set(field, detail.path(field).isMissingNode() ? null : detail.get(field).deepCopy());
        }
        card.put("seq", 1);
        boardResponses.rememberPushed(taskId, card);
        timing.markSent();
        live.sendSignal(world.page(), "task", CardSignals.withCard(taskId, 1, boardResponses.businessDate(), card));
    }

    /** 訊號掛在畫面上第一張卡片的單，事件步數比那張卡片多 1，所以不會被當成漏了訊號。 */
    @當("後端送來一則 {string}")
    public void backendPushesSignalOfKind(String kind) {
        live.waitUntilResyncReceived();
        JsonNode first = boardResponses.lastBoard().path("tasks").path(0);
        if (!first.isObject()) {
            throw new IllegalStateException("畫面上的看板沒有任何卡片，訊號沒有單可以掛");
        }
        UUID taskId = UUID.fromString(first.path("id").asText());
        int step = first.path("seq").asInt() + 1;
        ObjectNode card = ((ObjectNode) first.deepCopy()).put("seq", step);
        LocalDate businessDate = boardResponses.businessDate();
        String data = switch (kind.strip()) {
            case "沒有帶卡片的單變化訊號" -> CardSignals.withoutCard(taskId, step);
            case "看不懂的單變化訊號" -> CardSignals.unreadable(taskId, step, businessDate, card);
            case "營業日是隔天的單變化訊號" -> CardSignals.withCard(taskId, step, businessDate.plusDays(1), card);
            default -> throw new IllegalArgumentException("不認得「" + kind + "」這種訊號，可以用的見即時更新的用語對照");
        };
        timing.markSent();
        live.sendSignal(world.page(), "task", data);
    }

    /**
     * 後端先撥到隔天那一刻，瀏覽器再分步推過去，每推一步代替後端補一則心跳。
     * 一次推到底會讓畫面以為超過 35 秒沒有訊號而出現提醒列，改成定時重讀，案例要看的「自己在換日時重讀」就分不出來。
     * 推的過程中畫面在換日時刻送出的重讀也算這一句造成的，所以起算的請求數是這一句開始時的，時間則從推到那一刻起算。
     */
    @當("時間走到隔天 {string}，這段期間現場沒有任何變化")
    public void timePassesToNextDay(String time) {
        live.waitUntilResyncReceived();
        Page page = world.page();
        LocalTime at = LocalTime.parse(time.strip(), DateTimeFormatter.ofPattern("H:mm"));
        world.clocks().moveToInSteps(Clocks.at(world.clocks().today().plusDays(1), at), CLOCK_STEP,
                () -> live.sendSignal(page, "heartbeat", HEARTBEAT));
        scenario.markSignalSent(Instant.now());
    }

    /** 放行之後等畫面真的收到這份看板；畫面拿它和讀取期間收到的卡片合併，由結果句看合併的結果。 */
    @當("扣住的看板資料讀回來了")
    public void heldBoardReturns() {
        Page page = world.page();
        Network network = world.network();
        LiveConnection.waitFor(page, boardResponses::holding, RELEASE_TIMEOUT_MS,
                "畫面一直沒有送出要被扣住的那一次看板讀取");
        int before = network.deskBoardsReceived(page);
        boardResponses.releaseHeld();
        LiveConnection.waitFor(page, () -> network.deskBoardsReceived(page) > before, RELEASE_TIMEOUT_MS,
                "放行之後畫面沒有收到扣住的看板資料");
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
