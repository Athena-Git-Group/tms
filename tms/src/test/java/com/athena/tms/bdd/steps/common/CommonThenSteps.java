package com.athena.tms.bdd.steps.common;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.OutboxAwait;
import com.athena.tms.bdd.support.PushPayloadView;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.service.JwtService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 介面根共通句的 Then（dsl.md Then 句型區）。每句除了查 tasks／task_events／outbox
 * 這些權威資料，還要在能自然決定「用誰的身分」時另外呼叫一次對應的員工 API 驗證
 * dsl.md 點名的「跨視角／再讀確認」——這份身分不是 Gherkin 參數給的，因此直接用
 * {@link JwtService} 現場幫該任務的負責人簽一張新權杖，不透過 {@code StaffFixtures}
 * （那個入口只服務「Given 建的員工」，跨視角驗證要用的是「DB 裡任務當下的負責人」，
 * 兩者不必是同一人，例如改派後的情境）。
 */
public class CommonThenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final LastOperationGuard lastOperationGuard;
    private final OutboxAwait outboxAwait;
    private final MutableClock clock;
    private final JwtService jwtService;
    private final TenantFixtures tenantFixtures;
    private final ObjectMapper objectMapper;

    public CommonThenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            LastOperationGuard lastOperationGuard,
            OutboxAwait outboxAwait,
            MutableClock clock,
            JwtService jwtService,
            TenantFixtures tenantFixtures,
            ObjectMapper objectMapper) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.lastOperationGuard = lastOperationGuard;
        this.outboxAwait = outboxAwait;
        this.clock = clock;
        this.jwtService = jwtService;
        this.tenantFixtures = tenantFixtures;
        this.objectMapper = objectMapper;
    }

    @那麼("這次操作被拒絕")
    public void operationRejected() {
        assertRejectedResponse();
        assertAuthoritativeStateUnchanged();
    }

    @那麼("這次操作被拒絕，提示 {string}")
    public void operationRejectedWithHint(String hint) {
        assertRejectedResponse();
        String message = textOrNull(world.lastResponse().json(), "message");
        if (message == null || !message.contains(hint)) {
            throw new AssertionError("預期錯誤訊息包含「" + hint + "」，實際 message=" + message);
        }
        assertAuthoritativeStateUnchanged();
    }

    @那麼("{string} 的狀態為 {string}")
    public void taskStateIs(String taskKey, String stateChinese) {
        UUID taskId = resolveTaskId(taskKey);
        final ExpectedTaskState expected = resolveExpectedState(stateChinese);
        Map<String, Object> row = requireTaskRow(taskId);
        check("tasks.state", expected.state(), row.get("state"));
        if (expected.inspection() != null) {
            check("tasks.inspection", expected.inspection(), row.get("inspection"));
        }
        if (expected.cancelReason() != null) {
            check("tasks.cancel_reason", expected.cancelReason(), row.get("cancel_reason"));
        }

        withAssigneeReadTasks(
                taskId, row, json -> check("readTasks.state", expected.state(), textOrNull(json, "state")));
    }

    /**
     * dsl.md「{任務} 的狀態為 {狀態}」必查的不只 tasks.state：狀態用語對照裡
     * 「查房通過」「完成 · 免查房」還規定了 tasks.inspection（passed／waived），
     * 工務單「已撤回」還規定了 tasks.cancel_reason（staff_withdrew）；沒有附帶
     * 子欄位的用語，對應欄位為 null 表示不必查。
     */
    private record ExpectedTaskState(String state, String inspection, String cancelReason) {}

    private static ExpectedTaskState resolveExpectedState(String stateChinese) {
        if ("查房通過".equals(stateChinese)) {
            return new ExpectedTaskState(Terms.taskState(stateChinese), Terms.inspection("通過"), null);
        }
        if ("完成 · 免查房".equals(stateChinese)) {
            return new ExpectedTaskState(Terms.taskState(stateChinese), Terms.inspection("免查"), null);
        }
        try {
            return new ExpectedTaskState(Terms.taskState(stateChinese), null, null);
        } catch (IllegalArgumentException notPlainTaskState) {
            // 工務單專屬狀態詞（已接單/維修中/已完修/已撤回）走工務單對照
            String cancelReason = "已撤回".equals(stateChinese) ? Terms.repairTicketWithdrawnReason() : null;
            return new ExpectedTaskState(Terms.repairTicketState(stateChinese), null, cancelReason);
        }
    }

    @那麼("{string} 的負責人是 {string}")
    public void taskAssigneeIs(String taskKey, String staffName) {
        UUID taskId = resolveTaskId(taskKey);
        UUID expected = UUID.fromString(world.staffId(staffName));
        Map<String, Object> row = requireTaskRow(taskId);
        if (!sameUuid(row.get("assignee_id"), expected)) {
            throw new AssertionError("tasks.assignee_id 預期 " + expected + "，實際 " + row.get("assignee_id"));
        }

        JsonNode json = readTasks(taskId, world.jwt(staffName));
        String assigneeId = textOrNull(json, "assigneeId");
        if (assigneeId == null || !expected.toString().equalsIgnoreCase(assigneeId)) {
            throw new AssertionError(
                    staffName + " 呼叫 readTasks 看不到「" + taskKey + "」是自己的單（assigneeId=" + assigneeId + "）");
        }
    }

    @那麼("{string} 的停住原因為 {string}")
    public void taskHoldReasonIs(String taskKey, String reason) {
        UUID taskId = resolveTaskId(taskKey);
        Map<String, Object> row = requireTaskRow(taskId);
        check("tasks.hold_reason", reason, row.get("hold_reason"));

        withAssigneeReadTasks(
                taskId, row, json -> check("readTasks.holdReason", reason, textOrNull(json, "holdReason")));
    }

    @那麼("{string} 的已做時間為 {int} 分鐘")
    public void taskElapsedMinutesIs(String taskKey, int minutes) {
        UUID taskId = resolveTaskId(taskKey);
        Map<String, Object> row = requireTaskRow(taskId);
        long elapsedSec = toLong(row.getOrDefault("worked_sec", 0));
        Object segmentStarted = row.get("segment_started_at");
        if (segmentStarted != null) {
            elapsedSec += Duration.between(toInstant(segmentStarted), clock.instant()).getSeconds();
        }
        long actualMinutes = elapsedSec / 60;
        if (actualMinutes != minutes) {
            throw new AssertionError("已做時間預期 " + minutes + " 分鐘，實際 " + actualMinutes + " 分鐘（worked_sec="
                    + row.get("worked_sec") + ", segment_started_at=" + segmentStarted + "）");
        }

        withAssigneeReadTasks(taskId, row, json -> {
            int actual = json.path("elapsedMinutes").asInt(-1);
            if (actual != minutes) {
                throw new AssertionError("readTasks.elapsedMinutes 預期 " + minutes + "，實際 " + actual);
            }
        });
    }

    @那麼("{string} 收到推播 {string}")
    public void staffReceivedPush(String staffName, String title) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        outboxAwait.waitForPush(
                v -> staffId.equals(v.recipientStaffId()) && title.equals(v.title()),
                staffName + " 收到標題「" + title + "」的推播");

        // dsl.md 必查「不該發生：同一事件不會對該員工推播兩次」——同收件人、同標題的
        // kind push outbox 必須恰好一筆。
        long count = dbProbe.outboxRows("push").stream()
                .map(this::parsePush)
                .filter(v -> staffId.equals(v.recipientStaffId()) && title.equals(v.title()))
                .count();
        if (count != 1) {
            throw new AssertionError(staffName + " 的標題「" + title + "」推播預期恰好一筆（同一事件不推兩次），實際 outbox 有 "
                    + count + " 筆");
        }
    }

    @那麼("{string} 收到以下推播：")
    public void staffReceivedPushes(String staffName, DataTable table) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String title = blankToNull(row.get("標題"));
            String destination = blankToNull(row.get("去處"));
            PushPayloadView push = outboxAwait.waitForPush(
                    v -> staffId.equals(v.recipientStaffId()) && (title == null || title.equals(v.title())),
                    staffName + " 收到" + (title != null ? "標題「" + title + "」的" : "") + "推播");
            if (destination != null) {
                assertDestinationMatches(push.target(), destination);
            }
        }
    }

    @那麼("{string} 沒有收到任何推播")
    public void staffReceivedNoPush(String staffName) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        boolean found = dbProbe.outboxRows("push").stream()
                .map(this::parsePush)
                .anyMatch(v -> staffId.equals(v.recipientStaffId()));
        if (found) {
            throw new AssertionError(staffName + " 預期沒有收到任何推播，但 outbox 裡找到至少一筆");
        }
    }

    private void assertRejectedResponse() {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        if (resp.status() < 400 || resp.status() > 499) {
            throw new AssertionError("預期 4xx 回應，實際狀態碼=" + resp.status() + "，body=" + resp.body());
        }
        JsonNode json = resp.json();
        if (json == null || !json.has("code") || !json.has("message")) {
            throw new AssertionError("4xx 回應不符合 contracts Error（需要 code 與 message）：" + resp.body());
        }
    }

    private void assertAuthoritativeStateUnchanged() {
        LastOperationGuard.Snapshot before = lastOperationGuard.current();
        Map<String, Object> afterRow = dbProbe.taskRow(before.taskId()).orElse(null);
        if (!Objects.equals(before.taskRow(), afterRow)) {
            throw new AssertionError("操作被拒絕後 tasks 仍被改動：操作前=" + before.taskRow() + "，操作後=" + afterRow);
        }
        int afterEvents = dbProbe.eventsOf(before.taskId()).size();
        if (afterEvents != before.eventCount()) {
            throw new AssertionError("操作被拒絕後仍新增了 task_events：操作前 " + before.eventCount() + " 筆，操作後 "
                    + afterEvents + " 筆");
        }
        String aggregateKey = "task:" + before.taskId();
        int afterOutbox = dbProbe.outboxRows("task_event", aggregateKey).size()
                + dbProbe.outboxRows("push", aggregateKey).size();
        if (afterOutbox != before.outboxCount()) {
            throw new AssertionError("操作被拒絕後仍新增了 outbox：操作前 " + before.outboxCount() + " 筆，操作後 "
                    + afterOutbox + " 筆");
        }
    }

    /**
     * 待認領等沒有負責人的任務，dsl.md「以負責人或主管身分」沒有自然對應的身分可用；
     * 跳過跨視角檢查，不杜撰一個不存在的操作者。
     */
    private void withAssigneeReadTasks(
            UUID taskId, Map<String, Object> taskRow, java.util.function.Consumer<JsonNode> check) {
        Object assigneeId = taskRow.get("assignee_id");
        if (assigneeId == null) {
            return;
        }
        String jwt = mintJwtFor(UUID.fromString(String.valueOf(assigneeId)));
        check.accept(readTasks(taskId, jwt));
    }

    private JsonNode readTasks(UUID taskId, String jwt) {
        ScenarioWorld.ApiResponse resp = apiClient.get("/tasks/" + taskId, BddApiClient.Auth.staff(jwt));
        if (resp.status() != 200 || resp.json() == null) {
            throw new AssertionError("readTasks(" + taskId + ") 預期 200，實際 status=" + resp.status()
                    + "，body=" + resp.body());
        }
        return resp.json();
    }

    private String mintJwtFor(UUID staffId) {
        Map<String, Object> staff = dbProbe
                .queryOne("SELECT role, dept FROM staff_accounts WHERE id = ?", staffId)
                .orElseThrow(() -> new IllegalStateException("staff_accounts 找不到 id=" + staffId));
        return jwtService
                .issue(tenantFixtures.testTenantId(), staffId, (String) staff.get("role"), (String) staff.get("dept"))
                .token();
    }

    private void assertDestinationMatches(PushPayloadView.Target target, String destination) {
        if ("待認領".equals(destination)) {
            requireKind(target, "pool");
        } else if ("首頁".equals(destination)) {
            requireKind(target, "home");
        } else if (destination.startsWith("待認領並標亮 ")) {
            requireKind(target, "pool");
            String label = destination.substring("待認領並標亮 ".length());
            if (world.hasTask(label)) {
                requireUuidEquals("highlightTaskId", target.highlightTaskId(), UUID.fromString(world.taskId(label)));
            }
        } else if (destination.endsWith(" 的任務頁")) {
            requireKind(target, "task");
            String label = destination.substring(0, destination.length() - " 的任務頁".length());
            if (world.hasTask(label)) {
                requireUuidEquals("taskId", target.taskId(), UUID.fromString(world.taskId(label)));
            }
        } else if (destination.endsWith(" 的工務單詳情")) {
            // dsl.md：工務單詳情為 kind=ticket 並帶該修繕任務的 taskId（本人的修繕任務一律用這種，不用 task）。
            requireKind(target, "ticket");
            String ticketNo = destination.substring(0, destination.length() - " 的工務單詳情".length());
            if (world.hasRepairTicket(ticketNo)) {
                requireUuidEquals("taskId", target.taskId(), UUID.fromString(world.repairTicketTaskId(ticketNo)));
            }
        } else {
            throw new AssertionError("無法解析的推播去處格式（不在 dsl.md PushTarget 對照範圍內）：" + destination);
        }
    }

    private static void requireKind(PushPayloadView.Target target, String expectedKind) {
        if (!expectedKind.equals(target.kind())) {
            throw new AssertionError("PushTarget.kind 預期 " + expectedKind + "，實際 " + target.kind());
        }
    }

    private static void requireUuidEquals(String field, UUID actual, UUID expected) {
        if (!expected.equals(actual)) {
            throw new AssertionError("PushTarget." + field + " 預期 " + expected + "，實際 " + actual);
        }
    }

    private PushPayloadView parsePush(Map<String, Object> outboxRow) {
        Object payload = outboxRow.get("payload");
        String json = payload instanceof String s ? s : String.valueOf(payload);
        try {
            return objectMapper.readValue(json, PushPayloadView.class);
        } catch (RuntimeException e) {
            throw new AssertionError("解析 push payload 失敗：" + json, e);
        }
    }

    private UUID resolveTaskId(String key) {
        if (key.startsWith("W-")) {
            return UUID.fromString(world.repairTicketTaskId(key));
        }
        return UUID.fromString(world.taskId(key));
    }

    private Map<String, Object> requireTaskRow(UUID taskId) {
        return dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此任務：" + taskId));
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

    private static long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalStateException("預期數字型別，實際 " + (v == null ? "null" : v.getClass()));
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

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
