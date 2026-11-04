package com.athena.tms.bdd.steps.pool;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.service.JwtService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * 待認領模組的 Then（待認領/dsl.md Then 句型區）。
 *
 * <p>「待認領順序為」「標示同樓層的單為」「待認領中…資訊如下」「待認領分類筆數如下」
 * 這四句都接在前一句「查看待認領」之後，斷言的是那次呼叫留在 {@link ScenarioWorld#lastResponse()}
 * 的 {@code DeptPool} JSON，不另外重打一次 API（比照 dsl.md 根目錄 Then 句型的既有模式）。
 * 其餘幾句是獨立的權威狀態斷言，各自視需要另外呼叫一次 API 核對「跨視角」。
 */
public class PoolThenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final MutableClock clock;
    private final TenantFixtures tenantFixtures;
    private final JwtService jwtService;

    public PoolThenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            MutableClock clock,
            TenantFixtures tenantFixtures,
            JwtService jwtService) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.clock = clock;
        this.tenantFixtures = tenantFixtures;
        this.jwtService = jwtService;
    }

    @那麼("^待認領順序為 (.+)$")
    public void poolOrderIs(String roomNoList) {
        // dsl.md（待認領/dsl.md）：呈現結果只核對 DeptPool.items[].roomNo 的最終順序與
        // 房號清單完全一致（不多不少）。排序條件本身（未接超時的單排最前、彼此間依房號，
        // 其餘依優先通知、同樓層、房號）是產品碼（PoolService）的職責，這裡不重新實作
        // 排序規則，只核對呈現結果。
        List<String> expected = splitList(roomNoList);
        List<String> actual = poolItems().stream().map(item -> textOrNull(item, "roomNo")).toList();
        if (!expected.equals(actual)) {
            throw new AssertionError("待認領順序預期 " + expected + "，實際 " + actual);
        }
    }

    @那麼("^標示同樓層的單為 (.+)$")
    public void sameFloorMarkedAs(String roomNoList) {
        Set<String> expected = new LinkedHashSet<>(splitList(roomNoList));
        Set<String> actual = new LinkedHashSet<>();
        for (JsonNode item : poolItems()) {
            if (item.path("sameFloor").asBoolean(false)) {
                actual.add(textOrNull(item, "roomNo"));
            }
        }
        if (!expected.equals(actual)) {
            throw new AssertionError("標示同樓層的單預期 " + expected + "，實際 " + actual);
        }
    }

    @那麼("待認領中 {string} 的資訊如下：")
    public void poolItemInfo(String taskKey, DataTable table) {
        JsonNode item = findPoolItem(taskKey);
        Map<String, Object> taskRow = requireTaskRow(UUID.fromString(world.taskId(taskKey)));

        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String field = requireCell(row, "項目");
            String expected = requireCell(row, "值");
            switch (field) {
                case "來源" -> {
                    String expectedSource = Terms.poolSource(expected);
                    check("PoolItem.source", expectedSource, textOrNull(item, "source"));
                    check("tasks.pool_source", expectedSource, taskRow.get("pool_source"));
                }
                case "原因" -> {
                    check("PoolItem.returnReason", expected, textOrNull(item, "returnReason"));
                    check("tasks.return_reason", expected, taskRow.get("return_reason"));
                }
                case "接手進度" -> {
                    check("PoolItem.handoverNote", expected, textOrNull(item, "handoverNote"));
                    check("tasks.requeue_note", expected, taskRow.get("requeue_note"));
                }
                case "說明" -> {
                    // PoolItem 的 contracts schema 沒有承載「規則引擎命中：…」這句話的欄位
                    // （只有 taskId/roomNo/.../returnReason/handoverNote），呈現結果這半沒有
                    // 對應欄位可核對；只核對 dsl.md 點名的權威狀態 tasks.rule_hit。
                    String rulePrefix = "規則引擎命中：";
                    String expectedRuleHit = expected.startsWith(rulePrefix)
                            ? expected.substring(rulePrefix.length())
                            : expected;
                    check("tasks.rule_hit", expectedRuleHit, taskRow.get("rule_hit"));
                }
                default -> throw new IllegalArgumentException("待認領資訊不支援的項目: " + field);
            }
        }
    }

    @那麼("待認領中 {string} 的位置顯示為 \"公共區域\"")
    public void poolItemLocationIsPublicArea(String taskKey) {
        JsonNode item = findPoolItem(taskKey);
        String roomNo = textOrNull(item, "roomNo");
        if (!"公共區域".equals(roomNo)) {
            throw new AssertionError("PoolItem.roomNo 預期顯示「公共區域」，實際 " + roomNo);
        }

        // dsl.md：權威狀態是 tasks.room_id 指向公共區域佔位房（rooms.room_no＝公共區域、floor＝0），
        // 不屬於任何客房。
        Map<String, Object> taskRow = requireTaskRow(UUID.fromString(world.taskId(taskKey)));
        Map<String, Object> roomRow =
                dbProbe.queryOne("SELECT room_no, floor FROM rooms WHERE id = ?", taskRow.get("room_id"))
                        .orElseThrow(() -> new AssertionError("tasks.room_id 查無對應 rooms 列"));
        check("rooms.room_no", "公共區域", roomRow.get("room_no"));
        check("rooms.floor", 0, ((Number) roomRow.get("floor")).intValue());
    }

    @那麼("待認領分類筆數如下：")
    public void poolCategoryCounts(DataTable table) {
        JsonNode counts = world.lastResponse().json().path("counts");
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String categoryChinese = requireCell(row, "類別");
            int expected = Integer.parseInt(requireCell(row, "筆數").trim());
            String field = "全部".equals(categoryChinese) ? "all" : Terms.poolCategory(categoryChinese);
            int actual = counts.path(field).asInt(Integer.MIN_VALUE);
            if (actual != expected) {
                throw new AssertionError("DeptPool.counts." + field + " 預期 " + expected + "，實際 " + actual);
            }
        }
    }

    @那麼("{string} 的準備工單在 {string} 名下，狀態為 {string}")
    public void prepTaskAssignedToStaffWithState(String roomNo, String staffName, String stateChinese) {
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        UUID expectedStaffId = UUID.fromString(world.staffId(staffName));
        String expectedState = Terms.taskState(stateChinese);
        Map<String, Object> row = requireTaskRow(taskId);

        check("tasks.dept", "housekeeping", row.get("dept"));
        check("tasks.state", expectedState, row.get("state"));
        if (!expectedStaffId.toString().equalsIgnoreCase(String.valueOf(row.get("assignee_id")))) {
            throw new AssertionError(
                    "tasks.assignee_id 預期 " + expectedStaffId + "，實際 " + row.get("assignee_id"));
        }

        ScenarioWorld.ApiResponse resp =
                apiClient.get("/me/today", BddApiClient.Auth.staff(world.jwt(staffName)));
        if (resp.status() != 200 || resp.json() == null) {
            throw new AssertionError(
                    "getMyToday 預期 200，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        boolean found = false;
        for (JsonNode next : resp.json().path("nextTasks")) {
            if (roomNo.equals(textOrNull(next, "roomNo"))) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new AssertionError(
                    staffName + " 呼叫 getMyToday 的 nextTasks 裡看不到「" + roomNo + "」");
        }
    }

    @那麼("{string} 的準備工單在房務待認領")
    public void prepTaskInHousekeepingPool(String roomNo) {
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        Map<String, Object> row = requireTaskRow(taskId);
        check("tasks.dept", "housekeeping", row.get("dept"));
        check("tasks.state", "pool", row.get("state"));
        if (row.get("assignee_id") != null) {
            throw new AssertionError("tasks.assignee_id 預期為空，實際 " + row.get("assignee_id"));
        }

        requirePoolItemVisibleToHousekeeping(item -> roomNo.equals(textOrNull(item, "roomNo")),
                "房號「" + roomNo + "」");
    }

    @那麼("{string} 的排程工單在房務待認領")
    public void scheduledTaskInHousekeepingPool(String content) {
        UUID taskId = UUID.fromString(world.taskId(content));
        Map<String, Object> row = requireTaskRow(taskId);
        check("tasks.dept", "housekeeping", row.get("dept"));
        check("tasks.state", "pool", row.get("state"));
        check("tasks.pool_source", "system", row.get("pool_source"));
        check("tasks.title", content, row.get("title"));

        requirePoolItemVisibleToHousekeeping(item -> content.equals(textOrNull(item, "title")),
                "內容「" + content + "」");
    }

    @那麼("今天 {string} 規則只建立一張工單")
    public void ruleCreatesOnlyOneTaskToday(String ruleName) {
        long tenantId = tenantFixtures.testTenantId();
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        long count = dbProbe.count(
                """
                SELECT count(*) FROM tasks
                 WHERE tenant_id = ? AND rule_hit = ? AND created_at >= ? AND created_at < ?
                """,
                tenantId, ruleName, startOfDay(today), startOfDay(today.plusDays(1)));
        if (count != 1) {
            throw new AssertionError(
                    "今天「" + ruleName + "」規則建立的工單預期 1 張，實際 " + count + " 張");
        }
    }

    @那麼("{string} 今天只有一張準備工單")
    public void onlyOnePrepTaskToday(String roomNo) {
        long tenantId = tenantFixtures.testTenantId();
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        long count = dbProbe.count(
                """
                SELECT count(*) FROM tasks t
                  JOIN rooms r ON r.id = t.room_id AND r.tenant_id = t.tenant_id
                 WHERE t.tenant_id = ? AND r.room_no = ? AND t.pool_source = 'system'
                   AND t.created_at >= ? AND t.created_at < ?
                """,
                tenantId, roomNo, startOfDay(today), startOfDay(today.plusDays(1)));
        if (count != 1) {
            throw new AssertionError(
                    "「" + roomNo + "」今天由自動化規則建立的準備工單預期 1 張，實際 " + count + " 張");
        }
    }

    @那麼("{string} 的準備工單的到期時間為 {string}")
    public void prepTaskDueAtIs(String roomNo, String hhmm) {
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        Map<String, Object> row = requireTaskRow(taskId);
        OffsetDateTime expected = todayAt(hhmm);
        Object dueAt = row.get("due_at");
        OffsetDateTime actual = dueAt == null ? null : toOffsetDateTime(dueAt);
        if (actual == null || actual.toInstant().compareTo(expected.toInstant()) != 0) {
            throw new AssertionError("tasks.due_at 預期 " + expected + "，實際 " + actual);
        }
        // dsl.md 的「再讀確認」要呼叫 readTasks，但待認領任務沒有負責人，沒有自然對應的
        // 身分可用（比照 CommonThenSteps.withAssigneeReadTasks 同樣的理由），這裡跳過，
        // 不杜撰一個不存在的操作者。
    }

    private List<JsonNode> poolItems() {
        // JsonNode 本身就是 Iterable<JsonNode>（比照 CommonThenSteps 既有的
        // for-each 用法），先收集成 List 讓呼叫端能用 stream／多次走訪。
        List<JsonNode> result = new java.util.ArrayList<>();
        for (JsonNode item : world.lastResponse().json().path("items")) {
            result.add(item);
        }
        return result;
    }

    private JsonNode findPoolItem(String taskKey) {
        for (JsonNode item : poolItems()) {
            if (taskKey.equals(textOrNull(item, "roomNo")) || taskKey.equals(textOrNull(item, "title"))) {
                return item;
            }
        }
        throw new AssertionError("待認領回應裡找不到「" + taskKey + "」");
    }

    /** 以在職的房務員身分呼叫 listDeptPool，核對回應裡有一筆符合 predicate 的 PoolItem。 */
    private void requirePoolItemVisibleToHousekeeping(
            java.util.function.Predicate<JsonNode> predicate, String describe) {
        String jwt = mintJwtForAnyActiveStaff("housekeeping");
        ScenarioWorld.ApiResponse resp = apiClient.get("/pool", BddApiClient.Auth.staff(jwt));
        if (resp.status() != 200 || resp.json() == null) {
            throw new AssertionError(
                    "listDeptPool 預期 200，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        boolean found = false;
        for (JsonNode item : resp.json().path("items")) {
            if (predicate.test(item)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new AssertionError("listDeptPool 看不到" + describe + "的待認領任務");
        }
    }

    private String mintJwtForAnyActiveStaff(String dept) {
        long tenantId = tenantFixtures.testTenantId();
        Map<String, Object> staff = dbProbe
                .queryOne(
                        "SELECT id FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND active = true LIMIT 1",
                        tenantId, dept)
                .orElseThrow(() -> new IllegalStateException("找不到在職的「" + dept + "」員工，無法模擬跨視角呼叫"));
        UUID staffId = UUID.fromString(String.valueOf(staff.get("id")));
        return jwtService.issue(tenantId, staffId, dept, dept).token();
    }

    private Map<String, Object> requireTaskRow(UUID taskId) {
        return dbProbe.taskRow(taskId).orElseThrow(() -> new AssertionError("tasks 查無此任務：" + taskId));
    }

    private OffsetDateTime todayAt(String hhmm) {
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        return ZonedDateTime.of(today, LocalTime.parse(hhmm), clock.getZone()).toOffsetDateTime();
    }

    private OffsetDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay(clock.getZone()).toOffsetDateTime();
    }

    private static OffsetDateTime toOffsetDateTime(Object v) {
        if (v instanceof Timestamp ts) {
            return ts.toInstant().atZone(java.time.ZoneOffset.UTC).toOffsetDateTime();
        }
        if (v instanceof OffsetDateTime odt) {
            return odt;
        }
        throw new IllegalStateException("預期時間型別，實際 " + (v == null ? "null" : v.getClass()));
    }

    private static List<String> splitList(String raw) {
        return Arrays.stream(raw.split("、")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static void check(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v;
    }
}
