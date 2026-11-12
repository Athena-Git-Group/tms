package com.athena.app.bdd.steps.pool;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 待認領模組的 Then。語意逐列對照
 * {@code specs/truth/features/ios-app/待認領/dsl.md} 的「必查」欄：畫面半邊用
 * accessibility id（{@code pool.card.*}／{@code pool.category.*}，命名見 ui-plan.md）
 * 斷言；「與後端回應一致」半邊走 {@link BackendChannel} 以當事員工身分讀回
 * {@code GET /pool}（契約 listDeptPool），兩邊都要對上才算通過。
 */
public class PoolThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    /** 單卡 accessibility id 的共同前綴（ui-plan F4：{@code pool.card.{roomNo}}）。 */
    private static final String CARD_PREFIX = "pool.card.";

    /** 分類中文 → 契約 PoolCategory 的值；「全部」是分類磚特有的彙總列，不是任務類別。 */
    private static final Map<String, String> CATEGORY_KEYS = Map.of(
            "全部", "all",
            "打掃", "cleaning",
            "備品", "amenity",
            "修繕", "repair",
            "其他", "other");

    /**
     * 單卡資訊表「項目」欄 → 單卡上那一列的 accessibility id 後綴。
     *
     * <p>四列在卡上長得不一樣：來源是標題底下那一行副標，接手進度是卡內整塊的
     * 備註，原因與說明是標籤列上的膠囊。讀值的方式一樣，所以這裡只記 id。
     */
    private static final Map<String, String> CARD_FIELD_IDS = Map.of(
            "來源", "subtitle",
            "原因", "return-reason",
            "接手進度", "handoff-progress",
            "說明", "rule-hit-tag");

    /** 未接提醒階段 → 單卡標籤的 id 後綴（ui-plan 裁決 2 的兩段標籤）。 */
    private static final Map<String, String> REMINDER_TAG_IDS = Map.of(
            "未接 · 已提醒同仁", "status-tag--unaccepted-notified-peer",
            "未接 · 已通知主管", "status-tag--unaccepted-notified-manager");

    private final AppiumWorld world;
    private final PoolContext context;

    public PoolThenSteps(AppiumWorld world, PoolContext context) {
        this.world = world;
        this.context = context;
    }

    static String categoryKey(String chinese) {
        String key = CATEGORY_KEYS.get(chinese);
        if (key == null) {
            throw new IllegalArgumentException("找不到分類「" + chinese + "」對應的契約值");
        }
        return key;
    }

    /**
     * 必查：目前部門的列表中單卡順序剛好等於清單，沒有多也沒有少，並與後端 listDeptPool
     * （帶目前部門的 {@code dept}）回應一致。清單寫的是房務池的房號或工務池的工務單號。
     */
    @那麼("^待認領列表依序為 (.+)$")
    public void poolListOrderIs(String roomListText) {
        List<String> expected = splitList(roomListText);
        String dept = PoolDept.currentDept(world);

        List<String> backendOrder = new ArrayList<>();
        for (JsonNode item : poolResponse().path("items")) {
            backendOrder.add(PoolDept.itemKey(dept, item));
        }
        if (!expected.equals(backendOrder)) {
            throw new AssertionError("後端 GET /pool（dept=" + dept + "）的順序與劇本不符：預期 "
                    + expected + "，實際 " + backendOrder);
        }

        List<String> screenOrder = cardKeysOnScreen(dept);
        if (!expected.equals(screenOrder)) {
            throw new AssertionError("畫面單卡順序不符：預期 " + expected + "，實際 " + screenOrder
                    + "；單卡矩形 " + world.describeTestIdsWithPrefix(PoolDept.cardPrefix(dept))
                    + "；元素樹 " + world.dumpPageSourceTagged("pool-order"));
        }
    }

    /**
     * 必查：待認領標題下方有部門切換且停在該部門（{@code pool.dept-switch} 的選中項），
     * 分類磚是該部門那一組（房務：全部、備品、打掃、其他；工務：全部、急件、房務報修、櫃檯），
     * 列表內容與 listDeptPool 帶該部門 {@code dept} 的回應一致，App 本機的部門選擇等於該部門。
     *
     * <p>選中項有兩種看法：iOS 把 Flutter 的 selected 語意映成元素的 {@code selected} 屬性；
     * App 若改以 {@code --selected} 的狀態修飾 id 表達（單卡標亮用的同一套慣例），也認。
     * 兩種都沒有才算沒停在該部門。
     */
    @那麼("待認領目前的部門為 {string}")
    public void poolCurrentDeptIs(String deptChinese) {
        String dept = PoolDept.deptKey(deptChinese);
        world.waitForTestId(PoolDept.DEPT_SWITCH_ID, SCREEN_TIMEOUT);
        WebElement side = world.waitForTestId(PoolDept.switchSideId(dept), SCREEN_TIMEOUT);
        boolean selectedAttribute = "true".equalsIgnoreCase(String.valueOf(side.getAttribute("selected")));
        boolean selectedModifier = !world.driver().findElements(
                world.byTestId(PoolDept.switchSideId(dept) + "--selected")).isEmpty();
        if (!selectedAttribute && !selectedModifier) {
            throw new AssertionError("部門切換沒有停在「" + deptChinese + "」：" + PoolDept.switchSideId(dept)
                    + " 的 selected=" + side.getAttribute("selected")
                    + "；元素樹 " + world.dumpPageSourceTagged("pool-dept"));
        }

        for (String chipId : deptChipIds(dept)) {
            world.waitForTestId(chipId, SCREEN_TIMEOUT);
        }
        String otherDept = PoolDept.ENGINEERING.equals(dept) ? PoolDept.HOUSEKEEPING : PoolDept.ENGINEERING;
        for (String chipId : deptChipIds(otherDept)) {
            assertAbsent(chipId);
        }

        List<String> backendOrder = new ArrayList<>();
        for (JsonNode item : world.backend().getJson("/pool?dept=" + dept, world.loggedInStaff()).path("items")) {
            backendOrder.add(PoolDept.itemKey(dept, item));
        }
        List<String> screenOrder = cardKeysOnScreen(dept);
        if (!backendOrder.equals(screenOrder)) {
            throw new AssertionError("畫面列表與後端 GET /pool?dept=" + dept + " 不一致：後端 "
                    + backendOrder + "，畫面 " + screenOrder);
        }

        String local = PoolDept.localSelection(world);
        if (!dept.equals(local)) {
            throw new AssertionError("App 本機的部門選擇應為 " + dept + "，實際 " + local);
        }
    }

    /**
     * 必查：該單卡上沒有 {@code pool.card.{房號}.claim-button}（工務池為
     * {@code engPool.item.{單號}.claim-button}）節點，也沒有任何可觸發認領的操作；後端該單
     * 仍是待認領、沒有負責人。
     *
     * <p>單卡存在與沒有認領鈕兩個條件缺一不可：整張卡不在畫面上時找不到鈕也是真的，
     * 但那驗的是「清單上沒這張單」，不是「看得到卻不能搶」。
     */
    @那麼("{string} 的單卡沒有搶單鈕")
    public void poolCardHasNoClaimButton(String task) {
        String dept = PoolDept.currentDept(world);
        String cardId = PoolDept.cardPrefix(dept) + task;
        if (!cardKeysOnScreen(dept).contains(task)) {
            throw new AssertionError("畫面上找不到「" + task + "」的單卡（" + cardId + "）；實際有 "
                    + cardKeysOnScreen(dept) + "；元素樹 " + world.dumpPageSourceTagged("pool-no-claim"));
        }
        assertAbsent(cardId + ".claim-button");
        if (!world.driver().findElements(
                org.openqa.selenium.By.xpath("//*[contains(@label, '認領這單')]")).isEmpty()) {
            throw new AssertionError("主管的待認領不該有任何可觸發認領的操作，畫面上卻有「認領這單」");
        }

        List<Map<String, Object>> rows = PoolDept.ENGINEERING.equals(dept)
                ? world.backend().queryRows("""
                        SELECT t.state, t.assignee_id FROM repair_tickets rt
                        JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                        WHERE rt.tenant_id = ? AND rt.ticket_no = ?
                        """, world.backend().ensureTenant(), task)
                : world.backend().queryRows("""
                        SELECT t.state, t.assignee_id FROM tasks t
                        JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                        WHERE t.tenant_id = ? AND r.room_no = ? AND t.dept = 'housekeeping'
                        """, world.backend().ensureTenant(), task);
        if (rows.size() != 1) {
            throw new AssertionError("預期後端剛好一張「" + task + "」的單，實際 " + rows.size() + " 張");
        }
        Map<String, Object> row = rows.get(0);
        if (!"pool".equals(row.get("state")) || row.get("assignee_id") != null) {
            throw new AssertionError("後端權威狀態不符：「" + task + "」應仍是待認領（pool）且沒有負責人，實際 " + row);
        }
    }

    /** 該部門那一組分類磚的 id：房務沿用 {@code pool.category.*}，工務沿用工務待認領的 {@code engPool.category.*}。 */
    private static List<String> deptChipIds(String dept) {
        if (PoolDept.ENGINEERING.equals(dept)) {
            return List.of("engPool.category.all.chip", "engPool.category.urgent.chip",
                    "engPool.category.staff.chip", "engPool.category.desk.chip");
        }
        return List.of("pool.category.all.chip", "pool.category.amenity.chip",
                "pool.category.cleaning.chip", "pool.category.other.chip");
    }

    @那麼("^待認領列表中標示同樓層的單為 (.+)$")
    public void poolSameFloorTagsAre(String roomListText) {
        List<String> expected = splitList(roomListText);

        List<String> backendSameFloor = new ArrayList<>();
        for (JsonNode item : poolResponse().path("items")) {
            if (item.path("sameFloor").asBoolean()) {
                backendSameFloor.add(item.path("roomNo").asText());
            }
        }
        if (!sameElements(expected, backendSameFloor)) {
            throw new AssertionError(
                    "後端 GET /pool 的 sameFloor 與劇本不符：預期 " + expected + "，實際 " + backendSameFloor);
        }

        List<String> screenSameFloor = new ArrayList<>();
        for (String key : cardKeysOnScreen()) {
            if (!world.driver().findElements(world.byTestId(CARD_PREFIX + key + ".same-floor-tag")).isEmpty()) {
                screenSameFloor.add(key);
            }
        }
        if (!sameElements(expected, screenSameFloor)) {
            throw new AssertionError(
                    "畫面同樓層標示不符：預期 " + expected + "，實際 " + screenSameFloor);
        }
    }

    @那麼("{string} 的單卡顯示以下資訊：")
    public void poolCardShowsInfo(String task, DataTable table) {
        String key = cardKey(task);
        for (Map<String, String> row : table.asMaps()) {
            String field = row.get("項目");
            String value = row.get("值");
            String suffix = CARD_FIELD_IDS.get(field);
            if (suffix == null) {
                throw new IllegalArgumentException("單卡資訊表的「項目」只支援 "
                        + CARD_FIELD_IDS.keySet() + "，收到：" + field);
            }
            WebElement element = world.waitForTestId(CARD_PREFIX + key + "." + suffix, SCREEN_TIMEOUT);
            assertLabelContains(element, CARD_PREFIX + key + "." + suffix, value);
            if ("說明".equals(field)) {
                assertRuleHitMatches(task, value);
            }
        }
    }

    /**
     * 必查：該單卡的位置顯示「公共區域」而不是房號；後端該任務沒有房號。
     * accessibility id：{@code pool.card.common-area-label}（ui-plan.md 標記「待原型補畫面」，
     * 依 dsl 指定的 id 做最薄呈現）。
     *
     * <p>「後端該任務沒有房號」在資料庫的樣子是掛在公共區域佔位房上——{@code tasks.room_id}
     * 是 NOT NULL，產品碼（AutomationRuleService／RepairService）一律用一間
     * {@code room_type='public_area'} 的房代表無房號，所以這裡驗的是該房的 room_type。
     */
    @那麼("{string} 的單卡位置顯示 {string}")
    public void poolCardLocationShows(String task, String location) {
        WebElement label = world.waitForTestId(CARD_PREFIX + "common-area-label", SCREEN_TIMEOUT);
        assertLabelContains(label, CARD_PREFIX + "common-area-label", location);

        List<Map<String, Object>> rows = world.backend().queryRows("""
                SELECT r.room_type FROM tasks t
                JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                WHERE t.tenant_id = ? AND t.title = ?
                """, world.backend().ensureTenant(), task);
        if (rows.size() != 1 || !"public_area".equals(rows.get(0).get("room_type"))) {
            throw new AssertionError("後端權威狀態不符：「" + task + "」應掛在公共區域佔位房（room_type=public_area），實際 " + rows);
        }
    }

    /**
     * 必查：分類標籤逐列顯示類別與筆數，並與後端回應一致。筆數 0 的分類畫面上沒有筆數節點。
     * accessibility id：{@code pool.category.{category}.chip}、{@code pool.category.{category}.count}。
     *
     * <p>筆數 0 的分類要斷言節點不存在，而不是斷言節點上寫著 0。畫面上一顆寫著 0 的紅色
     * 徽章看起來像有單子等著接，員工會白點一次；設計因此在 0 的時候整顆徽章不畫。
     */
    @那麼("待認領分類標籤顯示如下：")
    public void poolCategoryChipsShow(DataTable table) {
        JsonNode counts = poolResponse().path("counts");
        for (Map<String, String> row : table.asMaps()) {
            String key = categoryKey(row.get("類別"));
            int expected = Integer.parseInt(row.get("筆數").trim());

            int backendCount = counts.path(key).asInt(-1);
            if (backendCount != expected) {
                throw new AssertionError("後端 GET /pool 的 counts." + key + " 與劇本不符：預期 "
                        + expected + "，實際 " + backendCount);
            }
            String countId = "pool.category." + key + ".count";
            if (expected == 0) {
                assertAbsent(countId);
                continue;
            }
            WebElement count = world.waitForTestId(countId, SCREEN_TIMEOUT);
            assertLabelContains(count, countId, String.valueOf(expected));
        }
    }

    /**
     * 必查：池裡一張單都接不到時，待認領顯示這個標題與這句說明。
     * accessibility id：{@code pool.empty-state}。
     *
     * <p>標題與說明讀的是同一個節點：空狀態整塊只掛一個 id，兩句話一起放進它的
     * label，定位與讀值才落在同一個元素上。
     */
    @那麼("待認領空狀態顯示標題 {string} 與說明 {string}")
    public void poolEmptyStateShows(String title, String description) {
        WebElement emptyState = world.waitForTestId("pool.empty-state", SCREEN_TIMEOUT);
        assertLabelContains(emptyState, "pool.empty-state", title);
        assertLabelContains(emptyState, "pool.empty-state", description);
    }

    @那麼("待認領空狀態的動作鈕是 {string}")
    public void poolEmptyStateActionIs(String label) {
        WebElement action = world.waitForTestId("pool.empty-state.action", SCREEN_TIMEOUT);
        assertLabelContains(action, "pool.empty-state.action", label);
    }

    /**
     * 必查：空狀態底下沒有任何動作鈕。
     *
     * <p>整池都沒有單的時候切回全部分類看到的還是空清單，所以這個分支不給按鈕，
     * 員工才不會按一下發現什麼都沒變。
     */
    @那麼("待認領空狀態沒有動作鈕")
    public void poolEmptyStateHasNoAction() {
        world.waitForTestId("pool.empty-state", SCREEN_TIMEOUT);
        assertAbsent("pool.empty-state.action");
    }

    /** 斷言某個 accessibility id 不在畫面上。用 findElements（空清單）而不是 findElement（要等逾時）。 */
    private void assertAbsent(String id) {
        if (!world.driver().findElements(world.byTestId(id)).isEmpty()) {
            throw new AssertionError("畫面上不應出現 " + id + "，實際找得到");
        }
    }

    /**
     * 必查：待認領列表區顯示該空狀態文字，搶單動作不可用；後端該部門沒有在職員工
     * （待開通與停用的員工不能搶單）。
     * accessibility id：{@code pool.empty-state--no-active-staff}（ui-plan.md 標記「待原型補畫面」，
     * 依 dsl 指定的 id 做最薄呈現）。
     */
    @那麼("待認領顯示空狀態 {string}")
    public void poolShowsEmptyState(String message) {
        WebElement emptyState = world.waitForTestId("pool.empty-state--no-active-staff", SCREEN_TIMEOUT);
        assertLabelContains(emptyState, "pool.empty-state--no-active-staff", message);
        if (!cardKeysOnScreen().isEmpty()) {
            throw new AssertionError("空狀態下不該有任何可搶的單卡，實際還有 " + cardKeysOnScreen());
        }

        List<Map<String, Object>> active = world.backend().queryRows(
                "SELECT name FROM staff_accounts WHERE tenant_id = ? AND dept = 'housekeeping' AND active = true",
                world.backend().ensureTenant());
        if (!active.isEmpty()) {
            throw new AssertionError("後端權威狀態不符：房務部應沒有在職員工，實際還有 " + active);
        }
    }

    @那麼("{string} 的單卡標示為 {string}")
    public void poolCardLabeledAs(String task, String label) {
        String suffix = REMINDER_TAG_IDS.get(label);
        if (suffix == null) {
            throw new IllegalArgumentException("未接超時標籤只有兩段（" + REMINDER_TAG_IDS.keySet() + "），收到：" + label);
        }
        String key = cardKey(task);
        WebElement tag = world.waitForTestId(CARD_PREFIX + key + "." + suffix, SCREEN_TIMEOUT);
        assertLabelContains(tag, CARD_PREFIX + key + "." + suffix, label);
    }

    /**
     * 以目前登入者的身分讀回 {@code GET /pool}，並帶上畫面當下選中的分類——App 點分類磚
     * 是重打一次帶 {@code category} 的查詢，讀回不帶就會跟畫面比到不同的兩份資料。
     * 主管沒有部門，後端要靠 {@code dept} 才知道回哪一池，所以主管讀回時明帶目前的部門；
     * 現場員工省略，後端依登入身分回她自己部門的池。
     */
    private JsonNode poolResponse() {
        List<String> query = new ArrayList<>();
        if (PoolDept.isManager(world.loggedInStaff())) {
            query.add("dept=" + PoolDept.currentDept(world));
        }
        String category = context.selectedCategory();
        if (category != null) {
            query.add("category=" + category);
        }
        String path = query.isEmpty() ? "/pool" : "/pool?" + String.join("&", query);
        return world.backend().getJson(path, world.loggedInStaff());
    }

    private List<String> cardKeysOnScreen() {
        return cardKeysOnScreen(PoolDept.HOUSEKEEPING);
    }

    /**
     * 畫面上目前有哪些單卡，依由上而下排序。房務池的單卡 id 是 {@code pool.card.{房號}}，
     * 卡內各列是 {@code pool.card.{房號}.{欄位}}，所以掃完前綴後把帶「.」的子節點與帶「--」
     * 的狀態修飾（例如推播標亮）濾掉。工務池的單卡是 {@code engPool.item.{單號}.card}，
     * 卡本身就掛在 {@code .card} 這一段上，掃到它時取單號那一段。
     */
    private List<String> cardKeysOnScreen(String dept) {
        String prefix = PoolDept.cardPrefix(dept);
        List<String> keys = new ArrayList<>();
        for (String id : world.findTestIdsWithPrefix(prefix)) {
            String rest = id.substring(prefix.length());
            if (rest.contains("--")) {
                continue;
            }
            if (PoolDept.ENGINEERING.equals(dept) && rest.endsWith(".card")) {
                rest = rest.substring(0, rest.length() - ".card".length());
            }
            if (!rest.contains(".") && !keys.contains(rest)) {
                keys.add(rest);
            }
        }
        return keys;
    }

    /**
     * 劇本用「任務」指涉單卡（有房號的寫房號，沒房號的寫內容），單卡的 id 用房號當
     * 識別段，所以要先用後端回應把內容換算成房號——公共區域的單在後端的房號就是
     * 「公共區域」。
     */
    private String cardKey(String task) {
        Map<String, String> titleToRoom = new LinkedHashMap<>();
        for (JsonNode item : poolResponse().path("items")) {
            String roomNo = item.path("roomNo").asText();
            if (roomNo.equals(task)) {
                return roomNo;
            }
            titleToRoom.put(item.path("title").asText(), roomNo);
        }
        String byTitle = titleToRoom.get(task);
        if (byTitle == null) {
            throw new AssertionError("後端 GET /pool 裡找不到「" + task + "」這張單（房號或內容）；實際有 " + titleToRoom);
        }
        return byTitle;
    }

    private void assertRuleHitMatches(String task, String shown) {
        List<Map<String, Object>> rows = world.backend().queryRows(
                "SELECT rule_hit FROM tasks WHERE tenant_id = ? AND title = ?",
                world.backend().ensureTenant(), task);
        if (rows.size() != 1) {
            throw new AssertionError("預期剛好一張「" + task + "」的任務，實際 " + rows.size() + " 張");
        }
        Object ruleHit = rows.get(0).get("rule_hit");
        if (ruleHit == null || !shown.endsWith(String.valueOf(ruleHit))) {
            throw new AssertionError("單卡說明「" + shown + "」與後端 rule_hit（" + ruleHit + "）不一致");
        }
    }

    private static List<String> splitList(String text) {
        return Arrays.stream(text.trim().split("、")).map(String::trim).toList();
    }

    private static boolean sameElements(List<String> a, List<String> b) {
        return a.size() == b.size() && a.containsAll(b) && b.containsAll(a);
    }

    private static void assertLabelContains(WebElement element, String id, String expected) {
        String label = element.getAttribute("label");
        String text = label != null ? label : element.getText();
        if (text == null || !text.contains(expected)) {
            throw new AssertionError(id + " 應顯示含「" + expected + "」的文字，實際是「" + text + "」");
        }
    }
}
