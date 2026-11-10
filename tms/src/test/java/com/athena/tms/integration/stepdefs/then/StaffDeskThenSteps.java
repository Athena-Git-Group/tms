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

    @Then(value="^\u6070\u6709\u4e00\u4eba\u6210\u529f\u3001\u53e6\u4e00\u4eba\u7684\u8acb\u6c42\u61c9\u8a72\u5931\u6557\uff0c\u56de\u61c9\u70ba 409$")
    public void exactlyOneClaimWins() {
        List statuses = this.ctx.rejectionStatuses().stream().sorted().toList();
        Assertions.assertEquals(List.of(Integer.valueOf(200), Integer.valueOf(409)), statuses, (String)"\u6070\u4e00\u4eba\u6210\u529f\u3001\u53e6\u4e00\u4eba 409\uff08\u689d\u4ef6\u5f0f UPDATE \u5b88\u885b\uff09");
    }

    @Then(value="^\u4efb\u52d9 event_seq \u50c5\u905e\u589e\u4e00\u6b21\uff1acreated \u7684 seq \u70ba 1\u3001claimed \u7684 seq \u70ba 2\uff0c\u8f38\u5bb6\u4e0d\u4f54\u865f$")
    public void eventSeqIncrementedOnce() {
        Map<String, Object> row = this.db.taskRow(this.ctx.taskId());
        Assertions.assertEquals((Object)"accepted", (Object)row.get("state"));
        Assertions.assertEquals((int)2, (int)((Number)row.get("event_seq")).intValue(), (String)"\u8f38\u5bb6\u4e0d\u4f54\u865f");
        Assertions.assertEquals(List.of("created", "claimed"), this.db.eventTypes(this.ctx.taskId()));
    }

    @Then(value="^state \u4f9d\u5e8f\u70ba \"doing\"\u3001\"done\"$")
    public void statesAreDoingThenDone() {
        JsonNode doing = this.json((ScenarioContext.HttpResult)this.ctx.get("startedResponse"));
        Assertions.assertEquals((Object)"doing", (Object)doing.get("state").asText());
        ScenarioContext.HttpResult completed = (ScenarioContext.HttpResult)this.ctx.get("completedResponse");
        Assertions.assertEquals((int)200, (int)completed.status(), (String)("\u5b8c\u6210\u8a72\u6210\u529f: " + completed.body()));
        Assertions.assertEquals((Object)"done", (Object)this.json(completed).get("state").asText());
    }

    @Then(value="^started_at \u8207 done_at \u88ab\u8a18\u9304$")
    public void timestampsRecordedWithoutDerivedColumn() {
        JsonNode doing = this.json((ScenarioContext.HttpResult)this.ctx.get("startedResponse"));
        Assertions.assertFalse((boolean)doing.get("startedAt").isNull(), (String)"start \u8a18 started_at");
        JsonNode done = this.json((ScenarioContext.HttpResult)this.ctx.get("completedResponse"));
        Assertions.assertFalse((boolean)done.get("doneAt").isNull(), (String)"complete \u8a18 done_at");
        Assertions.assertFalse((boolean)done.has("actualMinutes"), (String)"\u5be6\u969b\u5206\u9418\u4e0d\u843d\u6b04\u3001\u4e0d\u53e6\u5916\u586b\uff08FR-012\uff09");
        Map row = this.db.admin().queryForMap("SELECT started_at, done_at FROM tasks WHERE id = ?", new Object[]{this.ctx.taskId()});
        Timestamp startedAt = (Timestamp)row.get("started_at");
        Timestamp doneAt = (Timestamp)row.get("done_at");
        Assertions.assertNotNull((Object)startedAt);
        Assertions.assertNotNull((Object)doneAt);
        Assertions.assertFalse((boolean)doneAt.before(startedAt), (String)"done_at \u2265 started_at \u624d\u80fd\u76f8\u6e1b\u51fa\u5de5\u6642");
    }

    @Then(value="^\u4e8b\u4ef6 \"started\" \u8207 \"completed\" \u5beb\u5165$")
    public void startedAndCompletedEventsWritten() {
        List<String> types = this.db.eventTypes(this.ctx.taskId());
        Assertions.assertTrue((types.contains("started") && types.contains("completed") ? 1 : 0) != 0, (String)("\u4e8b\u4ef6 started/completed \u5beb\u5165: " + String.valueOf(types)));
    }

    @Then(value="^\u5b8c\u6210\u6210\u529f$")
    public void completeSucceeded() {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("\u8df3\u904e\u5099\u54c1\u9805\u4ecd\u53ef\u5b8c\u6210\uff08FR-013\uff09: " + last.body()));
        Assertions.assertEquals((Object)"done", (Object)this.json(last).get("state").asText());
    }

    @Then(value="^\u52fe\u8a18\u5171 (\\d+) \u7b46\u4e14\u300c([^\u300d]+)\u300d\u7559\u75d5\u70ba\u8df3\u904e\uff0c\u8a18\u9304\u64cd\u4f5c\u8005\u3001\u6642\u9593\u8207 label \u5feb\u7167$")
    public void sopChecksRecorded(int expected, String skippedLabel) {
        Integer total = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM task_sop_checks WHERE task_id = ?", Integer.class, new Object[]{this.ctx.taskId()});
        Assertions.assertEquals((int)expected, (int)total, (String)("\u4e09\u52fe\u4e00\u8df3\u5171 " + expected + " \u7b46\u52fe\u8a18"));
        Map skipped = this.db.admin().queryForMap("SELECT checked, skipped, checked_by, checked_at, label FROM task_sop_checks WHERE task_id = ? AND item_key = ?", new Object[]{this.ctx.taskId(), this.ctx.sopItemKey(skippedLabel)});
        Assertions.assertEquals((Object)Boolean.TRUE, skipped.get("skipped"));
        Assertions.assertEquals((Object)Boolean.FALSE, skipped.get("checked"));
        Assertions.assertEquals((Object)((UUID)this.ctx.get("sopActorId")).toString(), (Object)skipped.get("checked_by").toString(), (String)"\u8ab0\u8df3\u904e\u7684\u8981\u7559\u75d5");
        Assertions.assertNotNull(skipped.get("checked_at"), (String)"\u5e7e\u9ede\u52d5\u7684\u8981\u7559\u75d5");
        Assertions.assertEquals((Object)skippedLabel, skipped.get("label"), (String)"label \u7528 sop_items \u5feb\u7167");
    }

    @Then(value="^\u6bcf\u6b21\u52fe\u9078\u8207\u8df3\u904e\u90fd\u5beb\u5165\u4e8b\u4ef6 \"sop_checked\"$")
    public void everySopActionWritesEvent() {
        Assertions.assertEquals((int)4, (int)this.db.eventCountOfType(this.ctx.taskId(), "sop_checked"), (String)"\u6bcf\u6b21\u52fe\u9078\u8207\u8df3\u904e\u90fd\u8981\u5beb\u5165\u4e8b\u4ef6 sop_checked\uff08FR-013\uff09");
    }

    @Then(value="^\u6e05\u55ae\u542b\u300c([^\u300d]+)\u300d\u4f46\u4e0d\u542b\u300c([^\u300d]+)\u300d\u4e14\u5168\u662f pool \u72c0\u614b\u7684\u623f\u52d9\u55ae$")
    public void hk1PoolFiltered(String expectedTitle, String excludedTitle) {
        JsonNode pool = this.objectMapper.readTree((String)this.ctx.get("poolBody:\u5c0f\u7f8e"));
        Assertions.assertTrue((boolean)StaffDeskThenSteps.containsId(pool, this.taskIdByTitle(expectedTitle)), (String)("\u623f\u52d9\u770b\u5f97\u5230\u300c" + expectedTitle + "\u300d"));
        Assertions.assertFalse((boolean)StaffDeskThenSteps.containsId(pool, this.taskIdByTitle(excludedTitle)), (String)"\u623f\u52d9\u770b\u4e0d\u5230\u5de5\u52d9\u7684\u55ae");
        for (JsonNode task : pool) {
            Assertions.assertEquals((Object)"pool", (Object)task.get("state").asText(), (String)"\u6c60\u6e05\u55ae\u53ea\u542b pool \u72c0\u614b");
            Assertions.assertEquals((Object)"housekeeping", (Object)task.get("dept").asText(), (String)"\u6c60\u6e05\u55ae\u53ea\u542b\u672c\u90e8\u9580");
        }
    }

    @Then(value="^\u300c\u963f\u5f37\u300d\u67e5\u8a62\u5f85\u8a8d\u9818\u6642\u6e05\u55ae\u542b\u300c([^\u300d]+)\u300d\u4f46\u4e0d\u542b\u300c([^\u300d]+)\u300d$")
    public void mt1PoolFiltered(String expectedTitle, String excludedTitle) {
        ScenarioContext.HttpResult pool = this.api.get("/staff/pool", this.api.staffToken(1L, this.ctx.staffLogin("\u963f\u5f37")));
        Assertions.assertEquals((int)200, (int)pool.status(), (String)pool.body());
        JsonNode body = this.objectMapper.readTree(pool.body());
        Assertions.assertTrue((boolean)StaffDeskThenSteps.containsId(body, this.taskIdByTitle(expectedTitle)), (String)"\u5de5\u52d9\u770b\u5f97\u5230\u4fee\u7e55\u55ae");
        Assertions.assertFalse((boolean)StaffDeskThenSteps.containsId(body, this.taskIdByTitle(excludedTitle)), (String)"\u5de5\u52d9\u770b\u4e0d\u5230\u623f\u52d9\u7684\u55ae");
    }

    @Then(value="^\u8a72\u4efb\u52d9\u4ecd\u505c\u5728 \"triage\"$")
    public void taskStillInTriage() {
        Assertions.assertEquals((Object)"triage", (Object)this.db.taskRow(this.ctx.taskId()).get("state"), (String)"\u88ab\u62d2\u7684\u5224\u8b80\u4e0d\u5f97\u6539\u72c0\u614b");
    }

    @Then(value="^\u623f\u52d9\u300c\u5c0f\u7f8e\u300d\u7684\u5f85\u8a8d\u9818\u7acb\u523b\u770b\u5f97\u5230\u9019\u55ae$")
    public void housekeepingPoolSeesTaskImmediately() {
        ScenarioContext.HttpResult pool = this.api.get("/staff/pool", this.api.staffToken(1L, this.ctx.staffLogin("\u5c0f\u7f8e")));
        Assertions.assertEquals((int)200, (int)pool.status(), (String)pool.body());
        Assertions.assertTrue((boolean)StaffDeskThenSteps.containsId(this.objectMapper.readTree(pool.body()), this.ctx.taskId()), (String)"\u5224\u8b80\u5f8c\u623f\u52d9\u6c60\u7acb\u523b\u770b\u5f97\u5230\u9019\u55ae");
    }

    @Then(value="^\u56de\u61c9\u7684\u4efb\u52d9 \"([^\"]+)\" \u7684 doneAt \u61c9\u6709\u503c$")
    public void responseDoneAtPresent(String symbol) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("\u5224\u8b80\u8a72\u6210\u529f: " + last.body()));
        Assertions.assertEquals((Object)this.ctx.entityId(symbol).toString(), (Object)this.json(last).get("id").asText(), (String)("\u56de\u61c9\u7684\u4efb\u52d9\u8981\u662f " + symbol));
        Assertions.assertFalse((boolean)this.json(last).get("doneAt").isNull(), (String)"doneAt \u8a72\u6709\u503c");
    }

    @Then(value="^\u6e05\u55ae\u542b\u6a19\u984c\u300c([^\u300d]+)\u300d\u4e14\u5176\u72c0\u614b\u70ba \"([a-z_]+)\"$")
    public void deskListContainsTitleWithState(String title, String state) {
        JsonNode all = this.objectMapper.readTree((String)this.ctx.get("deskTasksBody"));
        UUID expectedId = this.taskIdByTitle(title);
        JsonNode found = null;
        for (JsonNode task : all) {
            if (!expectedId.toString().equals(task.get("id").asText())) continue;
            found = task;
            break;
        }
        Assertions.assertNotNull(found, (String)("\u6e05\u55ae\u542b\u6a19\u984c\u300c" + title + "\u300d: " + String.valueOf(all)));
        Assertions.assertEquals((Object)state, (Object)found.get("state").asText(), (String)("\u6a19\u984c\u300c" + title + "\u300d\u7684\u72c0\u614b"));
    }

    @Then(value="^\u5f85\u5224\u8b80\u6e05\u55ae\u53ea\u542b triage \u7684\u4efb\u52d9$")
    public void triageListOnlyContainsTriage() {
        ScenarioContext.HttpResult triage = this.api.get("/desk/triage", this.api.staffToken(1L, this.ctx.staffLogin("\u6ac3\u6aaf")));
        Assertions.assertEquals((int)200, (int)triage.status(), (String)triage.body());
        JsonNode list = this.objectMapper.readTree(triage.body());
        Assertions.assertTrue((boolean)StaffDeskThenSteps.containsId(list, this.taskIdInState("triage")), (String)"\u5f85\u5224\u8b80\u6e05\u55ae\u542b triage \u4efb\u52d9");
        Assertions.assertFalse((boolean)StaffDeskThenSteps.containsId(list, this.taskIdInState("pool")), (String)"\u5f85\u5224\u8b80\u6e05\u55ae\u4e0d\u542b\u5df2\u5728\u6c60\u4e2d\u7684\u4efb\u52d9\uff0c\u53ea\u56de triage");
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
