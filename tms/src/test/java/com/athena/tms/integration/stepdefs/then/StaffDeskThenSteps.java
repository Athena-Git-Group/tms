package com.athena.tms.integration.stepdefs.then;

import com.athena.tms.common.codes.TaskEventType;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import io.cucumber.java.en.Then;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.athena.tms.integration.stepdefs.utils.TestData.TENANT_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StaffDeskThenSteps {

    private final ScenarioContext ctx;
    private final DbSupport db;
    private final ApiClient api;
    private final ObjectMapper objectMapper;

    public StaffDeskThenSteps(ScenarioContext ctx, DbSupport db, ApiClient api,
                              ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.db = db;
        this.api = api;
        this.objectMapper = objectMapper;
    }

    @Then("^恰有一人成功、另一人的請求應該失敗，回應為 409$")
    public void exactlyOneClaimWins() {
        List<Integer> statuses = ctx.rejectionStatuses().stream().sorted().toList();
        assertEquals(List.of(200, 409), statuses, "恰一人成功、另一人 409（條件式 UPDATE 守衛）");
    }

    @Then("^任務 event_seq 僅遞增一次：created 的 seq 為 1、claimed 的 seq 為 2，輸家不佔號$")
    public void eventSeqIncrementedOnce() {
        Map<String, Object> row = db.taskRow(ctx.taskId());
        assertEquals(TaskState.ACCEPTED, row.get("state"));
        assertEquals(2, ((Number) row.get("event_seq")).intValue(), "輸家不佔號");
        assertEquals(List.of(TaskEventType.CREATED, TaskEventType.CLAIMED),
                db.eventTypes(ctx.taskId()));
    }

    @Then("^state 依序為 \"doing\"、\"done\"$")
    public void statesAreDoingThenDone() {
        JsonNode doing = json(ctx.get("startedResponse"));
        assertEquals(TaskState.DOING, doing.get("state").asText());
        HttpResult completed = ctx.get("completedResponse");
        assertEquals(200, completed.status(), "完成該成功: " + completed.body());
        assertEquals(TaskState.DONE, json(completed).get("state").asText());
    }

    @Then("^started_at 與 done_at 被記錄$")
    public void timestampsRecordedWithoutDerivedColumn() {
        JsonNode doing = json(ctx.get("startedResponse"));
        assertFalse(doing.get("startedAt").isNull(), "start 記 started_at");
        JsonNode done = json(ctx.get("completedResponse"));
        assertFalse(done.get("doneAt").isNull(), "complete 記 done_at");
        assertFalse(done.has("actualMinutes"), "實際分鐘不落欄、不另外填（FR-012）");
        Map<String, Object> row = db.admin().queryForMap(
                "SELECT started_at, done_at FROM tasks WHERE id = ?", ctx.taskId());
        Timestamp startedAt = (Timestamp) row.get("started_at");
        Timestamp doneAt = (Timestamp) row.get("done_at");
        assertNotNull(startedAt);
        assertNotNull(doneAt);
        assertFalse(doneAt.before(startedAt), "done_at ≥ started_at 才能相減出工時");
    }

    @Then("^事件 \"started\" 與 \"completed\" 寫入$")
    public void startedAndCompletedEventsWritten() {
        List<String> types = db.eventTypes(ctx.taskId());
        assertTrue(types.contains(TaskEventType.STARTED) && types.contains(TaskEventType.COMPLETED),
                "事件 started/completed 寫入: " + types);
    }

    @Then("^完成成功$")
    public void completeSucceeded() {
        HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "跳過備品項仍可完成（FR-013）: " + last.body());
        assertEquals(TaskState.DONE, json(last).get("state").asText());
    }

    @Then("^勾記共 (\\d+) 筆且「([^」]+)」留痕為跳過，記錄操作者、時間與 label 快照$")
    public void sopChecksRecorded(int expected, String skippedLabel) {
        Integer total = db.admin().queryForObject(
                "SELECT count(*) FROM task_sop_checks WHERE task_id = ?", Integer.class, ctx.taskId());
        assertEquals(expected, total.intValue(), "三勾一跳共 " + expected + " 筆勾記");
        Map<String, Object> skipped = db.admin().queryForMap(
                "SELECT checked, skipped, checked_by, checked_at, label FROM task_sop_checks"
                        + " WHERE task_id = ? AND item_key = ?",
                ctx.taskId(), ctx.sopItemKey(skippedLabel));
        assertEquals(Boolean.TRUE, skipped.get("skipped"));
        assertEquals(Boolean.FALSE, skipped.get("checked"));
        assertEquals(ctx.<UUID>get("sopActorId").toString(), skipped.get("checked_by").toString(),
                "誰跳過的要留痕");
        assertNotNull(skipped.get("checked_at"), "幾點動的要留痕");
        assertEquals(skippedLabel, skipped.get("label"), "label 用 sop_items 快照");
    }

    @Then("^每次勾選與跳過都寫入事件 \"sop_checked\"$")
    public void everySopActionWritesEvent() {
        assertEquals(4, db.eventCountOfType(ctx.taskId(), TaskEventType.SOP_CHECKED),
                "每次勾選與跳過都要寫入事件 sop_checked（FR-013）");
    }

    @Then("^清單含「([^」]+)」但不含「([^」]+)」且全是 pool 狀態的房務單$")
    public void hk1PoolFiltered(String expectedTitle, String excludedTitle) {
        JsonNode pool = objectMapper.readTree(ctx.<String>get("poolBody:小美"));
        assertTrue(containsId(pool, taskIdByTitle(expectedTitle)),
                "房務看得到「" + expectedTitle + "」");
        assertFalse(containsId(pool, taskIdByTitle(excludedTitle)),
                "房務看不到工務的單");
        for (JsonNode task : pool) {
            assertEquals(TaskState.POOL, task.get("state").asText(), "池清單只含 pool 狀態");
            assertEquals("housekeeping", task.get("dept").asText(), "池清單只含本部門");
        }
    }

    @Then("^「阿強」查詢待接池時清單含「([^」]+)」但不含「([^」]+)」$")
    public void mt1PoolFiltered(String expectedTitle, String excludedTitle) {
        HttpResult pool = api.get("/staff/pool", api.staffToken(TENANT_1, ctx.staffLogin("阿強")));
        assertEquals(200, pool.status(), pool.body());
        JsonNode body = objectMapper.readTree(pool.body());
        assertTrue(containsId(body, taskIdByTitle(expectedTitle)), "工務看得到修繕單");
        assertFalse(containsId(body, taskIdByTitle(excludedTitle)), "工務看不到房務的單");
    }

    // ---------------------------------------------------------------- desk

    @Then("^該任務仍停在 \"triage\"$")
    public void taskStillInTriage() {
        assertEquals(TaskState.TRIAGE, db.taskRow(ctx.taskId()).get("state"), "被拒的判讀不得改狀態");
    }

    @Then("^房務「小美」的待接池立刻看得到這單$")
    public void housekeepingPoolSeesTaskImmediately() {
        HttpResult pool = api.get("/staff/pool", api.staffToken(TENANT_1, ctx.staffLogin("小美")));
        assertEquals(200, pool.status(), pool.body());
        assertTrue(containsId(objectMapper.readTree(pool.body()), ctx.taskId()),
                "判讀後房務池立刻看得到這單");
    }

    @Then("^回應的任務 \"([^\"]+)\" 的 doneAt 應有值$")
    public void responseDoneAtPresent(String symbol) {
        HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "判讀該成功: " + last.body());
        assertEquals(ctx.entityId(symbol).toString(), json(last).get("id").asText(),
                "回應的任務要是 " + symbol);
        assertFalse(json(last).get("doneAt").isNull(), "doneAt 該有值");
    }

    @Then("^清單含標題「([^」]+)」且其狀態為 \"([a-z_]+)\"$")
    public void deskListContainsTitleWithState(String title, String state) {
        JsonNode all = objectMapper.readTree(ctx.<String>get("deskTasksBody"));
        UUID expectedId = taskIdByTitle(title);
        JsonNode found = null;
        for (JsonNode task : all) {
            if (expectedId.toString().equals(task.get("id").asText())) {
                found = task;
                break;
            }
        }
        assertNotNull(found, "清單含標題「" + title + "」: " + all);
        assertEquals(state, found.get("state").asText(), "標題「" + title + "」的狀態");
    }

    @Then("^待判讀清單只含 triage 的任務$")
    public void triageListOnlyContainsTriage() {
        HttpResult triage = api.get("/desk/triage",
                api.staffToken(TENANT_1, ctx.staffLogin("櫃檯")));
        assertEquals(200, triage.status(), triage.body());
        JsonNode list = objectMapper.readTree(triage.body());
        assertTrue(containsId(list, taskIdInState("triage")), "待判讀清單含 triage 任務");
        assertFalse(containsId(list, taskIdInState("pool")),
                "待判讀清單不含已在池中的任務，只回 triage");
    }

    /** 場景開跑前任務表已清空，標題即可唯一定位本場景造的那張單。 */
    private UUID taskIdByTitle(String title) {
        return db.admin().queryForObject(
                "SELECT id FROM tasks WHERE tenant_id = 1 AND title = ? LIMIT 1",
                UUID.class, title);
    }

    /** 場景開跑前任務表已清空，狀態即可唯一定位本場景造的那張單。 */
    private UUID taskIdInState(String state) {
        return db.admin().queryForObject(
                "SELECT id FROM tasks WHERE tenant_id = 1 AND state = ? LIMIT 1",
                UUID.class, state);
    }

    private JsonNode json(HttpResult result) {
        return objectMapper.readTree(result.body());
    }

    private static boolean containsId(JsonNode array, UUID id) {
        for (JsonNode node : array) {
            if (id.toString().equals(node.get("id").asText())) {
                return true;
            }
        }
        return false;
    }
}
