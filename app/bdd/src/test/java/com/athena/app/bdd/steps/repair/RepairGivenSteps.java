package com.athena.app.bdd.steps.repair;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    /** 工務單來源中文 → 權威 {@code tasks.source}。 */
    private static final Map<String, String> TICKET_SOURCE = Map.of(
            "房務報修", "staff",
            "櫃檯", "desk");

    /** 「剛送出驗收不通過」句型不帶原因時用的預設原因（dsl 該列的預設值）。 */
    private static final String DEFAULT_VERIFY_FAIL_REASON = "刷卡仍偶爾無反應";

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
     * <p>注入限定在 {@code POST /repair-tickets} 這條路徑上。要重送的是開單請求，不是
     * 照片位置：離線押著的那一筆動作送出時會先建立每張照片的上傳位置，那也是一個帶
     * {@code Idempotency-Key} 的寫入，不限定路徑的話前幾次額度全被照片那一步吃掉，
     * 開單請求一次都沒有重送過，這條 Rule 就只是看起來綠的。
     *
     * <p>不用在送出前攔下請求的做法：那樣後端一張單都沒有，重送幾次都只會建立一張，
     * 驗不到「後端只認列一次」。
     */
    @假設("恢復連線後的前 {int} 次送出會因網路不穩而重送")
    public void firstRetriesAfterReconnectAreFlaky(int retryCount) {
        world.postBackdoorJson("/drop-write-response",
                "{\"count\":" + retryCount + ",\"pathPrefix\":\"/repair-tickets\"}");
    }

    /**
     * 怎麼做：透過後端測試資料建立該工務單。權威狀態落地：後端有該單。
     *
     * <p>句型沒有帶處理人，但「維修中」這類狀態在後端一定要有負責人，所以由
     * {@link AppiumWorld#onlyEngineer()} 取背景裡那位工務；pool 還沒有人接，
     * 不需要負責人，給了也用不到。
     */
    @假設("後端已有 {string} 報的 {string} 冷氣工務單 {string}，狀態為 {string}")
    public void backendHasExistingAcTicket(String reporter, String roomNo, String ticketNo, String status) {
        String stateCode = BackendChannel.repairTicketState(status);
        boolean needsAssignee = !"pool".equals(stateCode);
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
     * {@code 單號}／{@code 房號}（客房房號或「公共區域」）／{@code 類別}／{@code 狀態}（待認領、
     * 已接單、待開始、維修中、已完修、已結案、已撤回）必填；{@code 報修人}（來源為定期保養時
     * 可留空，其餘必填）、{@code 處理人}（待認領以外的狀態必要）、{@code 說明}、{@code 照片張數}
     * （報修照片，預設 1）、{@code 急件}（「是」才算急件）、{@code 來源}（房務報修／櫃檯／定期保養，
     * 省略視為房務報修）、{@code 處理結果}（已修好／暫時處理／無法修復／不需處理，只用於已完修與
     * 已結案，預設已修好；無法修復時房況轉停用）、{@code 處理說明}、{@code 完修照片張數}（預設 1，
     * 不需處理時 0）、{@code 完修時間}、{@code 驗收人}（已結案且處理結果不是不需處理時必填）、
     * {@code 不通過原因}／{@code 不通過判定人}／{@code 不通過時間}（這張單曾被主管驗收不通過；
     * 有原因時判定人必填，時間預設：已完修是完修時間前 30 分鐘，其他狀態是現在前 30 分鐘）選填。
     * 中文對照一律走權威值對照表，不自己猜 enum。落地走
     * {@link BackendChannel#createRepairTicket(BackendChannel.RepairTicketSpec)}。
     */
    @假設("已有以下工務單：")
    public void hasExistingTickets(DataTable table) {
        LocalDate today = world.deviceNow().toLocalDate();
        for (Map<String, String> row : table.asMaps()) {
            String ticketNo = requireCell(row, "單號");
            String roomNo = requireCell(row, "房號");
            boolean publicArea = BackendChannel.PUBLIC_AREA_ROOM_NO.equals(roomNo);
            String categoryChinese = requireCell(row, "類別");
            String stateChinese = requireCell(row, "狀態");
            String sourceChinese = blankToNull(row.get("來源"));
            boolean scheduled = "定期保養".equals(sourceChinese);

            // 報修人多半是房務同事，工務視角的背景只列工務員工，不會有他；
            // 缺的時候就地補一位房務，不要求劇本為了一個報修人名稱多寫一列背景。
            // 定期保養的單沒有人報，報修人欄可以留空。
            String reporterName = blankToNull(row.get("報修人"));
            if (reporterName == null && !scheduled) {
                throw new IllegalArgumentException("工務單「" + ticketNo + "」缺報修人：只有來源為定期保養的單可以留空");
            }
            BackendChannel.CreatedStaff reporter = null;
            if (reporterName != null) {
                world.seedStaff(reporterName, "房務");
                reporter = world.staff(reporterName);
            }

            String assigneeName = blankToNull(row.get("處理人"));
            BackendChannel.CreatedStaff assignee =
                    assigneeName == null ? null : world.staff(assigneeName);
            boolean urgent = "是".equals(blankToNull(row.get("急件")));
            String resolutionChinese = blankToNull(row.get("處理結果"));
            String doneAtText = blankToNull(row.get("完修時間"));
            String completionPhotosText = blankToNull(row.get("完修照片張數"));
            int reportPhotos = optionalInt(row, "照片張數", 1);
            String stateCode = BackendChannel.repairTicketState(stateChinese);

            // 驗收人與不通過判定人是主管端的人，背景的員工表只列現場員工（工務／房務），
            // 不會有他們；缺的時候就地補一位主管，不要求劇本為了一個顯示名稱多寫一列背景。
            BackendChannel.CreatedStaff verifier = managerCell(row, "驗收人");
            String failedReason = blankToNull(row.get("不通過原因"));
            BackendChannel.CreatedStaff failedBy = managerCell(row, "不通過判定人");
            String failedAtText = blankToNull(row.get("不通過時間"));

            UUID taskId = world.backend().createRepairTicket(new BackendChannel.RepairTicketSpec(
                    ticketNo,
                    roomNo,
                    publicArea,
                    lookup(REPAIR_CATEGORY, categoryChinese, "工務單類別"),
                    stateCode,
                    reporter,
                    assignee,
                    blankToNull(row.get("說明")),
                    urgent,
                    scheduled ? "scheduled" : "reported",
                    resolutionChinese == null ? null : BackendChannel.resolutionType(resolutionChinese),
                    blankToNull(row.get("處理說明")),
                    completionPhotosText == null ? null : Integer.valueOf(completionPhotosText),
                    doneAtText == null ? null : parseMoment(doneAtText, today),
                    verifier,
                    failedReason,
                    failedBy,
                    failedAtText == null ? null : parseMoment(failedAtText, today)));
            world.backend().insertReportPhotos(taskId, (reporter != null ? reporter : assignee).id(), reportPhotos);

            if (sourceChinese != null && !scheduled) {
                world.backend().setTaskSource(
                        taskId, lookup(TICKET_SOURCE, sourceChinese, "工務單來源"));
            }
            // 房務報修的單在產品上一定是從那位房務自己的任務開出來的
            // （契約 CreateRepairTicketRequest 路徑①的 sourceTaskId），而報修人
            // 追蹤這張單的 App 路徑也是走那張任務的任務頁（同房工務單區）。
            // 測試資料只落工務單、不落來源任務的話，報修人在 App 上沒有任何
            // 入口進得了自己的單——那是劇本湊不出來的狀態，不是待驗的行為。
            // 公共區域沒有房務任務可當來源，跳過。
            if (reporter != null && "housekeeping".equals(reporter.dept()) && !publicArea) {
                seedSourceTaskFor(reporter, roomNo);
            }
        }
    }

    /** 主管身分的選填欄（驗收人、不通過判定人）：有值就確保有這位主管並回傳，留白回 null。 */
    private BackendChannel.CreatedStaff managerCell(Map<String, String> row, String column) {
        String name = blankToNull(row.get(column));
        if (name == null) {
            return null;
        }
        world.seedStaff(name, "主管");
        return world.staff(name);
    }

    /** 選填的整數欄；缺欄或留白用預設值。 */
    private static int optionalInt(Map<String, String> row, String column, int defaultValue) {
        String value = blankToNull(row.get(column));
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    /** 「今天 HH:mm」／「昨天 HH:mm」→ 時刻；日期以裝置生效的日期為準，與通知中心那一句同一套。 */
    private static OffsetDateTime parseMoment(String text, LocalDate today) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("時刻格式須為「今天 HH:mm」或「昨天 HH:mm」，實際=" + text);
        }
        LocalDate date = switch (parts[0]) {
            case "今天" -> today;
            case "昨天" -> today.minusDays(1);
            default -> throw new IllegalArgumentException("時刻的日期只收「今天」或「昨天」，實際=" + text);
        };
        return ZonedDateTime.of(date, LocalTime.parse(parts[1]), ZoneId.systemDefault()).toOffsetDateTime();
    }

    @假設("{string} 已將 {string} 降級為一般件，原因為 {string}")
    public void managerDowngradesTicket(String managerName, String ticketNo, String reason) {
        Map<String, Object> ticket = world.backend().repairTicketRow(ticketNo);
        world.backend().downgradeRepairTicketUrgency(
                (UUID) ticket.get("task_id"), world.staff(managerName), reason);
    }

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

    @假設("{string} 已在 App 打開工務待認領")
    public void alreadyOpenedEngineeringPool(String staffName) {
        world.ensureOnEngineeringPool(world.staff(staffName));
    }

    /**
     * 怎麼做：不經 App，直接以該員工身分對該工務單的任務呼叫 {@code POST /tasks/{id}/claim}。
     * 權威狀態落地：該單已是已接單，處理人是這位員工。
     *
     * <p>撞單要的是「列表載入之後、按下認領之前這段時間裡單被別人接走」，所以這一句一定
     * 排在「已在 App 打開工務待認領」之後，而且刻意不讓 App 重取列表：受測的就是員工手上
     * 那份已經過時的列表按下去會怎樣。反過來先把單建成已接單再開列表，那張單從第一刻就
     * 不在待認領池裡，認領鈕根本畫不出來，撞單這條路也就走不到。
     */
    @假設("{string} 已搶先認領 {string}")
    public void alreadyClaimedFirst(String staffName, String ticketNo) {
        Map<String, Object> ticket = world.backend().repairTicketRow(ticketNo);
        world.backend().postTaskAction(
                (UUID) ticket.get("task_id"), "claim", world.staff(staffName));
    }

    /**
     * 怎麼做：以該員工身分登入，從首頁、清單或通知進入該工務單的詳情頁，停在詳情頁。
     * 這是 Arrange，給「畫面已開著、世界在背後改變」或「接著按某個動作」的情境鋪底；
     * 開頁本身是受測動作時用同名的 When（{@code RepairWhenSteps}，不共用 annotation）。
     * 主管進入時從待驗收清單點該單。回寫：畫面停在工務單詳情。
     * 進法三個視角的分流在 {@link RepairContext#openTicketDetail}。
     */
    @假設("{string} 已在 App 打開 {string} 的工務單詳情")
    public void alreadyOnTicketDetail(String staffName, String ticketNo) {
        context.openTicketDetail(world.staff(staffName), ticketNo);
    }

    /**
     * 怎麼做：不經 App，以先驗收那位主管的身分呼叫 {@code verifyRepairTicket}
     * （{@code POST /repair-tickets/{ticketId}/verify}，{@code VerifyRepairTicketRequest{result: passed}}）；
     * 不觸發畫面刷新。權威狀態落地：後端該單已結案、有驗收人；畫面開著的那位看到的仍是
     * 已完修、等主管驗收的詳情。
     *
     * <p>這一句一定排在「已在 App 打開 … 的工務單詳情」之後：先驗收再開頁的話那張單從
     * 第一刻就不在待驗收清單裡，驗收鈕畫不出來，撞單這條路走不到。
     */
    @假設("{string} 剛被 {string} 驗收，{string} 的畫面還沒更新")
    public void justVerifiedByAnotherManager(String ticketNo, String otherManagerName, String managerName) {
        context.requireTicketDetailOpenAs(managerName);
        world.backend().verifyRepairTicket(
                context.ticketTaskId(ticketNo), world.staff(otherManagerName), "passed", null);
    }

    /**
     * 怎麼做：不經 App，以先送出那位主管的身分呼叫 {@code verifyRepairTicket}
     * （{@code VerifyRepairTicketRequest{result: failed, reason: 原因}}）；不觸發畫面刷新。
     * 權威狀態落地：後端該單已退回重修（待開始或工務待認領），有最近一次不通過的原因與判定人；
     * 畫面開著的那位看到的仍是已完修、等主管驗收的詳情。排在「已在 App 打開 … 的工務單詳情」
     * 之後，理由同「剛被另一位主管驗收」。
     */
    @假設("{string} 剛被 {string} 以原因 {string} 判定驗收不通過，{string} 的畫面還沒更新")
    public void justFailedByAnotherManager(
            String ticketNo, String otherManagerName, String reason, String managerName) {
        context.requireTicketDetailOpenAs(managerName);
        world.backend().verifyRepairTicket(
                context.ticketTaskId(ticketNo), world.staff(otherManagerName), "failed", reason);
    }

    /**
     * 怎麼做：把裝置時間與後端 {@code Clock} 設到今天、落在飯店所有班別時段之外的時刻
     * （同後端 {@code 工務單} 模組的同名句型）。後端半邊由
     * {@link BackendChannel#setBackendClockOutsideAllShifts()} 找出時刻並撥過去，裝置半邊注入
     * 測試後門的 {@code POST /clock}，兩邊同一刻。權威狀態落地：無資料變動；該工務人員不在請假中
     * （這句不建請假資料）。
     *
     * <p>工務沒有個人班表，「班已經結束」只看飯店班別時段，所以句型帶的工務名字只用來確認
     * 劇本裡有這個人，不寫任何屬於他的資料。
     */
    @假設("{string} 今天的班已經結束")
    public void engineerShiftHasEnded(String engineerName) {
        world.staff(engineerName);
        OffsetDateTime moment = world.backend().setBackendClockOutsideAllShifts();
        world.postBackdoorJson("/clock", "{\"isoTime\":\"" + moment + "\"}");
    }

    /**
     * 怎麼做：以該主管身分從待驗收清單打開該單，按不通過、寫下原因並送出，停在驗收結果卡
     * （{@code managerTicket.result}）不再操作。句型不帶原因，用 dsl 的預設原因。
     * 權威狀態落地：後端該單已退回重修（待開始或工務待認領）——結果卡出現之後回後端讀一次
     * 狀態，沒有離開已完修就是前提沒鋪起來，在這裡就說清楚。回寫：畫面停在驗收結果卡。
     */
    @假設("{string} 剛送出 {string} 的驗收不通過，結果卡停在畫面上")
    public void managerJustSubmittedVerifyFail(String managerName, String ticketNo) {
        Object state = world.backend().repairTicketRow(ticketNo).get("task_state");
        if (!"done".equals(String.valueOf(state))) {
            throw new IllegalStateException("「" + ticketNo + "」要是已完修才在待驗收清單上，實際狀態=" + state);
        }
        context.openTicketDetail(world.staff(managerName), ticketNo);
        world.tapTestId(RepairContext.MANAGER_FAIL_BUTTON, SCREEN_TIMEOUT);
        world.typeInto(RepairContext.MANAGER_FAIL_REASON_FIELD, SCREEN_TIMEOUT, DEFAULT_VERIFY_FAIL_REASON);
        world.tapTestId(RepairContext.MANAGER_FAIL_SUBMIT_BUTTON, SCREEN_TIMEOUT);
        world.waitForTestId(RepairContext.MANAGER_RESULT_CARD, SCREEN_TIMEOUT);
        Object after = world.backend().repairTicketRow(ticketNo).get("task_state");
        if (!"accepted".equals(String.valueOf(after)) && !"pool".equals(String.valueOf(after))) {
            throw new AssertionError("「" + ticketNo + "」驗收不通過送出後應退回重修（待開始或工務待認領），"
                    + "後端實際狀態=" + after);
        }
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

    private static String lookup(Map<String, String> table, String chinese, String what) {
        String value = table.get(chinese);
        if (value == null) {
            throw new IllegalArgumentException("找不到「" + chinese + "」在" + what
                    + "對照表中的權威值（合法值：" + table.keySet() + "）");
        }
        return value;
    }

    private static String requireCell(Map<String, String> row, String column) {
        String value = blankToNull(row.get(column));
        if (value == null) {
            throw new IllegalArgumentException("工務單 data table 缺必填欄「" + column + "」");
        }
        return value;
    }

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
