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

    /** 單卡資訊表「項目」欄 → 單卡上那一列的 accessibility id 後綴。 */
    private static final Map<String, String> CARD_FIELD_IDS = Map.of(
            "來源", "source-tag",
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

    /** 分類中文 → 契約 PoolCategory 的值；查不到就噴錯帶原詞。 */
    static String categoryKey(String chinese) {
        String key = CATEGORY_KEYS.get(chinese);
        if (key == null) {
            throw new IllegalArgumentException("找不到分類「" + chinese + "」對應的契約值");
        }
        return key;
    }

    /**
     * 必查：列表中的單卡順序剛好等於清單，沒有多也沒有少，並與後端回應一致。房號清單以
     * 「、」分隔，純文字（非 quoted string），故用 regex 形式的 step definition 擷取。
     */
    @那麼("^待認領列表依序為 (.+)$")
    public void poolListOrderIs(String roomListText) {
        List<String> expected = splitList(roomListText);

        List<String> backendOrder = new ArrayList<>();
        for (JsonNode item : poolResponse().path("items")) {
            backendOrder.add(item.path("roomNo").asText());
        }
        if (!expected.equals(backendOrder)) {
            throw new AssertionError("後端 GET /pool 的順序與劇本不符：預期 " + expected + "，實際 " + backendOrder);
        }

        List<String> screenOrder = cardKeysOnScreen();
        if (!expected.equals(screenOrder)) {
            throw new AssertionError("畫面單卡順序不符：預期 " + expected + "，實際 " + screenOrder
                    + "；單卡矩形 " + world.describeTestIdsWithPrefix(CARD_PREFIX)
                    + "；元素樹 " + world.dumpPageSourceTagged("pool-order"));
        }
    }

    /**
     * 必查：有同樓層標示的單卡剛好是這些房號。
     * accessibility id：{@code pool.card.{roomNo}.same-floor-tag}。
     */
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

    /**
     * 必查：該單卡逐項顯示表格中的值；「說明」為「規則引擎命中：{規則名}」，與後端
     * {@code rule_hit} 一致。accessibility id：{@code pool.card.{roomNo}.source-tag}、
     * {@code .return-reason}、{@code .handoff-progress}、{@code .rule-hit-tag}。
     */
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

    /**
     * 必查：該單卡顯示此標籤文字，與後端未接提醒所到達的階段一致。
     * accessibility id：{@code pool.card.{roomNo}.status-tag--unaccepted-notified-peer}／
     * {@code pool.card.{roomNo}.status-tag--unaccepted-notified-manager}。
     */
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
     * 以目前登入者的身分讀回 {@code GET /pool}（後端只回她自己部門的池），並帶上
     * 畫面當下選中的分類——App 點分類磚是重打一次帶 {@code category} 的查詢，
     * 讀回不帶就會跟畫面比到不同的兩份資料。
     */
    private JsonNode poolResponse() {
        String category = context.selectedCategory();
        String path = category == null ? "/pool" : "/pool?category=" + category;
        return world.backend().getJson(path, world.loggedInStaff());
    }

    /**
     * 畫面上目前有哪些單卡，依由上而下排序。單卡本身的 id 是
     * {@code pool.card.{房號}}，卡內各列是 {@code pool.card.{房號}.{欄位}}，
     * 所以掃完前綴後把帶「.」的子節點與帶「--」的狀態修飾（例如推播標亮）濾掉。
     */
    private List<String> cardKeysOnScreen() {
        List<String> keys = new ArrayList<>();
        for (String id : world.findTestIdsWithPrefix(CARD_PREFIX)) {
            String rest = id.substring(CARD_PREFIX.length());
            if (!rest.contains(".") && !rest.contains("--")) {
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

    /** 「說明」那一列的值必須與後端 {@code tasks.rule_hit} 一致（dsl 的必查）。 */
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
