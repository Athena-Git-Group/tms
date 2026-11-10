package com.athena.tms.bdd.steps.inspection;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.OutboxAwait;
import com.athena.tms.bdd.support.PmsProbe;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.service.JwtService;
import io.cucumber.java.ParameterType;
import io.cucumber.java.zh_tw.那麼;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 查房模組的 Then（查房/dsl.md Then 句型區）。查 tasks／task_events／outbox 這些權威資料，
 * 並在 dsl.md 點名「跨視角／再讀確認」之處另外驗證。
 */
public class InspectionThenSteps {

    // 查房/dsl.md「查房結果用語」表只收錄查房「結果」的中譯，「房況」是另一組詞彙
    // （root dsl.md「判定查房全部項目通過」列：pms_status=inspected（已整理）），
    // Terms 共用對照表尚未收錄，這裡只收本模組用得到的這一個值，查不到一律噴錯。
    private static final Map<String, String> ROOM_STATUS = Map.of("已整理（Inspected）", "inspected");

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final OutboxAwait outboxAwait;
    private final PmsProbe pmsProbe;
    private final TenantFixtures tenantFixtures;
    private final MutableClock clock;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public InspectionThenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            OutboxAwait outboxAwait,
            PmsProbe pmsProbe,
            TenantFixtures tenantFixtures,
            MutableClock clock,
            JwtService jwtService,
            ObjectMapper objectMapper) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.outboxAwait = outboxAwait;
        this.pmsProbe = pmsProbe;
        this.tenantFixtures = tenantFixtures;
        this.clock = clock;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    /** dsl.md「事件清單」：以頓號分隔、各自加引號的 task_events.type 清單，例如 "a"、"b"。 */
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

        pmsProbe.lastWritebackFor(roomNo);
    }

    @那麼("{string} 沒有回寫 PMS，同步紀錄記一筆未回寫")
    public void taskDoesNotWriteBackPms(String roomNo) {
        boolean hasWriteback = dbProbe.outboxRows("pms_status").stream()
                .anyMatch(r -> roomNo.equals(payloadText(r, "roomNo")));
        if (hasWriteback) {
            throw new AssertionError("「" + roomNo + "」預期沒有回寫 PMS，但 outbox 找到 kind=pms_status 的資料");
        }

        pmsProbe.lastWritebackFor(roomNo);
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
