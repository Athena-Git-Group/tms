package com.athena.app.bdd.steps.repair;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RepairContext {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(8);

    private static final Duration MENU_TIMEOUT = Duration.ofSeconds(3);

    private static final String PHOTO_MENU_CAMERA = "photoMenu.camera-button";

    public static final String TICKET_SCREEN = "ticket.screen";
    public static final String MANAGER_PASS_BUTTON = "managerTicket.pass-button";
    public static final String MANAGER_FAIL_BUTTON = "managerTicket.fail-button";
    public static final String MANAGER_FAIL_REASON_FIELD = "managerTicket.fail-sheet.reason-field";
    public static final String MANAGER_FAIL_SUBMIT_BUTTON = "managerTicket.fail-sheet.submit-button";
    public static final String MANAGER_RESULT_CARD = "managerTicket.result";

    private static final String MANAGER_HOME = "manager.home";
    private static final String MANAGER_QUEUE = "manager.queue";

    private final AppiumWorld world;

    private String repairRoomNo;

    public RepairContext(AppiumWorld world) {
        this.world = world;
    }

    public void openRepairSheet(String staffName, String roomNo) {
        repairRoomNo = roomNo;
        if (!world.driver().findElements(world.byTestId("repair.sheet")).isEmpty()) {
            return;
        }
        world.ensureOnTaskDetail(world.staff(staffName), roomNo);
        world.tapTestId("task.repair-button", SCREEN_TIMEOUT);
        world.waitForTestId("repair.sheet", SCREEN_TIMEOUT);
    }

    public void addPhoto(String addButtonId) {
        world.tapTestId(addButtonId, SCREEN_TIMEOUT);
        long deadline = System.nanoTime() + MENU_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (!world.driver().findElements(world.byTestId(PHOTO_MENU_CAMERA)).isEmpty()) {
                world.tapTestId(PHOTO_MENU_CAMERA, SCREEN_TIMEOUT);
                return;
            }
            if (world.driver().findElements(world.byTestId(addButtonId)).isEmpty()) {
                return;
            }
        }
        throw new AssertionError("點了 " + addButtonId + " 之後沒有跳出拍照／從相簿選擇的選單");
    }

    public void openTicketDetail(BackendChannel.CreatedStaff staff, String ticketNo) {
        world.ensureLoggedInAs(staff);
        if ("manager".equals(staff.role())) {
            openManagerTicketDetail(staff, ticketNo);
        } else if ("engineering".equals(staff.dept())) {
            world.tapTestId("engHome.in-hand." + ticketNo + ".card", SCREEN_TIMEOUT);
        } else {
            world.tapTestId("home.task." + roomNoOf(ticketNo) + ".card", SCREEN_TIMEOUT);
            world.waitForTestId("task.screen", SCREEN_TIMEOUT);
            world.tapTestId("task.info-row.repair-tickets", SCREEN_TIMEOUT);
            if (uncancelledTicketCountOfRoom(ticketNo) > 1) {
                world.waitForTestId("room-info.repair-tickets.screen", SCREEN_TIMEOUT);
                world.tapTestId("room-info.repair-ticket." + ticketNo + ".row", SCREEN_TIMEOUT);
            }
        }
        world.waitForTestId(TICKET_SCREEN, SCREEN_TIMEOUT);
    }

    private void openManagerTicketDetail(BackendChannel.CreatedStaff manager, String ticketNo) {
        Map<String, Object> ticket = world.backend().repairTicketRow(ticketNo);
        String state = String.valueOf(ticket.get("task_state"));
        if ("done".equals(state)) {
            world.waitForTestId(MANAGER_HOME, SCREEN_TIMEOUT);
            world.tapUntilVisible("manager.home.tile.verify", MANAGER_QUEUE, SCREEN_TIMEOUT);
            world.tapUntilVisible("manager.queue.row." + ticketNo, TICKET_SCREEN, SCREEN_TIMEOUT);
            return;
        }
        UUID notificationId = world.backend().createNotification(
                manager.id(), "repair_urgent", roomNoOf(ticketNo) + " " + ticketNo,
                java.time.OffsetDateTime.now(), false,
                Map.of("kind", "ticket", "taskId", String.valueOf(ticket.get("task_id"))));
        world.waitForLabelContaining("通知", SCREEN_TIMEOUT).click();
        world.waitForTestId("notification.screen", SCREEN_TIMEOUT);
        world.tapUntilVisible("notification.item." + notificationId + ".title", TICKET_SCREEN, SCREEN_TIMEOUT);
    }

    private String roomNoOf(String ticketNo) {
        List<Map<String, Object>> rows = world.backend().queryRows(
                """
                SELECT r.room_no FROM repair_tickets rt
                JOIN rooms r ON r.tenant_id = rt.tenant_id AND r.id = rt.room_id
                WHERE rt.tenant_id = ? AND rt.ticket_no = ?
                """, world.backend().ensureTenant(), ticketNo);
        if (rows.size() != 1) {
            throw new IllegalStateException("單號「" + ticketNo + "」在後端應恰有 1 張工務單，實際 "
                    + rows.size() + " 張");
        }
        return (String) rows.get(0).get("room_no");
    }

    private int uncancelledTicketCountOfRoom(String ticketNo) {
        return world.backend().queryRows(
                """
                SELECT rt.ticket_no FROM repair_tickets target
                JOIN repair_tickets rt ON rt.tenant_id = target.tenant_id AND rt.room_id = target.room_id
                JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                WHERE target.tenant_id = ? AND target.ticket_no = ? AND t.state <> 'cancelled'
                """, world.backend().ensureTenant(), ticketNo).size();
    }

    public UUID ticketTaskId(String ticketNo) {
        return (UUID) world.backend().repairTicketRow(ticketNo).get("task_id");
    }

    public void requireTicketDetailOpenAs(String staffName) {
        if (!world.loggedInStaff().name().equals(staffName)
                || world.driver().findElements(world.byTestId(TICKET_SCREEN)).isEmpty()) {
            throw new IllegalStateException("「" + staffName + "」的工務單詳情還沒開著——"
                    + "劇本是否漏了「已在 App 打開 … 的工務單詳情」那一句？");
        }
    }

    public String repairRoomNo() {
        if (repairRoomNo == null) {
            throw new IllegalStateException(
                    "本情境還沒有進過報修面板——場景是否漏了「在 X 的報修畫面…」這類句子？");
        }
        return repairRoomNo;
    }
}
