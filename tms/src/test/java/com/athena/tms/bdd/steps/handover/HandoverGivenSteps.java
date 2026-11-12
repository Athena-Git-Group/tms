package com.athena.tms.bdd.steps.handover;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.HandoverFixtures;
import com.athena.tms.bdd.support.InspectionFixtures;
import com.athena.tms.bdd.support.JobTrigger;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.TaskFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.common.UuidV7;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class HandoverGivenSteps {

    private final ScenarioWorld world;
    private final TaskFixtures taskFixtures;
    private final RoomFixtures roomFixtures;
    private final HandoverFixtures handoverFixtures;
    private final InspectionFixtures inspectionFixtures;
    private final TenantFixtures tenantFixtures;
    private final MutableClock clock;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final JobTrigger jobTrigger;
    private final JdbcClient jdbcClient;

    public HandoverGivenSteps(
            ScenarioWorld world,
            TaskFixtures taskFixtures,
            RoomFixtures roomFixtures,
            HandoverFixtures handoverFixtures,
            InspectionFixtures inspectionFixtures,
            TenantFixtures tenantFixtures,
            MutableClock clock,
            BddApiClient apiClient,
            DbProbe dbProbe,
            JobTrigger jobTrigger,
            JdbcClient jdbcClient) {
        this.world = world;
        this.taskFixtures = taskFixtures;
        this.roomFixtures = roomFixtures;
        this.handoverFixtures = handoverFixtures;
        this.inspectionFixtures = inspectionFixtures;
        this.tenantFixtures = tenantFixtures;
        this.clock = clock;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.jobTrigger = jobTrigger;
        this.jdbcClient = jdbcClient;
    }

    @假設("{string} {word} 凍結的交班單有以下項目：")
    public void staffFreezesHandoverAt(String staffName, String hhmm, DataTable table) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        String dept = (String) dbProbe
                .queryOne("SELECT dept FROM staff_accounts WHERE id = ?", staffId)
                .orElseThrow(() -> new IllegalStateException("staff_accounts 查無此員工：" + staffId))
                .get("dept");
        String shift = LocalTime.parse(hhmm).isBefore(LocalTime.NOON) ? "morning" : "evening";
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        OffsetDateTime frozenAt = ZonedDateTime.of(today, LocalTime.parse(hhmm), clock.getZone()).toOffsetDateTime();

        List<HandoverFixtures.HandoverItem> items = table.asMaps(String.class, String.class).stream()
                .map(row -> new HandoverFixtures.HandoverItem(
                        requireCell(row, "對象"), requireCell(row, "內容"), staffName))
                .toList();

        handoverFixtures.freezeHandover(dept, shift, frozenAt, items);
    }

    @假設("櫃檯 {string} 為 {string} 留了備註 {string}")
    public void deskStaffLeavesNote(String staffName, String roomNo, String content) {
        UUID roomId = roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        Map<String, Object> body = Map.of("roomId", roomId.toString(), "content", content);
        apiClient.post("/desk-notes", BddApiClient.Auth.staff(world.jwt(staffName)), body);
    }

    @假設("{string} 已接手 {string}，原負責 {string} {int} 分鐘前留下交代 {string}")
    public void staffTakesOverWithHandoffNote(
            String staffName, String roomNo, String priorStaffName, int minutesAgo, String note) {
        UUID priorStaffId = UUID.fromString(world.staffId(priorStaffName));
        roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));

        UUID taskId = taskFixtures
                .builder()
                .roomNo(roomNo)
                .title("退房清潔")
                .type("cleaning")
                .dept("housekeeping")
                .source("desk")
                .estMin(35)
                .state("accepted")
                .assignee(priorStaffId)
                .create();

        Instant original = clock.instant();
        clock.setInstant(original.minusSeconds(minutesAgo * 60L));
        apiClient.post(
                "/tasks/" + taskId + "/requeue", BddApiClient.Auth.staff(world.jwt(priorStaffName)),
                Map.of("note", note));
        clock.setInstant(original);
        apiClient.post("/tasks/" + taskId + "/claim", BddApiClient.Auth.staff(world.jwt(staffName)), null);

        world.rememberTask(roomNo, taskId.toString());
    }

    @假設("{string} 已開始 {string}")
    public void staffAlreadyStartedTask(String staffName, String taskKey) {
        UUID taskId = UUID.fromString(world.taskId(taskKey));
        apiClient.post("/tasks/" + taskId + "/start", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }

    @假設("{string} 的退房清潔由 {string} 清完，{string} 判定 {string} 不合格")
    public void checkoutCleaningFailedInspection(
            String roomNo, String priorStaffName, String managerName, String itemLabel) {
        UUID priorStaffId = UUID.fromString(world.staffId(priorStaffName));
        roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));

        UUID taskId = taskFixtures
                .builder()
                .roomNo(roomNo)
                .title("退房清潔")
                .type("cleaning")
                .dept("housekeeping")
                .source("desk")
                .estMin(35)
                .state("done")
                .assignee(priorStaffId)
                .create();

        long tenantId = tenantFixtures.testTenantId();
        List<Map<String, Object>> items = buildInspectionItemsFailingOne(tenantId, itemLabel);

        inspectionFixtures.submit(world, managerName, taskId, items);

        world.rememberTask(roomNo, taskId.toString());
    }

    @假設("{string} 已下班，{string} 改由 {string} 接手")
    public void staffOffShiftRoomTakenOver(String staffName, String roomNo, String successorName) {
        UUID staffId = UUID.fromString(world.staffId(staffName));

        String shiftEndHHmm = (String) dbProbe
                .queryOne(
                        """
                        SELECT cp.shift
                          FROM plan_rooms pr
                          JOIN cleaning_plans cp ON cp.id = pr.plan_id
                         WHERE pr.staff_id = ?
                         ORDER BY cp.generated_at DESC
                         LIMIT 1
                        """,
                        staffId)
                .orElseThrow(() -> new IllegalStateException(
                        "查無「" + staffName + "」的班次結束時間，請先在「飯店有以下員工：」給「班次結束」欄"))
                .get("shift");

        LocalTime afterShiftEnd = LocalTime.parse(shiftEndHHmm).plusMinutes(1);
        clock.setTodayAt(afterShiftEnd.format(DateTimeFormatter.ofPattern("HH:mm")));

        jobTrigger.run("shift-end-scan");

        UUID taskId = UUID.fromString(world.taskId(roomNo));
        ScenarioWorld.ApiResponse claimed = apiClient.post(
                "/tasks/" + taskId + "/claim", BddApiClient.Auth.staff(world.jwt(successorName)), null);
        if (claimed.status() / 100 != 2) {
            throw new IllegalStateException("「" + successorName + "」接手「" + roomNo + "」失敗：status="
                    + claimed.status() + "，body=" + claimed.body());
        }
    }

    private List<Map<String, Object>> buildInspectionItemsFailingOne(long tenantId, String failLabel) {
        List<Map<String, Object>> existing = jdbcClient
                .sql(
                        "SELECT item_key, label FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection' AND active = true ORDER BY sort_order")
                .params(tenantId)
                .query()
                .listOfRows();

        String failKey = existing.stream()
                .filter(row -> failLabel.equals(row.get("label")))
                .map(row -> (String) row.get("item_key"))
                .findFirst()
                .orElseGet(() -> insertAdHocInspectionItem(tenantId, failLabel));

        List<Map<String, Object>> items = new ArrayList<>();
        boolean matched = false;
        for (Map<String, Object> row : existing) {
            String key = (String) row.get("item_key");
            boolean pass = !key.equals(failKey);
            matched = matched || !pass;
            items.add(Map.of("key", key, "pass", pass));
        }
        if (!matched) {
            items.add(Map.of("key", failKey, "pass", false));
        }
        return items;
    }

    private String insertAdHocInspectionItem(long tenantId, String label) {
        String itemKey = "adhoc_" + UuidV7.generate().toString().replace("-", "").substring(0, 8);
        jdbcClient
                .sql(
                        """
                        INSERT INTO sop_items (id, tenant_id, applies_to, item_key, label, sort_order, active, created_at)
                        VALUES (?, ?, 'inspection', ?, ?, 999, true, ?)
                        """)
                .params(UuidV7.generate(), tenantId, itemKey, label, OffsetDateTime.now())
                .update();
        return itemKey;
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v;
    }
}
