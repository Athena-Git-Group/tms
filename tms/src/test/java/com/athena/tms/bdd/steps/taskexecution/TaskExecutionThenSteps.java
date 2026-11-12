package com.athena.tms.bdd.steps.taskexecution;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.service.JwtService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class TaskExecutionThenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final TenantFixtures tenantFixtures;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final MutableClock clock;

    public TaskExecutionThenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            TenantFixtures tenantFixtures,
            JwtService jwtService,
            ObjectMapper objectMapper,
            MutableClock clock) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.tenantFixtures = tenantFixtures;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    private static final List<String> UNFINISHED_STATES = List.of("doing", "interrupted", "deferred");

    @那麼("房務待認領不包含 {string}")
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

    @那麼("{string} 沒有停住原因")
    public void taskHasNoHoldReason(String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        Map<String, Object> row = requireTaskRow(taskId);
        Object holdReason = row.get("hold_reason");
        if (holdReason != null) {
            throw new AssertionError(
                    "tasks.hold_reason 預期為 NULL，實際為「" + holdReason + "」（任務「" + taskKey + "」）");
        }

        withAssigneeReadTasks(taskId, row, json -> {
            String actual = textOrNull(json, "holdReason");
            if (actual != null) {
                throw new AssertionError("readTasks.holdReason 預期為 null，實際為「" + actual + "」");
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

    @那麼("完成回報的今日進度為第 {int} 單")
    public void completeResponseCleaningProgressIs(int ordinal) {
        JsonNode completeJson = requireCompleteResponseJson();
        if (!completeJson.has("cleaningDoneToday") || completeJson.get("cleaningDoneToday").isNull()) {
            throw new AssertionError(
                    "完成回報預期帶 cleaningDoneToday＝" + ordinal
                            + "，實際沒有這個欄位（或值為 null）："
                            + world.lastResponse().body());
        }
        int reported = completeJson.get("cleaningDoneToday").asInt(Integer.MIN_VALUE);
        String completedTaskIdText = textOrNull(completeJson, "id");
        if (completedTaskIdText == null) {
            throw new AssertionError(
                    "完成回報沒有任務 id，無法判斷是誰完成了哪一張單："
                            + world.lastResponse().body());
        }

        if (reported != ordinal) {
            throw new AssertionError(
                    "完成回報的 cleaningDoneToday 預期 " + ordinal + "，實際 " + reported);
        }

        UUID completedTaskId = UUID.fromString(completedTaskIdText);
        Map<String, Object> completedRow = requireTaskRow(completedTaskId);
        Object assigneeId = completedRow.get("assignee_id");
        if (assigneeId == null) {
            throw new AssertionError(
                    "剛完成的任務沒有負責人，無法重算呼叫者的今日進度（taskId=" + completedTaskId + "）");
        }
        UUID callerId = UUID.fromString(String.valueOf(assigneeId));
        Object workDate = completedRow.get("work_date");

        long recounted = dbProbe.count(
                "SELECT count(*) FROM tasks WHERE tenant_id = ? AND assignee_id = ? AND work_date = ?"
                        + " AND type = 'cleaning' AND state IN ('done', 'inspected')",
                tenantFixtures.testTenantId(), callerId, workDate);
        if (recounted != ordinal) {
            throw new AssertionError(
                    "tasks 重算今日（work_date=" + workDate + "）已結案的打掃單預期 " + ordinal
                            + " 張，實際 " + recounted + " 張");
        }

        ScenarioWorld.ApiResponse today =
                apiClient.get("/me/today", BddApiClient.Auth.staff(mintJwtFor(callerId)));
        if (today.status() != 200 || today.json() == null) {
            throw new AssertionError(
                    "getMyToday 預期 200，實際 status=" + today.status() + "，body=" + today.body());
        }
        int cleaningDone = today.json().path("cleaningDone").asInt(Integer.MIN_VALUE);
        if (cleaningDone != ordinal) {
            throw new AssertionError(
                    "getMyToday 的 cleaningDone 預期 " + ordinal + "，實際 " + cleaningDone);
        }
    }

    @那麼("完成回報不含今日進度")
    public void completeResponseHasNoCleaningProgress() {
        JsonNode json = requireCompleteResponseJson();
        if (json.has("cleaningDoneToday")) {
            JsonNode value = json.get("cleaningDoneToday");
            throw new AssertionError(
                    "完成回報預期沒有 cleaningDoneToday 欄位，實際有（值為 "
                            + (value.isNull() ? "null" : value.toString()) + "）：" + world.lastResponse().body());
        }
    }

    @那麼("完成回報的下一步是待開始的 {string}")
    public void completeResponseNextStepIsAcceptedTask(String roomNo) {
        JsonNode json = requireCompleteResponseJson();
        String completeBody = world.lastResponse().body();
        UUID expectedTaskId = UUID.fromString(world.taskId(roomNo));

        JsonNode nextStep = json.get("nextStep");
        if (nextStep == null || nextStep.isNull()) {
            throw new AssertionError("完成回報預期帶 nextStep，實際沒有：" + completeBody);
        }
        String kind = textOrNull(nextStep, "kind");
        if (!"next_task".equals(kind)) {
            throw new AssertionError("nextStep.kind 預期 next_task，實際 " + kind + "：" + completeBody);
        }
        String actualRoomNo = textOrNull(nextStep, "roomNo");
        if (!roomNo.equals(actualRoomNo)) {
            throw new AssertionError("nextStep.roomNo 預期「" + roomNo + "」，實際「" + actualRoomNo + "」");
        }
        String actualTaskId = textOrNull(nextStep, "taskId");
        if (actualTaskId == null || !expectedTaskId.toString().equalsIgnoreCase(actualTaskId)) {
            throw new AssertionError(
                    "nextStep.taskId 預期指向「" + roomNo + "」今日的任務 " + expectedTaskId + "，實際 " + actualTaskId);
        }

        Map<String, Object> row = requireTaskRow(expectedTaskId);
        if (!"accepted".equals(row.get("state"))) {
            throw new AssertionError(
                    "tasks.state 預期 accepted，實際 " + row.get("state") + "（房號「" + roomNo + "」）");
        }
        UUID callerId = completedTaskAssigneeId();
        if (!callerId.toString().equalsIgnoreCase(String.valueOf(row.get("assignee_id")))) {
            throw new AssertionError(
                    "tasks.assignee_id 預期為完成回報的呼叫者 " + callerId + "，實際 " + row.get("assignee_id"));
        }

        ScenarioWorld.ApiResponse today =
                apiClient.get("/me/today", BddApiClient.Auth.staff(mintJwtFor(callerId)));
        if (today.status() != 200 || today.json() == null) {
            throw new AssertionError(
                    "getMyToday 預期 200，實際 status=" + today.status() + "，body=" + today.body());
        }
        JsonNode firstNext = today.json().path("nextTasks").path(0);
        if (firstNext.isMissingNode() || firstNext.isNull()) {
            throw new AssertionError("getMyToday 的 nextTasks 預期至少有一筆，實際是空的：" + today.body());
        }
        String firstRoomNo = textOrNull(firstNext, "roomNo");
        if (!roomNo.equals(firstRoomNo)) {
            throw new AssertionError(
                    "完成回報指向「" + roomNo + "」，但 getMyToday 的 nextTasks 第一筆是「" + firstRoomNo + "」——兩處指向不同房間");
        }
    }

    private JsonNode requireCompleteResponseJson() {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        if (resp.status() != 200 || resp.json() == null) {
            throw new AssertionError(
                    "上一次完成回報預期 200 且有 JSON body，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        return resp.json();
    }

    @那麼("完成回報的下一步是還沒做完的 {string}")
    public void completeNextStepIsUnfinishedTask(String roomNo) {
        JsonNode json = requireCompleteResponseJson();
        JsonNode nextStep = json.get("nextStep");
        if (nextStep == null || nextStep.isNull()) {
            throw new AssertionError("完成回報預期帶 nextStep，實際沒有：" + world.lastResponse().body());
        }
        String kind = textOrNull(nextStep, "kind");
        if (!"unfinished_task".equals(kind)) {
            throw new AssertionError(
                    "nextStep.kind 預期 unfinished_task，實際為 " + kind + "：" + world.lastResponse().body());
        }
        String actualRoomNo = textOrNull(nextStep, "roomNo");
        if (!roomNo.equals(actualRoomNo)) {
            throw new AssertionError("nextStep.roomNo 預期「" + roomNo + "」，實際為「" + actualRoomNo + "」");
        }

        UUID taskId = resolveTaskId(roomNo);
        Map<String, Object> row = requireTaskRow(taskId);
        String state = String.valueOf(row.get("state"));
        if (!UNFINISHED_STATES.contains(state)) {
            throw new AssertionError("tasks.state 預期為 doing、interrupted 或 deferred 其中之一，實際為 "
                    + state + "（房號「" + roomNo + "」）");
        }

        Object assigneeId = row.get("assignee_id");
        if (assigneeId == null) {
            throw new AssertionError(
                    "tasks.assignee_id 預期是這次完成回報的呼叫者，實際為 NULL（房號「" + roomNo + "」）");
        }
        UUID callerId = UUID.fromString(String.valueOf(assigneeId));

        List<Map<String, Object>> accepted = todayTasksInStates(callerId, List.of("accepted"));
        if (!accepted.isEmpty()) {
            throw new AssertionError("呼叫者今日預期沒有待開始（accepted）的任務，實際還有 "
                    + describeTasks(accepted) + "——還有待開始的單時，下一步應該是那一張（next_task）");
        }

        List<Map<String, Object>> unfinished = todayTasksInStates(callerId, UNFINISHED_STATES);
        if (unfinished.isEmpty()) {
            throw new AssertionError("呼叫者今日預期還有未結束的任務，實際一張都沒有");
        }
        String firstRoomNo = String.valueOf(unfinished.get(0).get("room_no"));
        if (!roomNo.equals(firstRoomNo)) {
            throw new AssertionError("依 doing→interrupted→deferred、同組房號遞增排序，第一筆預期是房號「"
                    + roomNo + "」，實際是「" + firstRoomNo + "」（今日未結束的單依序為 "
                    + describeTasks(unfinished) + "）");
        }
    }

    private List<Map<String, Object>> todayTasksInStates(UUID callerId, List<String> states) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(states.size(), "?"));
        List<Object> params = new java.util.ArrayList<>();
        params.add(tenantFixtures.testTenantId());
        params.add(callerId);
        params.addAll(states);
        params.add(java.sql.Date.valueOf(today()));
        return dbProbe.queryMaps(
                "SELECT t.state AS state, r.room_no AS room_no"
                        + " FROM tasks t JOIN rooms r ON r.id = t.room_id AND r.tenant_id = t.tenant_id"
                        + " WHERE t.tenant_id = ? AND t.assignee_id = ?"
                        + " AND t.state IN (" + placeholders + ")"
                        + " AND (t.work_date = ? OR t.work_date IS NULL)"
                        + " ORDER BY CASE t.state WHEN 'doing' THEN 0 WHEN 'interrupted' THEN 1"
                        + " WHEN 'deferred' THEN 2 ELSE 3 END, r.room_no",
                params.toArray());
    }

    private static String describeTasks(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(r -> r.get("room_no") + "（" + r.get("state") + "）")
                .collect(java.util.stream.Collectors.joining("、"));
    }

    @那麼("完成回報說今天排定的房都清完了")
    public void completionReportSaysAllClear() {
        JsonNode nextStep = requireCompletionNextStep();
        requireNextStepKind(nextStep, "all_clear");
        requireNextStepFieldAbsent(nextStep, "taskId");
        requireNextStepFieldAbsent(nextStep, "roomNo");

        UUID callerId = completedTaskAssigneeId();
        LocalDate today = today();
        List<Map<String, Object>> leftovers = dbProbe.queryMaps(
                """
                SELECT t.state AS state, r.room_no AS room_no
                  FROM tasks t JOIN rooms r ON r.id = t.room_id AND r.tenant_id = t.tenant_id
                 WHERE t.tenant_id = ? AND t.assignee_id = ?
                   AND t.state IN ('accepted', 'doing', 'interrupted', 'deferred')
                   AND (t.work_date IS NULL OR t.work_date = ?)
                 ORDER BY r.room_no
                """,
                tenantFixtures.testTenantId(), callerId, today);
        if (!leftovers.isEmpty()) {
            String detail = leftovers.stream()
                    .map(row -> row.get("room_no") + "（" + row.get("state") + "）")
                    .collect(java.util.stream.Collectors.joining("、"));
            throw new AssertionError(
                    "nextStep.kind 回了 all_clear，但呼叫者今日還有待開始或沒做完的任務："
                            + detail + "（呼叫者 assignee_id=" + callerId + "、今天=" + today + "）");
        }
    }

    private JsonNode requireCompletionNextStep() {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        if (resp.status() != 200 || resp.json() == null) {
            throw new AssertionError(
                    "tasksComplete 預期 200，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        JsonNode nextStep = resp.json().get("nextStep");
        if (nextStep == null || nextStep.isNull() || !nextStep.isObject()) {
            throw new AssertionError("tasksComplete 回應沒有 nextStep 物件，實際 body=" + resp.body());
        }
        return nextStep;
    }

    private void requireNextStepKind(JsonNode nextStep, String expectedKind) {
        String actualKind = textOrNull(nextStep, "kind");
        if (!expectedKind.equals(actualKind)) {
            throw new AssertionError(
                    "nextStep.kind 預期 " + expectedKind + "，實際 "
                            + (actualKind == null ? "沒有 kind 欄位" : actualKind)
                            + "（nextStep=" + nextStep + "）");
        }
    }

    private void requireNextStepFieldAbsent(JsonNode nextStep, String field) {
        if (nextStep.has(field)) {
            JsonNode value = nextStep.get(field);
            throw new AssertionError(
                    "nextStep.kind 為 all_clear 時不該帶 " + field + "，實際 " + field + "="
                            + (value.isNull() ? "null（欄位存在但為 null，仍算帶了）" : value)
                            + "（nextStep=" + nextStep + "）");
        }
    }

    private LocalDate today() {
        return clock.instant().atZone(clock.getZone()).toLocalDate();
    }

    private UUID completedTaskAssigneeId() {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        String completedTaskId = textOrNull(resp.json(), "id");
        if (completedTaskId == null) {
            throw new AssertionError("tasksComplete 回應沒有任務 id，無法確認呼叫者是誰，body=" + resp.body());
        }
        Map<String, Object> row = requireTaskRow(UUID.fromString(completedTaskId));
        Object assigneeId = row.get("assignee_id");
        if (assigneeId == null) {
            throw new AssertionError("剛完成的任務沒有 assignee_id，無法確認呼叫者是誰：" + completedTaskId);
        }
        return UUID.fromString(String.valueOf(assigneeId));
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
                .orElseThrow(() -> new IllegalStateException("找不到部門「" + dept + "」的在職員工，無法跨視角驗證待認領"));
        UUID staffId = UUID.fromString(String.valueOf(staff.get("id")));
        return jwtService.issue(tenantId, staffId, (String) staff.get("role"), (String) staff.get("dept")).token();
    }

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
