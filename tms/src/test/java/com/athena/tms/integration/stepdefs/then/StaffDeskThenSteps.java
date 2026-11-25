package com.athena.tms.integration.stepdefs.then;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.ScenarioKeys;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import io.cucumber.java.en.Then;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class StaffDeskThenSteps {
    private final ScenarioContext ctx;
    private final DbSupport db;
    private final ApiClient api;
    private final ObjectMapper objectMapper;

    public StaffDeskThenSteps(ScenarioContext ctx, DbSupport db, ApiClient api, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.db = db;
        this.api = api;
        this.objectMapper = objectMapper;
    }

    @Then("^state 依序為 \"doing\"、\"done\"$")
    public void statesAreDoingThenDone() {
        JsonNode doing = json(ctx.get(ScenarioKeys.STARTED_RESPONSE));
        assertEquals("doing", doing.get("state").asText());
        ScenarioContext.HttpResult completed = ctx.get(ScenarioKeys.COMPLETED_RESPONSE);
        assertEquals(200, completed.status(), "完成該成功: " + completed.body());
        assertEquals("done", json(completed).get("state").asText());
    }

    @Then("^started_at 與 done_at 被記錄$")
    public void timestampsRecordedWithoutDerivedColumn() {
        JsonNode done = json(ctx.get(ScenarioKeys.COMPLETED_RESPONSE));
        assertFalse(done.has("actualMinutes"), "實際分鐘不落欄、不另外填（FR-012）");
        Map<String, Object> row = db.admin().queryForMap("SELECT started_at, done_at FROM tasks WHERE id = ?", ctx.taskId());
        Timestamp startedAt = (Timestamp) row.get("started_at");
        Timestamp doneAt = (Timestamp) row.get("done_at");
        assertNotNull(startedAt);
        assertNotNull(doneAt);
        assertFalse(doneAt.before(startedAt), "done_at ≥ started_at 才能相減出工時");
    }

    @Then("^事件 \"started\" 與 \"completed\" 寫入$")
    public void startedAndCompletedEventsWritten() {
        List<String> types = db.eventTypes(ctx.taskId());
        assertTrue(types.contains("started") && types.contains("completed"), "事件 started/completed 寫入: " + types);
    }

    @Then("^完成成功$")
    public void completeSucceeded() {
        ScenarioContext.HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "跳過備品項仍可完成（FR-013）: " + last.body());
        assertEquals("done", json(last).get("state").asText());
    }

    @Then("^每個啟用的打掃 SOP 項各有一筆勾記、沒勾的記為跳過，「([^」]+)」留痕為跳過，記錄操作者、時間與 label 快照$")
    public void sopChecksRecorded(String skippedLabel) {
        int activeItems = db.admin().queryForObject(
                "SELECT count(*) FROM sop_items WHERE tenant_id = 1 AND applies_to = 'cleaning' AND active", Integer.class);
        int total = db.admin().queryForObject("SELECT count(*) FROM task_sop_checks WHERE task_id = ?", Integer.class, ctx.taskId());
        assertEquals(activeItems, total, "完成時每個啟用的打掃 SOP 項都要有一筆勾記");
        int untouchedNotSkipped = db.admin().queryForObject(
                "SELECT count(*) FROM task_sop_checks WHERE task_id = ? AND NOT checked AND NOT skipped", Integer.class, ctx.taskId());
        assertEquals(0, untouchedNotSkipped, "沒勾的 SOP 項在完成時記為跳過");
        Map<String, Object> skipped = db.admin().queryForMap(
                "SELECT checked, skipped, checked_by, checked_at, label FROM task_sop_checks WHERE task_id = ? AND item_key = ?",
                ctx.taskId(), ctx.sopItemKey(skippedLabel));
        assertEquals(Boolean.TRUE, skipped.get("skipped"));
        assertEquals(Boolean.FALSE, skipped.get("checked"));
        UUID sopActorId = ctx.get(ScenarioKeys.SOP_ACTOR_ID);
        assertEquals(sopActorId.toString(), skipped.get("checked_by").toString(), "誰跳過的要留痕");
        assertNotNull(skipped.get("checked_at"), "幾點動的要留痕");
        assertEquals(skippedLabel, skipped.get("label"), "label 用 sop_items 快照");
    }

    @Then("^每次勾選與跳過都寫入事件 \"sop_checked\"$")
    public void everySopActionWritesEvent() {
        assertEquals(4, db.eventCountOfType(ctx.taskId(), "sop_checked"), "每次勾選與跳過都要寫入事件 sop_checked（FR-013）");
    }

    @Then("^清單含「([^」]+)」但不含「([^」]+)」且全是 pool 狀態的房務單$")
    public void hk1PoolFiltered(String expectedTitle, String excludedTitle) {
        JsonNode items = poolItems(objectMapper.readTree(ctx.get(ScenarioKeys.poolBody("小美"))));
        assertTrue(containsTaskId(items, taskIdByTitle(expectedTitle)), "房務看得到「" + expectedTitle + "」");
        assertFalse(containsTaskId(items, taskIdByTitle(excludedTitle)), "房務看不到工務的單");
        for (JsonNode item : items) {
            Map<String, Object> row = db.admin().queryForMap(
                    "SELECT state, dept FROM tasks WHERE id = ?", UUID.fromString(item.get("taskId").asText()));
            assertEquals("pool", row.get("state"), "池清單只含 pool 狀態");
            assertEquals("housekeeping", row.get("dept"), "池清單只含本部門");
        }
    }

    @Then("^「阿強」查詢待認領時清單含「([^」]+)」但不含「([^」]+)」$")
    public void mt1PoolFiltered(String expectedTitle, String excludedTitle) {
        ScenarioContext.HttpResult pool = api.get("/api/v1/pool", api.staffToken(1L, ctx.staffLogin("阿強")));
        assertEquals(200, pool.status(), pool.body());
        JsonNode items = poolItems(objectMapper.readTree(pool.body()));
        assertTrue(containsTaskId(items, taskIdByTitle(expectedTitle)), "工務看得到修繕單");
        assertFalse(containsTaskId(items, taskIdByTitle(excludedTitle)), "工務看不到房務的單");
    }

    private UUID taskIdByTitle(String title) {
        return db.admin().queryForObject("SELECT id FROM tasks WHERE tenant_id = 1 AND title = ? LIMIT 1", UUID.class, title);
    }

    private JsonNode json(ScenarioContext.HttpResult result) {
        return objectMapper.readTree(result.body());
    }

    private static JsonNode poolItems(JsonNode body) {
        JsonNode items = body.get("items");
        assertNotNull(items, "待認領回應該有 items: " + body);
        return items;
    }

    private static boolean containsTaskId(JsonNode items, UUID id) {
        for (JsonNode item : items) {
            if (id.toString().equals(item.get("taskId").asText())) {
                return true;
            }
        }
        return false;
    }
}
