package com.athena.app.bdd.steps.repair;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    /** 報修面板上每一次點擊的等待上限；與 {@link RepairContext} 用同一個數字。 */
    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(8);

    private final AppiumWorld world;

    private final RepairContext context;

    public RepairGivenSteps(AppiumWorld world, RepairContext context) {
        this.world = world;
        this.context = context;
    }

    /**
     * 怎麼做：把裝置與相機的拍攝時間設為該時刻（{@code MM/DD HH:mm}），注入測試後門的
     * {@code POST /clock}（{@code TestHooks.injectedClockIso}）——照片浮水印上的拍攝時間
     * 從這個注入值取。
     *
     * <p>句型只給月日時分，年份補成執行當下這一年：後端的 {@code Clock} 設不進去
     * （理由見共通 Given「現在時間是」），裝置跨到另一個年份會讓兩個時鐘差一整年，
     * 任何以「今天」分組的讀回都會落空。
     */
    @假設("裝置時間是 {string}")
    public void deviceTimeIs(String monthDayHourMinute) {
        LocalDate sameYear = LocalDate.now();
        LocalDateTime moment = LocalDateTime.parse(
                sameYear.getYear() + "/" + monthDayHourMinute,
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
        world.postBackdoorJson("/clock", "{\"isoTime\":\""
                + moment.atZone(ZoneId.systemDefault()).toOffsetDateTime() + "\"}");
    }

    /**
     * 怎麼做：從任務頁進入報修畫面，選類別並拍指定張數的照片，停在報修畫面還沒送出。
     * 權威狀態落地：後端還沒有工務單。
     */
    @假設("{string} 在 {string} 的報修畫面選了類別 {string}，附了 {int} 張照片")
    public void repairScreenHasPhotosAttached(String staffName, String task, String category, int photoCount) {
        context.openRepairSheet(staffName, task);
        world.tapTestId("repair.category." + lookup(REPAIR_CATEGORY, category, "工務單類別") + ".chip",
                SCREEN_TIMEOUT);
        for (int i = 0; i < photoCount; i++) {
            context.addPhoto("repair.photo.add-button");
        }
        assertNoTicketInBackend("這句只準備報修面板，還沒按送出");
    }

    /**
     * 怎麼做：讓該張照片的上傳請求失敗，其他照片正常上傳。攔截點是測試後門的
     * {@code POST /upload-fail}（{@code TestHooks.uploadFailAtIndex}），序號與句型一樣
     * 從 1 算起。
     */
    @假設("第 {int} 張照片的上傳會失敗")
    public void nthPhotoUploadFails(int photoIndex) {
        world.postBackdoorJson("/upload-fail", "{\"photoIndex\":" + photoIndex + "}");
    }

    /**
     * 怎麼做：填好冷氣工務單後在離線狀態下送出，讓它留在手機的待送出佇列。
     * 權威狀態落地：後端還沒有這些單。房號為 feature 中的裸字（無引號），用 regex 形式的
     * step definition 擷取。
     *
     * <p>面板是在切離線「之前」打開並填好的：依 `系統層/離線模式與連線異常.feature`
     * 的 Rule「離線時開始、認領、延後、開新工務單一律被擋」，離線時點報修只會收到
     * 離線提示，根本進不了報修面板；能在離線時走完的只有「把已經填好的這張送出」
     * （`報修照片與送出.feature` 的「阿美在電梯裡送出 310 冷氣單」就是這條動線）。
     *
     * <p>也因為這樣，這一句一次只押得出一張：第一張送出之後手機已經離線，第二張連面板
     * 都打不開。張數要求超過 1 時直接噴錯，不假裝做得到——照著做只會在離線狀態下反覆點
     * 報修按鈕，最後定位不到面板而逾時，錯誤訊息還指向錯的地方。
     */
    @假設("^\"([^\"]+)\" 的手機有 (\\d+) 張待送出的 (\\S+) 冷氣工務單$")
    public void phoneHasPendingAcTickets(String staffName, int count, String roomNo) {
        // 開工是離線時被擋下來的動作，裝置一旦離線那一筆進行中就再也補不起來，
        // 所以切離線之前先把測試資料欠的「在 App 按開始」補完（同共通 Given「手機目前離線」）。
        world.settleDeferredStarts();
        if (count != 1) {
            throw new IllegalStateException("這一句一次只押得出 1 張待送出的工務單，劇本要求 "
                    + count + " 張。第一張送出之後手機已經離線，第二張連報修面板都打不開");
        }
        context.openRepairSheet(staffName, roomNo);
        world.tapTestId("repair.category." + REPAIR_CATEGORY.get("冷氣") + ".chip", SCREEN_TIMEOUT);
        context.addPhoto("repair.photo.add-button");
        world.goOffline();
        world.tapTestId("repair.submit-button", SCREEN_TIMEOUT);
        assertNoTicketInBackend("離線送出的單只會留在手機的待送出佇列");
    }

    /**
     * 怎麼做：測試後門的 {@code /drop-write-response} 讓前幾次開單請求在後端已處理後才
     * 逾時，迫使 App 用同一個 {@code Idempotency-Key} 重送。
     *
     * <p>不用在送出前攔下請求的做法：那樣後端一張單都沒有，重送幾次都只會建立一張，
     * 驗不到「後端只認列一次」。
     */
    @假設("恢復連線後的前 {int} 次送出會因網路不穩而重送")
    public void firstRetriesAfterReconnectAreFlaky(int retryCount) {
        world.postBackdoorJson("/drop-write-response", "{\"count\":" + retryCount + "}");
    }

    /**
     * 怎麼做：透過後端測試資料建立該工務單。權威狀態落地：後端有該單。
     *
     * <p>句型沒有帶處理人，但「維修中」這類狀態在後端一定要有負責人，所以由
     * {@link AppiumWorld#onlyEngineer()} 取背景裡那位工務；pool／pending_review
     * 這類還沒有人接的狀態不需要負責人，給了也用不到。
     */
    @假設("後端已有 {string} 報的 {string} 冷氣工務單 {string}，狀態為 {string}")
    public void backendHasExistingAcTicket(String reporter, String roomNo, String ticketNo, String status) {
        String stateCode = lookup(TICKET_STATE, status, "工務單狀態");
        boolean needsAssignee = !"pool".equals(stateCode) && !"pending_review".equals(stateCode);
        world.backend().createRepairTicket(
                ticketNo, roomNo, REPAIR_CATEGORY.get("冷氣"), stateCode,
                world.staff(reporter), needsAssignee ? world.onlyEngineer() : null, null, false);
    }

    /**
     * 怎麼做：透過後端測試資料寫入該房今日入住的 VIP 訂單（{@code reservations_cache}
     * 的 {@code attrs.vip}＋今天的 {@code checkin_at}）。急件判定只看這張表，所以這句
     * 落地之後，同一間房的開單前檢查就會判成急件、理由組成「VIP · {時刻} 入住」。
     */
    @假設("{string} 今天是 VIP 房，住客 {string} 入住")
    public void isVipRoomWithCheckin(String roomNo, String checkinTime) {
        world.backend().insertTodayReservation(roomNo, checkinTime, true);
    }

    /**
     * 怎麼做：透過後端測試資料建立這些工務單。權威狀態落地：後端有這些工務單。
     *
     * <p>權威位置在介面根 {@code specs/truth/features/ios-app/dsl.md}（不是本模組的
     * {@code 工務單/dsl.md}）：這句是跨模組共用句型，推播模組的
     * {@code 推播/點開推播的去處.feature} 也用它鋪工務單資料
     * （單號／房號／類別／狀態／報修人／處理人／來源）。
     *
     * <p>表頭與語意逐欄對照後端同名 Given（tms {@code RepairGivenSteps.existingRepairTickets}）：
     * {@code 單號}／{@code 房號}／{@code 類別}／{@code 狀態}／{@code 報修人} 必填，
     * {@code 處理人}（pool 以外的狀態必要）、{@code 急件}（「是」才算急件）、
     * {@code 來源}（省略視為房務報修）、{@code 驗收人}（有值代表主管已驗收）、
     * {@code 退回原因}（狀態為主管未核准時必要，主管退回時附的原因）選填。
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

            String rejectReason = blankToNull(row.get("退回原因"));
            String assigneeName = blankToNull(row.get("處理人"));
            BackendChannel.CreatedStaff assignee =
                    assigneeName == null ? null : world.staff(assigneeName);
            boolean urgent = "是".equals(blankToNull(row.get("急件")));
            String sourceChinese = blankToNull(row.get("來源"));
            String verifierName = blankToNull(row.get("驗收人"));

            String stateCode = lookup(TICKET_STATE, stateChinese, "工務單狀態");
            // 退回是主管的動作，退回原因由他附上；劇本的背景已經建好一位主管，
            // 就以那一位為準（沒有主管欄可指名，理由同 AppiumWorld.onlyManager）。
            BackendChannel.CreatedStaff rejecter =
                    "rejected".equals(stateCode) ? world.onlyManager() : null;
            UUID taskId = world.backend().createRepairTicket(
                    ticketNo, roomNo, lookup(REPAIR_CATEGORY, categoryChinese, "工務單類別"),
                    stateCode, reporter, assignee,
                    blankToNull(row.get("說明")), urgent, rejecter, rejectReason);

            if (sourceChinese != null) {
                world.backend().setTaskSource(
                        taskId, lookup(TICKET_SOURCE, sourceChinese, "工務單來源"));
            }
            // 房務報修的單在產品上一定是從那位房務自己的任務開出來的
            // （契約 CreateRepairTicketRequest 路徑①的 sourceTaskId），而報修人
            // 追蹤這張單的 App 路徑也是走那張任務的任務頁（同房工務單區）。
            // 測試資料只落工務單、不落來源任務的話，報修人在 App 上沒有任何
            // 入口進得了自己的單——那是劇本湊不出來的狀態，不是待驗的行為。
            if ("housekeeping".equals(reporter.dept())) {
                seedSourceTaskFor(reporter, roomNo);
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
        Map<String, Object> ticket = world.backend().repairTicketRow(ticketNo);
        world.backend().downgradeRepairTicketUrgency(
                (UUID) ticket.get("task_id"), world.staff(managerName), reason);
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

    /**
     * 給報修人補一張同房的房務任務（待開始），當作這張工務單的來源任務。
     * 同一間房只補一次：句型用房號指稱任務，同房兩筆房務任務會讓後續的
     * 權威讀回無法唯一指認。
     */
    private void seedSourceTaskFor(BackendChannel.CreatedStaff reporter, String roomNo) {
        List<Map<String, Object>> existing = world.backend().queryRows(
                """
                SELECT t.id FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? AND r.room_no = ? AND t.type <> 'repair'
                """, world.backend().ensureTenant(), roomNo);
        if (!existing.isEmpty()) {
            return;
        }
        world.backend().createTask(
                roomNo, "退房清潔", "cleaning", "housekeeping", null, "accepted",
                reporter, null, null);
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

    /**
     * 「這一步不該在後端留下工務單」的守門：報修面板上的準備動作與離線送出都只動手機，
     * 真的建單的只有線上按送出那一步。少了這個檢查，上游要是哪天提前建單，情境會一路
     * 綠到最後才在別的斷言上以看不懂的方式紅掉。
     *
     * @param why 寫進錯誤訊息，說明這一步為什麼不該建單
     */
    private void assertNoTicketInBackend(String why) {
        List<Map<String, Object>> rows = world.backend().queryRows(
                "SELECT ticket_no FROM repair_tickets WHERE tenant_id = ?",
                world.backend().ensureTenant());
        if (!rows.isEmpty()) {
            throw new AssertionError("後端不該有工務單（" + why + "），實際查到 " + rows.size() + " 張");
        }
    }
}
