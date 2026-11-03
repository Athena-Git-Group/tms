package com.athena.tms.bdd.steps.repair;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.OutboxAwait;
import com.athena.tms.bdd.support.PushPayloadView;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.service.JwtService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * 工務單模組的 Then（dsl.md Then 句型區）。「{string} 的狀態為 {string}」等任務通用句型
 * 已由 {@code steps.common.CommonThenSteps} 定義（會用 {@code Terms.taskState} 查狀態，
 * 查不到工務單專屬用語時噴 IllegalArgumentException——那是合法的 RED 訊號，不在本類別重複定義）。
 */
public class RepairThenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final OutboxAwait outboxAwait;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;
    private final JwtService jwtService;

    public RepairThenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            OutboxAwait outboxAwait,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures,
            JwtService jwtService) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.outboxAwait = outboxAwait;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
        this.jwtService = jwtService;
    }

    @那麼("系統建立工務單 {string}，房號 {string}、報修人 {string}")
    public void systemCreatesRepairTicket(String ticketNo, String roomNo, String staffName) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        String actualTicketNo = resp.json() != null ? resp.json().path("ticketNo").asText(null) : null;
        if (!ticketNo.equals(actualTicketNo)) {
            throw new AssertionError(
                    "預期 RepairTicket.ticketNo=" + ticketNo + "，實際 status=" + resp.status()
                            + " ticketNo=" + actualTicketNo + "，body=" + resp.body());
        }

        long tenantId = tenantFixtures.testTenantId();
        Map<String, Object> ticketRow = dbProbe
                .queryOne("SELECT * FROM repair_tickets WHERE tenant_id = ? AND ticket_no = ?", tenantId, ticketNo)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無單號 " + ticketNo));

        Map<String, Object> roomRow = dbProbe
                .queryOne("SELECT room_no FROM rooms WHERE id = ?", ticketRow.get("room_id"))
                .orElseThrow(() -> new AssertionError("rooms 查無 id=" + ticketRow.get("room_id")));
        check("repair_tickets.room_no", roomNo, roomRow.get("room_no"));

        UUID expectedReporter = UUID.fromString(world.staffId(staffName));
        if (!sameUuid(ticketRow.get("reporter_id"), expectedReporter)) {
            throw new AssertionError(
                    "repair_tickets.reporter_id 預期 " + expectedReporter + "，實際 " + ticketRow.get("reporter_id"));
        }

        if (world.hasTask(roomNo)) {
            UUID expectedSourceTaskId = UUID.fromString(world.taskId(roomNo));
            if (!sameUuid(ticketRow.get("source_task_id"), expectedSourceTaskId)) {
                throw new AssertionError("repair_tickets.source_task_id 預期 " + expectedSourceTaskId + "，實際 "
                        + ticketRow.get("source_task_id"));
            }
        }

        UUID taskId = toUuid(ticketRow.get("task_id"));
        Map<String, Object> taskRow =
                dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此工務單對應任務 id=" + taskId));
        check("tasks.type", "repair", taskRow.get("type"));
        check("tasks.dept", "engineering", taskRow.get("dept"));
        check("tasks.source", "staff", taskRow.get("source"));
    }

    @那麼("系統沒有建立工務單")
    public void noRepairTicketCreated() {
        RepairCountGuard.Snapshot before = RepairCountGuard.current();
        long tenantId = tenantFixtures.testTenantId();
        long ticketCountAfter = dbProbe.count("SELECT count(*) FROM repair_tickets WHERE tenant_id = ?", tenantId);
        long taskCountAfter =
                dbProbe.count("SELECT count(*) FROM tasks WHERE tenant_id = ? AND type = 'repair'", tenantId);
        if (ticketCountAfter != before.repairTicketCount() || taskCountAfter != before.repairTaskCount()) {
            throw new AssertionError(
                    "預期沒有新建工務單，但 repair_tickets " + before.repairTicketCount() + "→" + ticketCountAfter
                            + "，type=repair tasks " + before.repairTaskCount() + "→" + taskCountAfter);
        }
    }

    @那麼("工務待接池包含 {string}")
    public void poolContainsTicket(String ticketNo) {
        ScenarioWorld.ApiResponse resp = apiClient.get("/pool?category=repair", BddApiClient.Auth.staff(engineeringStaffJwt()));
        if (!containsTicketNo(resp, ticketNo)) {
            throw new AssertionError(
                    "預期工務待接池包含 " + ticketNo + "，實際 status=" + resp.status() + "，body=" + resp.body());
        }

        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        Map<String, Object> row = dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此工務單"));
        check("tasks.state", "pool", row.get("state"));

        boolean hasTriagedOrReview = dbProbe.eventsOf(taskId).stream()
                .anyMatch(e -> "triaged".equals(e.get("type")) || "manager_review".equals(e.get("type")));
        if (hasTriagedOrReview) {
            throw new AssertionError("工務單 " + ticketNo + " 不應該有 triaged 或主管審核事件");
        }
    }

    @那麼("工務待接池不包含 {string}")
    public void poolDoesNotContainTicket(String ticketNo) {
        ScenarioWorld.ApiResponse resp = apiClient.get("/pool?category=repair", BddApiClient.Auth.staff(engineeringStaffJwt()));
        if (containsTicketNo(resp, ticketNo)) {
            throw new AssertionError("預期工務待接池不包含 " + ticketNo + "，但找到了（body=" + resp.body() + "）");
        }
    }

    // dsl.md「工務待接池順序為 {單號清單}」在 Gherkin 裡不加引號（以「、」分隔的裸文字），
    // {string} 無法匹配沒有引號的文字；用 ^...$ 讓 Cucumber 當成 Regular Expression 編譯。
    @那麼("^工務待接池順序為 (.+)$")
    public void poolOrderIs(String rawList) {
        List<String> expected = Arrays.asList(rawList.split("、"));
        ScenarioWorld.ApiResponse resp = apiClient.get("/pool?category=repair", BddApiClient.Auth.staff(engineeringStaffJwt()));
        List<String> actual = new ArrayList<>();
        if (resp.json() != null) {
            for (JsonNode item : resp.json().path("items")) {
                actual.add(item.path("ticketNo").asText(null));
            }
        }
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    "工務待接池順序預期 " + expected + "，實際 " + actual + "（status=" + resp.status() + "）");
        }
    }

    @那麼("系統建議設為急件，提示 {string}")
    public void systemSuggestsUrgent(String hint) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode json = resp.json();
        boolean suggested = json != null && json.path("urgentSuggestion").path("suggested").asBoolean(false);
        String message = json != null ? json.path("urgentSuggestion").path("message").asText(null) : null;
        if (!suggested || !hint.equals(message)) {
            throw new AssertionError(
                    "預期 urgentSuggestion.suggested=true, message=" + hint + "，實際 status=" + resp.status()
                            + " suggested=" + suggested + " message=" + message);
        }
        assertNoRepairTicketCreatedSinceGuard();
    }

    @那麼("以下員工都收到 {string} 的急件推播：")
    public void staffAllReceivedUrgentPush(String ticketNo, DataTable table) {
        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String name = requireCell(row, "員工");
            UUID staffId = UUID.fromString(world.staffId(name));
            outboxAwait.waitForPush(
                    v -> staffId.equals(v.recipientStaffId())
                            && "repair_urgent".equals(v.event())
                            && v.target() != null
                            && taskId.equals(v.target().highlightTaskId()),
                    name + " 收到 " + ticketNo + " 的急件推播（event=repair_urgent, highlightTaskId=" + taskId + "）");
        }
    }

    @那麼("系統提示同房已有未結案的工務單 {string}，狀態為 {string}")
    public void systemShowsDuplicateTicket(String ticketNo, String stateChinese) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode json = resp.json();
        String actualTicketNo = json != null ? json.path("duplicate").path("ticketNo").asText(null) : null;
        String actualState = json != null ? json.path("duplicate").path("state").asText(null) : null;
        String expectedState = repairStatus(stateChinese);
        if (!ticketNo.equals(actualTicketNo) || !expectedState.equals(actualState)) {
            throw new AssertionError(
                    "預期 duplicate.ticketNo=" + ticketNo + ", state=" + expectedState + "，實際 status=" + resp.status()
                            + " ticketNo=" + actualTicketNo + " state=" + actualState);
        }
        assertNoRepairTicketCreatedSinceGuard();
    }

    @那麼("{string} 共有 {int} 張照片")
    public void ticketHasPhotoCount(String ticketNo, int expectedCount) {
        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        long actualCount = dbProbe.count(
                "SELECT count(*) FROM repair_photos WHERE ticket_task_id = ? AND purpose = 'report'", taskId);
        if (actualCount != expectedCount) {
            throw new AssertionError(
                    "repair_photos(purpose=report) 預期 " + expectedCount + " 張，實際 " + actualCount + " 張");
        }

        Map<String, Object> row = dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此工務單"));
        Object assigneeId = row.get("assignee_id");
        if (assigneeId != null) {
            String jwt = mintJwtFor(toUuid(assigneeId));
            ScenarioWorld.ApiResponse resp = apiClient.get("/repair-tickets/" + taskId, BddApiClient.Auth.staff(jwt));
            int photoCount = resp.json() != null ? resp.json().path("photoCount").asInt(-1) : -1;
            if (photoCount != expectedCount) {
                throw new AssertionError("readRepairTicket.photoCount 預期 " + expectedCount + "，實際 " + photoCount
                        + "（status=" + resp.status() + "）");
            }
        }
    }

    @那麼("{string} 未結案的 {string} 工務單只有 {int} 張")
    public void unresolvedTicketsOfCategoryCount(String roomNo, String categoryChinese, int expectedCount) {
        long tenantId = tenantFixtures.testTenantId();
        UUID roomId = roomFixtures.roomId(roomNo);
        String category = Terms.repairCategory(categoryChinese);
        long actualCount = dbProbe.count(
                """
                SELECT count(*) FROM repair_tickets rt
                  JOIN tasks t ON t.id = rt.task_id AND t.tenant_id = rt.tenant_id
                 WHERE rt.tenant_id = ? AND rt.room_id = ? AND rt.category = ?
                   AND t.state NOT IN ('done', 'cancelled')
                """,
                tenantId, roomId, category);
        if (actualCount != expectedCount) {
            throw new AssertionError(
                    "房號 " + roomNo + " 未結案「" + categoryChinese + "」工務單預期 " + expectedCount + " 張，實際 "
                            + actualCount + " 張");
        }

        ScenarioWorld.ApiResponse resp = apiClient.get("/pool?category=repair", BddApiClient.Auth.staff(engineeringStaffJwt()));
        long poolCount = 0;
        if (resp.json() != null) {
            for (JsonNode item : resp.json().path("items")) {
                if (roomNo.equals(item.path("roomNo").asText(null)) && category.equals(item.path("category").asText(null))) {
                    poolCount++;
                }
            }
        }
        if (poolCount != expectedCount) {
            throw new AssertionError(
                    "工務待接池裡 " + roomNo + " 的「" + categoryChinese + "」單預期 " + expectedCount + " 張，實際 " + poolCount
                            + " 張（status=" + resp.status() + "）");
        }
    }

    @那麼("{string} 的處理說明為 {string}")
    public void resolutionNoteIs(String ticketNo, String note) {
        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        Map<String, Object> row = dbProbe
                .queryOne("SELECT resolution_note, reporter_id FROM repair_tickets WHERE task_id = ?", taskId)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無此工務單 id=" + taskId));
        check("repair_tickets.resolution_note", note, row.get("resolution_note"));

        String jwt = mintJwtFor(toUuid(row.get("reporter_id")));
        ScenarioWorld.ApiResponse resp = apiClient.get("/repair-tickets/" + taskId, BddApiClient.Auth.staff(jwt));
        String actual = resp.json() != null ? resp.json().path("resolutionNote").asText(null) : null;
        if (!note.equals(actual)) {
            throw new AssertionError(
                    "readRepairTicket.resolutionNote 預期 " + note + "，實際 " + actual + "（status=" + resp.status() + "）");
        }
    }

    private void assertNoRepairTicketCreatedSinceGuard() {
        RepairCountGuard.Snapshot before = RepairCountGuard.current();
        long tenantId = tenantFixtures.testTenantId();
        long ticketCountAfter = dbProbe.count("SELECT count(*) FROM repair_tickets WHERE tenant_id = ?", tenantId);
        long taskCountAfter =
                dbProbe.count("SELECT count(*) FROM tasks WHERE tenant_id = ? AND type = 'repair'", tenantId);
        if (ticketCountAfter != before.repairTicketCount() || taskCountAfter != before.repairTaskCount()) {
            throw new AssertionError(
                    "預期沒有新建工務單，但 repair_tickets " + before.repairTicketCount() + "→" + ticketCountAfter
                            + "，type=repair tasks " + before.repairTaskCount() + "→" + taskCountAfter);
        }
    }

    private boolean containsTicketNo(ScenarioWorld.ApiResponse resp, String ticketNo) {
        if (resp.json() == null) {
            return false;
        }
        for (JsonNode item : resp.json().path("items")) {
            if (ticketNo.equals(item.path("ticketNo").asText(null))) {
                return true;
            }
        }
        return false;
    }

    /** 待接池查詢需要「工務員工」的身分，dsl.md 這幾句本身沒有指名是哪一位，就取任一在職工務員工。 */
    private String engineeringStaffJwt() {
        long tenantId = tenantFixtures.testTenantId();
        UUID staffId = dbProbe
                .queryOne(
                        "SELECT id FROM staff_accounts WHERE tenant_id = ? AND dept = 'engineering' AND active = true LIMIT 1",
                        tenantId)
                .map(row -> toUuid(row.get("id")))
                .orElseThrow(() -> new IllegalStateException("找不到在職的工務員工，請先在 Given 建立"));
        return jwtService.issue(tenantId, staffId, "engineering", "engineering").token();
    }

    private String mintJwtFor(UUID staffId) {
        Map<String, Object> staff = dbProbe
                .queryOne("SELECT role, dept FROM staff_accounts WHERE id = ?", staffId)
                .orElseThrow(() -> new IllegalStateException("staff_accounts 找不到 id=" + staffId));
        return jwtService
                .issue(tenantFixtures.testTenantId(), staffId, (String) staff.get("role"), (String) staff.get("dept"))
                .token();
    }

    private static String repairStatus(String chinese) {
        if ("待接".equals(chinese)) {
            return "pool";
        }
        return Terms.repairTicketState(chinese);
    }

    private static void check(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual);
        }
    }

    private static boolean sameUuid(Object dbValue, UUID expected) {
        if (dbValue == null || expected == null) {
            return dbValue == expected;
        }
        return expected.toString().equalsIgnoreCase(String.valueOf(dbValue));
    }

    private static UUID toUuid(Object v) {
        if (v instanceof UUID u) {
            return u;
        }
        if (v instanceof String s) {
            return UUID.fromString(s);
        }
        throw new IllegalStateException("預期 UUID 型別，實際 " + (v == null ? "null" : v.getClass()));
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v;
    }
}
