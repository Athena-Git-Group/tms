package com.athena.tms.integration.stepdefs.then;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import io.cucumber.java.en.Then;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
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

    @Then(value="^恰有一人成功、另一人的請求應該失敗，回應為 409$")
    public void exactlyOneClaimWins() {
        List statuses = this.ctx.rejectionStatuses().stream().sorted().toList();
        Assertions.assertEquals(List.of(Integer.valueOf(200), Integer.valueOf(409)), statuses, (String)"恰一人成功、另一人 409（條件式 UPDATE 守衛）");
    }

    @Then(value="^任務 event_seq 僅遞增一次：created 的 seq 為 1、claimed 的 seq 為 2，輸家不佔號$")
    public void eventSeqIncrementedOnce() {
        Map<String, Object> row = this.db.taskRow(this.ctx.taskId());
        Assertions.assertEquals((Object)"accepted", (Object)row.get("state"));
        Assertions.assertEquals((int)2, (int)((Number)row.get("event_seq")).intValue(), (String)"輸家不佔號");
        Assertions.assertEquals(List.of("created", "claimed"), this.db.eventTypes(this.ctx.taskId()));
    }

    @Then(value="^state 依序為 \"doing\"、\"done\"$")
    public void statesAreDoingThenDone() {
        JsonNode doing = this.json((ScenarioContext.HttpResult)this.ctx.get("startedResponse"));
        Assertions.assertEquals((Object)"doing", (Object)doing.get("state").asText());
        ScenarioContext.HttpResult completed = (ScenarioContext.HttpResult)this.ctx.get("completedResponse");
        Assertions.assertEquals((int)200, (int)completed.status(), (String)("完成該成功: " + completed.body()));
        Assertions.assertEquals((Object)"done", (Object)this.json(completed).get("state").asText());
    }

    @Then(value="^started_at 與 done_at 被記錄$")
    public void timestampsRecordedWithoutDerivedColumn() {
        JsonNode doing = this.json((ScenarioContext.HttpResult)this.ctx.get("startedResponse"));
        Assertions.assertFalse((boolean)doing.get("startedAt").isNull(), (String)"start 記 started_at");
        JsonNode done = this.json((ScenarioContext.HttpResult)this.ctx.get("completedResponse"));
        Assertions.assertFalse((boolean)done.get("doneAt").isNull(), (String)"complete 記 done_at");
        Assertions.assertFalse((boolean)done.has("actualMinutes"), (String)"實際分鐘不落欄、不另外填（FR-012）");
        Map row = this.db.admin().queryForMap("SELECT started_at, done_at FROM tasks WHERE id = ?", new Object[]{this.ctx.taskId()});
        Timestamp startedAt = (Timestamp)row.get("started_at");
        Timestamp doneAt = (Timestamp)row.get("done_at");
        Assertions.assertNotNull((Object)startedAt);
        Assertions.assertNotNull((Object)doneAt);
        Assertions.assertFalse((boolean)doneAt.before(startedAt), (String)"done_at ≥ started_at 才能相減出工時");
    }

    @Then(value="^事件 \"started\" 與 \"completed\" 寫入$")
    public void startedAndCompletedEventsWritten() {
        List<String> types = this.db.eventTypes(this.ctx.taskId());
        Assertions.assertTrue((types.contains("started") && types.contains("completed") ? 1 : 0) != 0, (String)("事件 started/completed 寫入: " + String.valueOf(types)));
    }

    @Then(value="^完成成功$")
    public void completeSucceeded() {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("跳過備品項仍可完成（FR-013）: " + last.body()));
        Assertions.assertEquals((Object)"done", (Object)this.json(last).get("state").asText());
    }

    @Then(value="^勾記共 (\\d+) 筆且「([^」]+)」留痕為跳過，記錄操作者、時間與 label 快照$")
    public void sopChecksRecorded(int expected, String skippedLabel) {
        Integer total = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM task_sop_checks WHERE task_id = ?", Integer.class, new Object[]{this.ctx.taskId()});
        Assertions.assertEquals((int)expected, (int)total, (String)("三勾一跳共 " + expected + " 筆勾記"));
        Map skipped = this.db.admin().queryForMap("SELECT checked, skipped, checked_by, checked_at, label FROM task_sop_checks WHERE task_id = ? AND item_key = ?", new Object[]{this.ctx.taskId(), this.ctx.sopItemKey(skippedLabel)});
        Assertions.assertEquals((Object)Boolean.TRUE, skipped.get("skipped"));
        Assertions.assertEquals((Object)Boolean.FALSE, skipped.get("checked"));
        Assertions.assertEquals((Object)((UUID)this.ctx.get("sopActorId")).toString(), (Object)skipped.get("checked_by").toString(), (String)"誰跳過的要留痕");
        Assertions.assertNotNull(skipped.get("checked_at"), (String)"幾點動的要留痕");
        Assertions.assertEquals((Object)skippedLabel, skipped.get("label"), (String)"label 用 sop_items 快照");
    }

    @Then(value="^每次勾選與跳過都寫入事件 \"sop_checked\"$")
    public void everySopActionWritesEvent() {
        Assertions.assertEquals((int)4, (int)this.db.eventCountOfType(this.ctx.taskId(), "sop_checked"), (String)"每次勾選與跳過都要寫入事件 sop_checked（FR-013）");
    }

    @Then(value="^清單含「([^」]+)」但不含「([^」]+)」且全是 pool 狀態的房務單$")
    public void hk1PoolFiltered(String expectedTitle, String excludedTitle) {
        JsonNode pool = this.objectMapper.readTree((String)this.ctx.get("poolBody:小美"));
        Assertions.assertTrue((boolean)StaffDeskThenSteps.containsId(pool, this.taskIdByTitle(expectedTitle)), (String)("房務看得到「" + expectedTitle + "」"));
        Assertions.assertFalse((boolean)StaffDeskThenSteps.containsId(pool, this.taskIdByTitle(excludedTitle)), (String)"房務看不到工務的單");
        for (JsonNode task : pool) {
            Assertions.assertEquals((Object)"pool", (Object)task.get("state").asText(), (String)"池清單只含 pool 狀態");
            Assertions.assertEquals((Object)"housekeeping", (Object)task.get("dept").asText(), (String)"池清單只含本部門");
        }
    }

    @Then(value="^「阿強」查詢待認領時清單含「([^」]+)」但不含「([^」]+)」$")
    public void mt1PoolFiltered(String expectedTitle, String excludedTitle) {
        ScenarioContext.HttpResult pool = this.api.get("/staff/pool", this.api.staffToken(1L, this.ctx.staffLogin("阿強")));
        Assertions.assertEquals((int)200, (int)pool.status(), (String)pool.body());
        JsonNode body = this.objectMapper.readTree(pool.body());
        Assertions.assertTrue((boolean)StaffDeskThenSteps.containsId(body, this.taskIdByTitle(expectedTitle)), (String)"工務看得到修繕單");
        Assertions.assertFalse((boolean)StaffDeskThenSteps.containsId(body, this.taskIdByTitle(excludedTitle)), (String)"工務看不到房務的單");
    }

    @Then(value="^該任務仍停在 \"triage\"$")
    public void taskStillInTriage() {
        Assertions.assertEquals((Object)"triage", (Object)this.db.taskRow(this.ctx.taskId()).get("state"), (String)"被拒的判讀不得改狀態");
    }

    @Then(value="^房務「小美」的待認領立刻看得到這單$")
    public void housekeepingPoolSeesTaskImmediately() {
        ScenarioContext.HttpResult pool = this.api.get("/staff/pool", this.api.staffToken(1L, this.ctx.staffLogin("小美")));
        Assertions.assertEquals((int)200, (int)pool.status(), (String)pool.body());
        Assertions.assertTrue((boolean)StaffDeskThenSteps.containsId(this.objectMapper.readTree(pool.body()), this.ctx.taskId()), (String)"判讀後房務池立刻看得到這單");
    }

    @Then(value="^回應的任務 \"([^\"]+)\" 的 doneAt 應有值$")
    public void responseDoneAtPresent(String symbol) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("判讀該成功: " + last.body()));
        Assertions.assertEquals((Object)this.ctx.entityId(symbol).toString(), (Object)this.json(last).get("id").asText(), (String)("回應的任務要是 " + symbol));
        Assertions.assertFalse((boolean)this.json(last).get("doneAt").isNull(), (String)"doneAt 該有值");
    }

    @Then(value="^清單含標題「([^」]+)」且其狀態為 \"([a-z_]+)\"$")
    public void deskListContainsTitleWithState(String title, String state) {
        JsonNode all = this.objectMapper.readTree((String)this.ctx.get("deskTasksBody"));
        UUID expectedId = this.taskIdByTitle(title);
        JsonNode found = null;
        for (JsonNode task : all) {
            if (!expectedId.toString().equals(task.get("id").asText())) continue;
            found = task;
            break;
        }
        Assertions.assertNotNull(found, (String)("清單含標題「" + title + "」: " + String.valueOf(all)));
        Assertions.assertEquals((Object)state, (Object)found.get("state").asText(), (String)("標題「" + title + "」的狀態"));
    }

    @Then(value="^待判讀清單只含 triage 的任務$")
    public void triageListOnlyContainsTriage() {
        ScenarioContext.HttpResult triage = this.api.get("/desk/triage", this.api.staffToken(1L, this.ctx.staffLogin("櫃檯")));
        Assertions.assertEquals((int)200, (int)triage.status(), (String)triage.body());
        JsonNode list = this.objectMapper.readTree(triage.body());
        Assertions.assertTrue((boolean)StaffDeskThenSteps.containsId(list, this.taskIdInState("triage")), (String)"待判讀清單含 triage 任務");
        Assertions.assertFalse((boolean)StaffDeskThenSteps.containsId(list, this.taskIdInState("pool")), (String)"待判讀清單不含已在池中的任務，只回 triage");
    }

    private UUID taskIdByTitle(String title) {
        return (UUID)this.db.admin().queryForObject("SELECT id FROM tasks WHERE tenant_id = 1 AND title = ? LIMIT 1", UUID.class, new Object[]{title});
    }

    private UUID taskIdInState(String state) {
        return (UUID)this.db.admin().queryForObject("SELECT id FROM tasks WHERE tenant_id = 1 AND state = ? LIMIT 1", UUID.class, new Object[]{state});
    }

    private JsonNode json(ScenarioContext.HttpResult result) {
        return this.objectMapper.readTree(result.body());
    }

    private static boolean containsId(JsonNode array, UUID id) {
        for (JsonNode node : array) {
            if (!id.toString().equals(node.get("id").asText())) continue;
            return true;
        }
        return false;
    }
}
