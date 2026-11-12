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

    private static final Map<String, String> REPAIR_CATEGORY = Map.of(
            "冷氣", "air_conditioning",
            "水電", "plumbing_electrical",
            "家具／設備", "furniture_equipment",
            "門鎖", "door_lock",
            "其他", "other");

    private static final Map<String, String> TICKET_SOURCE = Map.of(
            "房務報修", "staff",
            "櫃檯", "desk");

    private static final String DEFAULT_VERIFY_FAIL_REASON = "刷卡仍偶爾無反應";

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(8);

    private final AppiumWorld world;

    private final RepairContext context;

    public RepairGivenSteps(AppiumWorld world, RepairContext context) {
        this.world = world;
        this.context = context;
    }

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

    @假設("第 {int} 張照片的上傳會失敗")
    public void nthPhotoUploadFails(int photoIndex) {
        world.postBackdoorJson("/upload-fail", "{\"photoIndex\":" + photoIndex + "}");
    }

    @假設("^\"([^\"]+)\" 的手機有 (\\d+) 張待送出的 (\\S+) 冷氣工務單$")
    public void phoneHasPendingAcTickets(String staffName, int count, String roomNo) {
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

    @假設("恢復連線後的前 {int} 次送出會因網路不穩而重送")
    public void firstRetriesAfterReconnectAreFlaky(int retryCount) {
        world.postBackdoorJson("/drop-write-response",
                "{\"count\":" + retryCount + ",\"pathPrefix\":\"/repair-tickets\"}");
    }

    @假設("後端已有 {string} 報的 {string} 冷氣工務單 {string}，狀態為 {string}")
    public void backendHasExistingAcTicket(String reporter, String roomNo, String ticketNo, String status) {
        String stateCode = BackendChannel.repairTicketState(status);
        boolean needsAssignee = !"pool".equals(stateCode);
        world.backend().createRepairTicket(
                ticketNo, roomNo, REPAIR_CATEGORY.get("冷氣"), stateCode,
                world.staff(reporter), needsAssignee ? world.onlyEngineer() : null, null, false);
    }

    @假設("{string} 今天是 VIP 房，住客 {string} 入住")
    public void isVipRoomWithCheckin(String roomNo, String checkinTime) {
        world.backend().insertTodayReservation(roomNo, checkinTime, true);
    }

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
            if (reporter != null && "housekeeping".equals(reporter.dept()) && !publicArea) {
                seedSourceTaskFor(reporter, roomNo);
            }
        }
    }

    private BackendChannel.CreatedStaff managerCell(Map<String, String> row, String column) {
        String name = blankToNull(row.get(column));
        if (name == null) {
            return null;
        }
        world.seedStaff(name, "主管");
        return world.staff(name);
    }

    private static int optionalInt(Map<String, String> row, String column, int defaultValue) {
        String value = blankToNull(row.get(column));
        return value == null ? defaultValue : Integer.parseInt(value);
    }

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

    @假設("{string} 已搶先認領 {string}")
    public void alreadyClaimedFirst(String staffName, String ticketNo) {
        Map<String, Object> ticket = world.backend().repairTicketRow(ticketNo);
        world.backend().postTaskAction(
                (UUID) ticket.get("task_id"), "claim", world.staff(staffName));
    }

    @假設("{string} 已在 App 打開 {string} 的工務單詳情")
    public void alreadyOnTicketDetail(String staffName, String ticketNo) {
        context.openTicketDetail(world.staff(staffName), ticketNo);
    }

    @假設("{string} 剛被 {string} 驗收，{string} 的畫面還沒更新")
    public void justVerifiedByAnotherManager(String ticketNo, String otherManagerName, String managerName) {
        context.requireTicketDetailOpenAs(managerName);
        world.backend().verifyRepairTicket(
                context.ticketTaskId(ticketNo), world.staff(otherManagerName), "passed", null);
    }

    @假設("{string} 剛被 {string} 以原因 {string} 判定驗收不通過，{string} 的畫面還沒更新")
    public void justFailedByAnotherManager(
            String ticketNo, String otherManagerName, String reason, String managerName) {
        context.requireTicketDetailOpenAs(managerName);
        world.backend().verifyRepairTicket(
                context.ticketTaskId(ticketNo), world.staff(otherManagerName), "failed", reason);
    }

    @假設("{string} 今天的班已經結束")
    public void engineerShiftHasEnded(String engineerName) {
        world.staff(engineerName);
        OffsetDateTime moment = world.backend().setBackendClockOutsideAllShifts();
        world.postBackdoorJson("/clock", "{\"isoTime\":\"" + moment + "\"}");
    }

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

    private void createEngineeringTicketFor(
            BackendChannel.CreatedStaff engineer, String stateCode, boolean urgent) {
        String ticketNo = String.format("W-%04d", world.backend().allocateNextRepairTicketNo());
        world.backend().createRepairTicket(
                ticketNo, "ENG-" + ticketNo, "other", stateCode, engineer, engineer, null, urgent);
    }

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

    private void assertNoTicketInBackend(String why) {
        List<Map<String, Object>> rows = world.backend().queryRows(
                "SELECT ticket_no FROM repair_tickets WHERE tenant_id = ?",
                world.backend().ensureTenant());
        if (!rows.isEmpty()) {
            throw new AssertionError("後端不該有工務單（" + why + "），實際查到 " + rows.size() + " 張");
        }
    }
}
