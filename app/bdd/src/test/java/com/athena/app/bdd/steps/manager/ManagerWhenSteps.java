package com.athena.app.bdd.steps.manager;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ManagerWhenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration SUBMIT_TIMEOUT = Duration.ofSeconds(20);

    private static final Map<String, String> QUEUE_KEY_BY_LABEL = Map.of(
            "待查房", "inspection",
            "待驗收", "verify");

    private static final Map<String, String> VERDICT_BUTTON_SUFFIX_BY_LABEL = Map.of(
            "通過", "pass-button",
            "不通過", "fail-button");

    private final AppiumWorld world;
    private final BackendChannel backend;
    private final ManagerContext context;

    public ManagerWhenSteps(AppiumWorld world, BackendChannel backend, ManagerContext context) {
        this.world = world;
        this.backend = backend;
        this.context = context;
    }

    @當("{string} 在主管首頁點 {string}")
    public void tapHomeTile(String managerName, String tileLabel) {
        world.staff(managerName);
        String queueKey = queueKey(tileLabel);
        world.waitForTestId("manager.home", SCREEN_TIMEOUT);
        ManagerGivenSteps.rememberQueueCount(world, context, queueKey);
        world.tapTestId("manager.home.tile." + queueKey, SCREEN_TIMEOUT);
        world.waitForTestId("manager.queue", SCREEN_TIMEOUT);
        context.openQueue(queueKey);
    }

    @當("{string} 從待查房清單打開 {string}")
    public void openInspectionFromQueue(String managerName, String roomNo) {
        world.staff(managerName);
        world.waitForTestId("manager.queue", SCREEN_TIMEOUT);
        world.tapTestId("manager.queue.row." + roomNo, SCREEN_TIMEOUT);
        world.waitForTestId("inspect", SCREEN_TIMEOUT);
        world.waitForTestId("inspect.submit-button", SCREEN_TIMEOUT);
        for (Map<String, Object> row : backend.queryRows(
                "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection'"
                        + " AND active = true ORDER BY sort_order", backend.ensureTenant())) {
            world.waitForTestId("inspect.item." + row.get("item_key"), SCREEN_TIMEOUT);
        }
        context.openInspection(roomNo);
        context.clearJudgements();
    }

    @當("{string} 把 {string} 判 {string}")
    public void judgeOneItem(String managerName, String itemLabel, String verdict) {
        world.staff(managerName);
        world.waitForTestId("inspect", SCREEN_TIMEOUT);
        judgeItem(itemLabel, verdict);
    }

    @當("{string} 把以下項目判定後送出：")
    public void judgeItemsAndSubmit(String managerName, DataTable table) {
        world.staff(managerName);
        world.waitForTestId("inspect", SCREEN_TIMEOUT);
        for (Map<String, String> row : table.asMaps()) {
            judgeItem(required(row, "查房項目"), required(row, "判定"));
        }
        tapSubmit();
    }

    @當("{string} 送出查房")
    public void submitInspection(String managerName) {
        world.staff(managerName);
        world.waitForTestId("inspect", SCREEN_TIMEOUT);
        tapSubmit();
    }

    @當("{string} 從結果畫面回到待查房清單")
    public void backToInspectionQueue(String managerName) {
        world.staff(managerName);
        world.waitForTestId("inspect.result", SCREEN_TIMEOUT);
        world.tapTestId("inspect.result.back-button", SCREEN_TIMEOUT);
        world.waitForTestId("manager.queue", SCREEN_TIMEOUT);
        context.openQueue("inspection");
    }

    @當("{string} 在「我名下的單」點 {string}")
    public void tapMineTicket(String managerName, String ticketNo) {
        world.staff(managerName);
        world.waitForTestId("manager.home.mine", SCREEN_TIMEOUT);
        world.tapUntilVisible("engHome.in-hand." + ticketNo + ".card", "ticket.screen", SCREEN_TIMEOUT);
    }

    void judgeItem(String itemLabel, String verdict) {
        String suffix = VERDICT_BUTTON_SUFFIX_BY_LABEL.get(verdict);
        if (suffix == null) {
            throw new IllegalArgumentException("認不得的判定：「" + verdict + "」"
                    + "（合法值：" + VERDICT_BUTTON_SUFFIX_BY_LABEL.keySet() + "）");
        }
        world.tapTestId("inspect.item." + inspectionItemKey(itemLabel) + "." + suffix, SCREEN_TIMEOUT);
        context.recordJudgement(itemLabel, verdict);
    }

    private void tapSubmit() {
        world.tapTestId("inspect.submit-button", SCREEN_TIMEOUT);
        try {
            world.waitForTestId("inspect.result", SUBMIT_TIMEOUT);
        } catch (RuntimeException | AssertionError notTheResultScreen) {
        }
    }

    private String inspectionItemKey(String label) {
        List<Map<String, Object>> rows = backend.queryRows(
                "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection'"
                        + " AND active = true AND label = ?",
                backend.ensureTenant(), label);
        if (rows.size() != 1) {
            throw new IllegalStateException("查房項目「" + label + "」在後端現行啟用的 sop_items（applies_to=inspection）"
                    + "應恰有 1 筆，實際 " + rows.size() + " 筆——場景是否漏了「飯店的查房項目」那句 Given？");
        }
        return String.valueOf(rows.get(0).get("item_key"));
    }

    private static String queueKey(String tileLabel) {
        String key = QUEUE_KEY_BY_LABEL.get(tileLabel);
        if (key == null) {
            throw new IllegalArgumentException("認不得的主管首頁格：「" + tileLabel + "」"
                    + "（合法值：" + QUEUE_KEY_BY_LABEL.keySet() + "）");
        }
        return key;
    }

    private static String required(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺必填欄「" + column + "」：" + row);
        }
        return value.trim();
    }
}
