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

public class TriageWhenSteps {

    private static final String MENU_ITEM_ROOM = "601";

    private static final String RULE_HIT_ROOM = "602";

    private static final String MENU_ITEM_KEY = "fx_bath_towel";

    private static final String MENU_ITEM_LABEL = "浴巾";

    private static final String RULE_KEYWORD = "毛巾";

    private static final String RULE_HIT_TEXT = "想多一條毛巾";

    private static final double CARDS_ARRIVE_TIMEOUT_MS = 5_000;

    private final WebWorld world;

    public TriageWhenSteps(WebWorld world) {
        this.world = world;
    }

    @當("派單浮條出現後過了 {int} 秒")
    public void secondsAfterAssignToast(int seconds) {
        long browserNow = ((Number) world.page().evaluate("Date.now()")).longValue();
        OffsetDateTime from = OffsetDateTime.ofInstant(Instant.ofEpochMilli(browserNow), ZoneId.systemDefault());
        world.clocks().moveTo(from.plusSeconds(seconds));
    }

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

    private void requireInPool(UUID taskId, String label) {
        Map<String, Object> row = world.backend().taskRow(taskId);
        if (!"pool".equals(row.get("state")) || !"housekeeping".equals(row.get("dept"))) {
            throw new IllegalStateException(label + "沒有直接進房務待認領：state=" + row.get("state")
                    + "、dept=" + row.get("dept"));
        }
    }
}
