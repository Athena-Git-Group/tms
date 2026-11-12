package com.athena.app.bdd.steps.manager;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.appium.java_client.AppiumBy;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ManagerGivenSteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String HOME = "manager.home";
    private static final String QUEUE = "manager.queue";
    private static final String INSPECT = "inspect";
    private static final String INSPECT_SUBMIT_BUTTON = "inspect.submit-button";
    private static final String INSPECT_RESULT = "inspect.result";

    private static final Map<String, String> QUEUE_KEYS = Map.of(
            "待查房清單", "inspection",
            "待驗收清單", "verify");

    private final AppiumWorld world;
    private final BackendChannel backend;
    private final ManagerContext context;

    public ManagerGivenSteps(AppiumWorld world, BackendChannel backend, ManagerContext context) {
        this.world = world;
        this.backend = backend;
        this.context = context;
    }

    @假設("{string} 已在 App 打開主管首頁")
    public void managerIsOnHome(String managerName) {
        ensureOnManagerHome(world.staff(managerName));
    }

    @假設("{string} 已在 App 打開 {string}")
    public void managerIsOnQueue(String managerName, String queueChinese) {
        openQueue(world.staff(managerName), queueKey(queueChinese));
    }

    @假設("飯店目前設定的查房項目如下：")
    public void hotelHasInspectionItems(DataTable table) {
        List<String> labels = table.asMaps().stream()
                .map(row -> requireCell(row, "查房項目"))
                .toList();
        backend.replaceInspectionItems(labels);
    }

    @假設("飯店目前沒有任何啟用的查房項目")
    public void hotelHasNoActiveInspectionItems() {
        backend.disableAllInspectionItems();
    }

    @假設("{string} 已從待查房清單打開 {string}，還沒判任何一項")
    public void managerOpenedInspectionUntouched(String managerName, String roomNo) {
        openInspection(world.staff(managerName), roomNo);
    }

    @假設("{string} 已從待查房清單打開 {string}，判定如下：")
    public void managerOpenedInspectionWithJudgements(String managerName, String roomNo, DataTable table) {
        openInspection(world.staff(managerName), roomNo);
        for (Map<String, String> row : table.asMaps()) {
            String label = requireCell(row, "查房項目");
            String verdict = requireCell(row, "判定");
            switch (verdict) {
                case "通過" -> judge(label, "pass-button", verdict);
                case "不通過" -> judge(label, "fail-button", verdict);
                case "未判" -> { }
                default -> throw new IllegalArgumentException(
                        "判定欄只接受「通過」「不通過」「未判」，拿到：" + verdict);
            }
        }
    }

    @假設("飯店在 {string} 送出前新增了查房項目 {string}")
    public void hotelAppendedInspectionItemBeforeSubmit(String managerName, String label) {
        requireInspectionOpen(managerName);
        backend.appendInspectionItem(label);
    }

    @假設("{string} 剛被 {string} 查完，{string} 的畫面還沒更新")
    public void anotherManagerJustPassedInspection(String roomNo, String otherManagerName, String managerName) {
        requireInspectionOpen(managerName);
        UUID taskId = (UUID) world.taskRowByRoomNo(roomNo).get("id");
        backend.passInspectionAs(taskId, world.staff(otherManagerName));
    }

    @假設("{string} 剛送出 {string} 的查房，結果畫面停在畫面上")
    public void managerJustSubmittedInspection(String managerName, String roomNo) {
        openInspection(world.staff(managerName), roomNo);
        for (String key : activeInspectionItemKeys()) {
            world.tapTestId("inspect.item." + key + ".pass-button", TIMEOUT);
        }
        world.tapTestId(INSPECT_SUBMIT_BUTTON, TIMEOUT);
        world.waitForTestId(INSPECT_RESULT, TIMEOUT);
    }

    private void ensureOnManagerHome(BackendChannel.CreatedStaff manager) {
        world.ensureLoggedInAs(manager);
        if (!isVisible(HOME) && !hasBackButton()) {
            world.waitForLabelContaining("首頁", TIMEOUT).click();
        }
        world.ensureOnScreenRoot(HOME, TIMEOUT);
        ManagerHomeWait.await(world, manager.name(), TIMEOUT);
    }

    private void openQueue(BackendChannel.CreatedStaff manager, String queueKey) {
        ensureOnManagerHome(manager);
        rememberQueueCount(world, context, queueKey);
        world.tapUntilVisible("manager.home.tile." + queueKey, QUEUE, TIMEOUT);
        context.openQueue(queueKey);
    }

    private void openInspection(BackendChannel.CreatedStaff manager, String roomNo) {
        openQueue(manager, "inspection");
        world.tapUntilVisible("manager.queue.row." + roomNo, INSPECT, TIMEOUT);
        world.waitForTestId(INSPECT_SUBMIT_BUTTON, TIMEOUT);
        for (String key : activeInspectionItemKeys()) {
            world.waitForTestId("inspect.item." + key, TIMEOUT);
        }
        context.openInspection(roomNo);
        context.clearJudgements();
    }

    private void judge(String label, String buttonSuffix, String verdict) {
        world.tapTestId("inspect.item." + inspectionItemKey(label) + "." + buttonSuffix, TIMEOUT);
        context.recordJudgement(label, verdict);
    }

    private void requireInspectionOpen(String managerName) {
        if (context.inspectingRoomNo() == null
                || !world.loggedInStaff().name().equals(managerName) || !isVisible(INSPECT)) {
            throw new IllegalStateException("「" + managerName + "」的查房畫面還沒開著——"
                    + "劇本是否漏了「已從待查房清單打開 … 」那一句？");
        }
    }

    private List<String> activeInspectionItemKeys() {
        return backend.queryRows(
                        "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection'"
                                + " AND active = true ORDER BY sort_order", backend.ensureTenant())
                .stream()
                .map(row -> String.valueOf(row.get("item_key")))
                .toList();
    }

    private String inspectionItemKey(String label) {
        List<Map<String, Object>> rows = backend.queryRows(
                "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection'"
                        + " AND active = true AND label = ?", backend.ensureTenant(), label);
        if (rows.size() != 1) {
            throw new IllegalStateException("查房項目「" + label + "」在後端現行啟用集合裡有 "
                    + rows.size() + " 筆，無法對到畫面上的列；背景「飯店目前設定的查房項目如下」是否列了它？");
        }
        return String.valueOf(rows.get(0).get("item_key"));
    }

    private static String queueKey(String chinese) {
        String key = QUEUE_KEYS.get(chinese);
        if (key == null) {
            throw new IllegalArgumentException(
                    "清單只接受 待查房清單／待驗收清單，拿到：" + chinese);
        }
        return key;
    }

    private boolean isVisible(String testId) {
        return !world.driver().findElements(world.byTestId(testId)).isEmpty();
    }

    static void rememberQueueCount(AppiumWorld world, ManagerContext context, String queueKey) {
        String shown = world.labelOf("manager.home.tile." + queueKey + ".count");
        if (shown == null) {
            return;
        }
        try {
            context.recordQueueCountBefore(queueKey, Integer.parseInt(shown.trim()));
        } catch (NumberFormatException e) {
        }
    }

    private boolean hasBackButton() {
        return !world.driver().findElements(world.byTestId("nav.back-button")).isEmpty()
                || !world.driver().findElements(AppiumBy.accessibilityId("Back")).isEmpty();
    }

    private static String requireCell(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return value;
    }
}
