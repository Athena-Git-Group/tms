package com.athena.tms.bdd.steps.inspection;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.OutboxAwait;
import com.athena.tms.bdd.support.PmsProbe;
import com.athena.tms.bdd.support.PushPayloadView;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.service.JwtService;
import io.cucumber.java.ParameterType;
import io.cucumber.java.zh_tw.那麼;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class InspectionThenSteps {

    private static final Map<String, String> ROOM_STATUS = Map.of("已整理（Inspected）", "inspected");

    private static final Map<String, String> DESTINATION = Map.of(
            "結案", "closed",
            "原清房者", "cleaner",
            "待認領", "pool");

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final OutboxAwait outboxAwait;
    private final PmsProbe pmsProbe;
    private final TenantFixtures tenantFixtures;
    private final MutableClock clock;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final LastOperationGuard lastOperationGuard;

    public InspectionThenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            OutboxAwait outboxAwait,
            PmsProbe pmsProbe,
            TenantFixtures tenantFixtures,
            MutableClock clock,
            JwtService jwtService,
            ObjectMapper objectMapper,
            LastOperationGuard lastOperationGuard) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.outboxAwait = outboxAwait;
        this.pmsProbe = pmsProbe;
        this.tenantFixtures = tenantFixtures;
        this.clock = clock;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.lastOperationGuard = lastOperationGuard;
    }

    @ParameterType(name = "項目清單", value = "[^、\\s]+(?:、[^、\\s]+)*")
    public List<String> 項目清單(String raw) {
        return List.of(raw.split("、"));
    }

    @ParameterType(name = "事件清單", value = "\"[^\"]*\"(?:、\"[^\"]*\")*")
    public List<String> 事件清單(String raw) {
        List<String> items = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(raw);
        while (m.find()) {
            items.add(m.group(1));
        }
        return items;
    }

    @那麼("{string} 的查房結果為 {string}")
    public void taskInspectionResultIs(String roomNo, String resultChinese) {
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        String expected = Terms.inspection(resultChinese);
        Map<String, Object> row = requireTaskRow(taskId);
        Object actual = row.get("inspection");
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("tasks.inspection 預期 " + expected + "，實際 " + actual);
        }

        Object assigneeId = row.get("assignee_id");
        if (assigneeId != null) {
            String jwt = mintJwtFor(UUID.fromString(String.valueOf(assigneeId)));
            JsonNode json = readTask(taskId, jwt);
            String actualInspection = textOrNull(json, "inspection");
            if (!Objects.equals(expected, actualInspection)) {
                throw new AssertionError("readTasks.inspection 預期 " + expected + "，實際 " + actualInspection);
            }
        }
    }

    @那麼("{string} 回寫 PMS 的房況為 {string}")
    public void taskWritesBackPmsStatus(String roomNo, String statusChinese) {
        String expectedStatus = ROOM_STATUS.get(statusChinese);
        if (expectedStatus == null) {
            throw new IllegalArgumentException("找不到「" + statusChinese + "」在房況對照表中的權威值");
        }

        outboxAwait.waitForOutboxRow(
                "pms_status",
                r -> roomNo.equals(payloadText(r, "roomNo")) && expectedStatus.equals(payloadText(r, "status")),
                "「" + roomNo + "」回寫 PMS 房況為 " + expectedStatus);

        long otherStatusCount = dbProbe.outboxRows("pms_status").stream()
                .filter(r -> roomNo.equals(payloadText(r, "roomNo")))
                .filter(r -> !expectedStatus.equals(payloadText(r, "status")))
                .count();
        if (otherStatusCount > 0) {
            throw new AssertionError(
                    "「" + roomNo + "」同一事件不該有另一筆不同房況的回寫，但找到 " + otherStatusCount + " 筆");
        }

        Map<String, Object> received = pmsProbe.lastWritebackFor(roomNo);
        if (!expectedStatus.equals(received.get("status"))) {
            throw new AssertionError(
                    "模擬 PMS adapter 預期收到「" + roomNo + "」房況 " + expectedStatus + "，實際收到 " + received);
        }
    }

    @那麼("{string} 沒有回寫 PMS，同步紀錄記一筆未回寫")
    public void taskDoesNotWriteBackPms(String roomNo) {
        boolean hasWriteback = dbProbe.outboxRows("pms_status").stream()
                .anyMatch(r -> roomNo.equals(payloadText(r, "roomNo")));
        if (hasWriteback) {
            throw new AssertionError("「" + roomNo + "」預期沒有回寫 PMS，但 outbox 找到 kind=pms_status 的資料");
        }

        Map<String, Object> received = pmsProbe.lastWritebackFor(roomNo);
        if (!received.isEmpty()) {
            throw new AssertionError("模擬 PMS adapter 預期沒有收到「" + roomNo + "」的任何房況，實際收到 " + received);
        }
    }

    @那麼("{string} 的任務事件依序包含 {事件清單}")
    public void taskEventsInOrderInclude(String roomNo, List<String> eventKinds) {
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        List<String> actualTypes = dbProbe.eventsOf(taskId).stream()
                .map(e -> (String) e.get("type"))
                .toList();

        int from = 0;
        for (String kind : eventKinds) {
            int found = indexOfFrom(actualTypes, kind, from);
            if (found < 0) {
                throw new AssertionError(
                        "task_events 預期依序包含 " + eventKinds + "，但「" + kind + "」沒有依序出現。實際事件："
                                + actualTypes);
            }
            from = found + 1;
        }
    }

    @那麼("{string} 的重做次數為 {int}")
    public void taskRedoCountIs(String roomNo, int expected) {
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        Map<String, Object> row = requireTaskRow(taskId);
        int actual = ((Number) row.get("redo_count")).intValue();
        if (actual != expected) {
            throw new AssertionError("tasks.redo_count 預期 " + expected + "，實際 " + actual);
        }
    }

    @那麼("{string} 沒有任何 SOP 勾記")
    public void taskHasNoSopChecks(String roomNo) {
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        long checkedCount = dbProbe.count(
                "SELECT count(*) FROM task_sop_checks WHERE task_id = ? AND checked = true", taskId);
        if (checkedCount > 0) {
            throw new AssertionError(
                    "「" + roomNo + "」預期沒有任何 SOP 勾記，但 task_sop_checks 找到 " + checkedCount + " 筆 checked=true");
        }
    }

    @那麼("今日查房合格率為 {string}")
    public void todaysPassRateIs(String ratio) {
        int[] parsed = parseRatio(ratio);
        int expectedPassed = parsed[0];
        int expectedTotal = parsed[1];

        JsonNode json = world.lastResponse().json();
        Integer passed = json != null && json.has("passed") ? json.get("passed").asInt() : null;
        Integer failed = json != null && json.has("failed") ? json.get("failed").asInt() : null;
        if (passed == null || failed == null || passed != expectedPassed || (passed + failed) != expectedTotal) {
            throw new AssertionError(
                    "PassRate 預期 passed=" + expectedPassed + "、分母=" + expectedTotal + "，實際回應："
                            + world.lastResponse().body());
        }

        long tenantId = tenantFixtures.testTenantId();
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        long actualPassed = dbProbe.count(
                "SELECT count(*) FROM tasks WHERE tenant_id = ? AND type = 'cleaning' AND inspection = 'passed' AND work_date = ?",
                tenantId, today);
        long actualFailed = dbProbe.count(
                "SELECT count(*) FROM tasks WHERE tenant_id = ? AND type = 'cleaning' AND inspection = 'failed' AND work_date = ?",
                tenantId, today);
        if (actualPassed != expectedPassed || (actualPassed + actualFailed) != expectedTotal) {
            throw new AssertionError(
                    "以 tasks.inspection 重算合格率預期 " + expectedPassed + "/" + expectedTotal + "，實際 passed="
                            + actualPassed + "、failed=" + actualFailed);
        }
    }

    @那麼("查房者名單包含 {string}")
    public void inspectorListIncludes(String staffName) {
        assertInList(staffName, true);
        assertStaffActive(staffName, true);
    }

    @那麼("查房者名單不包含 {string}")
    public void inspectorListExcludes(String staffName) {
        assertInList(staffName, false);
        assertStaffActive(staffName, false);
    }

    @那麼("這次查房的結果為 {string}，去向為 {string}")
    public void thisInspectionResultAndDestinationAre(String resultChinese, String destinationChinese) {
        String expectedResult = Terms.inspection(resultChinese);
        String expectedDestination = DESTINATION.get(destinationChinese);
        if (expectedDestination == null) {
            throw new IllegalArgumentException("找不到「" + destinationChinese + "」在去向對照表中的權威值");
        }

        ScenarioWorld.ApiResponse resp = world.lastInspectionResponse();
        if (resp.status() != 201 || resp.json() == null) {
            throw new AssertionError("createInspections 預期 201，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        JsonNode json = resp.json();
        check("InspectionOutcome.result", expectedResult, textOrNull(json, "result"));
        check("InspectionOutcome.destination", expectedDestination, textOrNull(json, "destination"));

        LastOperationGuard.Snapshot before = lastOperationGuard.current();
        UUID taskId = before.taskId();
        Object cleanerId = before.taskRow() != null ? before.taskRow().get("assignee_id") : null;

        List<String> failedKeys = world.lastInspectionItems().stream()
                .filter(item -> Boolean.FALSE.equals(item.get("pass")))
                .map(item -> String.valueOf(item.get("key")))
                .toList();
        List<String> expectedFailed = "failed".equals(expectedResult)
                ? labelsInHotelOrder(failedKeys)
                : List.of();
        List<String> actualFailed = new ArrayList<>();
        for (JsonNode item : json.path("failedItems")) {
            actualFailed.add(textOrNull(item, "key") + "=" + textOrNull(item, "label"));
        }
        check("InspectionOutcome.failedItems", expectedFailed, actualFailed);
        if ("failed".equals(expectedResult)) {
            String expectedCleaner = cleanerId == null ? null : staffName(UUID.fromString(String.valueOf(cleanerId)));
            check("InspectionOutcome.cleanerName", expectedCleaner, textOrNull(json, "cleanerName"));
        }

        Map<String, Object> row = requireTaskRow(taskId);
        switch (expectedDestination) {
            case "closed" -> check("tasks.state", "inspected", row.get("state"));
            case "cleaner" -> {
                check("tasks.state", "accepted", row.get("state"));
                check("tasks.assignee_id", String.valueOf(cleanerId), String.valueOf(row.get("assignee_id")));
            }
            case "pool" -> {
                check("tasks.state", "pool", row.get("state"));
                check("tasks.assignee_id", null, row.get("assignee_id"));
            }
            default -> throw new IllegalStateException("未知去向: " + expectedDestination);
        }
    }

    @那麼("這次查房被拒絕，原因是項目集合不符，回應帶現行項目 {項目清單}")
    public void thisInspectionRejectedForItemsMismatch(List<String> expectedLabels) {
        ScenarioWorld.ApiResponse resp = world.lastInspectionResponse();
        if (resp.status() != 422 || resp.json() == null) {
            throw new AssertionError("createInspections 預期 422，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        JsonNode json = resp.json();
        check("Error.code", "inspection_items_mismatch", textOrNull(json, "code"));

        JsonNode context = json.path("context");
        List<String> actualLabels = new ArrayList<>();
        List<String> currentKeys = new ArrayList<>();
        for (JsonNode item : context.path("currentItems")) {
            actualLabels.add(textOrNull(item, "label"));
            currentKeys.add(textOrNull(item, "key"));
        }
        check("Error.context.currentItems.label", expectedLabels, actualLabels);

        List<String> activeKeys = activeInspectionItemKeys();
        check("Error.context.currentItems.key", activeKeys, currentKeys);

        Set<String> sentKeys = new LinkedHashSet<>();
        world.lastInspectionItems().forEach(item -> sentKeys.add(String.valueOf(item.get("key"))));
        List<String> expectedMissing = activeKeys.stream().filter(k -> !sentKeys.contains(k)).toList();
        List<String> expectedUnexpected = sentKeys.stream().filter(k -> !activeKeys.contains(k)).toList();
        check("Error.context.missing", expectedMissing, textList(context.path("missing")));
        check("Error.context.unexpected", expectedUnexpected, textList(context.path("unexpected")));

        LastOperationGuard.Snapshot before = lastOperationGuard.current();
        UUID taskId = before.taskId();
        Map<String, Object> row = requireTaskRow(taskId);
        check("tasks.state", "done", row.get("state"));
        check("task_events 筆數", before.eventCount(), dbProbe.eventsOf(taskId).size());
        long inspectionRows = dbProbe.count("SELECT count(*) FROM inspections WHERE task_id = ?", taskId);
        long resultEvents = dbProbe.count(
                "SELECT count(*) FROM task_events WHERE task_id = ? AND type IN ('inspection_passed', 'inspection_failed')",
                taskId);
        check("inspections 列數（應與查房結果事件數一致，沒有新增）", resultEvents, inspectionRows);
        int outboxNow = dbProbe.outboxRows("task_event", "task:" + taskId).size()
                + dbProbe.outboxRows("push", "task:" + taskId).size();
        check("outbox 筆數（清房者不該因這次送出收到推播）", before.outboxCount(), outboxNow);
    }

    @那麼("{string} 在房務待認領")
    public void taskIsInHousekeepingPool(String roomNo) {
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        Map<String, Object> row = requireTaskRow(taskId);
        check("tasks.state", "pool", row.get("state"));
        check("tasks.dept", "housekeeping", row.get("dept"));
        check("tasks.assignee_id", null, row.get("assignee_id"));
        check("tasks.pool_source", Terms.poolSource("查房退回"), row.get("pool_source"));

        String jwt = mintJwtForAnyActiveStaff("housekeeping");
        ScenarioWorld.ApiResponse resp = apiClient.get("/pool", BddApiClient.Auth.staff(jwt));
        if (resp.status() != 200 || resp.json() == null) {
            throw new AssertionError("listDeptPool 預期 200，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        boolean found = false;
        for (JsonNode item : resp.json().path("items")) {
            if (taskId.toString().equals(textOrNull(item, "taskId")) || roomNo.equals(textOrNull(item, "roomNo"))) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new AssertionError("房務員呼叫 listDeptPool 看不到「" + roomNo + "」：" + resp.body());
        }
    }

    @那麼("{string} 的查房紀錄只有 {int} 筆")
    public void taskHasExactlyInspectionRecords(String roomNo, int expected) {
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        long inspectionRows = dbProbe.count("SELECT count(*) FROM inspections WHERE task_id = ?", taskId);
        long resultEvents = dbProbe.count(
                "SELECT count(*) FROM task_events WHERE task_id = ? AND type IN ('inspection_passed', 'inspection_failed')",
                taskId);
        check("inspections 列數", (long) expected, inspectionRows);
        check("task_events 中 inspection_passed＋inspection_failed 筆數", (long) expected, resultEvents);
    }

    @那麼("{string} 沒有收到查房退回的推播")
    public void staffReceivedNoInspectionFailedPush(String staffName) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        long count = dbProbe.outboxRows("push").stream()
                .map(this::parsePush)
                .filter(v -> staffId.equals(v.recipientStaffId()) && "inspection_failed".equals(v.event()))
                .count();
        if (count > 0) {
            throw new AssertionError(staffName + " 預期沒有收到查房退回（event=inspection_failed）的推播，但 outbox 找到 " + count + " 筆");
        }
    }

    private List<String> activeInspectionItemKeys() {
        return dbProbe.queryMaps(
                        "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection' AND active = true ORDER BY sort_order",
                        tenantFixtures.testTenantId())
                .stream()
                .map(r -> String.valueOf(r.get("item_key")))
                .toList();
    }

    private List<String> labelsInHotelOrder(List<String> keys) {
        return dbProbe.queryMaps(
                        "SELECT item_key, label FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection' ORDER BY sort_order",
                        tenantFixtures.testTenantId())
                .stream()
                .filter(r -> keys.contains(String.valueOf(r.get("item_key"))))
                .map(r -> r.get("item_key") + "=" + r.get("label"))
                .toList();
    }

    private String staffName(UUID staffId) {
        return dbProbe
                .queryOne("SELECT name FROM staff_accounts WHERE id = ?", staffId)
                .map(r -> String.valueOf(r.get("name")))
                .orElseThrow(() -> new AssertionError("staff_accounts 查無此員工：" + staffId));
    }

    private String mintJwtForAnyActiveStaff(String dept) {
        Map<String, Object> staff = dbProbe
                .queryOne(
                        "SELECT id FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND active = true LIMIT 1",
                        tenantFixtures.testTenantId(), dept)
                .orElseThrow(() -> new IllegalStateException("找不到在職的「" + dept + "」員工，無法模擬跨視角呼叫"));
        return mintJwtFor(UUID.fromString(String.valueOf(staff.get("id"))));
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

    private static List<String> textList(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            out.add(n.asText());
        }
        return out;
    }

    private static void check(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual);
        }
    }

    private void assertInList(String staffName, boolean shouldContain) {
        JsonNode json = world.lastResponse().json();
        boolean found = false;
        if (json != null && json.isArray()) {
            for (JsonNode item : json) {
                if (staffName.equals(textOrNull(item, "name"))) {
                    found = true;
                    break;
                }
            }
        }
        if (found != shouldContain) {
            throw new AssertionError(
                    "查房者名單預期" + (shouldContain ? "包含" : "不包含") + "「" + staffName + "」，實際"
                            + (found ? "包含" : "不包含") + "（回應：" + world.lastResponse().body() + "）");
        }
    }

    private void assertStaffActive(String staffName, boolean expectedActive) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        Map<String, Object> row = dbProbe
                .queryOne("SELECT active FROM staff_accounts WHERE id = ?", staffId)
                .orElseThrow(() -> new AssertionError("staff_accounts 查無此員工：" + staffId));
        boolean active = Boolean.TRUE.equals(row.get("active"));
        if (active != expectedActive) {
            throw new AssertionError("staff_accounts.active 預期 " + expectedActive + "，實際 " + active);
        }
    }

    private Map<String, Object> requireTaskRow(UUID taskId) {
        return dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此任務：" + taskId));
    }

    private JsonNode readTask(UUID taskId, String jwt) {
        ScenarioWorld.ApiResponse resp = apiClient.get("/tasks/" + taskId, BddApiClient.Auth.staff(jwt));
        if (resp.status() != 200 || resp.json() == null) {
            throw new AssertionError(
                    "readTasks(" + taskId + ") 預期 200，實際 status=" + resp.status() + "，body=" + resp.body());
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

    private String payloadText(Map<String, Object> outboxRow, String field) {
        Object payload = outboxRow.get("payload");
        if (payload == null) {
            return null;
        }
        String json = payload instanceof String s ? s : payload.toString();
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
        } catch (RuntimeException e) {
            throw new AssertionError("解析 outbox payload 失敗：" + json, e);
        }
    }

    private static int indexOfFrom(List<String> list, String value, int from) {
        for (int i = from; i < list.size(); i++) {
            if (value.equals(list.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int[] parseRatio(String ratio) {
        String[] parts = ratio.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("合格率格式預期「分子 / 分母」，收到: " + ratio);
        }
        return new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }
}
