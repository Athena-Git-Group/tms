package com.athena.app.bdd.steps.repair;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.假設;

import java.util.Map;
import java.util.UUID;

/** 工務單模組的 Given。 */
public class RepairGivenSteps {

    /**
     * 工務單類別中文 → 契約 {@code RepairCategory}。與後端 BDD 的 Terms
     * 共用同一套權威值；對不上的中文直接噴錯，不猜。
     */
    private static final Map<String, String> REPAIR_CATEGORY = Map.of(
            "冷氣", "air_conditioning",
            "水電", "plumbing_electrical",
            "家具／設備", "furniture_equipment",
            "門鎖", "door_lock",
            "其他", "other");

    /** 工務單狀態中文 → 權威 {@code tasks.state}（對照 dsl 的狀態用語表）。 */
    private static final Map<String, String> TICKET_STATE = Map.of(
            "等主管審核", "pending_review",
            "主管未核准", "rejected",
            "待認領", "pool",
            "已接單", "accepted",
            "維修中", "doing",
            "已完修", "done");

    /** 工務單來源中文 → 權威 {@code tasks.source}。 */
    private static final Map<String, String> TICKET_SOURCE = Map.of(
            "房務報修", "staff",
            "櫃檯", "desk");

    private final AppiumWorld world;

    public RepairGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /** 怎麼做：把裝置與相機的拍攝時間設為該時刻（{@code MM/DD HH:mm}）。 */
    @假設("裝置時間是 {string}")
    public void deviceTimeIs(String monthDayHourMinute) {
        throw new PendingException();
    }

    /**
     * 怎麼做：從任務頁進入報修畫面，選類別並拍指定張數的照片，停在報修畫面還沒送出。
     * 權威狀態落地：後端還沒有工務單。
     */
    @假設("{string} 在 {string} 的報修畫面選了類別 {string}，附了 {int} 張照片")
    public void repairScreenHasPhotosAttached(String staffName, String task, String category, int photoCount) {
        throw new PendingException();
    }

    /** 怎麼做：攔截網路，讓該張照片的上傳請求失敗，其他照片正常上傳。 */
    @假設("第 {int} 張照片的上傳會失敗")
    public void nthPhotoUploadFails(int photoIndex) {
        throw new PendingException();
    }

    /**
     * 怎麼做：在離線狀態下填好並送出指定張數的冷氣工務單，讓它留在手機的待送出佇列。
     * 權威狀態落地：後端還沒有這些單。房號為 feature 中的裸字（無引號），用 regex 形式的
     * step definition 擷取。
     */
    @假設("^\"([^\"]+)\" 的手機有 (\\d+) 張待送出的 (\\S+) 冷氣工務單$")
    public void phoneHasPendingAcTickets(String staffName, int count, String roomNo) {
        throw new PendingException();
    }

    /**
     * 怎麼做：攔截網路，讓前幾次開單請求在後端已處理後才逾時，迫使 App 用同一個
     * {@code Idempotency-Key} 重送。
     */
    @假設("恢復連線後的前 {int} 次送出會因網路不穩而重送")
    public void firstRetriesAfterReconnectAreFlaky(int retryCount) {
        throw new PendingException();
    }

    /** 怎麼做：透過後端測試資料建立該工務單。權威狀態落地：後端有該單。 */
    @假設("後端已有 {string} 報的 {string} 冷氣工務單 {string}，狀態為 {string}")
    public void backendHasExistingAcTicket(String reporter, String roomNo, String ticketNo, String status) {
        throw new PendingException();
    }

    /** 怎麼做：透過後端測試資料寫入該房今日入住的 VIP 訂單。 */
    @假設("{string} 今天是 VIP 房，住客 {string} 入住")
    public void isVipRoomWithCheckin(String roomNo, String checkinTime) {
        throw new PendingException();
    }

    /**
     * 怎麼做：透過後端測試資料建立這些工務單。權威狀態落地：後端有這些工務單。
     *
     * <p>表頭與語意逐欄對照後端同名 Given（tms {@code RepairGivenSteps.existingRepairTickets}）：
     * {@code 單號}／{@code 房號}／{@code 類別}／{@code 狀態}／{@code 報修人} 必填，
     * {@code 處理人}（pool 以外的狀態必要）、{@code 急件}（「是」才算急件）、
     * {@code 來源}（省略視為房務報修）、{@code 驗收人}（有值代表主管已驗收）選填。
     * 中文對照一律走權威值對照表，不自己猜 enum。
     */
    @假設("已有以下工務單：")
    public void hasExistingTickets(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            String ticketNo = requireCell(row, "單號");
            String roomNo = requireCell(row, "房號");
            String categoryChinese = requireCell(row, "類別");
            String stateChinese = requireCell(row, "狀態");
            // 報修人多半是房務同事，工務視角的背景只列工務員工，不會有他；
            // 缺的時候就地補一位房務，不要求劇本為了一個報修人名稱多寫一列背景。
            String reporterName = requireCell(row, "報修人");
            world.seedStaff(reporterName, "房務");
            BackendChannel.CreatedStaff reporter = world.staff(reporterName);

            String assigneeName = blankToNull(row.get("處理人"));
            BackendChannel.CreatedStaff assignee =
                    assigneeName == null ? null : world.staff(assigneeName);
            boolean urgent = "是".equals(blankToNull(row.get("急件")));
            String sourceChinese = blankToNull(row.get("來源"));
            String verifierName = blankToNull(row.get("驗收人"));

            UUID taskId = world.backend().createRepairTicket(
                    ticketNo, roomNo, lookup(REPAIR_CATEGORY, categoryChinese, "工務單類別"),
                    lookup(TICKET_STATE, stateChinese, "工務單狀態"), reporter, assignee,
                    blankToNull(row.get("說明")), urgent);

            if (sourceChinese != null) {
                world.backend().setTaskSource(
                        taskId, lookup(TICKET_SOURCE, sourceChinese, "工務單來源"));
            }
            if (verifierName != null) {
                // 驗收人是主管端的人，背景的員工表只列現場員工（工務／房務），
                // 不會有他；缺的時候就地補一位主管，不要求劇本為了一個顯示
                // 名稱多寫一列背景。
                world.seedStaff(verifierName, "主管");
                world.backend().markRepairTicketVerified(taskId, world.staff(verifierName).id());
            }
        }
    }

    /**
     * 怎麼做：透過後端測試資料（或先執行一次等效的降級操作）讓該工務單
     * {@code is_urgent}=false、{@code urgent_downgrade_reason}＝該原因。權威狀態落地：
     * 後端該工務單已是一般件，並記錄降級原因。
     */
    @假設("{string} 已將 {string} 降級為一般件，原因為 {string}")
    public void managerDowngradesTicket(String managerName, String ticketNo, String reason) {
        throw new PendingException();
    }

    // ──── 工務視角（T312–T314 落點） ────

    /**
     * 怎麼做：透過後端測試資料建立符合統計數字的工務單。權威狀態落地：後端有這些工務單。
     * 建法對照 tms {@code RepairGivenSteps.engineerWorkloadToday}（後端同名 Given）。
     *
     * <p>「急件數」是「手上張數」（維修中）裡 isUrgent 的子集，不是「今天已完修」的子集——
     * 依 {@code specs/truth/contracts/components/schemas/員工工作台.yaml} 的
     * {@code engineeringSummary.urgentCount}（手上單中 isUrgent=true 的筆數）。今天已完修
     * 那批 urgent 一律 false，避免誤導後續斷言。
     *
     * <p>先建「今天已完修」再建「手上」那批：完修單的狀態重演（claim→start→complete）會
     * 短暫佔用這個人唯一的 doing 名額（uq_tasks_one_doing_per_staff），要趁手上還沒有停在
     * doing 的單時先演完；「手上張數」只有第 1 張落 doing，其餘落 accepted（待開始），
     * 因為同一人同時只能有一張 doing。
     */
    @假設("{string} 手上有 {int} 張維修中的工務單，今天已完修 {int} 張，其中 {int} 張是急件")
    public void engineerWorkloadToday(String staffName, int doingCount, int doneTodayCount, int urgentCount) {
        if (urgentCount > doingCount) {
            throw new IllegalArgumentException(
                    "急件數不能超過手上張數（維修中）：" + urgentCount + " > " + doingCount);
        }
        BackendChannel.CreatedStaff engineer = world.staff(staffName);
        for (int i = 0; i < doneTodayCount; i++) {
            createEngineeringTicketFor(engineer, "done", false);
        }
        for (int i = 0; i < doingCount; i++) {
            createEngineeringTicketFor(engineer, i == 0 ? "doing" : "accepted", i < urgentCount);
        }
    }

    /**
     * 怎麼做：以該工務身分登入後切到待認領分頁（engPool），等列表載入完成。
     *
     * <p>TEST_BACKDOOR 建置下 App 跳過登入殼直接進首頁（{@code app/mobile/lib/main.dart}），
     * 工務視角的分頁入口要等 Phase 3V 的 [BDD-GREEN] 掛上；這裡以 {@code engPool.screen}
     * （T305 骨架已掛的畫面根節點）出現作為「列表載入完成」的機械判準——入口還沒接上時
     * 的失敗形式是定位不到元素，不是 undefined step。
     */
    @假設("{string} 已在 App 打開工務待認領")
    public void alreadyOpenedEngineeringPool(String staffName) {
        // 場景引用沒建過的員工是劇本錯誤，world.staff 會先在這裡爆出來。
        world.ensureOnEngineeringPool(world.staff(staffName));
    }

    /**
     * {@link #engineerWorkloadToday} 專用：建一張最小可用的工務單（單號取自租戶計數器、
     * 房號為合成值，情境不依賴這兩者；類別固定 other），對照 tms
     * {@code RepairGivenSteps.createEngineeringTicketFor}，落地 tasks 與 repair_tickets
     * 兩張權威表（經 {@link com.athena.app.bdd.BackendChannel#createRepairTicket}）。
     */
    private void createEngineeringTicketFor(
            BackendChannel.CreatedStaff engineer, String stateCode, boolean urgent) {
        String ticketNo = String.format("W-%04d", world.backend().allocateNextRepairTicketNo());
        world.backend().createRepairTicket(
                ticketNo, "ENG-" + ticketNo, "other", stateCode, engineer, engineer, null, urgent);
    }

    /** 中文 → 權威值；查不到就噴錯帶原詞，對齊 BackendChannel 的對照表行為。 */
    private static String lookup(Map<String, String> table, String chinese, String what) {
        String value = table.get(chinese);
        if (value == null) {
            throw new IllegalArgumentException("找不到「" + chinese + "」在" + what
                    + "對照表中的權威值（合法值：" + table.keySet() + "）");
        }
        return value;
    }

    /** 必填欄：缺欄或留白都是劇本錯誤，直接噴錯說明缺哪一欄。 */
    private static String requireCell(Map<String, String> row, String column) {
        String value = blankToNull(row.get(column));
        if (value == null) {
            throw new IllegalArgumentException("工務單 data table 缺必填欄「" + column + "」");
        }
        return value;
    }

    /** 空字串與全空白都當成沒填。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ──── 房務端生命週期（T312–T314 落點） ────
}
