package com.athena.tms.bdd.steps.repair;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.OutboxAwait;
import com.athena.tms.bdd.support.RepairCountGuard;
import com.athena.tms.bdd.support.PushPayloadView;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.StaffFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.service.JwtService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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
    private final RepairCountGuard repairCountGuard;
    private final OutboxAwait outboxAwait;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;
    private final JwtService jwtService;
    private final MutableClock clock;
    private final LastOperationGuard lastOperationGuard;
    private final StaffFixtures staffFixtures;

    public RepairThenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            RepairCountGuard repairCountGuard,
            OutboxAwait outboxAwait,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures,
            JwtService jwtService,
            MutableClock clock,
            LastOperationGuard lastOperationGuard,
            StaffFixtures staffFixtures) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.repairCountGuard = repairCountGuard;
        this.outboxAwait = outboxAwait;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
        this.jwtService = jwtService;
        this.clock = clock;
        this.lastOperationGuard = lastOperationGuard;
        this.staffFixtures = staffFixtures;
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
        // 來源由開單者部門決定（truth：櫃檯交辦標 desk，房務報修與工務自開標 staff），
        // 不能寫死 staff——本句被房務報修、工務自開、櫃檯交辦三種場景共用。
        String reporterDept = dbProbe
                .queryOne("SELECT dept FROM staff_accounts WHERE id = ?", expectedReporter)
                .map(row -> String.valueOf(row.get("dept")))
                .orElseThrow(() -> new AssertionError("staff_accounts 查無報修人 " + expectedReporter));
        String expectedSource = "frontdesk".equals(reporterDept) ? "desk" : "staff";
        check("tasks.source", expectedSource, taskRow.get("source"));
    }

    @那麼("系統判定 {string} 為急件，理由 {string}")
    public void systemJudgesTicketAsUrgent(String ticketNo, String reason) {
        long tenantId = tenantFixtures.testTenantId();
        Map<String, Object> ticketRow = dbProbe
                .queryOne(
                        "SELECT is_urgent, urgent_rule FROM repair_tickets WHERE tenant_id = ? AND ticket_no = ?",
                        tenantId, ticketNo)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無單號 " + ticketNo));
        boolean isUrgent = Boolean.TRUE.equals(ticketRow.get("is_urgent"));
        if (!isUrgent) {
            throw new AssertionError("repair_tickets.is_urgent 預期 true，實際 false（單號 " + ticketNo + "）");
        }
        check("repair_tickets.urgent_rule", reason, ticketRow.get("urgent_rule"));

        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode json = resp.json();
        boolean suggested = json != null && json.path("urgentJudgement").path("suggested").asBoolean(false);
        String rule = json != null ? json.path("urgentJudgement").path("rule").asText(null) : null;
        if (!suggested || !reason.equals(rule)) {
            throw new AssertionError(
                    "預期 urgentJudgement.suggested=true, rule=" + reason + "，實際 status=" + resp.status()
                            + " suggested=" + suggested + " rule=" + rule + "，body=" + resp.body());
        }
    }

    @那麼("系統判定 {string} 為一般件")
    public void systemJudgesTicketAsNormal(String ticketNo) {
        long tenantId = tenantFixtures.testTenantId();
        Map<String, Object> ticketRow = dbProbe
                .queryOne("SELECT is_urgent FROM repair_tickets WHERE tenant_id = ? AND ticket_no = ?", tenantId, ticketNo)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無單號 " + ticketNo));
        boolean isUrgent = Boolean.TRUE.equals(ticketRow.get("is_urgent"));
        if (isUrgent) {
            throw new AssertionError("repair_tickets.is_urgent 預期 false，實際 true（單號 " + ticketNo + "）");
        }

        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode json = resp.json();
        boolean suggested = json != null && json.path("urgentJudgement").path("suggested").asBoolean(true);
        if (suggested) {
            throw new AssertionError(
                    "預期 urgentJudgement.suggested=false，實際 status=" + resp.status() + " suggested=" + suggested
                            + "，body=" + resp.body());
        }
    }

    @那麼("系統沒有建立工務單")
    public void noRepairTicketCreated() {
        RepairCountGuard.Snapshot before = repairCountGuard.current();
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

    @那麼("{string} 的來源為 {string}")
    public void ticketSourceIs(String ticketNo, String sourceChinese) {
        String expectedTaskSource = repairTicketSource(sourceChinese);
        String expectedPoolSource = repairTicketPoolSource(sourceChinese);

        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        Map<String, Object> row = dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此工務單"));
        check("tasks.source", expectedTaskSource, row.get("source"));

        ScenarioWorld.ApiResponse resp = apiClient.get("/pool?category=repair", BddApiClient.Auth.staff(engineeringStaffJwt()));
        String actualPoolSource = null;
        if (resp.json() != null) {
            for (JsonNode item : resp.json().path("items")) {
                if (ticketNo.equals(item.path("ticketNo").asText(null))) {
                    actualPoolSource = item.path("source").asText(null);
                    break;
                }
            }
        }
        // 已認領（非 pool）的單不會出現在 listDeptPool，PoolItem.source 比對只在找得到該單時才做；
        // 找不到不代表失敗，tasks.source 的權威狀態已在上面驗過。
        if (actualPoolSource != null && !expectedPoolSource.equals(actualPoolSource)) {
            throw new AssertionError(
                    "預期 PoolItem.source=" + expectedPoolSource + "，實際 " + actualPoolSource + "（status="
                            + resp.status() + "）");
        }
    }

    @那麼("工務待認領包含 {string}")
    public void poolContainsTicket(String ticketNo) {
        ScenarioWorld.ApiResponse resp = apiClient.get("/pool?category=repair", BddApiClient.Auth.staff(engineeringStaffJwt()));
        if (!containsTicketNo(resp, ticketNo)) {
            throw new AssertionError(
                    "預期工務待認領包含 " + ticketNo + "，實際 status=" + resp.status() + "，body=" + resp.body());
        }

        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        Map<String, Object> row = dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此工務單"));
        check("tasks.state", "pool", row.get("state"));

        if (countEvents(taskId, "triaged") > 0) {
            throw new AssertionError("工務單 " + ticketNo + " 不應該有 triaged 事件");
        }
    }

    @那麼("工務待認領不包含 {string}")
    public void poolDoesNotContainTicket(String ticketNo) {
        ScenarioWorld.ApiResponse resp = apiClient.get("/pool?category=repair", BddApiClient.Auth.staff(engineeringStaffJwt()));
        // 純負向斷言會假綠：listDeptPool 回 403／500 或 items 空陣列也「不包含」。
        // 先斷言這次讀取本身成功，再驗不包含。
        if (resp.status() != 200 || resp.json() == null) {
            throw new AssertionError(
                    "listDeptPool 預期 200，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        if (containsTicketNo(resp, ticketNo)) {
            throw new AssertionError("預期工務待認領不包含 " + ticketNo + "，但找到了（body=" + resp.body() + "）");
        }
    }

    // dsl.md「工務待認領順序為 {單號清單}」在 Gherkin 裡不加引號（以「、」分隔的裸文字），
    // {string} 無法匹配沒有引號的文字；用 ^...$ 讓 Cucumber 當成 Regular Expression 編譯。
    @那麼("^工務待認領順序為 (.+)$")
    public void poolOrderIs(String rawList) {
        List<String> expected = Arrays.asList(rawList.split("、"));
        // 這句被兩種前置 When 用到：①「只看…的工務待認領」已經帶著 urgent／source 篩選查過
        // listDeptPool，這裡要沿用那次的回應，不能另外重查一次不帶篩選的池，否則篩選條件
        // 就被洗掉；②開單類 When（例如「開立以下工務單」）沒有查過池，這裡才需要重新查。
        // 用「上次回應有沒有 items 欄位」區分這兩種前置，而不是看 Gherkin 文字本身——
        // DeptPool 是唯一會有這個欄位的回應形狀。
        ScenarioWorld.ApiResponse lastResp = world.lastResponse();
        boolean lastResponseIsPoolListing = lastResp.json() != null && lastResp.json().has("items");
        ScenarioWorld.ApiResponse resp = lastResponseIsPoolListing
                ? lastResp
                : apiClient.get("/pool?category=repair", BddApiClient.Auth.staff(engineeringStaffJwt()));
        List<String> actual = new ArrayList<>();
        if (resp.json() != null) {
            for (JsonNode item : resp.json().path("items")) {
                actual.add(item.path("ticketNo").asText(null));
            }
        }
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    "工務待認領順序預期 " + expected + "，實際 " + actual + "（status=" + resp.status() + "）");
        }
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
                   AND t.state NOT IN ('done', 'closed', 'cancelled')
                """,
                tenantId, roomId, category);
        if (actualCount != expectedCount) {
            throw new AssertionError(
                    "房號 " + roomNo + " 未結案「" + categoryChinese + "」工務單預期 " + expectedCount + " 張，實際 "
                            + actualCount + " 張");
        }

        // 跨視角：待認領只收 state='pool' 的單，所以池中應出現的是未結案單裡仍在 pool 的那些；
        // 比對鍵用 ticketNo（池 item 的 category 是 PoolCategory（repair），不是工務類別）。
        List<String> expectedPoolTicketNos = dbProbe.queryMaps(
                """
                SELECT rt.ticket_no FROM repair_tickets rt
                  JOIN tasks t ON t.id = rt.task_id AND t.tenant_id = rt.tenant_id
                 WHERE rt.tenant_id = ? AND rt.room_id = ? AND rt.category = ?
                   AND t.state = 'pool'
                """,
                tenantId, roomId, category)
                .stream().map(r -> String.valueOf(r.get("ticket_no"))).sorted().toList();
        ScenarioWorld.ApiResponse resp = apiClient.get("/pool?category=repair", BddApiClient.Auth.staff(engineeringStaffJwt()));
        List<String> actualPoolTicketNos = new java.util.ArrayList<>();
        if (resp.json() != null) {
            for (JsonNode item : resp.json().path("items")) {
                String ticketNo = item.path("ticketNo").asText(null);
                if (roomNo.equals(item.path("roomNo").asText(null)) && ticketNo != null
                        && expectedPoolTicketNos.contains(ticketNo)) {
                    actualPoolTicketNos.add(ticketNo);
                }
            }
        }
        actualPoolTicketNos.sort(String::compareTo);
        if (!actualPoolTicketNos.equals(expectedPoolTicketNos)) {
            throw new AssertionError(
                    "工務待認領裡 " + roomNo + " 的「" + categoryChinese + "」單預期 " + expectedPoolTicketNos + "，實際 "
                            + actualPoolTicketNos + "（status=" + resp.status() + "）");
        }
    }

    @那麼("{string} 不是急件")
    public void ticketIsNotUrgent(String ticketNo) {
        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        Map<String, Object> row = dbProbe
                .queryOne("SELECT is_urgent, reporter_id FROM repair_tickets WHERE task_id = ?", taskId)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無此工務單 id=" + taskId));
        check("repair_tickets.is_urgent", Boolean.FALSE, row.get("is_urgent"));

        String jwt = mintJwtFor(toUuid(row.get("reporter_id")));
        ScenarioWorld.ApiResponse resp = apiClient.get("/repair-tickets/" + taskId, BddApiClient.Auth.staff(jwt));
        boolean actualIsUrgent = resp.json() != null && resp.json().path("isUrgent").asBoolean(true);
        if (actualIsUrgent) {
            throw new AssertionError(
                    "readRepairTicket.isUrgent 預期 false，實際 " + actualIsUrgent + "（status=" + resp.status() + "）");
        }
    }

    @那麼("{string} 的急件降級原因為 {string}")
    public void urgentDowngradeReasonIs(String ticketNo, String reason) {
        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        Map<String, Object> row = dbProbe
                .queryOne("SELECT urgent_downgrade_reason, reporter_id FROM repair_tickets WHERE task_id = ?", taskId)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無此工務單 id=" + taskId));
        check("repair_tickets.urgent_downgrade_reason", reason, row.get("urgent_downgrade_reason"));

        String jwt = mintJwtFor(toUuid(row.get("reporter_id")));
        ScenarioWorld.ApiResponse resp = apiClient.get("/repair-tickets/" + taskId, BddApiClient.Auth.staff(jwt));
        String actual = resp.json() != null ? resp.json().path("urgentDowngradeReason").asText(null) : null;
        if (!reason.equals(actual)) {
            throw new AssertionError("readRepairTicket.urgentDowngradeReason 預期 " + reason + "，實際 " + actual
                    + "（status=" + resp.status() + "）");
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

    /**
     * dsl.md「{單號} 已由 {主管} 驗收」：權威狀態 tasks.state=closed、repair_tickets.verified_by 是該主管、
     * verified_at 已寫入、task_events 有一筆 repair_verified；再讀確認 readRepairTicket 的
     * state、verifiedBy、verifiedAt 一致。
     */
    @那麼("{string} 已由 {string} 驗收")
    public void ticketVerifiedBy(String ticketNo, String managerName) {
        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        UUID expectedManager = UUID.fromString(world.staffId(managerName));

        Map<String, Object> row = dbProbe
                .queryOne("SELECT verified_by, verified_at FROM repair_tickets WHERE task_id = ?", taskId)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無此工務單 task_id=" + taskId));
        if (!sameUuid(row.get("verified_by"), expectedManager)) {
            throw new AssertionError("repair_tickets.verified_by 預期 " + expectedManager + "（" + managerName
                    + "），實際 " + row.get("verified_by"));
        }
        if (row.get("verified_at") == null) {
            throw new AssertionError("repair_tickets.verified_at 預期已寫入，實際 NULL（單號 " + ticketNo + "）");
        }

        Map<String, Object> taskRow =
                dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此工務單 id=" + taskId));
        check("tasks.state", "closed", taskRow.get("state"));
        check("task_events 中 " + ticketNo + " 的 repair_verified 事件筆數", 1L, countEvents(taskId, "repair_verified"));

        ScenarioWorld.ApiResponse resp = apiClient.get("/repair-tickets/" + taskId, BddApiClient.Auth.staff(engineeringStaffJwt()));
        JsonNode json = resp.json();
        String actualState = json != null ? json.path("state").asText(null) : null;
        String actualVerifiedBy = json != null ? json.path("verifiedBy").asText(null) : null;
        String actualVerifiedAt = json != null ? json.path("verifiedAt").asText(null) : null;
        if (!"closed".equals(actualState) || !managerName.equals(actualVerifiedBy) || actualVerifiedAt == null) {
            throw new AssertionError("readRepairTicket 預期 state=closed、verifiedBy=" + managerName + "、verifiedAt 已寫入，實際 state="
                    + actualState + "、verifiedBy=" + actualVerifiedBy + "、verifiedAt=" + actualVerifiedAt
                    + "（status=" + resp.status() + "）");
        }
    }

    /**
     * dsl.md「{單號} 尚未驗收」：verified_by 與 verified_at 為空、沒有 repair_verified 事件、
     * tasks.state 若為 closed 只能是不需處理結案；readRepairTicket 的 verifiedBy 也為空。
     */
    @那麼("{string} 尚未驗收")
    public void ticketNotYetVerified(String ticketNo) {
        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));

        Map<String, Object> row = dbProbe
                .queryOne("SELECT verified_by, verified_at FROM repair_tickets WHERE task_id = ?", taskId)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無此工務單 task_id=" + taskId));
        if (row.get("verified_by") != null || row.get("verified_at") != null) {
            throw new AssertionError("repair_tickets.verified_by／verified_at 預期為空，實際 " + row.get("verified_by")
                    + "／" + row.get("verified_at") + "（單號 " + ticketNo + "）");
        }
        check("task_events 中 " + ticketNo + " 的 repair_verified 事件筆數", 0L, countEvents(taskId, "repair_verified"));

        Map<String, Object> taskRow =
                dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此工務單 id=" + taskId));
        if ("closed".equals(taskRow.get("state"))) {
            Object resolutionType = dbProbe
                    .queryOne("SELECT resolution_type FROM repair_tickets WHERE task_id = ?", taskId)
                    .map(r -> r.get("resolution_type"))
                    .orElse(null);
            if (!"no_action_needed".equals(resolutionType)) {
                throw new AssertionError("tasks.state 為 closed 時應是不需處理結案，實際 resolution_type=" + resolutionType
                        + "（單號 " + ticketNo + "）");
            }
        }

        ScenarioWorld.ApiResponse resp = apiClient.get("/repair-tickets/" + taskId, BddApiClient.Auth.staff(engineeringStaffJwt()));
        JsonNode json = resp.json();
        String actualVerifiedBy = json != null ? json.path("verifiedBy").asText(null) : null;
        if (actualVerifiedBy != null) {
            throw new AssertionError("readRepairTicket.verifiedBy 預期為空，實際 " + actualVerifiedBy + "（status="
                    + resp.status() + "）");
        }
    }

    /**
     * dsl.md「{單號} 的驗收時間為現在」：權威狀態 repair_tickets.verified_at 等於目前 Clock；
     * 再讀確認 readRepairTicket 的 verifiedAt 一致。時間比到微秒（timestamptz 的精度）。
     */
    @那麼("{string} 的驗收時間為現在")
    public void verifiedAtIsNow(String ticketNo) {
        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        Map<String, Object> row = dbProbe
                .queryOne("SELECT verified_at FROM repair_tickets WHERE task_id = ?", taskId)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無此工務單 task_id=" + taskId));
        if (row.get("verified_at") == null) {
            throw new AssertionError("repair_tickets.verified_at 預期等於目前 Clock " + now + "，實際 NULL（單號 " + ticketNo + "）");
        }
        check("repair_tickets.verified_at", now, toInstant(row.get("verified_at")).truncatedTo(ChronoUnit.MICROS));

        ScenarioWorld.ApiResponse resp = apiClient.get("/repair-tickets/" + taskId, BddApiClient.Auth.staff(engineeringStaffJwt()));
        String actualVerifiedAt = resp.json() != null ? resp.json().path("verifiedAt").asText(null) : null;
        if (actualVerifiedAt == null) {
            throw new AssertionError("readRepairTicket.verifiedAt 預期等於目前 Clock " + now + "，實際 null（status="
                    + resp.status() + "）");
        }
        check("readRepairTicket.verifiedAt", now, OffsetDateTime.parse(actualVerifiedAt).toInstant().truncatedTo(ChronoUnit.MICROS));
    }

    /**
     * dsl.md「{單號} 的驗收人只有 {主管} 一人，驗收只記下一次」：權威狀態 repair_tickets.verified_by
     * 等於該主管、沒有被後到的驗收改寫，tasks.state=closed；task_events 中該單的 repair_verified 事件剛好一筆、
     * repair_verification_failed 一筆都沒有。用於兩位主管撞單。
     */
    @那麼("{string} 的驗收人只有 {string} 一人，驗收只記下一次")
    public void verifiedOnlyOnceBy(String ticketNo, String managerName) {
        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        UUID expectedManager = UUID.fromString(world.staffId(managerName));

        Map<String, Object> row = dbProbe
                .queryOne("SELECT verified_by FROM repair_tickets WHERE task_id = ?", taskId)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無此工務單 task_id=" + taskId));
        if (!sameUuid(row.get("verified_by"), expectedManager)) {
            throw new AssertionError("repair_tickets.verified_by 預期 " + expectedManager + "（" + managerName
                    + "），實際 " + row.get("verified_by") + "——先到的驗收被後到的改寫了，或根本沒記下");
        }

        Map<String, Object> taskRow =
                dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此工務單 id=" + taskId));
        check("tasks.state", "closed", taskRow.get("state"));
        check("task_events 中 " + ticketNo + " 的 repair_verified 事件筆數", 1L, countEvents(taskId, "repair_verified"));
        check("task_events 中 " + ticketNo + " 的 repair_verification_failed 事件筆數", 0L,
                countEvents(taskId, "repair_verification_failed"));
    }

    /**
     * dsl.md「{單號} 的驗收不通過只記下一次」：task_events 中該單的 repair_verification_failed 剛好一筆、
     * repair_verified 一筆都沒有；outbox 裡 event=repair_verification_failed、指向這張單的 push 至多一筆。
     * 用於先送出不通過、後到的被拒。
     */
    @那麼("{string} 的驗收不通過只記下一次")
    public void verificationFailedRecordedOnce(String ticketNo) {
        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        check("task_events 中 " + ticketNo + " 的 repair_verification_failed 事件筆數", 1L,
                countEvents(taskId, "repair_verification_failed"));
        check("task_events 中 " + ticketNo + " 的 repair_verified 事件筆數", 0L, countEvents(taskId, "repair_verified"));

        long pushes = dbProbe.count(
                """
                SELECT count(*) FROM outbox
                 WHERE tenant_id = ? AND kind = 'push'
                   AND payload ->> 'event' = 'repair_verification_failed'
                   AND (payload -> 'target' ->> 'taskId' = ? OR payload -> 'target' ->> 'highlightTaskId' = ?)
                """,
                tenantFixtures.testTenantId(), taskId.toString(), taskId.toString());
        if (pushes > 1) {
            throw new AssertionError("outbox 中指向 " + ticketNo + " 的 repair_verification_failed 推播預期至多一筆，實際 " + pushes + " 筆");
        }
    }

    /**
     * dsl.md「這次驗收的結果為 {結果}，去處為 {去處}」：呈現結果是上一次 verifyRepairTicket 的回應
     * （200、result／destination 等於對照值、decidedBy 是判定的主管；不通過時 reason 等於最近一次不通過原因、
     * assigneeName 是原處理人）；權威狀態依 destination 分辨：closed＝任務 closed，assignee＝任務 accepted 且
     * 處理人不變，pool＝任務 pool 且處理人清空。
     *
     * <p>送出的主管與原因由 When 記在 {@link ScenarioWorld#setLastVerifyRequest}，回應與權威欄位
     * （通過看 verified_by、不通過看 last_verify_failed_by）都要等於它；原處理人取送出前
     * {@link LastOperationGuard#captureFor} 快照裡的 assignee_id。
     */
    @那麼("這次驗收的結果為 {string}，去處為 {string}")
    public void lastVerificationOutcomeIs(String resultChinese, String destinationChinese) {
        String expectedResult = verificationResult(resultChinese);
        String expectedDestination = verificationDestination(destinationChinese);

        ScenarioWorld.ApiResponse resp = world.lastVerifyResponse();
        JsonNode json = resp.json();
        if (resp.status() != 200 || json == null) {
            throw new AssertionError("verifyRepairTicket 預期 200，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        check("RepairVerificationOutcome.result", expectedResult, json.path("result").asText(null));
        check("RepairVerificationOutcome.destination", expectedDestination, json.path("destination").asText(null));

        UUID taskId = UUID.fromString(json.path("taskId").asText());
        Map<String, Object> ticketRow = dbProbe
                .queryOne(
                        "SELECT verified_by, last_verify_failed_by, last_verify_failed_reason FROM repair_tickets WHERE task_id = ?",
                        taskId)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無此工務單 task_id=" + taskId));
        Object decider = "passed".equals(expectedResult) ? ticketRow.get("verified_by") : ticketRow.get("last_verify_failed_by");
        if (decider == null) {
            throw new AssertionError("repair_tickets 沒有記下這次驗收的主管（" + ("passed".equals(expectedResult)
                    ? "verified_by" : "last_verify_failed_by") + " 為 NULL）");
        }
        check("RepairVerificationOutcome.decidedBy", world.lastVerifyManagerName(), json.path("decidedBy").asText(null));
        check("repair_tickets." + ("passed".equals(expectedResult) ? "verified_by" : "last_verify_failed_by"),
                world.lastVerifyManagerName(), staffName(toUuid(decider)));

        LastOperationGuard.Snapshot before = lastOperationGuard.current();
        if (!taskId.equals(before.taskId())) {
            throw new AssertionError("送出前快照的任務是 " + before.taskId() + "，不是這次驗收的 " + taskId);
        }
        Object originalAssignee = before.taskRow() != null ? before.taskRow().get("assignee_id") : null;
        if ("failed".equals(expectedResult)) {
            check("RepairVerificationOutcome.reason", world.lastVerifyReason(), json.path("reason").asText(null));
            check("repair_tickets.last_verify_failed_reason", world.lastVerifyReason(),
                    ticketRow.get("last_verify_failed_reason"));
            if (originalAssignee == null) {
                throw new AssertionError("送出前工務單沒有處理人，無法比對 assigneeName");
            }
            check("RepairVerificationOutcome.assigneeName", staffName(toUuid(originalAssignee)),
                    json.path("assigneeName").asText(null));
        }

        Map<String, Object> taskRow =
                dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此工務單 id=" + taskId));
        switch (expectedDestination) {
            case "closed" -> check("tasks.state", "closed", taskRow.get("state"));
            case "assignee" -> {
                check("tasks.state", "accepted", taskRow.get("state"));
                if (originalAssignee == null || !sameUuid(taskRow.get("assignee_id"), toUuid(originalAssignee))) {
                    throw new AssertionError("tasks.assignee_id 預期維持原處理人 " + originalAssignee + "，實際 "
                            + taskRow.get("assignee_id"));
                }
            }
            case "pool" -> {
                check("tasks.state", "pool", taskRow.get("state"));
                check("tasks.assignee_id", null, taskRow.get("assignee_id"));
            }
            default -> throw new IllegalStateException(expectedDestination);
        }
    }

    /**
     * dsl.md「{單號} 最近一次驗收不通過的原因為 {原因}，判定人為 {主管}」：last_verify_failed_reason、
     * last_verify_failed_by、last_verify_failed_at（等於目前 Clock）寫入，tasks.done_at、resolution_type、
     * resolution_note 已清空；readRepairTicket 的 lastVerifyFailed* 三欄一致。
     */
    @那麼("{string} 最近一次驗收不通過的原因為 {string}，判定人為 {string}")
    public void lastVerificationFailureIs(String ticketNo, String reason, String managerName) {
        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        UUID expectedManager = UUID.fromString(world.staffId(managerName));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        Map<String, Object> row = dbProbe
                .queryOne(
                        """
                        SELECT last_verify_failed_reason, last_verify_failed_by, last_verify_failed_at,
                               resolution_type, resolution_note
                          FROM repair_tickets WHERE task_id = ?
                        """,
                        taskId)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無此工務單 task_id=" + taskId));
        check("repair_tickets.last_verify_failed_reason", reason, row.get("last_verify_failed_reason"));
        if (!sameUuid(row.get("last_verify_failed_by"), expectedManager)) {
            throw new AssertionError("repair_tickets.last_verify_failed_by 預期 " + expectedManager + "（" + managerName
                    + "），實際 " + row.get("last_verify_failed_by"));
        }
        if (row.get("last_verify_failed_at") == null) {
            throw new AssertionError("repair_tickets.last_verify_failed_at 預期等於目前 Clock " + now + "，實際 NULL");
        }
        check("repair_tickets.last_verify_failed_at", now,
                toInstant(row.get("last_verify_failed_at")).truncatedTo(ChronoUnit.MICROS));
        check("repair_tickets.resolution_type", null, row.get("resolution_type"));
        check("repair_tickets.resolution_note", null, row.get("resolution_note"));

        Map<String, Object> taskRow =
                dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此工務單 id=" + taskId));
        check("tasks.done_at", null, taskRow.get("done_at"));

        ScenarioWorld.ApiResponse resp = apiClient.get("/repair-tickets/" + taskId, BddApiClient.Auth.staff(engineeringStaffJwt()));
        JsonNode json = resp.json();
        String actualReason = json != null ? json.path("lastVerifyFailedReason").asText(null) : null;
        String actualBy = json != null ? json.path("lastVerifyFailedBy").asText(null) : null;
        String actualAt = json != null ? json.path("lastVerifyFailedAt").asText(null) : null;
        if (!reason.equals(actualReason) || !managerName.equals(actualBy) || actualAt == null) {
            throw new AssertionError("readRepairTicket 預期 lastVerifyFailedReason=" + reason + "、lastVerifyFailedBy="
                    + managerName + "、lastVerifyFailedAt 有值，實際 " + actualReason + "、" + actualBy + "、" + actualAt
                    + "（status=" + resp.status() + "）");
        }
        check("readRepairTicket.lastVerifyFailedAt", now,
                OffsetDateTime.parse(actualAt).toInstant().truncatedTo(ChronoUnit.MICROS));
    }

    /**
     * dsl.md「{單號} 的完修結果為 {結果}」：repair_tickets.resolution_type 等於對照值；
     * 再讀確認 readRepairTicket 的 resolutionType 一致。
     */
    @那麼("{string} 的完修結果為 {string}")
    public void resolutionTypeIs(String ticketNo, String resultChinese) {
        String expected = Terms.resolutionType(resultChinese);
        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        Map<String, Object> row = dbProbe
                .queryOne("SELECT resolution_type FROM repair_tickets WHERE task_id = ?", taskId)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無此工務單 task_id=" + taskId));
        check("repair_tickets.resolution_type", expected, row.get("resolution_type"));

        ScenarioWorld.ApiResponse resp = apiClient.get("/repair-tickets/" + taskId, BddApiClient.Auth.staff(engineeringStaffJwt()));
        String actual = resp.json() != null ? resp.json().path("resolutionType").asText(null) : null;
        if (!expected.equals(actual)) {
            throw new AssertionError("readRepairTicket.resolutionType 預期 " + expected + "，實際 " + actual
                    + "（status=" + resp.status() + "）");
        }
    }

    /**
     * dsl.md「系統沒有為 {單號} 建立追蹤單」：觸發動作前後 type=repair 的 tasks 與 repair_tickets
     * 筆數不變，也沒有任何工務單的 origin_ticket_task_id 指回原單。
     */
    @那麼("系統沒有為 {string} 建立追蹤單")
    public void noFollowUpTicketCreated(String ticketNo) {
        assertNoRepairTicketCreatedSinceGuard();
        UUID originTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        long linked = dbProbe.count("SELECT count(*) FROM repair_tickets WHERE origin_ticket_task_id = ?", originTaskId);
        if (linked != 0) {
            throw new AssertionError("預期沒有工務單關聯到 " + ticketNo + "，實際有 " + linked + " 張追蹤單");
        }
    }

    /**
     * dsl.md「{房號} 的房況沒有改變」：rooms.hk_state 等於觸發動作前的快照
     * （When 送出前呼叫 {@link LastOperationGuard#captureRoom}）；outbox 沒有這次動作造成的該房 pms_status。
     * 「這次動作造成的」指 id 大於快照當時 outbox 最大 id 的列。
     */
    @那麼("{string} 的房況沒有改變")
    public void roomHkStateUnchanged(String roomNo) {
        LastOperationGuard.RoomSnapshot before = lastOperationGuard.currentRoom();
        if (!roomNo.equals(before.roomNo())) {
            throw new AssertionError("操作前房況快照記的是 " + before.roomNo() + "，不是 " + roomNo);
        }
        long tenantId = tenantFixtures.testTenantId();
        Map<String, Object> row = dbProbe
                .queryOne("SELECT hk_state FROM rooms WHERE tenant_id = ? AND room_no = ?", tenantId, roomNo)
                .orElseThrow(() -> new AssertionError("rooms 查無房號 " + roomNo));
        check("rooms.hk_state（" + roomNo + "）", before.roomRow().get("hk_state"), row.get("hk_state"));

        long writebacks = dbProbe.count(
                """
                SELECT count(*) FROM outbox
                 WHERE tenant_id = ? AND kind = 'pms_status' AND payload ->> 'roomNo' = ? AND id > ?
                """,
                tenantId, roomNo, before.outboxMaxId());
        if (writebacks != 0) {
            throw new AssertionError("預期這次動作沒有回寫 " + roomNo + " 的房況，但 outbox 有 " + writebacks + " 筆 pms_status");
        }
    }

    /**
     * dsl.md「工務待認領中 {單號} 帶驗收退回原因 {原因}」：工務員工 listDeptPool 時該單的
     * PoolItem.verificationReturnReason 等於原因；權威狀態該單 pool、pool_source=verification_return、
     * last_verify_failed_reason 相等。
     */
    @那麼("工務待認領中 {string} 帶驗收退回原因 {string}")
    public void poolItemHasVerificationReturnReason(String ticketNo, String reason) {
        ScenarioWorld.ApiResponse resp = apiClient.get("/pool?category=repair", BddApiClient.Auth.staff(engineeringStaffJwt()));
        JsonNode found = null;
        if (resp.json() != null) {
            for (JsonNode item : resp.json().path("items")) {
                if (ticketNo.equals(item.path("ticketNo").asText(null))) {
                    found = item;
                }
            }
        }
        if (found == null) {
            throw new AssertionError("預期工務待認領包含 " + ticketNo + "，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        check("PoolItem.verificationReturnReason", reason, found.path("verificationReturnReason").asText(null));

        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        Map<String, Object> taskRow = dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此工務單"));
        check("tasks.state", "pool", taskRow.get("state"));
        check("tasks.pool_source", "verification_return", taskRow.get("pool_source"));
        Map<String, Object> ticketRow = dbProbe
                .queryOne("SELECT last_verify_failed_reason FROM repair_tickets WHERE task_id = ?", taskId)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無此工務單 task_id=" + taskId));
        check("repair_tickets.last_verify_failed_reason", reason, ticketRow.get("last_verify_failed_reason"));
    }

    /**
     * dsl.md「主管待辦的任何一類都不包含 {單號}」：每一位在職主管呼叫 getManagerQueues，回應裡每一個清單陣列
     * 都沒有這張單（taskId 或 ticketNo 比對），counts 的每一格等於同名陣列長度；權威狀態該單不是 done。
     */
    @那麼("主管待辦的任何一類都不包含 {string}")
    public void noManagerQueueContainsTicket(String ticketNo) {
        UUID taskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        long tenantId = tenantFixtures.testTenantId();
        List<Map<String, Object>> managers = dbProbe.queryMaps(
                "SELECT id, name FROM staff_accounts WHERE tenant_id = ? AND role = 'manager' AND active = true",
                tenantId);
        if (managers.isEmpty()) {
            // 劇本背景沒有主管時，補一位房務主管與一位工務主管當觀察者，讓兩種主管的待辦都被查到；
            // 否則「每一位在職主管」是空集合，這一句什麼都沒驗就通過。
            staffFixtures.createManagerLikeStaff("待辦觀察房務主管", "manager", "active");
            staffFixtures.createDeptManager("待辦觀察工務主管", "engineering", null, null, "active");
            managers = dbProbe.queryMaps(
                    "SELECT id, name FROM staff_accounts WHERE tenant_id = ? AND role = 'manager' AND active = true",
                    tenantId);
        }
        for (Map<String, Object> manager : managers) {
            String name = String.valueOf(manager.get("name"));
            ScenarioWorld.ApiResponse resp = apiClient.get(
                    "/me/manager-queues", BddApiClient.Auth.staff(mintJwtFor(toUuid(manager.get("id")))));
            JsonNode json = resp.json();
            if (resp.status() != 200 || json == null) {
                throw new AssertionError(name + " 查主管待辦預期 200，實際 status=" + resp.status() + "，body=" + resp.body());
            }
            JsonNode counts = json.path("counts");
            for (Map.Entry<String, JsonNode> field : json.properties()) {
                if (!field.getValue().isArray()) {
                    continue;
                }
                for (JsonNode item : field.getValue()) {
                    if (ticketNo.equals(item.path("ticketNo").asText(null))
                            || taskId.toString().equalsIgnoreCase(item.path("taskId").asText(""))) {
                        throw new AssertionError(name + " 的主管待辦 " + field.getKey() + "[] 預期不包含 " + ticketNo
                                + "，但找到了（body=" + resp.body() + "）");
                    }
                }
                int count = counts.path(field.getKey()).asInt(-1);
                if (count != field.getValue().size()) {
                    throw new AssertionError(name + " 的 counts." + field.getKey() + "=" + count + " 與 "
                            + field.getKey() + "[] 長度 " + field.getValue().size() + " 不一致");
                }
            }
        }

        Map<String, Object> taskRow = dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此工務單"));
        if ("done".equals(taskRow.get("state"))) {
            throw new AssertionError("tasks.state 預期不是 done，實際 done（單號 " + ticketNo + "）");
        }
    }

    /**
     * dsl.md「系統為 {單號} 自動建立一張追蹤單，進工務待認領」：權威狀態查 repair_tickets
     * 有一張新單的 origin_ticket_task_id 指回原單、房號與類別相同、狀態為 pool；
     * 跨視角再查 listDeptPool 看得到這張追蹤單（用 ticketNo 比對，而非 task_id）。
     */
    @那麼("系統為 {string} 自動建立一張追蹤單，進工務待認領")
    public void systemAutoCreatesFollowUpTicket(String ticketNo) {
        UUID originTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        Map<String, Object> originTicket = dbProbe
                .queryOne("SELECT room_id, category FROM repair_tickets WHERE task_id = ?", originTaskId)
                .orElseThrow(() -> new AssertionError("repair_tickets 查無原單 task_id=" + originTaskId));

        Map<String, Object> followUp = dbProbe
                .queryOne(
                        "SELECT task_id, ticket_no, room_id, category FROM repair_tickets"
                                + " WHERE origin_ticket_task_id = ?",
                        originTaskId)
                .orElseThrow(() -> new AssertionError(
                        "預期 " + ticketNo + " 自動建立追蹤單（origin_ticket_task_id 指回原單），實際查無"));

        check("repair_tickets(追蹤單).room_id", originTicket.get("room_id"), followUp.get("room_id"));
        check("repair_tickets(追蹤單).category", originTicket.get("category"), followUp.get("category"));

        UUID followUpTaskId = toUuid(followUp.get("task_id"));
        Map<String, Object> followUpTaskRow = dbProbe
                .taskRow(followUpTaskId)
                .orElseThrow(() -> new AssertionError("tasks 查無追蹤單對應任務 id=" + followUpTaskId));
        check("tasks(追蹤單).state", "pool", followUpTaskRow.get("state"));

        String followUpTicketNo = String.valueOf(followUp.get("ticket_no"));
        ScenarioWorld.ApiResponse resp =
                apiClient.get("/pool?category=repair", BddApiClient.Auth.staff(engineeringStaffJwt()));
        if (!containsTicketNo(resp, followUpTicketNo)) {
            throw new AssertionError("預期工務待認領包含追蹤單 " + followUpTicketNo + "，實際 status=" + resp.status()
                    + "，body=" + resp.body());
        }
    }

    /**
     * dsl.md「"{房號}" 的房況轉為停用待確認」：權威狀態是 {@code rooms.hk_state}=out_of_order
     * （既有 CHECK 值，只是目前沒有任何操作會寫入它）；再讀確認用 {@code GET /rooms/{id}}
     * （contract readRooms）查 {@code hkState} 一致，代表該房在房況查詢中顯示停用待確認。
     */
    @那麼("{string} 的房況轉為停用待確認")
    public void roomHkStateBecomesOutOfOrder(String roomNo) {
        UUID roomId = roomFixtures.roomId(roomNo);
        Map<String, Object> row = dbProbe
                .queryOne("SELECT hk_state FROM rooms WHERE id = ?", roomId)
                .orElseThrow(() -> new AssertionError("rooms 查無此房 id=" + roomId));
        check("rooms.hk_state", "out_of_order", row.get("hk_state"));

        ScenarioWorld.ApiResponse resp = apiClient.get("/rooms/" + roomId, BddApiClient.Auth.staff(engineeringStaffJwt()));
        String actualHkState = resp.json() != null ? resp.json().path("hkState").asText(null) : null;
        if (!"out_of_order".equals(actualHkState)) {
            throw new AssertionError(
                    "readRooms.hkState 預期 out_of_order，實際 " + actualHkState + "（status=" + resp.status() + "）");
        }
    }

    /**
     * dsl.md「"{工務}" 的工務視角今日進度為：」：呈現結果查 {@code GET /me/today} 的
     * engineeringSummary（手上張數→openCount、今天已完修→completedToday、急件數→urgentCount）；
     * 權威狀態再從資料庫重算一次同一組數字：手上張數＝名下 accepted／doing 的工務單，
     * 今天已完修＝名下今天完修、現在為 done 或 closed 的工務單，急件數＝手上單中 is_urgent 的筆數。
     */
    @那麼("{string} 的工務視角今日進度為：")
    public void engineeringTodaySummaryIs(String staffName, DataTable table) {
        ScenarioWorld.ApiResponse resp =
                apiClient.get("/me/today", BddApiClient.Auth.staff(world.jwt(staffName)));
        JsonNode summary = resp.json() != null ? resp.json().path("engineeringSummary") : null;
        UUID staffId = UUID.fromString(world.staffId(staffName));
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String item = requireCell(row, "項目");
            int expected = Integer.parseInt(requireCell(row, "值"));
            String field = engineeringSummaryField(item);
            int actual = summary != null && !summary.isMissingNode() ? summary.path(field).asInt(Integer.MIN_VALUE) : Integer.MIN_VALUE;
            if (actual != expected) {
                throw new AssertionError("engineeringSummary." + field + "（" + item + "）預期 " + expected
                        + "，實際 " + actual + "（status=" + resp.status() + "）");
            }
            long authoritative = engineeringSummaryFromDb(staffId, item);
            if (authoritative != expected) {
                throw new AssertionError("資料庫重算的「" + item + "」預期 " + expected + "，實際 " + authoritative
                        + "（" + staffName + "）");
            }
        }
    }

    /** 權威狀態重算工務視角今日進度的單一項目，口徑見 dsl.md 該列。 */
    private long engineeringSummaryFromDb(UUID staffId, String item) {
        long tenantId = tenantFixtures.testTenantId();
        return switch (item) {
            case "手上張數" -> dbProbe.count(
                    """
                    SELECT count(*) FROM tasks t
                     WHERE t.tenant_id = ? AND t.type = 'repair' AND t.assignee_id = ?
                       AND t.state IN ('accepted', 'doing')
                    """,
                    tenantId, staffId);
            case "今天已完修" -> {
                LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());
                yield dbProbe.count(
                        """
                        SELECT count(*) FROM tasks t
                         WHERE t.tenant_id = ? AND t.type = 'repair' AND t.assignee_id = ?
                           AND t.state IN ('done', 'closed')
                           AND t.done_at >= ? AND t.done_at < ?
                        """,
                        tenantId, staffId,
                        today.atStartOfDay(clock.getZone()).toOffsetDateTime(),
                        today.plusDays(1).atStartOfDay(clock.getZone()).toOffsetDateTime());
            }
            case "急件數" -> dbProbe.count(
                    """
                    SELECT count(*) FROM tasks t
                      JOIN repair_tickets rt ON rt.task_id = t.id AND rt.tenant_id = t.tenant_id
                     WHERE t.tenant_id = ? AND t.type = 'repair' AND t.assignee_id = ?
                       AND t.state IN ('accepted', 'doing') AND rt.is_urgent = true
                    """,
                    tenantId, staffId);
            default -> throw new IllegalArgumentException("工務視角今日進度項目查無對照：" + item);
        };
    }

    /** dsl.md 工務視角今日進度表格的「項目」中文 → {@code MyToday.engineeringSummary} 欄位名。 */
    private static String engineeringSummaryField(String item) {
        return switch (item) {
            case "手上張數" -> "openCount";
            case "今天已完修" -> "completedToday";
            case "急件數" -> "urgentCount";
            default -> throw new IllegalArgumentException("工務視角今日進度項目查無對照：" + item);
        };
    }

    private void assertNoRepairTicketCreatedSinceGuard() {
        RepairCountGuard.Snapshot before = repairCountGuard.current();
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

    /** 待認領查詢需要「工務員工」的身分，dsl.md 這幾句本身沒有指名是哪一位，就取任一在職工務員工。 */
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

    private long countEvents(UUID taskId, String type) {
        return dbProbe.eventsOf(taskId).stream().filter(e -> type.equals(e.get("type"))).count();
    }

    private String staffName(UUID staffId) {
        return dbProbe.queryOne("SELECT name FROM staff_accounts WHERE id = ?", staffId)
                .map(r -> String.valueOf(r.get("name")))
                .orElseThrow(() -> new AssertionError("staff_accounts 找不到 id=" + staffId));
    }

    /** dsl.md「這次驗收的結果為…」的結果中文 → {@code RepairVerificationOutcome.result}。 */
    private static String verificationResult(String chinese) {
        return switch (chinese) {
            case "通過" -> "passed";
            case "不通過" -> "failed";
            default -> throw new IllegalArgumentException("驗收結果查無對照：" + chinese);
        };
    }

    /** dsl.md「這次驗收的結果為…」的去處中文 → {@code RepairVerificationOutcome.destination}。 */
    private static String verificationDestination(String chinese) {
        return switch (chinese) {
            case "結案" -> "closed";
            case "原處理人" -> "assignee";
            case "工務待認領" -> "pool";
            default -> throw new IllegalArgumentException("驗收去處查無對照：" + chinese);
        };
    }


    private String mintJwtFor(UUID staffId) {
        Map<String, Object> staff = dbProbe
                .queryOne("SELECT role, dept FROM staff_accounts WHERE id = ?", staffId)
                .orElseThrow(() -> new IllegalStateException("staff_accounts 找不到 id=" + staffId));
        return jwtService
                .issue(tenantFixtures.testTenantId(), staffId, (String) staff.get("role"), (String) staff.get("dept"))
                .token();
    }

    /** dsl.md「{單號} 的來源為 {來源}」的來源中文 → 權威 {@code tasks.source}（staff|desk）。 */
    private static String repairTicketSource(String chinese) {
        return switch (chinese) {
            case "房務報修" -> "staff";
            case "櫃檯" -> "desk";
            default -> throw new IllegalArgumentException("工務單來源查無對照：" + chinese);
        };
    }

    /** 同一個中文來源對照 {@code PoolItem.source}（staff_report|desk_forward），供跨視角比對。 */
    private static String repairTicketPoolSource(String chinese) {
        return switch (chinese) {
            case "房務報修" -> "staff_report";
            case "櫃檯" -> "desk_forward";
            default -> throw new IllegalArgumentException("工務單來源查無對照：" + chinese);
        };
    }

    private static String repairStatus(String chinese) {
        if ("待認領".equals(chinese)) {
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

    private static Instant toInstant(Object v) {
        if (v instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (v instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (v instanceof Instant i) {
            return i;
        }
        throw new IllegalStateException("預期時間型別，實際 " + (v == null ? "null" : v.getClass()));
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
