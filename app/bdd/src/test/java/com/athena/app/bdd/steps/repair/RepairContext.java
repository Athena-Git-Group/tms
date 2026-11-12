package com.athena.app.bdd.steps.repair;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 工務單模組的情境內共用狀態與進法。給同一情境的 Given／When／Then 共用兩件事：
 *
 * <ul>
 *   <li><b>報修面板的進法</b>：Given（「選了類別 X，附了 N 張照片」）與 When（「拍 N 張
 *       照片」「選擇類別 X」）都要先站在報修面板上，三處各寫一次就會各自漂移。</li>
 *   <li><b>報修面板是從哪一間房進來的</b>：急件判定的 Then 句型不帶房號，但「必查」
 *       要回頭讀該房今天的訂房快取才驗得到判定理由，只能由進面板的那一步留下來。</li>
 * </ul>
 *
 * <p>Cucumber 以 constructor injection 讓同情境的 step 類別共用同一個實例。
 */
public class RepairContext {

    /** 畫面切換有轉場動畫，用輪詢等待取代固定 sleep。 */
    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(8);

    /** 動作選單的進場動畫約 0.26 秒，等它站定再點。 */
    private static final Duration MENU_TIMEOUT = Duration.ofSeconds(3);

    private static final String PHOTO_MENU_CAMERA = "photoMenu.camera-button";

    // ---- 主管視角工務單詳情（畫面簡稱 managerTicket）的 accessibility id，照
    // app/mobile/lib/features/manager/manager_ids.dart 的 ManagerIds.ticket*。 ----

    /** 工務單詳情頁本身（三個視角共用同一個根節點 id）。 */
    public static final String TICKET_SCREEN = "ticket.screen";
    public static final String MANAGER_PASS_BUTTON = "managerTicket.pass-button";
    public static final String MANAGER_FAIL_BUTTON = "managerTicket.fail-button";
    public static final String MANAGER_FAIL_REASON_FIELD = "managerTicket.fail-sheet.reason-field";
    public static final String MANAGER_FAIL_SUBMIT_BUTTON = "managerTicket.fail-sheet.submit-button";
    /** 不通過送出後的驗收結果卡。 */
    public static final String MANAGER_RESULT_CARD = "managerTicket.result";

    /** 主管首頁與清單（照 ManagerIds.home／queue／queueRow）。 */
    private static final String MANAGER_HOME = "manager.home";
    private static final String MANAGER_QUEUE = "manager.queue";

    private final AppiumWorld world;

    private String repairRoomNo;

    public RepairContext(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 確保 App 停在該房的報修面板上（{@code repair.sheet}）。房務報修的進法只有一條：
     * 登入 → 首頁該房的任務卡 → 任務頁 → 報修圖示（{@code task.repair-button}）。
     * 已經停在報修面板時不重複進入——Given 鋪好面板、When 再對同一個面板操作是常見的接法。
     */
    public void openRepairSheet(String staffName, String roomNo) {
        repairRoomNo = roomNo;
        if (!world.driver().findElements(world.byTestId("repair.sheet")).isEmpty()) {
            return;
        }
        world.ensureOnTaskDetail(world.staff(staffName), roomNo);
        world.tapTestId("task.repair-button", SCREEN_TIMEOUT);
        world.waitForTestId("repair.sheet", SCREEN_TIMEOUT);
    }

    /**
     * 加一張照片到目前這張表單上。
     *
     * <p>點「加入照片」會先跳出 iOS 動作選單問要拍照還是從相簿選，所以每一個
     * 要照片的劇本都得點兩下。兩下分散在各個 step 裡各寫一次，選單日後改了
     * 就得在六個地方各改一次，所以集中在這裡。
     *
     * @param addButtonId 該畫面「加入照片」的 accessibility id；報修面板與完修
     *                    回報面板的前綴不同。
     */
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

    /**
     * 讓 App 以該員工身分停在該工務單的詳情頁（{@code ticket.screen}）。三個視角三條進法：
     * <ul>
     *   <li>主管：已完修（done）的單從待驗收清單點該單（dsl：主管進入時從待驗收清單點該單）；
     *       其他狀態不在任何清單上，改從通知中心進，見 {@link #openManagerTicketDetail}。</li>
     *   <li>工務：工務首頁「手上」列表的單卡（{@code engHome.in-hand.{單號}.card}）。</li>
     *   <li>房務（報修人）：首頁該房任務列 → 任務頁 → 「這間房的資訊」的工務單列；該房
     *       未撤回的單兩張以上時先推清單頁再點該單那一列。張數去後端問，不試點看看。</li>
     * </ul>
     */
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

    /**
     * 主管進工務單詳情。已完修（done）的單一律在待驗收清單：從首頁的待驗收格進清單，再點
     * 該單那一列（{@code manager.queue.row.{單號}}），不看 {@code verified_at}——驗收通過
     * 就離開 done。其他狀態（待認領、待開始、維修中、已結案）不在任何清單上，走 dsl 點名的另一個入口——
     * 通知中心：先透過後端測試資料種一則指向這張單的通知（{@code target.kind=ticket}，
     * 與 seed-demo 的工務單通知同形狀）給這位主管，再從通知中心點它進詳情。
     */
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
        // 通知入口與既有 push steps 同一條路：Tab bar「通知」只有文字沒有 id，通知中心本體
        // 是 notification.screen，列表項是 notification.item.{id}.title。
        world.waitForLabelContaining("通知", SCREEN_TIMEOUT).click();
        world.waitForTestId("notification.screen", SCREEN_TIMEOUT);
        world.tapUntilVisible("notification.item." + notificationId + ".title", TICKET_SCREEN, SCREEN_TIMEOUT);
    }

    /** 該單所在房的房號（報修人從首頁任務列進工務單時要靠它找卡片）。 */
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

    /**
     * 權威讀回：該工務單所在房間有幾張未撤回的單——任務頁那一列是直接開詳情還是先推
     * 清單頁，取決於這個數字。排除值只有 {@code cancelled} 一個，跟後端
     * {@code listRepairTickets}（{@code RepairService.listByRoom}）同一口徑。
     */
    private int uncancelledTicketCountOfRoom(String ticketNo) {
        return world.backend().queryRows(
                """
                SELECT rt.ticket_no FROM repair_tickets target
                JOIN repair_tickets rt ON rt.tenant_id = target.tenant_id AND rt.room_id = target.room_id
                JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                WHERE target.tenant_id = ? AND target.ticket_no = ? AND t.state <> 'cancelled'
                """, world.backend().ensureTenant(), ticketNo).size();
    }

    /** 該單的任務 id（{@code repair_tickets.task_id}），給不經 App 的後端動作用。 */
    public UUID ticketTaskId(String ticketNo) {
        return (UUID) world.backend().repairTicketRow(ticketNo).get("task_id");
    }

    /**
     * 「畫面開著、世界在背後改變」這類 Given 的前提：該員工已登入且畫面停在工務單詳情。
     * 不成立就是劇本漏了「已在 App 打開 … 的工務單詳情」那一句，直接噴錯。
     */
    public void requireTicketDetailOpenAs(String staffName) {
        if (!world.loggedInStaff().name().equals(staffName)
                || world.driver().findElements(world.byTestId(TICKET_SCREEN)).isEmpty()) {
            throw new IllegalStateException("「" + staffName + "」的工務單詳情還沒開著——"
                    + "劇本是否漏了「已在 App 打開 … 的工務單詳情」那一句？");
        }
    }

    /**
     * 本情境的報修面板是從哪一間房進來的。還沒進過報修面板就問，代表劇本漏了帶人進
     * 面板的那一句，直接噴錯而不是回 null 讓後面的查詢查出一個空結果。
     */
    public String repairRoomNo() {
        if (repairRoomNo == null) {
            throw new IllegalStateException(
                    "本情境還沒有進過報修面板——場景是否漏了「在 X 的報修畫面…」這類句子？");
        }
        return repairRoomNo;
    }
}
