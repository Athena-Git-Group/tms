package com.athena.app.bdd.steps.system;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.appium.java_client.AppiumBy;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class SystemWhenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private static final String HOME_TASK_PREFIX = "home.task.";

    private static final String POOL_CARD_PREFIX = "pool.card.";

    private static final String QUEUE_ROW_PREFIX = "manager.queue.row.";

    private static final String INSPECT_ITEM_PREFIX = "inspect.item.";

    private static final String VERIFY_FAIL_REASON = "水龍頭仍在滴";

    private final AppiumWorld world;

    public SystemWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    @當("{string} 在 App 離線時嘗試 {string}")
    public void attemptActionWhileOffline(String staffName, String action) {
        BackendChannel.CreatedStaff staff = world.staff(staffName);
        switch (action) {
            case "開始任務" -> {
                world.ensureOnTaskDetail(staff, onlyTaskRoomOnHome(staff));
                world.tapTestId("task.primary-button", SCREEN_TIMEOUT);
            }
            case "認領待認領的單" -> {
                world.ensurePoolTask("309", "退房清潔");
                world.ensureOnPool(staff);
                world.tapTestId(onlyPoolCardId() + ".claim-button", SCREEN_TIMEOUT);
            }
            case "延後任務" -> {
                world.ensureOnTaskDetail(staff, onlyTaskRoomOnHome(staff));
                world.tapTestId("task.defer-button", SCREEN_TIMEOUT);
                world.tapTestId("hold.reason.請勿打擾", SCREEN_TIMEOUT);
            }
            case "開立新工務單" -> {
                world.ensureOnTaskDetail(staff, onlyTaskRoomOnHome(staff));
                world.tapTestId("task.repair-button", SCREEN_TIMEOUT);
            }
            case "送出查房結果" -> {
                openManagerQueueRow(staff, "inspection", onlyInspectionRoom(), "inspect");
                world.waitForTestId("inspect.submit-button", SCREEN_TIMEOUT);
                for (String passButton : passButtonsOnInspection()) {
                    world.tapTestId(passButton, SCREEN_TIMEOUT);
                }
                world.tapTestId("inspect.submit-button", SCREEN_TIMEOUT);
            }
            case "驗收通過" -> {
                openManagerQueueRow(staff, "verify", firstResolvedTicketNo(), "managerTicket");
                world.tapTestId("managerTicket.pass-button", SCREEN_TIMEOUT);
            }
            case "驗收不通過" -> {
                openManagerQueueRow(staff, "verify", temporaryResolvedTicketNo(), "managerTicket");
                world.tapTestId("managerTicket.fail-button", SCREEN_TIMEOUT);
                world.typeInto("managerTicket.fail-sheet.reason-field", SCREEN_TIMEOUT, VERIFY_FAIL_REASON);
                world.tapTestId("managerTicket.fail-sheet.submit-button", SCREEN_TIMEOUT);
            }
            default -> throw new IllegalArgumentException(
                    "離線時嘗試的動作只支援員工的開始任務｜認領待認領的單｜延後任務｜開立新工務單，"
                            + "與主管的送出查房結果｜驗收通過｜驗收不通過，收到：" + action);
        }
    }

    private String onlyTaskRoomOnHome(BackendChannel.CreatedStaff staff) {
        world.ensureLoggedInAs(staff);
        world.waitForTestId("home.screen", SCREEN_TIMEOUT);
        world.waitForTestId("home.progress.cleaned-ratio", SCREEN_TIMEOUT);
        List<String> cards = world.findTestIdsWithPrefix(HOME_TASK_PREFIX).stream()
                .filter(id -> id.endsWith(".card"))
                .toList();
        if (cards.size() != 1) {
            throw new IllegalStateException(
                    "首頁上有 " + cards.size() + " 張任務卡，無法指認劇本說的那一筆：" + cards);
        }
        String id = cards.get(0);
        return id.substring(HOME_TASK_PREFIX.length(), id.length() - ".card".length());
    }

    private String onlyPoolCardId() {
        List<String> cards = world.findTestIdsWithPrefix(POOL_CARD_PREFIX).stream()
                .filter(id -> !id.substring(POOL_CARD_PREFIX.length()).contains("."))
                .toList();
        if (cards.size() != 1) {
            throw new IllegalStateException(
                    "待認領池裡有 " + cards.size() + " 張單卡，無法指認要認領哪一張：" + cards);
        }
        return cards.get(0);
    }

    private void openManagerQueueRow(BackendChannel.CreatedStaff manager, String queueKey, String rowKey,
                                     String targetScreenId) {
        ensureOnManagerHome(manager);
        world.tapUntilVisible("manager.home.tile." + queueKey, "manager.queue", SCREEN_TIMEOUT);
        world.tapUntilVisible(QUEUE_ROW_PREFIX + rowKey, targetScreenId, SCREEN_TIMEOUT);
    }

    private String onlyInspectionRoom() {
        List<Map<String, Object>> rows = world.backend().queryRows("""
                SELECT r.room_no FROM tasks t
                JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                WHERE t.tenant_id = ? AND t.type = 'cleaning' AND t.state = 'done'
                """, world.backend().ensureTenant());
        if (rows.size() != 1) {
            throw new IllegalStateException("後端待查房的房間應恰有 1 間，實際 " + rows.size() + " 間：" + rows);
        }
        return String.valueOf(rows.get(0).get("room_no"));
    }

    private String firstResolvedTicketNo() {
        List<Map<String, Object>> rows = resolvedTicketRows("");
        if (rows.isEmpty()) {
            throw new IllegalStateException("後端沒有已完修的工務單，無從驗收通過");
        }
        return String.valueOf(rows.get(0).get("ticket_no"));
    }

    private String temporaryResolvedTicketNo() {
        List<Map<String, Object>> rows = resolvedTicketRows(" AND rt.resolution_type = 'temporary'");
        if (rows.size() != 1) {
            throw new IllegalStateException(
                    "後端完修結果為暫時處理的已完修工務單應恰有 1 張，實際 " + rows.size() + " 張：" + rows);
        }
        return String.valueOf(rows.get(0).get("ticket_no"));
    }

    private List<Map<String, Object>> resolvedTicketRows(String extraCondition) {
        return world.backend().queryRows("""
                SELECT rt.ticket_no FROM repair_tickets rt
                JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                WHERE rt.tenant_id = ? AND t.state = 'done'""" + extraCondition + " ORDER BY rt.ticket_no",
                world.backend().ensureTenant());
    }

    private void ensureOnManagerHome(BackendChannel.CreatedStaff manager) {
        world.ensureLoggedInAs(manager);
        if (!isVisible("manager.home") && !hasBackButton()) {
            world.waitForLabelContaining("首頁", SCREEN_TIMEOUT).click();
        }
        world.ensureOnScreenRoot("manager.home", SCREEN_TIMEOUT);
    }

    private boolean hasBackButton() {
        return !world.driver().findElements(world.byTestId("nav.back-button")).isEmpty()
                || !world.driver().findElements(AppiumBy.accessibilityId("Back")).isEmpty();
    }

    private List<String> passButtonsOnInspection() {
        return world.findTestIdsWithPrefix(INSPECT_ITEM_PREFIX).stream()
                .filter(id -> id.endsWith(".pass-button"))
                .toList();
    }

    private boolean isVisible(String testId) {
        return !world.driver().findElements(world.byTestId(testId)).isEmpty();
    }

}
