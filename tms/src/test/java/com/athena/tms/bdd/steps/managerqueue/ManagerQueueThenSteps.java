package com.athena.tms.bdd.steps.managerqueue;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * 主管工作台模組的 Then（dsl.md Then 句型區）：格子數字、待查房與待驗收兩類清單的順序與內容、
 * 今天或不是今天的標記、非主管被拒。
 *
 * <p>每句的「呈現結果」看 {@link ScenarioWorld#lastManagerQueuesResponse()}；「權威狀態」
 * 依 data-model.dbml tasks Note 的兩條公式直接查 tasks 與 repair_tickets 重算：
 * 待查房＝type=cleaning 且 state=done 且 assignee_id 不是呼叫者；待驗收＝type=repair 且
 * state=done 且 repair_tickets.resolution_type 不是 no_action_needed。
 *
 * <p>工務主管的回應沒有 {@code inspection} 與 {@code counts.inspection} 兩個鍵，
 * 所以跨清單找列時，缺少的清單直接略過，不當成格式錯誤。
 */
public class ManagerQueueThenSteps {

    private static final Map<String, String> COUNT_SLOT = Map.of(
            "待查房", "inspection",
            "待驗收", "verify");

    // 對照見 dsl.md「待驗收清單中 {單號} 的完修結果為 {結果}、來源為 {來源}」的參數說明，
    // 值集對齊 repair_tickets.origin 的 CHECK；完修結果的對照在 Terms.resolutionType。
    private static final Map<String, String> ORIGIN = Map.of(
            "報修", "reported",
            "定期保養", "scheduled");

    private final ScenarioWorld world;
    private final DbProbe dbProbe;
    private final ManagerQueueWhenSteps whenSteps;
    private final TenantFixtures tenantFixtures;
    private final MutableClock clock;

    public ManagerQueueThenSteps(
            ScenarioWorld world,
            DbProbe dbProbe,
            ManagerQueueWhenSteps whenSteps,
            TenantFixtures tenantFixtures,
            MutableClock clock) {
        this.world = world;
        this.dbProbe = dbProbe;
        this.whenSteps = whenSteps;
        this.tenantFixtures = tenantFixtures;
        this.clock = clock;
    }

    @那麼("主管待辦的格子數字為：")
    public void countsAre(DataTable table) {
        JsonNode body = okBody();
        JsonNode counts = body.path("counts");
        if (!counts.isObject()) {
            throw new AssertionError("回應缺少 counts：" + world.lastManagerQueuesResponse().body());
        }
        List<Map<String, String>> rows = table.asMaps();
        Set<String> expectedSlots = new TreeSet<>();
        for (Map<String, String> row : rows) {
            expectedSlots.add(required(COUNT_SLOT, row.get("格"), "格"));
        }
        Set<String> actualSlots = new TreeSet<>(counts.propertyNames());
        if (!actualSlots.equals(expectedSlots)) {
            throw new AssertionError("counts 的格子預期剛好是 " + expectedSlots + "，實際 " + actualSlots + "：" + counts);
        }
        for (Map<String, String> row : rows) {
            String slot = required(COUNT_SLOT, row.get("格"), "格");
            int expected = Integer.parseInt(row.get("數字").trim());
            int actual = counts.path(slot).asInt(-1);
            if (actual != expected) {
                throw new AssertionError("counts." + slot + " 預期 " + expected + "，實際 " + actual);
            }
            int listSize = body.path(slot).size();
            if (listSize != actual) {
                throw new AssertionError("counts." + slot + "=" + actual + " 與 " + slot + "[] 長度 " + listSize + " 不一致");
            }
            long recomputed = recount(slot);
            if (recomputed != expected) {
                throw new AssertionError("依 tasks Note 公式重算 " + slot + " 為 " + recomputed + "，預期 " + expected);
            }
        }
    }

    private long recount(String slot) {
        long tenantId = tenantFixtures.testTenantId();
        return switch (slot) {
            case "inspection" -> dbProbe.count(
                    "SELECT count(*) FROM tasks WHERE tenant_id = ? AND type = 'cleaning' AND state = 'done'"
                            + " AND assignee_id IS DISTINCT FROM ?",
                    tenantId, whenSteps.lastCallerStaffId());
            case "verify" -> dbProbe.count(
                    "SELECT count(*) FROM tasks t JOIN repair_tickets r ON r.task_id = t.id"
                            + " WHERE t.tenant_id = ? AND t.type = 'repair' AND t.state = 'done'"
                            + " AND r.resolution_type IS DISTINCT FROM 'no_action_needed'",
                    tenantId);
            default -> throw new IllegalArgumentException(slot);
        };
    }

    @那麼("主管待辦沒有待查房這一類")
    public void noInspectionCategory() {
        JsonNode body = okBody();
        if (body.has("inspection")) {
            throw new AssertionError("回應不應有 inspection 鍵（空陣列也不行）：" + body);
        }
        JsonNode counts = body.path("counts");
        if (!counts.isObject()) {
            throw new AssertionError("回應缺少 counts：" + body);
        }
        if (counts.has("inspection")) {
            throw new AssertionError("counts 不應有 inspection 鍵（0 也不行）：" + counts);
        }
    }

    @那麼("待查房清單依序為 {}")
    public void inspectionQueueIs(String roomList) {
        List<JsonNode> rows = listRows("inspection");
        assertOrder("inspection[].roomNo", "roomNo", rows, split(roomList));

        UUID caller = whenSteps.lastCallerStaffId();
        Instant previous = null;
        for (JsonNode row : rows) {
            Map<String, Object> task = taskRow(row);
            String roomNo = text(row, "roomNo");
            check(roomNo + " tasks.type", "cleaning", task.get("type"));
            check(roomNo + " tasks.state", "done", task.get("state"));
            Object assigneeId = task.get("assignee_id");
            if (assigneeId == null || caller.toString().equalsIgnoreCase(assigneeId.toString())) {
                throw new AssertionError("待查房 " + roomNo + " 的負責人是呼叫者本人（assignee_id=" + assigneeId + "）");
            }
            check(roomNo + " title", task.get("title"), text(row, "title"));
            check(roomNo + " cleanerName", staffName(assigneeId), text(row, "cleanerName"));
            Instant waitingSince = waitingSince(row);
            checkInstant(roomNo + " waitingSince 應等於 tasks.done_at", toInstant(task.get("done_at")), waitingSince);
            previous = assertAscending("待查房", roomNo, previous, waitingSince);
        }
    }

    @那麼("待查房清單不包含 {string}")
    public void inspectionQueueExcludes(String roomNo) {
        assertAbsent("inspection", "roomNo", roomNo);
    }

    @那麼("待驗收清單依序為 {}")
    public void verifyQueueIs(String ticketList) {
        List<JsonNode> rows = listRows("verify");
        assertOrder("verify[].ticketNo", "ticketNo", rows, split(ticketList));

        Instant previous = null;
        JsonNode previousRow = null;
        for (JsonNode row : rows) {
            String ticketNo = text(row, "ticketNo");
            requireFields(row, ticketNo, "category", "origin", "resolutionType", "assigneeName", "wasVerificationFailed");
            boolean publicArea = row.path("isPublicArea").asBoolean(false);
            JsonNode roomNo = row.path("roomNo");
            boolean roomNoNull = roomNo.isMissingNode() || roomNo.isNull();
            if (publicArea != roomNoNull) {
                throw new AssertionError(ticketNo + " roomNo 與 isPublicArea 不一致：roomNo=" + roomNo
                        + "，isPublicArea=" + publicArea);
            }
            Map<String, Object> task = taskRow(row);
            check(ticketNo + " tasks.type", "repair", task.get("type"));
            check(ticketNo + " tasks.state", "done", task.get("state"));
            Map<String, Object> ticket = repairTicketRow(task);
            if ("no_action_needed".equals(ticket.get("resolution_type"))) {
                throw new AssertionError(ticketNo + " 的完修結果是 no_action_needed，不應出現在待驗收");
            }
            Instant waitingSince = waitingSince(row);
            checkInstant(ticketNo + " waitingSince 應等於最近一次完修的 tasks.done_at",
                    toInstant(task.get("done_at")), waitingSince);
            if (previousRow != null && waitingSince.equals(waitingSince(previousRow))) {
                assertSameInstantTieBreak(previousRow, row);
            }
            previous = assertAscending("待驗收", ticketNo, previous, waitingSince);
            previousRow = row;
        }
    }

    /**
     * 待驗收同一時刻完修的兩列，依房號再依單號由小到大。
     *
     * <p>公共區域的單沒有房號，規格沒有規定它和有房號的單誰先。只要有一邊沒有房號，
     * 這一對就不判定先後，以免把規格沒寫的順序寫死在測試裡。
     */
    private static void assertSameInstantTieBreak(JsonNode before, JsonNode after) {
        String roomBefore = text(before, "roomNo");
        String roomAfter = text(after, "roomNo");
        String ticketBefore = text(before, "ticketNo");
        String ticketAfter = text(after, "ticketNo");
        if (roomBefore == null || roomAfter == null) {
            return;
        }
        int byRoom = roomBefore.compareTo(roomAfter);
        if (byRoom > 0 || (byRoom == 0 && ticketBefore.compareTo(ticketAfter) > 0)) {
            throw new AssertionError("待驗收同一時刻應依房號再依單號排序：" + ticketBefore + "（" + roomBefore + "）排在 "
                    + ticketAfter + "（" + roomAfter + "）前面");
        }
    }

    @那麼("待驗收清單不包含 {string}")
    public void verifyQueueExcludes(String ticketNo) {
        assertAbsent("verify", "ticketNo", ticketNo);
    }

    @那麼("待驗收清單中 {string} 的完修結果為 {string}、來源為 {string}")
    public void verifyRowResolutionAndOrigin(String ticketNo, String resultChinese, String originChinese) {
        String expectedResolution = Terms.resolutionType(resultChinese);
        String expectedOrigin = required(ORIGIN, originChinese, "來源");
        JsonNode row = findRow(listRows("verify"), "ticketNo", ticketNo)
                .orElseThrow(() -> new AssertionError("待驗收清單沒有 " + ticketNo + "：" + listBody("verify")));
        check(ticketNo + " resolutionType", expectedResolution, text(row, "resolutionType"));
        check(ticketNo + " origin", expectedOrigin, text(row, "origin"));

        Map<String, Object> task = taskRow(row);
        Map<String, Object> ticket = repairTicketRow(task);
        check(ticketNo + " repair_tickets.resolution_type", expectedResolution, ticket.get("resolution_type"));
        // repair_tickets.origin 資料模型只在說明裡提到、還沒有欄位；003 以 tasks.source 推導
        // （system＝scheduled、其餘＝reported），正式加欄前以推導值當權威。
        String originFromSource = "system".equals(task.get("source")) ? "scheduled" : "reported";
        check(ticketNo + " 由 tasks.source 推導的 origin", expectedOrigin, originFromSource);
    }

    @那麼("待驗收清單中 {string} 標為重修後再驗")
    public void verifyRowMarkedReverify(String ticketNo) {
        JsonNode row = findRow(listRows("verify"), "ticketNo", ticketNo)
                .orElseThrow(() -> new AssertionError("待驗收清單沒有 " + ticketNo + "：" + listBody("verify")));
        JsonNode flag = row.path("wasVerificationFailed");
        if (!flag.isBoolean() || !flag.asBoolean()) {
            throw new AssertionError(ticketNo + " 的 wasVerificationFailed 預期 true，實際 " + flag);
        }
        Map<String, Object> task = taskRow(row);
        Map<String, Object> ticket = repairTicketRow(task);
        Instant failedAt = toInstant(ticket.get("last_verify_failed_at"));
        Instant doneAt = toInstant(task.get("done_at"));
        if (failedAt == null) {
            throw new AssertionError(ticketNo + " 的 repair_tickets.last_verify_failed_at 應有值");
        }
        if (doneAt == null || !failedAt.isBefore(doneAt)) {
            throw new AssertionError(ticketNo + " 的 last_verify_failed_at（" + failedAt + "）應早於這一次完修的 tasks.done_at（"
                    + doneAt + "）");
        }
    }

    @那麼("{string} 在清單上標為不是今天開始等")
    public void markedNotToday(String key) {
        JsonNode row = anyQueueRow(key);
        if (row.path("waitingSinceIsToday").asBoolean(true)) {
            throw new AssertionError(key + " 的 waitingSinceIsToday 預期 false，實際 " + row.path("waitingSinceIsToday"));
        }
        LocalDate day = waitingSince(row).atZone(clock.getZone()).toLocalDate();
        LocalDate today = LocalDate.now(clock);
        if (!day.isBefore(today)) {
            throw new AssertionError(key + " 的 waitingSince 落在 " + day + "，應早於今天 " + today + "（時區 " + clock.getZone() + "）");
        }
    }

    @那麼("{string} 在清單上標為今天開始等")
    public void markedToday(String key) {
        JsonNode row = anyQueueRow(key);
        if (!row.path("waitingSinceIsToday").asBoolean(false)) {
            throw new AssertionError(key + " 的 waitingSinceIsToday 預期 true，實際 " + row.path("waitingSinceIsToday"));
        }
        LocalDate day = waitingSince(row).atZone(clock.getZone()).toLocalDate();
        LocalDate today = LocalDate.now(clock);
        if (!day.equals(today)) {
            throw new AssertionError(key + " 的 waitingSince 落在 " + day + "，應與今天 " + today + " 同一天（時區 " + clock.getZone() + "）");
        }
    }

    @那麼("這次查詢被拒絕，因為呼叫者不是主管")
    public void queryRejectedNotManager() {
        ScenarioWorld.ApiResponse resp = world.lastManagerQueuesResponse();
        if (resp.status() != 403) {
            throw new AssertionError("預期 403，實際狀態碼=" + resp.status() + "，body=" + resp.body());
        }
        JsonNode json = resp.json();
        if (json == null || !json.has("code") || !json.has("message")) {
            throw new AssertionError("403 回應不符合 contracts Error（需要 code 與 message）：" + resp.body());
        }
        check("403 回應的 code", "forbidden", text(json, "code"));
    }

    private JsonNode okBody() {
        ScenarioWorld.ApiResponse resp = world.lastManagerQueuesResponse();
        if (resp.status() != 200) {
            throw new AssertionError("預期 200，實際狀態碼=" + resp.status() + "，body=" + resp.body());
        }
        if (resp.json() == null) {
            throw new AssertionError("回應沒有 JSON body：" + resp.body());
        }
        return resp.json();
    }

    private List<JsonNode> listRows(String slot) {
        JsonNode list = okBody().path(slot);
        if (!list.isArray()) {
            throw new AssertionError("回應的 " + slot + " 不是陣列：" + world.lastManagerQueuesResponse().body());
        }
        List<JsonNode> rows = new ArrayList<>();
        list.forEach(rows::add);
        return rows;
    }

    private String listBody(String slot) {
        return okBody().path(slot).toString();
    }

    private static Optional<JsonNode> findRow(List<JsonNode> rows, String field, String value) {
        return rows.stream().filter(r -> value.equals(text(r, field))).findFirst();
    }

    /** 以房號或工務單號在待查房與待驗收清單裡找那一列；找不到即斷言失敗。 */
    private JsonNode anyQueueRow(String key) {
        Optional<JsonNode> inspection = okBody().has("inspection")
                ? findRow(listRows("inspection"), "roomNo", key)
                : Optional.empty();
        return inspection
                .or(() -> findRow(listRows("verify"), "ticketNo", key))
                .orElseThrow(() -> new AssertionError("待查房與待驗收清單都沒有「" + key + "」：" + okBody()));
    }

    private void assertOrder(String label, String field, List<JsonNode> rows, List<String> expected) {
        List<String> actual = rows.stream().map(r -> text(r, field)).toList();
        if (!actual.equals(expected)) {
            throw new AssertionError(label + " 預期依序為 " + expected + "，實際 " + actual);
        }
    }

    private void assertAbsent(String slot, String field, String value) {
        List<JsonNode> rows = listRows(slot);
        if (findRow(rows, field, value).isPresent()) {
            throw new AssertionError(slot + "[] 不應包含「" + value + "」，實際 " + listBody(slot));
        }
    }

    private static void requireFields(JsonNode row, String key, String... fields) {
        for (String field : fields) {
            JsonNode v = row.path(field);
            if (v.isMissingNode() || v.isNull()) {
                throw new AssertionError(key + " 這列缺少 " + field + "：" + row);
            }
        }
    }

    private static Instant waitingSince(JsonNode row) {
        String raw = text(row, "waitingSince");
        if (raw == null) {
            throw new AssertionError("這列沒有 waitingSince：" + row);
        }
        return OffsetDateTime.parse(raw).toInstant();
    }

    private static Instant assertAscending(String queue, String key, Instant previous, Instant current) {
        if (previous != null && current.isBefore(previous)) {
            throw new AssertionError(queue + " 清單沒有由舊到新：「" + key + "」的 waitingSince " + current + " 早於前一列 " + previous);
        }
        return current;
    }

    private static List<String> split(String list) {
        return Arrays.stream(list.split("、")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private Map<String, Object> taskRow(JsonNode row) {
        String taskId = text(row, "taskId");
        if (taskId == null) {
            throw new AssertionError("這列沒有 taskId：" + row);
        }
        return dbProbe.taskRow(UUID.fromString(taskId))
                .orElseThrow(() -> new AssertionError("tasks 找不到 taskId=" + taskId));
    }

    private Map<String, Object> repairTicketRow(Map<String, Object> task) {
        return dbProbe.queryOne("SELECT * FROM repair_tickets WHERE task_id = ?", task.get("id"))
                .orElseThrow(() -> new AssertionError("repair_tickets 找不到 task_id=" + task.get("id")));
    }

    private String staffName(Object staffId) {
        return dbProbe.queryOne("SELECT name FROM staff_accounts WHERE id = ?", staffId)
                .map(r -> Objects.toString(r.get("name"), null))
                .orElseThrow(() -> new AssertionError("staff_accounts 找不到 id=" + staffId));
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof ZonedDateTime zdt) {
            return zdt.toInstant();
        }
        return OffsetDateTime.parse(value.toString()).toInstant();
    }

    private static void checkInstant(String label, Instant expected, Instant actual) {
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(label + "：預期 " + expected + "，實際 " + actual);
        }
    }

    private static void check(String label, Object expected, Object actual) {
        String e = expected == null ? null : expected.toString();
        String a = actual == null ? null : actual.toString();
        if (!Objects.equals(e, a)) {
            throw new AssertionError(label + " 預期 " + e + "，實際 " + a);
        }
    }

    private static String required(Map<String, String> map, String key, String kind) {
        String v = key == null ? null : map.get(key.trim());
        if (v == null) {
            throw new IllegalArgumentException("dsl.md 未定義的" + kind + "「" + key + "」，可用值：" + map.keySet());
        }
        return v;
    }
}
