package com.athena.tms.bdd.steps.taskexecution;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.service.JwtService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 任務執行模組的 Then（dsl.md Then 句型區）。每句除了查 tasks／task_events／
 * task_sop_checks 這些權威資料，還在 dsl.md 點名「跨視角／再讀確認」時另外呼叫一次
 * 對應的員工 API 驗證——身分是任務當下的負責人或部門同仁，不透過 StaffFixtures
 * （那個入口只服務 Given 建的員工），而是現場用 JwtService 幫 DB 裡查到的員工簽一張新權杖。
 */
public class TaskExecutionThenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final TenantFixtures tenantFixtures;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public TaskExecutionThenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            TenantFixtures tenantFixtures,
            JwtService jwtService,
            ObjectMapper objectMapper) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.tenantFixtures = tenantFixtures;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @那麼("房務待接池不包含 {string}")
    public void poolDoesNotContain(String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        Map<String, Object> row = requireTaskRow(taskId);
        if ("pool".equals(row.get("state"))) {
            throw new AssertionError("tasks.state 預期不是 pool，實際為 pool（任務「" + taskKey + "」）");
        }

        long tenantId = tenantFixtures.testTenantId();
        String dept = String.valueOf(row.get("dept"));
        String jwt = mintJwtForDeptStaff(tenantId, dept);
        ScenarioWorld.ApiResponse resp = apiClient.get("/pool", BddApiClient.Auth.staff(jwt));
        if (resp.status() != 200 || resp.json() == null) {
            throw new AssertionError("GET /pool 預期 200，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        for (JsonNode item : resp.json().path("items")) {
            String itemTaskId = textOrNull(item, "taskId");
            if (taskId.toString().equalsIgnoreCase(itemTaskId)) {
                throw new AssertionError("GET /pool 預期看不到「" + taskKey + "」，實際有（taskId=" + itemTaskId + "）");
            }
        }
    }

    @那麼("{string} 已勾選的 SOP 項為：")
    public void taskCheckedSopItemsAre(String taskKey, DataTable table) {
        UUID taskId = resolveTaskId(taskKey);
        long tenantId = tenantFixtures.testTenantId();
        Set<String> expectedLabels = labelsFromTable(table);

        Set<String> actualLabels = dbProbe
                .queryMaps("SELECT label FROM task_sop_checks WHERE task_id = ? AND checked = true", taskId)
                .stream()
                .map(r -> String.valueOf(r.get("label")))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!expectedLabels.equals(actualLabels)) {
            throw new AssertionError("task_sop_checks.checked 的項目預期 " + expectedLabels + "，實際 " + actualLabels);
        }

        Set<String> expectedIds = expectedLabels.stream()
                .map(label -> sopItemId(tenantId, label).toString().toLowerCase())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Map<String, Object> row = requireTaskRow(taskId);
        withAssigneeReadTasks(taskId, row, json -> {
            Set<String> actualIds = toUuidStringSet(json.path("checkedSopItems"));
            if (!expectedIds.equals(actualIds)) {
                throw new AssertionError("readTasks.checkedSopItems 預期 " + expectedIds + "，實際 " + actualIds);
            }
        });
    }

    @那麼("{string} 的實際工時為 {int} 分鐘")
    public void taskActualWorkedMinutesIs(String taskKey, int minutes) {
        UUID taskId = resolveTaskId(taskKey);
        Map<String, Object> row = requireTaskRow(taskId);
        long workedSec = toLong(row.getOrDefault("worked_sec", 0));
        long actualMinutes = workedSec / 60;
        if (actualMinutes != minutes) {
            throw new AssertionError(
                    "tasks.worked_sec 換算分鐘預期 " + minutes + "，實際 " + actualMinutes + "（worked_sec=" + workedSec + "）");
        }

        long eventsMinutes = sumDoingMinutesFromEvents(taskId);
        if (eventsMinutes != minutes) {
            throw new AssertionError("task_events 各段進行中時間總和預期 " + minutes + " 分鐘，實際 " + eventsMinutes + " 分鐘");
        }

        withAssigneeReadTasks(taskId, row, json -> {
            int actual = json.path("accumulatedMinutes").asInt(-1);
            if (actual != minutes) {
                throw new AssertionError("readTasks.accumulatedMinutes 預期 " + minutes + "，實際 " + actual);
            }
        });
    }

    @那麼("{string} 記錄為跳過的 SOP 項為：")
    public void taskSkippedSopItemsAre(String taskKey, DataTable table) {
        UUID taskId = resolveTaskId(taskKey);
        Set<String> expectedLabels = labelsFromTable(table);

        Set<String> actualLabels = dbProbe
                .queryMaps("SELECT label FROM task_sop_checks WHERE task_id = ? AND skipped = true", taskId)
                .stream()
                .map(r -> String.valueOf(r.get("label")))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!expectedLabels.equals(actualLabels)) {
            throw new AssertionError("task_sop_checks.skipped 的項目預期 " + expectedLabels + "，實際 " + actualLabels);
        }

        Map<String, Object> completedEvent = dbProbe.eventsOf(taskId).stream()
                .filter(e -> "completed".equals(e.get("type")))
                .reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("task_events 找不到 completed 事件（任務「" + taskKey + "」）"));
        JsonNode payload = parsePayload(completedEvent.get("payload"));
        Set<String> payloadSkipped = skippedLabelsFromPayload(payload);
        if (!expectedLabels.equals(payloadSkipped)) {
            throw new AssertionError(
                    "completed 事件 payload 記錄的跳過項預期 " + expectedLabels + "，實際 " + payloadSkipped
                            + "（payload=" + completedEvent.get("payload") + "）");
        }
    }

    private long sumDoingMinutesFromEvents(UUID taskId) {
        long totalSeconds = 0;
        Instant openStart = null;
        for (Map<String, Object> event : dbProbe.eventsOf(taskId)) {
            String type = String.valueOf(event.get("type"));
            Instant at = toInstant(event.get("created_at"));
            if ("started".equals(type) || "resumed".equals(type)) {
                openStart = at;
            } else if (openStart != null && ("interrupted".equals(type) || "completed".equals(type))) {
                totalSeconds += Duration.between(openStart, at).getSeconds();
                openStart = null;
            }
        }
        return totalSeconds / 60;
    }

    private UUID sopItemId(long tenantId, String label) {
        return dbProbe
                .queryOne(
                        "SELECT id FROM sop_items WHERE tenant_id = ? AND applies_to = 'cleaning' AND label = ?",
                        tenantId, label)
                .map(row -> (UUID) row.get("id"))
                .orElseThrow(() -> new IllegalStateException(
                        "sop_items 查無標籤「" + label + "」（applies_to=cleaning），請先在 Given 建立"));
    }

    private String mintJwtForDeptStaff(long tenantId, String dept) {
        Map<String, Object> staff = dbProbe
                .queryOne(
                        "SELECT id, role, dept FROM staff_accounts WHERE tenant_id = ? AND dept = ? AND active = true ORDER BY id LIMIT 1",
                        tenantId, dept)
                .orElseThrow(() -> new IllegalStateException("找不到部門「" + dept + "」的在職員工，無法跨視角驗證待接池"));
        UUID staffId = UUID.fromString(String.valueOf(staff.get("id")));
        return jwtService.issue(tenantId, staffId, (String) staff.get("role"), (String) staff.get("dept")).token();
    }

    /**
     * 待接池等沒有負責人的任務，dsl.md「以負責人或主管身分」沒有自然對應的身分可用；
     * 跳過跨視角檢查，不杜撰一個不存在的操作者。
     */
    private void withAssigneeReadTasks(UUID taskId, Map<String, Object> taskRow, Consumer<JsonNode> check) {
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

    private JsonNode parsePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        String json = payload instanceof String s ? s : String.valueOf(payload);
        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException e) {
            throw new AssertionError("解析 task_events.payload 失敗：" + json, e);
        }
    }

    /**
     * 「完成時，未勾選的 SOP 項記錄為跳過」這句 Then 要求 completed 事件的 payload
     * 也記錄跳過項，但這個 payload 鍵名目前還沒有正式契約——data-model.dbml 只點名
     * cancelled／deferred／interrupted／inspection_failed 幾種事件的 payload 形狀，
     * 沒提到 completed。這裡依 sop_checked 事件既有的 label 慣例，嘗試幾個合理的鍵名；
     * 都找不到就視為沒有記錄任何跳過項，讓上層比對自然失敗並印出實際 payload，
     * 而不是在這裡假裝猜對一個不存在的欄位。
     */
    private Set<String> skippedLabelsFromPayload(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return Set.of();
        }
        for (String key : List.of("skipped_items", "skippedItems", "skipped_labels")) {
            JsonNode node = payload.get(key);
            if (node != null && node.isArray()) {
                Set<String> out = new LinkedHashSet<>();
                for (JsonNode item : node) {
                    if (item.isTextual()) {
                        out.add(item.asText());
                    } else if (item.has("label")) {
                        out.add(item.get("label").asText());
                    }
                }
                return out;
            }
        }
        return Set.of();
    }

    private static Set<String> labelsFromTable(DataTable table) {
        Set<String> labels = new LinkedHashSet<>();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String label = row.get("項目");
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("Data Table 缺少必填欄位「項目」：" + row);
            }
            labels.add(label);
        }
        return labels;
    }

    private static Set<String> toUuidStringSet(JsonNode arrayNode) {
        Set<String> out = new LinkedHashSet<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode n : arrayNode) {
                out.add(n.asText().toLowerCase());
            }
        }
        return out;
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
}
