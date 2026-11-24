package com.athena.web.bdd.steps.realtime;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.bddsupport.TaskSituation;
import com.athena.web.bdd.steps.common.TaskTableRows;
import com.athena.web.bdd.support.Clocks;
import com.athena.web.bdd.support.GuestScenario;
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

public class RealtimeWhenSteps {

    private static final Map<String, String> DEPT = Map.of("房務", "housekeeping", "工務", "engineering");

    private static final Map<String, String> DEFAULT_ASSIGNEE = Map.of("housekeeping", "小美", "engineering", "阿強");

    private static final Duration CLOCK_STEP = Duration.ofSeconds(10);

    private static final double RELEASE_TIMEOUT_MS = 10_000;

    private static final String HEARTBEAT = "{\"stream_pos\":0,\"degraded\":false}";

    private static final double REFETCH_TIMEOUT_MS = 10_000;

    private final WebWorld world;

    private final LiveConnection live;

    private final RealtimeScenario scenario;

    private final BoardResponses boardResponses;

    private final LiveUpdateTiming timing;

    private final GuestScenario guestScenario;

    private final com.athena.web.bdd.steps.guest.realtime.RealtimeGivenSteps guestGiven;

    private final com.athena.web.bdd.steps.guest.realtime.RealtimeWhenSteps guestWhen;

    public RealtimeWhenSteps(WebWorld world, LiveConnection live, RealtimeScenario scenario,
            BoardResponses boardResponses, LiveUpdateTiming timing, GuestScenario guestScenario,
            com.athena.web.bdd.steps.guest.realtime.RealtimeGivenSteps guestGiven,
            com.athena.web.bdd.steps.guest.realtime.RealtimeWhenSteps guestWhen) {
        this.world = world;
        this.live = live;
        this.scenario = scenario;
        this.boardResponses = boardResponses;
        this.timing = timing;
        this.guestScenario = guestScenario;
        this.guestGiven = guestGiven;
        this.guestWhen = guestWhen;
    }

    static Function<String, CreatedStaff> assigneeOf(WebWorld world) {
        return dept -> {
            String name = DEFAULT_ASSIGNEE.get(dept);
            if (name == null) {
                throw new IllegalStateException("「" + dept + "」部門沒有預設的負責人，卡片的狀況要有人做時只能是房務或工務的單");
            }
            return world.staff(name);
        };
    }

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

    @當("時間走到隔天 {string}，這段期間現場沒有任何變化")
    public void timePassesToNextDay(String time) {
        live.waitUntilResyncReceived();
        Page page = world.page();
        LocalTime at = LocalTime.parse(time.strip(), DateTimeFormatter.ofPattern("H:mm"));
        world.clocks().moveToInSteps(Clocks.at(world.clocks().today().plusDays(1), at), CLOCK_STEP,
                () -> live.sendSignal(page, "heartbeat", HEARTBEAT));
        scenario.markSignalSent(Instant.now());
    }

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

    @當("{string} 的住客自己收回需求")
    public void guestWithdraws(String roomNo) {
        UUID taskId = world.tasks().idOf(roomNo);
        scenario.markSignalSent(Instant.now());
        world.backend().guestWithdrawRequest(roomNo, taskId);
    }

    @當("即時連線中斷")
    public void connectionLost() {
        if (guestScenario.active()) {
            guestWhen.connectionLost();
            return;
        }
        live.cutAndBlockReconnect();
    }

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

    @當("超過 {int} 秒沒有收到後端的任何訊號")
    public void noSignalFor(int seconds) {
        if (guestScenario.active()) {
            guestGiven.noSignalFor(seconds);
            return;
        }
        live.waitUntilConnected();
        Page page = world.page();
        world.network().holdLiveSignals(page);
        world.clocks().elapse(page, Duration.ofSeconds(seconds + 1));
    }

    @當("即時連線恢復")
    public void connectionRestored() {
        live.allowReconnect();
    }

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
