package com.athena.app.bdd.steps.manager;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import com.fasterxml.jackson.databind.JsonNode;
import io.appium.java_client.AppiumBy;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 主管工作台模組的 Then（主管工作台 dsl.md Then 句型區的「必查」）：格子數字、清單順序與
 * 每一列的內容、「我名下的單」、分頁外殼、查房畫面的項目與送出鈕、查房結果畫面的三種說法。
 *
 * <p>每一句都兩面查：畫面（accessibility id 照
 * {@code app/mobile/lib/features/manager/manager_ids.dart}）與權威狀態（{@code getManagerQueues}、
 * {@code getMyToday} 或直接讀表）。只驗畫面不算數——App 自己湊出對的數字也會過關；只驗後端
 * 則完全沒驗到主管看見的東西。
 */
public class ManagerThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private static final String HOME = "manager.home";
    private static final String HOME_MINE = "manager.home.mine";
    private static final String QUEUE = "manager.queue";
    private static final String QUEUE_EMPTY = "manager.queue.empty";
    private static final String QUEUE_ROW_PREFIX = "manager.queue.row.";
    private static final String INSPECT = "inspect";
    private static final String INSPECT_SUBMIT_BUTTON = "inspect.submit-button";
    private static final String INSPECT_ITEM_PREFIX = "inspect.item.";
    private static final String INSPECT_RESULT = "inspect.result";
    private static final String TICKET_SCREEN = "ticket.screen";
    private static final String TICKET_WHERE = "ticket.hero.where";

    /** 底部分頁 → 切過去之後那一頁一定在的節點 id（主管的待認領是帶部門切換的那一頁）。 */
    private static final Map<String, String> TAB_SCREEN_BY_LABEL = Map.of(
            "待認領", "pool.dept-switch",
            "通知", "notification.screen",
            "我的", "me.screen");

    /** 房務首頁任務卡的 id 前綴與尾段；「我名下的單」用的是同一個元件。 */
    private static final String HOME_TASK_PREFIX = "home.task.";
    private static final String HOME_TASK_SUFFIX = ".card";

    /** 工務首頁「手上」單卡的 id 前綴與尾段；工務主管的「我名下的單」用的是同一個元件。 */
    private static final String ENG_CARD_PREFIX = "engHome.in-hand.";
    private static final String ENG_CARD_SUFFIX = ".card";

    /** 主管首頁的磚 id 前綴；後面接 ManagerQueues 的鍵。 */
    private static final String TILE_PREFIX = "manager.home.tile.";

    /** 主管格子可能出現的所有類別（房務主管兩格都有，工務主管只有 verify）。 */
    private static final List<String> ALL_QUEUES = List.of("inspection", "verify");

    /** 格子中文 → 契約 {@code ManagerQueues} 的鍵（也是 {@code manager.home.tile.{queue}} 的尾段）。 */
    private static final Map<String, String> QUEUE_KEY_BY_LABEL = Map.of(
            "待查房", "inspection",
            "待驗收", "verify");

    /** 兩類清單的空狀態主文（dsl「清單說明目前沒有等他處理的單」那一列）。 */
    private static final Map<String, String> EMPTY_TITLE_BY_QUEUE = Map.of(
            "inspection", "目前沒有等你查的房",
            "verify", "目前沒有等你驗收的工務單");

    /** 待驗收列的標籤 → 它對應的欄位與值（dsl「那一列標示」那一列）。 */
    private static final Map<String, Map.Entry<String, String>> TAG_FIELD_BY_LABEL = Map.of(
            "已修好", Map.entry("resolutionType", "fixed"),
            "暫時處理", Map.entry("resolutionType", "temporary"),
            "無法修復", Map.entry("resolutionType", "cannot_fix"),
            "定期保養", Map.entry("origin", "scheduled"),
            "重修後再驗", Map.entry("wasVerificationFailed", "true"));

    /** 完修結果 → 待驗收列上的結果標籤文字（dsl「每一列都看得到工務單號與完修結果」那一列）。 */
    private static final Map<String, String> RESOLUTION_LABEL_BY_TYPE = Map.of(
            "fixed", "已修好",
            "temporary", "暫時處理",
            "cannot_fix", "無法修復");

    /** 工務單狀態 → 工務首頁單卡的狀態標籤文字（急件另外加「急件」）。 */
    private static final Map<String, String> ENG_CARD_STATUS_BY_STATE = Map.of(
            "accepted", "待開始",
            "doing", "維修中",
            "paused", "已暫停",
            "done", "已完修");

    /** 兩格的 y 差在這個點數以內就算排在同一列。 */
    private static final int SAME_ROW_TOLERANCE = 8;

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("MM/dd");

    private final AppiumWorld world;
    private final BackendChannel backend;
    private final ManagerContext context;

    public ManagerThenSteps(AppiumWorld world, BackendChannel backend, ManagerContext context) {
        this.world = world;
        this.backend = backend;
        this.context = context;
    }

    /**
     * 必查：主管首頁最上方的磚剛好是表格列出的這幾格、依表格順序
     * （{@code manager.home.tile.{inspection|verify}.count}），沒有多出的格子，數字逐格等於表格，
     * 並等於 {@code getManagerQueues} 回應的 {@code counts} 與各陣列長度。進首頁後不需任何點按就看得到。
     *
     * <p>Data Table 欄：{@code 格}（待查房、待驗收）、{@code 數字}。表格列出的順序就是磚的順序；
     * 格子有幾格由表格決定，不在這裡寫死。
     */
    @那麼("首頁的格子依序顯示：")
    public void tilesShowInOrder(DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        List<String> keysInTable = rows.stream().map(row -> queueKey(requireCell(row, "格"))).toList();
        JsonNode queues = managerQueues();
        assertCountsKeysExactly(queues, keysInTable);
        world.waitForTestId(HOME, SCREEN_TIMEOUT);
        for (Map<String, String> row : rows) {
            assertTile(queues, queueKey(requireCell(row, "格")), Integer.parseInt(requireCell(row, "數字")));
        }
        assertTilesOnScreenExactly(keysInTable);
    }

    /**
     * 必查：主管首頁最上方只有一條寬磚 {@code manager.home.tile.verify}，左側只有「待驗收」、沒有副文，
     * 右側大數字等於值，並等於 {@code getManagerQueues.counts.verify} 與
     * {@code verify} 陣列長度；畫面上沒有 {@code manager.home.tile.inspection}。用於工務主管。
     */
    @那麼("首頁只有待驗收一格，數字為 {int}")
    public void onlyVerifyTileShows(int expected) {
        JsonNode queues = managerQueues();
        assertCountsKeysExactly(queues, List.of("verify"));
        world.waitForTestId(HOME, SCREEN_TIMEOUT);
        assertTile(queues, "verify", expected);
        assertTilesOnScreenExactly(List.of("verify"));
        assertAbsent(TILE_PREFIX + "inspection", "工務主管首頁不該有待查房格");
        String tileLabel = tileLabel("verify");
        if (!tileLabel.contains("待驗收")) {
            throw new AssertionError("待驗收寬磚應看得到「待驗收」，實際「" + tileLabel + "」");
        }
        if (tileLabel.contains("與房務主管看同一份")) {
            throw new AssertionError("待驗收寬磚不該有副文「與房務主管看同一份」，實際「" + tileLabel + "」");
        }
    }

    /**
     * 必查：主管首頁沒有 {@code manager.home.tile.inspection}；四個分頁、「我名下的單」與通知中心
     * 都沒有通往待查房清單或查房畫面的操作；{@code getManagerQueues} 回應沒有 {@code inspection} 鍵。
     *
     * <p>畫面上逐一切過四個分頁，每一頁都不能有待查房的磚、清單、查房畫面或「待查房」字樣，
     * 最後切回首頁。
     */
    @那麼("首頁沒有待查房格，也沒有任何進待查房清單的入口")
    public void noInspectionEntryAnywhere() {
        JsonNode queues = managerQueues();
        if (queues.has("inspection") || queues.path("counts").has("inspection")) {
            throw new AssertionError("後端 getManagerQueues 不該有 inspection 鍵：" + queues);
        }
        world.waitForTestId(HOME, SCREEN_TIMEOUT);
        world.waitForTestId(HOME_MINE, SCREEN_TIMEOUT);
        assertNoInspectionEntry("首頁");
        for (String tab : List.of("待認領", "通知", "我的")) {
            tapTab(tab);
            world.waitForTestId(TAB_SCREEN_BY_LABEL.get(tab), SCREEN_TIMEOUT);
            assertNoInspectionEntry(tab + "分頁");
        }
        tapTab("首頁");
        world.waitForTestId(HOME, SCREEN_TIMEOUT);
    }

    /** 必查：該格的數字等於值（0 時顯示 0，磚不隱藏），與 {@code getManagerQueues.counts} 一致。 */
    @那麼("{string} 格顯示 {int}")
    public void tileShows(String tileLabel, int expected) {
        world.waitForTestId(HOME, SCREEN_TIMEOUT);
        assertTile(managerQueues(), queueKey(tileLabel), expected);
    }

    /**
     * 必查：主管首頁格子下方「我名下的單」區（{@code manager.home.mine}）列出的任務卡房號順序
     * 等於清單，與 {@code getMyToday.todayTasks} 一致；每張卡片用的是房務首頁的任務卡元件
     * （同一組 accessibility id 前綴 {@code home.task.{房號}}），顯示內容、狀態與主按鈕文字與
     * 房務首頁相同——這裡以卡片節點的 label（房務首頁任務卡固定是「房號 標題」）對照
     * {@code todayTasks} 的 {@code roomNo}／{@code title}。
     */
    @那麼("^「我名下的單」依序列出 (.+)，卡片與房務首頁的任務卡一致$")
    public void mineListsInOrder(String roomListText) {
        List<String> expected = splitList(roomListText);
        JsonNode today = myToday();
        List<String> fromBackend = new ArrayList<>();
        today.path("todayTasks").forEach(task -> fromBackend.add(task.path("roomNo").asText()));
        if (!expected.equals(fromBackend)) {
            throw new AssertionError("後端 GET /me/today 的 todayTasks 順序與劇本不符：預期 "
                    + expected + "，實際 " + fromBackend);
        }
        world.waitForTestId(HOME_MINE, SCREEN_TIMEOUT);
        for (String roomNo : expected) {
            world.waitForTestId(HOME_TASK_PREFIX + roomNo + HOME_TASK_SUFFIX, SCREEN_TIMEOUT);
        }
        List<String> onScreen = homeTaskRoomNosOnScreen();
        if (!expected.equals(onScreen)) {
            throw new AssertionError("「我名下的單」的任務卡順序與劇本不符：預期 " + expected
                    + "，實際 " + onScreen + "（" + world.describeTestIdsWithPrefix(HOME_TASK_PREFIX) + "）");
        }
        for (JsonNode task : today.path("todayTasks")) {
            String roomNo = task.path("roomNo").asText();
            String expectedLabel = roomNo + " " + task.path("title").asText();
            String actualLabel = world.labelOf(HOME_TASK_PREFIX + roomNo + HOME_TASK_SUFFIX);
            if (!expectedLabel.equals(actualLabel)) {
                throw new AssertionError("「我名下的單」裡 " + roomNo + " 那張卡與房務首頁的任務卡不一致：預期 label「"
                        + expectedLabel + "」，實際「" + actualLabel + "」");
            }
        }
    }

    /**
     * 必查：工務主管首頁「我名下的單」區（{@code manager.home.mine}）有該單的卡片，與
     * {@code getMyToday.engineeringSummary.tickets} 一致；卡片用的是工務首頁手上的單的工務單卡元件
     * （同一個 id {@code engHome.in-hand.{單號}.card}），顯示房號、工務單號、說明與狀態標籤，
     * 急件與驗收退回各有標籤，與工務首頁相同。
     *
     * <p>工務單卡把內文併成一個節點，label 固定是「單號 地點 狀態」（急件時狀態前多「急件 · 」），
     * 說明文字在節點內的文字子節點；這裡兩處合起來比對。
     */
    @那麼("「我名下的單」列出 {string}，卡片與工務首頁的工務單卡一致")
    public void mineListsEngineeringTicket(String ticketNo) {
        JsonNode ticket = null;
        List<String> fromBackend = new ArrayList<>();
        for (JsonNode t : myToday().path("engineeringSummary").path("tickets")) {
            fromBackend.add(t.path("ticketNo").asText());
            if (ticketNo.equals(t.path("ticketNo").asText())) {
                ticket = t;
            }
        }
        if (ticket == null) {
            throw new AssertionError("後端 GET /me/today 的 engineeringSummary.tickets 沒有「" + ticketNo + "」："
                    + fromBackend);
        }
        world.waitForTestId(HOME_MINE, SCREEN_TIMEOUT);
        WebElement card = world.waitForTestId(ENG_CARD_PREFIX + ticketNo + ENG_CARD_SUFFIX, SCREEN_TIMEOUT);
        String label = String.valueOf(card.getAttribute("label")) + "\n" + textsUnder(card);
        String place = ticket.path("isPublicArea").asBoolean(false) || ticket.path("roomNo").isNull()
                ? "公共區域"
                : ticket.path("roomNo").asText();
        String state = ticket.path("state").asText();
        String status = ENG_CARD_STATUS_BY_STATE.get(state);
        if (status == null) {
            throw new AssertionError("「" + ticketNo + "」在後端是 " + state + "，不是工務首頁手上會有的狀態");
        }
        List<String> required = new ArrayList<>(List.of(ticketNo, place, ticket.path("title").asText(), status));
        if (ticket.path("isUrgent").asBoolean(false)) {
            required.add("急件");
        }
        if (!ticket.path("verificationReturnReason").isMissingNode() && !ticket.path("verificationReturnReason").isNull()) {
            required.add("驗收退回");
        }
        for (String text : required) {
            if (!label.contains(text)) {
                throw new AssertionError("「我名下的單」裡 " + ticketNo + " 那張卡應與工務首頁的工務單卡一致，看得到「"
                        + text + "」，實際「" + label + "」");
            }
        }
    }

    /**
     * 必查：畫面是該單的工務單詳情（以房號與後端 {@code readRepairTicket} 對上），底部動作列與工務員工
     * 看自己名下同狀態的單相同（待開始為「開始維修」，維修中為完修回報），沒有通過或不通過；
     * {@code RepairTicket.viewerIsAssignee}=true。
     *
     * <p>工務員工看自己名下的單時底部動作只看狀態與是不是公共區域（公共區域多一顆「放回待認領」），
     * 這裡照同一張表比對；主管專屬的通過、不通過與認領鈕都不能出現。
     */
    @那麼("App 開啟 {string} 的工務單詳情，底部動作與工務員工相同")
    public void ticketDetailActionsSameAsEngineer(String ticketNo) {
        Map<String, Object> row = backend.repairTicketRow(ticketNo);
        JsonNode detail = backend.getJson("/repair-tickets/" + row.get("task_id"), world.loggedInStaff());
        if (!detail.path("viewerIsAssignee").asBoolean(false)) {
            throw new AssertionError("後端 readRepairTicket 的 viewerIsAssignee 應為 true（登入者是處理人），實際："
                    + detail.path("viewerIsAssignee"));
        }
        world.waitForTestId(TICKET_SCREEN, SCREEN_TIMEOUT);
        boolean publicArea = detail.path("isPublicArea").asBoolean(false) || detail.path("roomNo").isNull();
        String where = world.labelOf(TICKET_WHERE);
        String expectedWhere = publicArea ? "公共區域" : detail.path("roomNo").asText();
        if (where == null || !where.contains(expectedWhere)) {
            throw new AssertionError("工務單詳情的地點應是「" + expectedWhere + "」（" + ticketNo + "），實際「" + where + "」");
        }
        String state = detail.path("state").asText();
        String primary = switch (state) {
            case "accepted" -> "ticket.start-button";
            case "doing" -> "ticket.complete-button";
            default -> throw new AssertionError("「" + ticketNo + "」在後端是 " + state
                    + "，這句只比對處理人名下待開始或維修中的單");
        };
        world.waitForTestId(primary, SCREEN_TIMEOUT);
        if (publicArea) {
            world.waitForTestId("ticket.release-to-pool-button", SCREEN_TIMEOUT);
        } else {
            assertAbsent("ticket.release-to-pool-button", "房內的單底部不該有放回待認領");
        }
        List<String> mustNotShow = new ArrayList<>(List.of(
                "managerTicket.pass-button", "managerTicket.fail-button", "ticket.claim-button"));
        mustNotShow.add("accepted".equals(state) ? "ticket.complete-button" : "ticket.start-button");
        for (String id : mustNotShow) {
            assertAbsent(id, "處理人看自己名下" + ENG_CARD_STATUS_BY_STATE.get(state) + "的單，底部動作與工務員工相同");
        }
    }

    /**
     * 必查：主管首頁上沒有房務首頁的問候列、進度環與「下一間」卡（對應的 accessibility id 不存在）。
     * 房務首頁的殼是 {@code home.screen}，進度環是 {@code home.progress.*}，主卡的位置是「下一間」
     * 卡或 {@code home.empty-state}；問候列沒有 id，用它的文字（早安／辛苦了）查。也沒有工務首頁的
     * 問候列、三格進度與急件、保養區：工務首頁的殼是 {@code engHome.screen}，三格進度是
     * {@code engHome.progress.*}，保養格沒有 id，用「今日保養」字樣查。
     */
    @那麼("主管首頁沒有問候列與今日進度")
    public void homeHasNoGreetingNorProgress() {
        world.waitForTestId(HOME, SCREEN_TIMEOUT);
        for (String id : List.of("home.screen", "home.progress.cleaned-ratio", "home.progress.remaining-minutes",
                "home.progress.inspected-count", "home.progress.ad-hoc-count", "home.empty-state")) {
            assertAbsent(id, "主管首頁不該有房務首頁的元素");
        }
        for (String id : List.of("engHome.screen", "engHome.progress.in-hand-count",
                "engHome.progress.repaired-count", "engHome.progress.urgent-count", "engHome.empty-state")) {
            assertAbsent(id, "主管首頁不該有工務首頁的元素");
        }
        for (String text : List.of("早安", "辛苦了", "下一間", "今日保養")) {
            assertNoLabelContaining(text, "主管首頁不該有員工首頁的問候列、「下一間」卡或保養區");
        }
    }

    /**
     * 必查：「我名下的單」區顯示「今天沒有名下的單」與一行副文，沒有任何卡片；房務主管的
     * {@code getMyToday.todayTasks} 為空，工務主管的 {@code engineeringSummary.tickets} 為空。
     * 主管種類照 {@link ManagerHomeWait} 同一條口徑（後端員工資料的 dept）。
     */
    @那麼("「我名下的單」說明目前沒有名下的單")
    public void mineIsEmpty() {
        boolean engineeringManager = ManagerHomeWait.queuesFor(world.loggedInStaff()).equals(List.of("verify"));
        JsonNode today = myToday();
        JsonNode mine = engineeringManager
                ? today.path("engineeringSummary").path("tickets")
                : today.path("todayTasks");
        if (!mine.isEmpty()) {
            throw new AssertionError("後端 GET /me/today 的 "
                    + (engineeringManager ? "engineeringSummary.tickets" : "todayTasks")
                    + " 不是空的，「我名下的單」不該是空狀態：" + mine);
        }
        world.waitForTestId(HOME_MINE, SCREEN_TIMEOUT);
        world.waitForLabelContaining("今天沒有名下的單", SCREEN_TIMEOUT);
        List<String> cards = engineeringManager ? engCardTicketNosOnScreen() : homeTaskRoomNosOnScreen();
        if (!cards.isEmpty()) {
            throw new AssertionError("「我名下的單」說明沒有單，畫面上卻還有卡片：" + cards);
        }
    }

    /**
     * 必查：房務或工務員工的首頁上沒有 {@code manager.home.tile.*} 任何一個磚，也沒有任何通往
     * 待查房或待驗收清單的入口（主管首頁殼、清單畫面、格子的文字都不在）。工務員工的首頁是
     * 工務首頁（{@code engHome.screen}），房務員工是房務首頁（{@code home.screen}）。
     */
    @那麼("首頁沒有主管的格子")
    public void homeHasNoManagerTiles() {
        if ("engineering".equals(world.loggedInStaff().dept())) {
            world.waitForTestId("engHome.screen", SCREEN_TIMEOUT);
        } else {
            world.ensureOnHome(SCREEN_TIMEOUT);
        }
        List<String> tiles = world.findTestIdsWithPrefix(TILE_PREFIX);
        if (!tiles.isEmpty()) {
            throw new AssertionError("員工首頁不該有主管的格子，卻找到 " + tiles);
        }
        for (String queue : ALL_QUEUES) {
            assertAbsent(TILE_PREFIX + queue, "員工首頁不該有主管的格子");
            assertAbsent(TILE_PREFIX + queue + ".count", "員工首頁不該有主管的格子");
        }
        assertAbsent(HOME, "員工首頁不該是主管首頁");
        assertAbsent(QUEUE, "員工首頁不該通往主管清單");
        for (String label : QUEUE_KEY_BY_LABEL.keySet()) {
            assertNoLabelContaining(label, "員工首頁不該有通往主管清單的入口");
        }
    }

    /**
     * 必查：Tab bar 的分頁標題順序等於清單；通知與我的兩個分頁可切換，切過去的畫面分別是
     * 員工也在用的通知中心（{@code notification.screen}）與「我的」（{@code me.screen}）。
     * 分頁格沒有 accessibility id（整格併成一個帶 label 的按鈕節點），用 label 認、依 x 座標排序。
     */
    @那麼("^底部分頁依序是 (.+)$")
    public void tabBarOrder(String tabListText) {
        List<String> expected = splitList(tabListText);
        List<String> onScreen = tabLabelsLeftToRight(expected);
        if (!expected.equals(onScreen)) {
            throw new AssertionError("底部分頁順序與劇本不符：預期 " + expected + "，實際 " + onScreen);
        }
        if (expected.contains("通知")) {
            tapTab("通知");
            world.waitForTestId("notification.screen", SCREEN_TIMEOUT);
        }
        if (expected.contains("我的")) {
            tapTab("我的");
            world.waitForTestId("me.screen", SCREEN_TIMEOUT);
        }
        tapTab(expected.get(0));
    }

    /**
     * 必查：目前清單畫面的列順序剛好等於清單，沒有多也沒有少，與 {@code getManagerQueues}
     * 對應陣列的順序一致（App 不重排）。哪一類清單由 {@link ManagerContext#openedQueue()} 決定。
     */
    @那麼("^清單依序為 (.+)$")
    public void queueOrder(String listText) {
        List<String> expected = splitList(listText);
        String queue = openedQueue();
        List<String> fromBackend = queueKeys(managerQueues(), queue);
        if (!expected.equals(fromBackend)) {
            throw new AssertionError("後端 getManagerQueues." + queue + " 的順序與劇本不符：預期 "
                    + expected + "，實際 " + fromBackend);
        }
        world.waitForTestId(QUEUE, SCREEN_TIMEOUT);
        for (String key : expected) {
            world.waitForTestId(QUEUE_ROW_PREFIX + key, SCREEN_TIMEOUT);
        }
        List<String> onScreen = queueRowKeysOnScreen();
        if (!expected.equals(onScreen)) {
            throw new AssertionError("清單畫面的列順序與劇本不符：預期 " + expected + "，實際 " + onScreen
                    + "（" + world.describeTestIdsWithPrefix(QUEUE_ROW_PREFIX) + "）");
        }
    }

    /** 必查：目前清單畫面沒有該單那一列，且 {@code getManagerQueues} 對應陣列也沒有它。 */
    @那麼("清單上沒有 {string}")
    public void queueLacks(String key) {
        String queue = openedQueue();
        List<String> fromBackend = queueKeys(managerQueues(), queue);
        if (fromBackend.contains(key)) {
            throw new AssertionError("後端 getManagerQueues." + queue + " 裡還有「" + key + "」：" + fromBackend);
        }
        world.waitForTestId(QUEUE, SCREEN_TIMEOUT);
        List<String> onScreen = queueRowKeysOnScreen();
        if (onScreen.contains(key)) {
            throw new AssertionError("清單畫面上不該有「" + key + "」那一列，實際清單為 " + onScreen);
        }
    }

    /**
     * 必查：該列右欄顯示日期與時刻（{@code manager.queue.row.{單}.waiting-since}），對應的
     * {@code waitingSinceIsToday}=false；日期與時刻直接取自 {@code waitingSince} 字串的時區位移，
     * 不是手機時區換算。日期寫「昨天」時，與後端時鐘的前一天相符（以 {@code waitingSince}
     * 自帶的位移算「今天」）。
     */
    @那麼("{string} 那一列標出日期 {string} 與時刻 {string}")
    public void rowShowsDateAndTime(String key, String date, String time) {
        JsonNode item = queueItem(managerQueues(), openedQueue(), key);
        if (item.path("waitingSinceIsToday").asBoolean(true)) {
            throw new AssertionError("後端說「" + key + "」是今天開始等的（waitingSinceIsToday=true），不該標日期");
        }
        OffsetDateTime waitingSince = OffsetDateTime.parse(item.path("waitingSince").asText());
        String expectedTime = waitingSince.format(HH_MM);
        if (!time.equals(expectedTime)) {
            throw new AssertionError("後端 waitingSince=" + waitingSince + " 的時刻是 " + expectedTime
                    + "，劇本寫 " + time);
        }
        if ("昨天".equals(date)) {
            OffsetDateTime nowThere = world.deviceNow().withOffsetSameInstant(waitingSince.getOffset());
            if (!waitingSince.toLocalDate().equals(nowThere.toLocalDate().minusDays(1))) {
                throw new AssertionError("後端 waitingSince=" + waitingSince + " 不是昨天（該時區今天是 "
                        + nowThere.toLocalDate() + "）");
            }
        } else if (!date.equals(waitingSince.format(MM_DD))) {
            throw new AssertionError("後端 waitingSince=" + waitingSince + " 的日期是 "
                    + waitingSince.format(MM_DD) + "，劇本寫 " + date);
        }
        String label = waitingSinceLabel(key);
        if (!label.contains(date) || !label.contains(time)) {
            throw new AssertionError("「" + key + "」那一列的右欄應標出日期「" + date + "」與時刻「" + time
                    + "」，實際「" + label + "」");
        }
    }

    /** 必查：該列右欄只有時刻、沒有日期，對應的 {@code waitingSinceIsToday}=true。 */
    @那麼("{string} 那一列只顯示時刻 {string}")
    public void rowShowsTimeOnly(String key, String time) {
        JsonNode item = queueItem(managerQueues(), openedQueue(), key);
        if (!item.path("waitingSinceIsToday").asBoolean(false)) {
            throw new AssertionError("後端說「" + key + "」不是今天開始等的（waitingSinceIsToday=false），該標日期");
        }
        OffsetDateTime waitingSince = OffsetDateTime.parse(item.path("waitingSince").asText());
        String expectedTime = waitingSince.format(HH_MM);
        if (!time.equals(expectedTime)) {
            throw new AssertionError("後端 waitingSince=" + waitingSince + " 的時刻是 " + expectedTime
                    + "，劇本寫 " + time);
        }
        String label = waitingSinceLabel(key);
        if (!time.equals(label.trim())) {
            throw new AssertionError("「" + key + "」那一列的右欄應只有時刻「" + time + "」，實際「" + label + "」");
        }
    }

    /**
     * 必查：清單每一列都顯示房號（公共區域的單顯示地點說明）與內容文字，分別與
     * {@code roomNo}／{@code title} 一致。列節點的 label 要同時含這兩段。
     */
    @那麼("每一列都看得到房號與內容")
    public void everyRowShowsRoomAndTitle() {
        String queue = openedQueue();
        JsonNode items = managerQueues().path(queue);
        world.waitForTestId(QUEUE, SCREEN_TIMEOUT);
        for (JsonNode item : items) {
            String key = rowKey(queue, item);
            String label = rowLabel(key);
            String where = item.path("isPublicArea").asBoolean(false) || item.path("roomNo").isNull()
                    ? item.path("title").asText()
                    : item.path("roomNo").asText();
            String title = item.path("title").asText();
            if (!label.contains(where) || !label.contains(title)) {
                throw new AssertionError("「" + key + "」那一列應看得到房號「" + where + "」與內容「" + title
                        + "」，實際 label「" + label + "」");
            }
        }
    }

    /**
     * 必查：待驗收清單每一列都顯示 {@code ticketNo} 與完修結果標籤（已修好、暫時處理、無法修復，
     * 對照 {@code resolutionType}；舊單 {@code resolutionType} 為 null 時不畫結果標籤）。
     */
    @那麼("每一列都看得到工務單號與完修結果")
    public void everyRowShowsTicketNoAndResolution() {
        String queue = openedQueue();
        if (!"verify".equals(queue)) {
            throw new AssertionError("這句只用在待驗收清單，目前打開的是 " + queue);
        }
        JsonNode items = managerQueues().path(queue);
        world.waitForTestId(QUEUE, SCREEN_TIMEOUT);
        for (JsonNode item : items) {
            String ticketNo = item.path("ticketNo").asText();
            String label = rowLabel(ticketNo);
            if (!label.contains(ticketNo)) {
                throw new AssertionError("「" + ticketNo + "」那一列應看得到工務單號，實際 label「" + label + "」");
            }
            JsonNode resolution = item.path("resolutionType");
            if (resolution.isMissingNode() || resolution.isNull()) {
                for (String text : RESOLUTION_LABEL_BY_TYPE.values()) {
                    if (label.contains(text)) {
                        throw new AssertionError("「" + ticketNo + "」沒有完修結果（resolutionType=null），不該畫結果標籤，"
                                + "實際 label「" + label + "」");
                    }
                }
                continue;
            }
            String expected = RESOLUTION_LABEL_BY_TYPE.get(resolution.asText());
            if (expected == null) {
                throw new AssertionError("後端待驗收清單裡「" + ticketNo + "」的 resolutionType 是「" + resolution.asText()
                        + "」，不是待驗收會有的完修結果（" + RESOLUTION_LABEL_BY_TYPE.keySet() + "）");
            }
            if (!label.contains(expected)) {
                throw new AssertionError("「" + ticketNo + "」那一列應標示完修結果「" + expected + "」，實際 label「" + label + "」");
            }
        }
    }

    /**
     * 必查：待驗收清單該列顯示該標籤文字，與 {@code resolutionType}（已修好＝fixed、暫時處理＝temporary、
     * 無法修復＝cannot_fix）、{@code origin}（定期保養＝scheduled）或 {@code wasVerificationFailed}
     * （重修後再驗＝true）一致。
     */
    @那麼("{string} 那一列標示 {string}")
    public void rowShowsTag(String ticketNo, String tag) {
        Map.Entry<String, String> field = TAG_FIELD_BY_LABEL.get(tag);
        if (field == null) {
            throw new IllegalArgumentException("認不得的標籤「" + tag + "」（合法值：" + TAG_FIELD_BY_LABEL.keySet() + "）");
        }
        JsonNode item = queueItem(managerQueues(), "verify", ticketNo);
        String actual = item.path(field.getKey()).asText();
        if (!field.getValue().equals(actual)) {
            throw new AssertionError("後端待驗收清單裡「" + ticketNo + "」的 " + field.getKey() + " 是「" + actual
                    + "」，標籤「" + tag + "」要的是「" + field.getValue() + "」");
        }
        String label = rowLabel(ticketNo);
        if (!label.contains(tag)) {
            throw new AssertionError("「" + ticketNo + "」那一列應標示「" + tag + "」，實際 label「" + label + "」");
        }
    }

    /**
     * 必查：清單畫面顯示該類的空狀態說明（{@code manager.queue.empty}；待查房「目前沒有等你查的房」、
     * 待驗收「目前沒有等你驗收的工務單」）與副文，沒有任何列；
     * {@code getManagerQueues} 對應陣列為空。
     */
    @那麼("清單說明目前沒有等他處理的單，畫面不是空白")
    public void queueIsEmptyWithExplanation() {
        String queue = openedQueue();
        JsonNode items = managerQueues().path(queue);
        if (!items.isEmpty()) {
            throw new AssertionError("後端 getManagerQueues." + queue + " 不是空的，清單不該是空狀態：" + items);
        }
        world.waitForTestId(QUEUE, SCREEN_TIMEOUT);
        WebElement empty = world.waitForTestId(QUEUE_EMPTY, SCREEN_TIMEOUT);
        String label = String.valueOf(empty.getAttribute("label"));
        String title = EMPTY_TITLE_BY_QUEUE.get(queue);
        if (!label.contains(title)) {
            throw new AssertionError("清單空狀態應說「" + title + "」，實際「" + label + "」");
        }
        if (label.replace(title, "").replace("。", "").isBlank()) {
            throw new AssertionError("清單空狀態只有主文沒有副文：「" + label + "」");
        }
        List<String> rows = queueRowKeysOnScreen();
        if (!rows.isEmpty()) {
            throw new AssertionError("清單說明沒有單，畫面上卻還有列：" + rows);
        }
    }

    /**
     * 必查：查房畫面的項目列（{@code inspect.item.{key}}）依序等於表格，每一列的「通過」與「不通過」
     * 都未選；與 {@code listSopItems} 篩 applies_to=inspection 且啟用的集合一致。
     */
    @那麼("查房畫面列出以下項目，每一項都還沒判：")
    public void inspectionListsUntouched(DataTable table) {
        List<String> expectedLabels = table.asMaps().stream().map(row -> requireCell(row, "查房項目")).toList();
        assertInspectionItemsUnjudged(expectedLabels);
    }

    /**
     * 必查：{@code inspect.submit-button} 的可互動狀態符合；禁用時無法真的觸發送出、後端沒有收到
     * 查房（與「完修回報的送出鈕為」同一套語意：真的按下去，再驗畫面沒走、後端沒多一筆查房）。
     */
    @那麼("查房送出鈕為 {string}")
    public void inspectionSubmitButtonState(String enabledOrDisabled) {
        boolean expectedEnabled = switch (enabledOrDisabled) {
            case "啟用" -> true;
            case "禁用" -> false;
            default -> throw new IllegalArgumentException(
                    "認不得的按鈕狀態「" + enabledOrDisabled + "」（只支援 啟用｜禁用）");
        };
        world.waitForTestId(INSPECT, SCREEN_TIMEOUT);
        WebElement submit = world.waitForTestId(INSPECT_SUBMIT_BUTTON, SCREEN_TIMEOUT);
        if (submit.isEnabled() != expectedEnabled) {
            throw new AssertionError("查房送出鈕應為「" + enabledOrDisabled + "」，實際是「"
                    + (submit.isEnabled() ? "啟用" : "禁用") + "」");
        }
        if (!expectedEnabled) {
            UUID taskId = inspectingTaskId();
            int before = inspectionCount(taskId);
            try {
                submit.click();
            } catch (RuntimeException ignored) {
                // 禁用鈕拒絕點擊而拋例外，正是「送出沒有發生」的一種表現。
            }
            world.waitForTestId(INSPECT_SUBMIT_BUTTON, SCREEN_TIMEOUT);
            assertAbsent(INSPECT_RESULT, "送出鈕禁用時按下去不該走到結果畫面");
            int after = inspectionCount(taskId);
            if (after != before) {
                throw new AssertionError("送出鈕禁用時按下去，後端卻多了查房紀錄（" + before + " → " + after + "）");
            }
        }
    }

    /** 必查：查房畫面上沒有任何可輸入文字的欄位，也沒有拍照入口。 */
    @那麼("查房畫面沒有備註欄")
    public void inspectionHasNoNoteField() {
        world.waitForTestId(INSPECT, SCREEN_TIMEOUT);
        for (String type : List.of("XCUIElementTypeTextField", "XCUIElementTypeTextView", "XCUIElementTypeSecureTextField")) {
            List<WebElement> fields = world.driver().findElements(By.className(type));
            if (!fields.isEmpty()) {
                throw new AssertionError("查房畫面上不該有可輸入文字的欄位，卻找到 " + fields.size() + " 個 " + type);
            }
        }
        for (String text : List.of("拍照", "照片", "備註")) {
            assertNoLabelContaining(text, "查房畫面不該有備註或拍照入口");
        }
    }

    /**
     * 必查：項目清單的位置顯示「這家飯店目前沒有設定查房項目，送出即視為全部通過」，沒有任何項目列；
     * 後端現行啟用集合也是空的。
     */
    @那麼("查房畫面說明目前沒有查房項目，送出即視為全部通過")
    public void inspectionExplainsNoItems() {
        List<String> active = activeInspectionItemKeys();
        if (!active.isEmpty()) {
            throw new AssertionError("後端還有啟用的查房項目 " + active + "，查房畫面不該是「沒有查房項目」");
        }
        world.waitForTestId(INSPECT, SCREEN_TIMEOUT);
        world.waitForLabelContaining("這家飯店目前沒有設定查房項目，送出即視為全部通過", SCREEN_TIMEOUT);
        List<String> rows = inspectionItemKeysOnScreen();
        if (!rows.isEmpty()) {
            throw new AssertionError("查房畫面說沒有項目，卻還列著 " + rows);
        }
    }

    /**
     * 必查：後端這次回 422 {@code inspection_items_mismatch}；App 留在查房畫面，頂部出現
     * 「查房項目剛更新，請重新判定」，項目列換成表格列出的集合（取自回應的 {@code currentItems}），
     * 所有判定清空，送出鈕禁用；後端該單仍為待查房。
     *
     * <p>422 本身在 App 與後端之間，測試看不到那次往返；以它的後果驗：單沒變、沒有新的查房紀錄、
     * 畫面換成現行集合（表格＝後端現行啟用集合）且判定全清。
     */
    @那麼("查房畫面重新載入以下項目並清空判定：")
    public void inspectionReloadedAndCleared(DataTable table) {
        List<String> expectedLabels = table.asMaps().stream().map(row -> requireCell(row, "查房項目")).toList();
        UUID taskId = inspectingTaskId();
        Map<String, Object> task = backend.taskRow(taskId);
        if (!"done".equals(task.get("state"))) {
            throw new AssertionError("後端該單應仍為待查房（done），實際 state=" + task.get("state"));
        }
        int inspections = inspectionCount(taskId);
        if (inspections != 0) {
            throw new AssertionError("後端該單不該有任何查房紀錄，卻有 " + inspections + " 筆");
        }
        world.waitForTestId(INSPECT, SCREEN_TIMEOUT);
        assertAbsent(INSPECT_RESULT, "項目不符時不該走到結果畫面");
        world.waitForLabelContaining("查房項目剛更新，請重新判定", SCREEN_TIMEOUT);
        assertInspectionItemsUnjudged(expectedLabels);
        context.clearJudgements();
        WebElement submit = world.waitForTestId(INSPECT_SUBMIT_BUTTON, SCREEN_TIMEOUT);
        if (submit.isEnabled()) {
            throw new AssertionError("判定清空後送出鈕應為禁用，實際是啟用");
        }
    }

    /**
     * 必查：結果畫面（{@code inspect.result}）標題含該房號與「查房通過」，副文說明清房者會收到通知；
     * 對應 {@code InspectionOutcome.result}=passed、{@code destination}=closed；後端該單為查房通過。
     */
    @那麼("查房結果畫面說明 {string} 查房通過、已結案")
    public void resultSaysPassed(String roomNo) {
        Map<String, Object> task = world.taskRowByRoomNo(roomNo);
        if (!"inspected".equals(task.get("state"))) {
            throw new AssertionError("後端 " + roomNo + " 應為查房通過（inspected），實際 state=" + task.get("state"));
        }
        Map<String, Object> inspection = latestInspection((UUID) task.get("id"));
        if (!"passed".equals(inspection.get("result"))) {
            throw new AssertionError("後端 " + roomNo + " 最新一筆查房的 result 應為 passed，實際 " + inspection.get("result"));
        }
        String label = resultLabel();
        for (String text : List.of(roomNo, "查房通過", "通知")) {
            if (!label.contains(text)) {
                throw new AssertionError("查房結果畫面應含「" + text + "」（通過：房號、「查房通過」、清房者會收到通知），實際「"
                        + label + "」");
            }
        }
    }

    /**
     * 必查：結果畫面標題含該房號與「退回重做」，副文含「回到{員工}名下」與不合格項目清單；
     * 對應 {@code result}=failed、{@code destination}=cleaner、{@code failedItems} 的 label 等於項目；
     * 後端該單為待開始、負責人不變。
     */
    @那麼("查房結果畫面說明 {string} 退回重做，回到 {string} 名下，不合格項為 {string}")
    public void resultSaysReturnedToCleaner(String roomNo, String cleanerName, String itemsText) {
        List<String> failedLabels = splitList(itemsText);
        Map<String, Object> task = world.taskRowByRoomNo(roomNo);
        if (!"accepted".equals(task.get("state"))) {
            throw new AssertionError("後端 " + roomNo + " 應為待開始（accepted），實際 state=" + task.get("state"));
        }
        UUID cleanerId = world.staff(cleanerName).id();
        if (!cleanerId.equals(task.get("assignee_id"))) {
            throw new AssertionError("後端 " + roomNo + " 的負責人應仍是「" + cleanerName + "」（" + cleanerId
                    + "），實際 assignee_id=" + task.get("assignee_id"));
        }
        assertLatestInspectionFailedWith((UUID) task.get("id"), roomNo, failedLabels);
        String label = resultLabel();
        List<String> required = new ArrayList<>(List.of(roomNo, "退回重做", "回到" + cleanerName + "名下"));
        required.addAll(failedLabels);
        for (String text : required) {
            if (!label.contains(text)) {
                throw new AssertionError("查房結果畫面應含「" + text + "」，實際「" + label + "」");
            }
        }
    }

    /**
     * 必查：結果畫面標題含該房號與「退回重做」，副文含「{員工}已下班」與「回到了房務待認領」；
     * 對應 {@code destination}=pool；後端該單為待認領、沒有負責人。App 不自己判斷班表，只照回應呈現。
     */
    @那麼("查房結果畫面說明 {string} 退回重做，{string} 已下班，回到房務待認領")
    public void resultSaysReturnedToPool(String roomNo, String cleanerName) {
        world.staff(cleanerName);
        Map<String, Object> task = world.taskRowByRoomNo(roomNo);
        if (!"pool".equals(task.get("state"))) {
            throw new AssertionError("後端 " + roomNo + " 應為待認領（pool），實際 state=" + task.get("state"));
        }
        if (task.get("assignee_id") != null) {
            throw new AssertionError("後端 " + roomNo + " 回到待認領後不該有負責人，實際 assignee_id=" + task.get("assignee_id"));
        }
        Map<String, Object> inspection = latestInspection((UUID) task.get("id"));
        if (!"failed".equals(inspection.get("result"))) {
            throw new AssertionError("後端 " + roomNo + " 最新一筆查房的 result 應為 failed，實際 " + inspection.get("result"));
        }
        String label = resultLabel();
        for (String text : List.of(roomNo, "退回重做", cleanerName + "已下班", "回到了房務待認領")) {
            if (!label.contains(text)) {
                throw new AssertionError("查房結果畫面應含「" + text + "」，實際「" + label + "」");
            }
        }
    }

    /** 該格畫面數字等於期待值，且等於後端 counts 與對應陣列長度。 */
    private void assertTile(JsonNode queues, String queue, int expected) {
        int count = queues.path("counts").path(queue).asInt(-1);
        int size = queues.path(queue).size();
        if (count != expected || size != expected) {
            throw new AssertionError("後端 getManagerQueues 的「" + queue + "」與劇本不符：預期 " + expected
                    + "，counts=" + count + "，陣列長度=" + size);
        }
        String id = "manager.home.tile." + queue + ".count";
        String onScreen = world.waitForLabelOf(id, String.valueOf(expected), SCREEN_TIMEOUT);
        if (!String.valueOf(expected).equals(onScreen)) {
            throw new AssertionError("格子「" + queue + "」（" + id + "）不符：預期 " + expected + "，實際 " + onScreen);
        }
    }

    /**
     * 畫面上的磚剛好是這幾格（{@code manager.home.tile.{queue}} 這一層，不收 {@code .count}），
     * 且由上而下（同列時由左而右）的順序等於給定順序。
     */
    private void assertTilesOnScreenExactly(List<String> expectedOrder) {
        List<String> present = world.findTestIdsWithPrefix(TILE_PREFIX).stream()
                .map(id -> id.substring(TILE_PREFIX.length()))
                .filter(key -> !key.contains("."))
                .distinct()
                .toList();
        if (!new java.util.HashSet<>(present).equals(new java.util.HashSet<>(expectedOrder))) {
            throw new AssertionError("主管首頁的格子應剛好是 " + expectedOrder + "，實際 " + present
                    + "（" + world.describeTestIdsWithPrefix(TILE_PREFIX) + "）");
        }
        List<Map.Entry<int[], String>> placed = new ArrayList<>();
        for (String queue : expectedOrder) {
            WebElement tile = world.waitForTestId(TILE_PREFIX + queue, SCREEN_TIMEOUT);
            org.openqa.selenium.Rectangle rect = tile.getRect();
            placed.add(Map.entry(new int[] {rect.getY(), rect.getX()}, queue));
        }
        // 同一列的格子讀出來的 y 可能差 1pt（讀兩次之間畫面還在收尾），差距在
        // SAME_ROW_TOLERANCE 以內就算同一列，改比 x。
        placed.sort((a, b) -> Math.abs(a.getKey()[0] - b.getKey()[0]) > SAME_ROW_TOLERANCE
                ? Integer.compare(a.getKey()[0], b.getKey()[0])
                : Integer.compare(a.getKey()[1], b.getKey()[1]));
        List<String> onScreen = placed.stream().map(Map.Entry::getValue).toList();
        if (!expectedOrder.equals(onScreen)) {
            throw new AssertionError("格子在畫面上的順序應為 " + expectedOrder + "，實際 " + onScreen);
        }
    }

    /** 後端 {@code getManagerQueues.counts} 的鍵剛好是這幾類，沒有多出的類別（例如已收回的 review）。 */
    private static void assertCountsKeysExactly(JsonNode queues, List<String> expected) {
        List<String> keys = new ArrayList<>();
        queues.path("counts").fieldNames().forEachRemaining(keys::add);
        if (!new java.util.HashSet<>(keys).equals(new java.util.HashSet<>(expected))) {
            throw new AssertionError("後端 getManagerQueues.counts 的類別應剛好是 " + expected + "，實際 " + keys);
        }
    }

    /** 磚節點的 label；殼節點沒併 label 時退而收它底下所有文字節點。 */
    private String tileLabel(String queue) {
        WebElement tile = world.waitForTestId(TILE_PREFIX + queue, SCREEN_TIMEOUT);
        String label = tile.getAttribute("label");
        return (label == null ? "" : label) + "\n" + textsUnder(tile);
    }

    /** 節點底下所有文字節點的 label，以換行串起來。 */
    private static String textsUnder(WebElement element) {
        StringBuilder joined = new StringBuilder();
        for (WebElement text : element.findElements(By.className("XCUIElementTypeStaticText"))) {
            joined.append(text.getAttribute("label")).append('\n');
        }
        return joined.toString();
    }

    /** 目前分頁上沒有任何通往待查房清單或查房畫面的東西：磚、清單、查房畫面、「待查房」字樣。 */
    private void assertNoInspectionEntry(String where) {
        String why = "工務主管的" + where + "不該有通往待查房清單或查房畫面的入口";
        assertAbsent(TILE_PREFIX + "inspection", why);
        assertAbsent(TILE_PREFIX + "inspection.count", why);
        assertAbsent(INSPECT, why);
        assertAbsent(INSPECT_RESULT, why);
        assertNoLabelContaining("待查房", why);
    }

    /** 畫面上工務單卡的單號；只收 {@code engHome.in-hand.{單號}.card}。 */
    private List<String> engCardTicketNosOnScreen() {
        return world.findTestIdsWithPrefix(ENG_CARD_PREFIX).stream()
                .filter(id -> id.endsWith(ENG_CARD_SUFFIX))
                .map(id -> id.substring(ENG_CARD_PREFIX.length(), id.length() - ENG_CARD_SUFFIX.length()))
                .filter(no -> !no.contains("."))
                .toList();
    }

    private String openedQueue() {
        String queue = context.openedQueue();
        if (queue == null) {
            throw new IllegalStateException("這個情境還沒打開任何一類清單——劇本是否漏了「已在 App 打開 …清單」或「在主管首頁點 …」？");
        }
        return queue;
    }

    /** 該類清單列的識別鍵：待查房用房號，待驗收用工務單號（同畫面列 id 的尾段）。 */
    private static String rowKey(String queue, JsonNode item) {
        return "inspection".equals(queue) ? item.path("roomNo").asText() : item.path("ticketNo").asText();
    }

    private static List<String> queueKeys(JsonNode queues, String queue) {
        List<String> keys = new ArrayList<>();
        queues.path(queue).forEach(item -> keys.add(rowKey(queue, item)));
        return keys;
    }

    private JsonNode queueItem(JsonNode queues, String queue, String key) {
        for (JsonNode item : queues.path(queue)) {
            if (key.equals(rowKey(queue, item))) {
                return item;
            }
        }
        throw new AssertionError("後端 getManagerQueues." + queue + " 裡沒有「" + key + "」：" + queueKeys(queues, queue));
    }

    /** 畫面上清單列的鍵，由上而下；只收 {@code manager.queue.row.{key}} 這一層，不收列內子節點。 */
    private List<String> queueRowKeysOnScreen() {
        return world.findTestIdsWithPrefix(QUEUE_ROW_PREFIX).stream()
                .map(id -> id.substring(QUEUE_ROW_PREFIX.length()))
                .filter(key -> !key.contains("."))
                .toList();
    }

    private String rowLabel(String key) {
        WebElement row = world.waitForTestId(QUEUE_ROW_PREFIX + key, SCREEN_TIMEOUT);
        return String.valueOf(row.getAttribute("label"));
    }

    private String waitingSinceLabel(String key) {
        WebElement cell = world.waitForTestId(QUEUE_ROW_PREFIX + key + ".waiting-since", SCREEN_TIMEOUT);
        return String.valueOf(cell.getAttribute("label"));
    }

    private List<String> homeTaskRoomNosOnScreen() {
        return world.findTestIdsWithPrefix(HOME_TASK_PREFIX).stream()
                .filter(id -> id.endsWith(HOME_TASK_SUFFIX))
                .map(id -> id.substring(HOME_TASK_PREFIX.length(), id.length() - HOME_TASK_SUFFIX.length()))
                .filter(roomNo -> !roomNo.contains("."))
                .toList();
    }

    /** 畫面上 label 剛好是這些分頁名的按鈕，依 x 座標由左而右。 */
    private List<String> tabLabelsLeftToRight(List<String> labels) {
        List<Map.Entry<Integer, String>> found = new ArrayList<>();
        for (String label : labels) {
            List<WebElement> matches = world.driver().findElements(
                    AppiumBy.iOSNsPredicateString("type == 'XCUIElementTypeButton' AND label == " + quote(label)));
            for (WebElement e : matches) {
                org.openqa.selenium.Rectangle rect = e.getRect();
                if (rect.getHeight() > 0) {
                    found.add(Map.entry(rect.getX(), label));
                    break;
                }
            }
        }
        found.sort(Map.Entry.comparingByKey());
        return found.stream().map(Map.Entry::getValue).toList();
    }

    private void tapTab(String label) {
        world.driver().findElement(
                AppiumBy.iOSNsPredicateString("type == 'XCUIElementTypeButton' AND label == " + quote(label))).click();
    }

    /** 畫面項目列依序等於這些顯示名稱，每一列的「通過」與「不通過」都未選；且等於後端現行啟用集合。 */
    private void assertInspectionItemsUnjudged(List<String> expectedLabels) {
        List<Map<String, Object>> active = backend.queryRows(
                "SELECT item_key, label FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection'"
                        + " AND active = true ORDER BY sort_order", backend.ensureTenant());
        List<String> activeLabels = active.stream().map(row -> String.valueOf(row.get("label"))).toList();
        if (!expectedLabels.equals(activeLabels)) {
            throw new AssertionError("後端現行啟用的查房項目與劇本不符：預期 " + expectedLabels + "，實際 " + activeLabels);
        }
        List<String> expectedKeys = active.stream().map(row -> String.valueOf(row.get("item_key"))).toList();
        world.waitForTestId(INSPECT, SCREEN_TIMEOUT);
        for (String key : expectedKeys) {
            world.waitForTestId(INSPECT_ITEM_PREFIX + key, SCREEN_TIMEOUT);
        }
        List<String> onScreen = inspectionItemKeysOnScreen();
        if (!expectedKeys.equals(onScreen)) {
            throw new AssertionError("查房畫面的項目列與劇本不符：預期 " + expectedKeys + "，實際 " + onScreen
                    + "（" + world.describeTestIdsWithPrefix(INSPECT_ITEM_PREFIX) + "）");
        }
        for (int i = 0; i < expectedKeys.size(); i++) {
            String key = expectedKeys.get(i);
            for (String suffix : List.of(".pass-button", ".fail-button")) {
                WebElement toggle = world.waitForTestId(INSPECT_ITEM_PREFIX + key + suffix, SCREEN_TIMEOUT);
                if (isSelected(toggle)) {
                    throw new AssertionError("「" + expectedLabels.get(i) + "」的 " + suffix + " 不該已選");
                }
            }
        }
    }

    private List<String> inspectionItemKeysOnScreen() {
        return world.findTestIdsWithPrefix(INSPECT_ITEM_PREFIX).stream()
                .map(id -> id.substring(INSPECT_ITEM_PREFIX.length()))
                .filter(key -> !key.contains("."))
                .toList();
    }

    private List<String> activeInspectionItemKeys() {
        return backend.queryRows(
                        "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection'"
                                + " AND active = true ORDER BY sort_order", backend.ensureTenant())
                .stream().map(row -> String.valueOf(row.get("item_key"))).toList();
    }

    /** 查房畫面目前開著的那間房在後端的任務 id。 */
    private UUID inspectingTaskId() {
        String roomNo = context.inspectingRoomNo();
        if (roomNo == null) {
            throw new IllegalStateException("這個情境還沒打開任何查房畫面——劇本是否漏了「已從待查房清單打開 …」？");
        }
        return (UUID) world.taskRowByRoomNo(roomNo).get("id");
    }

    private int inspectionCount(UUID taskId) {
        return backend.queryRows("SELECT id FROM inspections WHERE tenant_id = ? AND task_id = ?",
                backend.ensureTenant(), taskId).size();
    }

    private Map<String, Object> latestInspection(UUID taskId) {
        List<Map<String, Object>> rows = backend.queryRows(
                "SELECT * FROM inspections WHERE tenant_id = ? AND task_id = ? ORDER BY created_at DESC",
                backend.ensureTenant(), taskId);
        if (rows.isEmpty()) {
            throw new AssertionError("後端沒有這張單的任何查房紀錄（task_id=" + taskId + "）");
        }
        return rows.get(0);
    }

    /** 最新一筆查房 result=failed，且 items 裡 pass=false 的鍵對應的 label 剛好等於這些項目。 */
    private void assertLatestInspectionFailedWith(UUID taskId, String roomNo, List<String> failedLabels) {
        Map<String, Object> inspection = latestInspection(taskId);
        if (!"failed".equals(inspection.get("result"))) {
            throw new AssertionError("後端 " + roomNo + " 最新一筆查房的 result 應為 failed，實際 " + inspection.get("result"));
        }
        List<String> actual = new ArrayList<>();
        backend.queryRows("""
                SELECT s.label FROM inspections i
                  JOIN LATERAL jsonb_array_elements(i.items) AS it ON true
                  JOIN sop_items s ON s.tenant_id = i.tenant_id AND s.item_key = it->>'key'
                 WHERE i.tenant_id = ? AND i.id = ? AND (it->>'pass')::boolean = false
                 ORDER BY s.sort_order
                """, backend.ensureTenant(), inspection.get("id"))
                .forEach(row -> actual.add(String.valueOf(row.get("label"))));
        if (!failedLabels.equals(actual)) {
            throw new AssertionError("後端 " + roomNo + " 的不合格項應為 " + failedLabels + "，實際 " + actual);
        }
    }

    /**
     * 結果畫面整個節點的 label（標題＋副文）。殼節點沒併 label 時退而收它底下所有文字節點，
     * 三種說法的斷言都只看「含不含某段字」，不綁定排版。
     */
    private String resultLabel() {
        WebElement result = world.waitForTestId(INSPECT_RESULT, SCREEN_TIMEOUT);
        String label = result.getAttribute("label");
        if (label == null || label.isBlank()) {
            StringBuilder joined = new StringBuilder();
            for (WebElement text : result.findElements(By.className("XCUIElementTypeStaticText"))) {
                joined.append(text.getAttribute("label")).append('\n');
            }
            label = joined.toString();
        }
        return label;
    }

    /** 切換是否已選：iOS 走 selected 屬性，拿不到時退而看 value。 */
    private static boolean isSelected(WebElement element) {
        try {
            if (element.isSelected()) {
                return true;
            }
        } catch (RuntimeException ignored) {
            // 見方法說明。
        }
        String value = element.getAttribute("value");
        return value != null && ("1".equals(value) || "selected".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value));
    }

    private JsonNode managerQueues() {
        return backend.managerQueues(world.loggedInStaff());
    }

    private JsonNode myToday() {
        return backend.getJson("/me/today", world.loggedInStaff());
    }

    private void assertAbsent(String testId, String why) {
        if (!world.driver().findElements(world.byTestId(testId)).isEmpty()) {
            throw new AssertionError(why + "：畫面上有 " + testId);
        }
    }

    /** 畫面上沒有任何（有高度的）元素 label 含這段字；0×0 的節點是捲出畫面的殘影，不算。 */
    private void assertNoLabelContaining(String text, String why) {
        List<WebElement> found = world.driver().findElements(
                AppiumBy.iOSNsPredicateString("label CONTAINS " + quote(text)));
        for (WebElement e : found) {
            int height;
            try {
                height = e.getRect().getHeight();
            } catch (RuntimeException gone) {
                continue;
            }
            if (height > 0) {
                throw new AssertionError(why + "：畫面上有含「" + text + "」的元素（" + e.getAttribute("label") + "）");
            }
        }
    }

    private static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static String queueKey(String tileLabel) {
        String key = QUEUE_KEY_BY_LABEL.get(tileLabel.trim());
        if (key == null) {
            throw new IllegalArgumentException("認不得的格「" + tileLabel + "」（合法值：" + QUEUE_KEY_BY_LABEL.keySet() + "）");
        }
        return key;
    }

    private static List<String> splitList(String text) {
        return Arrays.stream(text.split("、")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String requireCell(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return value.trim();
    }
}
