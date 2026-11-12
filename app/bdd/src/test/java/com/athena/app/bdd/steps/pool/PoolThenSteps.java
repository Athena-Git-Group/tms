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

public class PoolThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private static final String CARD_PREFIX = "pool.card.";

    private static final Map<String, String> CATEGORY_KEYS = Map.of(
            "全部", "all",
            "打掃", "cleaning",
            "備品", "amenity",
            "修繕", "repair",
            "其他", "other");

    private static final Map<String, String> CARD_FIELD_IDS = Map.of(
            "來源", "subtitle",
            "原因", "return-reason",
            "接手進度", "handoff-progress",
            "說明", "rule-hit-tag");

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

    @那麼("待認領空狀態沒有動作鈕")
    public void poolEmptyStateHasNoAction() {
        world.waitForTestId("pool.empty-state", SCREEN_TIMEOUT);
        assertAbsent("pool.empty-state.action");
    }

    private void assertAbsent(String id) {
        if (!world.driver().findElements(world.byTestId(id)).isEmpty()) {
            throw new AssertionError("畫面上不應出現 " + id + "，實際找得到");
        }
    }

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
