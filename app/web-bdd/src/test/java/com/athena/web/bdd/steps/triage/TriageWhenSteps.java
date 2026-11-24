package com.athena.web.bdd.steps.triage;

import com.athena.bddsupport.BackendChannel;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.TriagePage;
import com.athena.web.bdd.support.ScenarioTasks;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import io.cucumber.java.zh_tw.當;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 「待派單」模組的 When 句型。 */
public class TriageWhenSteps {

    /** 勾選的那一張落在哪間房；背景表格沒有用到這間房，不會和案例的單混在一起。 */
    private static final String MENU_ITEM_ROOM = "601";

    /** 打字的那一張落在哪間房，理由同上。 */
    private static final String RULE_HIT_ROOM = "602";

    private static final String MENU_ITEM_KEY = "fx_bath_towel";

    private static final String MENU_ITEM_LABEL = "浴巾";

    /** 規則的關鍵字；案例裡其他住客原話都不含這兩個字，不會被這條規則接走。 */
    private static final String RULE_KEYWORD = "毛巾";

    private static final String RULE_HIT_TEXT = "想多一條毛巾";

    /** 送出之後最久等這麼久讓兩張卡片放上看板；即時更新的規格承諾別人動了單，畫面 5 秒內跟上。 */
    private static final double CARDS_ARRIVE_TIMEOUT_MS = 5_000;

    private final WebWorld world;

    public TriageWhenSteps(WebWorld world) {
        this.world = world;
    }

    /**
     * 只動時間、不碰畫面：從瀏覽器此刻的時間再往後推該秒數，後端跟著撥到同一個時刻。
     *
     * <p>瀏覽器時鐘照真實速度走，派單浮條出現時瀏覽器已經比場景的「現在」快了好幾秒；
     * 從場景的「現在」往後推會推不滿，所以以瀏覽器此刻的時間為起點。浮條出現之後才讀這個時間，
     * 推完時離浮條出現一定至少過了該秒數。
     */
    @當("派單浮條出現後過了 {int} 秒")
    public void secondsAfterAssignToast(int seconds) {
        long browserNow = ((Number) world.page().evaluate("Date.now()")).longValue();
        OffsetDateTime from = OffsetDateTime.ofInstant(Instant.ofEpochMilli(browserNow), ZoneId.systemDefault());
        world.clocks().moveTo(from.plusSeconds(seconds));
    }

    /**
     * 以住客身分在後端送出兩張需求：一張從需求選單勾選，一張打字且命中房務的分類規則，兩張都直接進房務的待認領。
     * 規則分給房務而不是工務：住客的需求命中工務規則時會停在待派單，只有房務會直接進待認領。
     * 送完等頁首的房務待接張數多兩張，代表畫面收到即時訊號、兩張卡片都放上看板了。
     * 等的是畫面上的張數而不是看板讀取：訊號帶著卡片時畫面直接換上，不會重讀整份看板。
     *
     * <p>需求選單與分類規則是這個場景自己寫進飯店設定的；每個場景開始時業務資料會清空，不會留給別的場景。
     */
    @當("一張住客勾選的需求與一張命中房務規則的打字需求進來")
    public void menuItemAndRuleHitArrive() {
        BackendChannel backend = world.backend();
        long tenantId = backend.ensureTenant();
        backend.setTenantConfig(tenantId, "request_menu", List.of(menuItem()));
        backend.setTenantConfig(tenantId, "classification_rules", List.of(classificationRule()));

        TriagePage triage = new TriagePage(world);
        int housekeeping = triage.deptLoadCount("房務");
        UUID menuTask = backend.guestSubmitMenuItem(MENU_ITEM_ROOM, MENU_ITEM_KEY, 1);
        UUID ruleTask = backend.guestSubmitRequest(RULE_HIT_ROOM, RULE_HIT_TEXT, null);
        requireInPool(menuTask, "勾選的「" + MENU_ITEM_LABEL + "」");
        requireInPool(ruleTask, "命中規則的「" + RULE_HIT_TEXT + "」");
        world.tasks().add(new ScenarioTasks.Entry(MENU_ITEM_ROOM, MENU_ITEM_LABEL, menuTask, "待認領"));
        world.tasks().add(new ScenarioTasks.Entry(RULE_HIT_ROOM, RULE_HIT_TEXT, ruleTask, "待認領"));
        awaitDeptLoad(triage, "房務", housekeeping + 2);
        new ShellFrame(world).settleAfter(() -> { });
    }

    private static void awaitDeptLoad(TriagePage triage, String deptName, int count) {
        try {
            triage.deptLoad(deptName, count).waitFor(new Locator.WaitForOptions().setTimeout(CARDS_ARRIVE_TIMEOUT_MS));
        } catch (com.microsoft.playwright.TimeoutError notYet) {
            throw new AssertionError("送出兩張直接進待認領的需求後，頁首沒有在 " + (long) (CARDS_ARRIVE_TIMEOUT_MS / 1_000)
                    + " 秒內變成「" + deptName + " " + count + " 單待接」，現在是 "
                    + triage.textsMatching(triage.header(), TriagePage.DEPT_LOAD));
        }
    }

    private static Map<String, Object> menuItem() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", MENU_ITEM_KEY);
        item.put("label", MENU_ITEM_LABEL);
        item.put("dept", "housekeeping");
        item.put("type", "amenity");
        return item;
    }

    private static Map<String, Object> classificationRule() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("rule_id", "fx-rule-" + UUID.randomUUID());
        rule.put("keywords", List.of(RULE_KEYWORD));
        rule.put("dept", "housekeeping");
        rule.put("type", "amenity");
        return rule;
    }

    /** 這張單要真的直接進了房務的待認領，否則案例要驗的「不會出現在待派單頁」沒有意義。 */
    private void requireInPool(UUID taskId, String label) {
        Map<String, Object> row = world.backend().taskRow(taskId);
        if (!"pool".equals(row.get("state")) || !"housekeeping".equals(row.get("dept"))) {
            throw new IllegalStateException(label + "沒有直接進房務待認領：state=" + row.get("state")
                    + "、dept=" + row.get("dept"));
        }
    }
}
