package com.athena.app.bdd.steps.common;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import com.athena.app.bdd.steps.manager.ManagerContext;
import com.athena.app.bdd.steps.repair.RepairContext;
import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class CommonThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private static final Map<String, String> REQUIRED_INSPECTION = Map.of(
            "查房通過", "passed",
            "完成 · 免查房", "waived");

    private static final Map<String, Boolean> REQUIRED_CLEANING = Map.of(
            "待查房", Boolean.TRUE,
            "已送達", Boolean.FALSE);

    private record TaskPageEvidence(String testId, String title, String step) {}

    private static final Map<String, TaskPageEvidence> TASK_PAGE_EVIDENCE = Map.ofEntries(
            Map.entry("待開始", new TaskPageEvidence("task.step-track", null, "已接")),
            Map.entry("退回重做", new TaskPageEvidence("task.status-row.return-info", null, "已接")),
            Map.entry("進行中", new TaskPageEvidence("task.step-track", null, "進行中")),
            Map.entry("已延後", new TaskPageEvidence("task.status-row.hold-reason", null, "已接")),
            Map.entry("已中斷", new TaskPageEvidence("task.status-row.hold-reason", null, "進行中")),
            Map.entry("待查房", new TaskPageEvidence("task.status-row.awaiting-inspection", "等主管查房", "完成")),
            Map.entry("已送達", new TaskPageEvidence("task.status-row.awaiting-inspection", "已送達", "送達")),
            Map.entry("查房通過", new TaskPageEvidence("task.status-row.finished", "查房通過", null)),
            Map.entry("完成 · 免查房", new TaskPageEvidence("task.status-row.finished", "完成 · 免查房", null)),
            Map.entry("已取消", new TaskPageEvidence("task.status-row.cancelled", "這單已取消", null)));

    private static final List<String> ALERT_DISMISS_BUTTONS = List.of(
            "task.busy-alert.dismiss-button",
            "system.offline-blocked-alert.dismiss-button",
            "celebrate.reassigned-alert.dismiss-button");

    private static final Map<String, String> TICKET_STATUS_TEXT = Map.ofEntries(
            Map.entry("待認領", "等工務接單"),
            Map.entry("待開始", "已接單"),
            Map.entry("已接單", "已接單"),
            Map.entry("維修中", "維修中"),
            Map.entry("已完修", "已完修"),
            Map.entry("已結案", "已結案"),
            Map.entry("退回重修", "退回重修"));

    private static final Map<String, String> MANAGER_TICKET_STATUS_TEXT = Map.of(
            "已完修", "已完修 · 等主管驗收");

    private static final String REPORTER_DONE_TEXT = "已修好 · 等主管驗收";

    private static final String FAIL_SHEET_REASON_FIELD = "managerTicket.fail-sheet.reason-field";

    private static final java.util.regex.Pattern TICKET_NO = java.util.regex.Pattern.compile("^W-\\d{4}$");

    private static final Map<String, String> QUEUE_KEY = Map.of(
            "待查房清單", "inspection",
            "待驗收清單", "verify");

    private final AppiumWorld world;
    private final ManagerContext managerContext;
    private final RepairContext repairContext;

    public CommonThenSteps(AppiumWorld world, ManagerContext managerContext, RepairContext repairContext) {
        this.world = world;
        this.managerContext = managerContext;
        this.repairContext = repairContext;
    }

    @那麼("畫面提示 {string}")
    public void screenPrompts(String message) {
        world.waitForLabelContaining(message, SCREEN_TIMEOUT);
        dismissAlertsCoveringTaskPage();
    }

    private void dismissAlertsCoveringTaskPage() {
        for (String dismissButton : ALERT_DISMISS_BUTTONS) {
            for (WebElement dismiss : world.driver().findElements(world.byTestId(dismissButton))) {
                dismiss.click();
            }
        }
    }

    @那麼("{string} 在 App 顯示為 {string}")
    public void taskDisplayedAs(String task, String state) {
        if (TICKET_NO.matcher(task).matches()) {
            ticketDisplayedAs(task, state);
            return;
        }
        assertBackendState(task, state);
        if (world.loggedInStaff() != null && "manager".equals(world.loggedInStaff().role())) {
            managerSeesTaskAs(task, state);
            return;
        }

        dismissAlertsCoveringTaskPage();
        returnToTaskDetailFromCelebration(task);
        TaskPageEvidence evidence = TASK_PAGE_EVIDENCE.get(state);
        if (evidence == null) {
            throw new IllegalArgumentException(
                    "找不到「" + state + "」在任務頁狀態呈現對照表中的權威值");
        }
        world.waitForTestId(evidence.testId(), SCREEN_TIMEOUT);
        if (evidence.title() != null) {
            String titleId = evidence.testId() + ".title";
            String actual = world.waitForLabelOf(titleId, evidence.title(), SCREEN_TIMEOUT);
            if (!evidence.title().equals(actual)) {
                throw new AssertionError("「" + task + "」的狀態列標題不符：預期「"
                        + evidence.title() + "」，實際「" + actual + "」");
            }
        }
        if (evidence.step() != null) {
            String actual = world.waitForLabelOf("task.step-track", evidence.step(), SCREEN_TIMEOUT);
            if (!evidence.step().equals(actual)) {
                throw new AssertionError("「" + task + "」的步驟條不符：狀態「" + state
                        + "」應該停在「" + evidence.step() + "」，實際停在「" + actual + "」");
            }
        }
    }

    private void returnToTaskDetailFromCelebration(String task) {
        if (world.driver().findElements(world.byTestId("celebrate.screen")).isEmpty()) {
            return;
        }
        world.tapTestId("celebrate.back-home-button", SCREEN_TIMEOUT);
        world.waitForTestId("home.screen", SCREEN_TIMEOUT);
        world.tapTestId("home.task." + task + ".card", SCREEN_TIMEOUT);
        world.waitForTestId("task.screen", SCREEN_TIMEOUT);
    }

    private void managerSeesTaskAs(String task, String state) {
        if (!world.driver().findElements(world.byTestId("inspect.result")).isEmpty()) {
            List<String> expected = MANAGER_RESULT_TEXT.get(state);
            if (expected == null) {
                throw new AssertionError("查房結果頁不會出現狀態「" + state + "」的單「" + task + "」");
            }
            String actual = world.waitForLabelOf("inspect.result", expected.get(expected.size() - 1), SCREEN_TIMEOUT);
            if (actual == null || !actual.contains(task) || !expected.stream().allMatch(actual::contains)) {
                throw new AssertionError("「" + task + "」的查房結果頁說法不符：預期含 " + expected
                        + "，實際「" + actual + "」");
            }
            return;
        }
        if (!world.driver().findElements(world.byTestId("inspect")).isEmpty()) {
            if (!"待查房".equals(state)) {
                throw new AssertionError("主管還停在「" + task + "」的查房畫面，但預期狀態是「" + state + "」");
            }
            return;
        }
        world.waitForTestId("manager.queue", SCREEN_TIMEOUT);
        if ("待查房".equals(state)) {
            world.waitForTestId("manager.queue.row." + task, SCREEN_TIMEOUT);
        } else if (!world.driver().findElements(world.byTestId("manager.queue.row." + task)).isEmpty()) {
            throw new AssertionError("「" + task + "」預期為「" + state + "」，卻還列在待查房清單上");
        }
    }

    private static final Map<String, List<String>> MANAGER_RESULT_TEXT = Map.of(
            "查房通過", List.of("查房通過"),
            "待開始", List.of("退回重做", "名下"),
            "待認領", List.of("退回重做", "房務待認領"));

    private void ticketDisplayedAs(String ticketNo, String state) {
        Map<String, Object> row = world.backend().repairTicketRow(ticketNo);
        String expectedState = BackendChannel.repairTicketState(state);
        String actualState = String.valueOf(row.get("task_state"));
        if (!expectedState.equals(actualState)) {
            throw new AssertionError("「" + ticketNo + "」的後端狀態不符：預期 " + expectedState
                    + "（" + state + "），實際 " + actualState);
        }
        dismissAlertsCoveringTaskPage();
        BackendChannel.CreatedStaff viewer = world.loggedInStaff();
        boolean manager = viewer != null && "manager".equals(viewer.role());
        if (manager) {
            openManagerTicketDetailFresh(viewer, ticketNo);
        }
        world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
        boolean reporterView = !manager && viewer != null && !"engineering".equals(viewer.dept());
        String expectedText = reporterView && "已完修".equals(state)
                ? REPORTER_DONE_TEXT
                : expectedTicketStatusText(state, row, manager);
        String actual = world.waitForLabelOf("ticket.status-summary-card", expectedText, SCREEN_TIMEOUT);
        boolean matches = actual != null && ("已結案".equals(state) && manager
                ? actual.startsWith(expectedText)
                : actual.contains(expectedText));
        if (!matches) {
            throw new AssertionError("「" + ticketNo + "」的工務單詳情狀態列不符：預期"
                    + ("已結案".equals(state) && manager ? "開頭為" : "含") + "「" + expectedText
                    + "」，實際「" + actual + "」");
        }
    }

    private static String expectedTicketStatusText(String state, Map<String, Object> row, boolean manager) {
        if (row.get("last_verify_failed_at") != null && List.of("待開始", "已接單", "維修中").contains(state)) {
            return TICKET_STATUS_TEXT.get("退回重修");
        }
        if (manager && MANAGER_TICKET_STATUS_TEXT.containsKey(state)) {
            return MANAGER_TICKET_STATUS_TEXT.get(state);
        }
        String text = TICKET_STATUS_TEXT.get(state);
        if (text == null) {
            throw new IllegalArgumentException("找不到「" + state + "」在工務單詳情狀態呈現對照表中的權威值");
        }
        return text;
    }

    private void openManagerTicketDetailFresh(BackendChannel.CreatedStaff manager, String ticketNo) {
        for (WebElement back : world.driver().findElements(world.byTestId("managerTicket.result.back-button"))) {
            back.click();
            world.waitForTestId("manager.queue", SCREEN_TIMEOUT);
        }
        world.ensureOnScreenRoot("manager.home", SCREEN_TIMEOUT);
        repairContext.openTicketDetail(manager, ticketNo);
    }

    @那麼("後端已對 {string} 送出推播 {string}")
    public void backendSentPushTo(String staffName, String title) {
        List<String> titles = world.backend().pushTitlesSentTo(world.staff(staffName).id());
        long matching = titles.stream().filter(title::equals).count();
        if (matching == 0) {
            throw new AssertionError("後端沒有對「" + staffName + "」送出標題為「" + title
                    + "」的推播，實際送出的標題：" + titles);
        }
        if (matching > 1) {
            throw new AssertionError("後端對「" + staffName + "」送出了 " + matching
                    + " 筆標題為「" + title + "」的推播，同一事件只該有一筆");
        }
    }

    @那麼("後端 {string} 的房況為 {string}")
    public void backendRoomHkStateIs(String roomNo, String hkStateChinese) {
        String expected = BackendChannel.hkState(hkStateChinese);
        String actual = world.backend().roomHkState(roomNo);
        if (!expected.equals(actual)) {
            throw new AssertionError("「" + roomNo + "」的後端房況不符：預期 " + expected
                    + "（" + hkStateChinese + "），實際 " + actual);
        }
    }

    @那麼("畫面回到 {string}，{string} 不在清單上")
    public void backOnQueueWithoutItem(String queueChinese, String unit) {
        String queueKey = queueKey(queueChinese);
        world.waitForTestId("manager.queue", SCREEN_TIMEOUT);
        world.waitForLabelContaining(queueChinese.replace("清單", ""), SCREEN_TIMEOUT);
        world.waitForTestIdGone("manager.queue.row." + unit, SCREEN_TIMEOUT);

        int count = assertQueueConsistentWithout(queueKey, unit);
        Integer before = managerContext.queueCountBefore(queueKey);
        if (before != null && count != before - 1) {
            throw new AssertionError("「" + queueChinese + "」的張數應比操作前少 1：操作前 " + before
                    + "，現在 getManagerQueues 回 " + count);
        }
        assertHomeTileShows(queueKey, count);
    }

    @那麼("畫面顯示 {string}，並回到 {string}，{string} 不在清單上")
    public void conflictAlertThenBackOnQueue(String message, String queueChinese, String unit) {
        String queueKey = queueKey(queueChinese);
        WebElement alert = world.waitForTestId("manager.conflict-alert", SCREEN_TIMEOUT);
        String text = alert.getText();
        if (text == null || !text.contains(message)) {
            throw new AssertionError("manager.conflict-alert 應顯示「" + message + "」，實際「" + text + "」");
        }
        world.waitForLabelContaining("知道了", SCREEN_TIMEOUT).click();

        world.waitForTestId("manager.queue", SCREEN_TIMEOUT);
        world.waitForTestIdGone(FAIL_SHEET_REASON_FIELD, SCREEN_TIMEOUT);
        world.waitForLabelContaining(queueChinese.replace("清單", ""), SCREEN_TIMEOUT);
        world.waitForTestIdGone("manager.queue.row." + unit, SCREEN_TIMEOUT);
        int count = assertQueueConsistentWithout(queueKey, unit);
        assertProcessedExactlyOnce(queueKey, unit);
        assertHomeTileShows(queueKey, count);
    }

    private static String queueKey(String queueChinese) {
        String key = QUEUE_KEY.get(queueChinese);
        if (key == null) {
            throw new IllegalArgumentException("找不到「" + queueChinese + "」在清單對照表中的權威值（合法值："
                    + QUEUE_KEY.keySet() + "）");
        }
        return key;
    }

    private int assertQueueConsistentWithout(String queueKey, String unit) {
        JsonNode queues = world.backend().managerQueues(world.loggedInStaff());
        JsonNode items = queues.path(queueKey);
        for (JsonNode item : items) {
            if (unit.equals(item.path("roomNo").asText()) || unit.equals(item.path("ticketNo").asText())) {
                throw new AssertionError("「" + unit + "」仍在 getManagerQueues 的 " + queueKey + " 陣列裡");
            }
        }
        int count = queues.path("counts").path(queueKey).asInt(-1);
        if (count != items.size()) {
            throw new AssertionError("getManagerQueues 的 counts." + queueKey + "（" + count
                    + "）與 " + queueKey + " 陣列長度（" + items.size() + "）不一致");
        }
        return count;
    }

    private void assertHomeTileShows(String queueKey, int count) {
        world.ensureOnScreenRoot("manager.home", SCREEN_TIMEOUT);
        String tileId = "manager.home.tile." + queueKey + ".count";
        String shown = world.waitForLabelOf(tileId, String.valueOf(count), SCREEN_TIMEOUT);
        if (!String.valueOf(count).equals(shown)) {
            throw new AssertionError("主管首頁 " + tileId + " 應顯示 " + count + "，實際「" + shown + "」");
        }
        world.tapUntilVisible("manager.home.tile." + queueKey, "manager.queue", SCREEN_TIMEOUT);
    }

    private void assertProcessedExactlyOnce(String queueKey, String unit) {
        long tenantId = world.backend().ensureTenant();
        switch (queueKey) {
            case "inspection" -> {
                Map<String, Object> task = world.taskRowByRoomNo(unit);
                if ("done".equals(String.valueOf(task.get("state")))) {
                    throw new AssertionError("「" + unit + "」在後端仍是待查房，不是被別人處理過的狀態");
                }
                List<Map<String, Object>> inspections = world.backend().queryRows(
                        "SELECT id FROM inspections WHERE tenant_id = ? AND task_id = ?", tenantId, task.get("id"));
                if (inspections.size() != 1) {
                    throw new AssertionError("「" + unit + "」的查房紀錄應剛好一筆（別人那一次），實際 "
                            + inspections.size() + " 筆");
                }
            }
            case "verify" -> {
                Map<String, Object> ticket = world.backend().repairTicketRow(unit);
                if ("done".equals(String.valueOf(ticket.get("task_state")))) {
                    throw new AssertionError("「" + unit + "」在後端仍是已完修、等主管驗收，不是被別人處理過的狀態");
                }
                List<Map<String, Object>> verifications = world.backend().queryRows("""
                        SELECT type FROM task_events
                        WHERE tenant_id = ? AND task_id = ?
                          AND type IN ('repair_verified', 'repair_verification_failed')
                        """, tenantId, ticket.get("task_id"));
                if (verifications.size() != 1) {
                    throw new AssertionError("「" + unit + "」的驗收紀錄（通過或不通過）應剛好一筆（別人那一次），實際 "
                            + verifications.size() + " 筆：" + verifications);
                }
            }
            default -> throw new IllegalArgumentException("未知的清單鍵：" + queueKey);
        }
    }

    private void assertBackendState(String task, String state) {
        Map<String, Object> row = world.taskRowByRoomNo(task);
        String expectedState = BackendChannel.taskState(state);
        String actualState = String.valueOf(row.get("state"));
        if (!expectedState.equals(actualState)) {
            throw new AssertionError("「" + task + "」的後端狀態不符：預期 " + expectedState
                    + "（" + state + "），實際 " + actualState);
        }
        String requiredInspection = REQUIRED_INSPECTION.get(state);
        if (requiredInspection != null && !requiredInspection.equals(String.valueOf(row.get("inspection")))) {
            throw new AssertionError("「" + task + "」的後端查房結果不符：預期 " + requiredInspection
                    + "，實際 " + row.get("inspection"));
        }
        Boolean requiredCleaning = REQUIRED_CLEANING.get(state);
        if (requiredCleaning != null
                && requiredCleaning != "cleaning".equals(String.valueOf(row.get("type")))) {
            throw new AssertionError("「" + task + "」的任務類別與狀態用語「" + state + "」不搭：實際 "
                    + row.get("type"));
        }
    }
}
