/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.cucumber.java.en.Given
 *  io.cucumber.java.en.When
 *  org.junit.jupiter.api.Assertions
 */
package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;

public class StaffDeskWhenSteps {
    private final ScenarioContext ctx;
    private final ApiClient api;
    private final DbSupport db;

    public StaffDeskWhenSteps(ScenarioContext ctx, ApiClient api, DbSupport db) {
        this.ctx = ctx;
        this.api = api;
        this.db = db;
    }

    @Given(value="^\u300c([^\u300d]+)\u300d\u5df2\u8a8d\u9818\u4efb\u52d9 \"([^\"]+)\"$")
    public void taskAlreadyClaimed(String name, String taskSymbol) {
        UUID taskId = this.ctx.entityId(taskSymbol);
        ScenarioContext.HttpResult claimed = this.claim(taskId, this.ctx.staffLogin(name));
        Assertions.assertEquals((int)200, (int)claimed.status(), (String)("\u524d\u7f6e\u8a8d\u9818\u5fc5\u9808\u6210\u529f: " + claimed.body()));
        this.ctx.taskId(taskId);
    }

    @When(value="^(?:\u53e6\u4e00\u4f4d)?(?:\u623f\u52d9|\u5de5\u52d9)?\u300c(\u5c0f\u7f8e|\u5c0f\u83ef|\u963f\u5f37)\u300d\u8a8d\u9818\u4efb\u52d9 \"([^\"]+)\"$")
    public void staffClaimsTask(String name, String taskSymbol) {
        UUID taskId = this.ctx.entityId(taskSymbol);
        this.ctx.taskId(taskId);
        this.ctx.lastResponse(this.claim(taskId, this.ctx.staffLogin(name)));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @When(value="^\u300c\u5c0f\u7f8e\u300d\u8207\u300c\u5c0f\u83ef\u300d\u540c\u79d2\u5c0d\u4efb\u52d9 \"([^\"]+)\" \u9001\u51fa\u8a8d\u9818$")
    public void concurrentClaims(String taskSymbol) throws Exception {
        UUID taskId = this.ctx.entityId(taskSymbol);
        List<String> tokens = List.of(this.api.staffToken(1L, this.ctx.staffLogin("\u5c0f\u7f8e")), this.api.staffToken(1L, this.ctx.staffLogin("\u5c0f\u83ef")));
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            ArrayList<Future<Integer>> futures = new ArrayList<Future<Integer>>();
            for (String token : tokens) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return this.api.post("/staff/tasks/" + String.valueOf(taskId) + "/claim", token, "bdd-" + String.valueOf(UUID.randomUUID())).status();
                }));
            }
            go.countDown();
            for (Future future : futures) {
                this.ctx.rejectionStatuses().add((Integer)future.get(60L, TimeUnit.SECONDS));
            }
        }
        finally {
            pool.shutdownNow();
        }
        this.ctx.taskId(taskId);
    }

    @When(value="^\u300c(\u5c0f\u7f8e|\u5c0f\u83ef|\u963f\u5f37)\u300d\u5c0d\u4efb\u52d9 \"([^\"]+)\" \u57f7\u884c (claim|start|complete)$")
    public void staffPerformsAction(String name, String taskSymbol, String action) {
        UUID taskId = this.ctx.entityId(taskSymbol);
        this.ctx.taskId(taskId);
        this.ctx.lastResponse(this.api.post("/staff/tasks/" + String.valueOf(taskId) + "/" + action, this.api.staffToken(1L, this.ctx.staffLogin(name)), "bdd-" + String.valueOf(UUID.randomUUID())));
    }

    @When(value="^\u300c\u5c0f\u7f8e\u300d\u6309\u300c\u958b\u59cb\u300d\u518d\u6309\u300c\u5b8c\u6210\u300d$")
    public void startsThenCompletes() {
        String token = this.api.staffToken(1L, this.ctx.staffLogin("\u5c0f\u7f8e"));
        ScenarioContext.HttpResult started = this.api.post("/staff/tasks/" + String.valueOf(this.ctx.taskId()) + "/start", token, "bdd-" + String.valueOf(UUID.randomUUID()));
        Assertions.assertEquals((int)200, (int)started.status(), (String)("\u958b\u59cb\u8a72\u6210\u529f: " + started.body()));
        this.ctx.put("startedResponse", started);
        ScenarioContext.HttpResult completed = this.api.post("/staff/tasks/" + String.valueOf(this.ctx.taskId()) + "/complete", token, "bdd-" + String.valueOf(UUID.randomUUID()));
        this.ctx.put("completedResponse", completed);
        this.ctx.lastResponse(completed);
    }

    @When(value="^\u300c\u5c0f\u7f8e\u300d\u52fe\u9078\u300c([^\u300d]+)\u300d\u300c([^\u300d]+)\u300d\u300c([^\u300d]+)\u300d\u4e26\u8df3\u904e\u300c([^\u300d]+)\u300d\u518d\u6309\u5b8c\u6210$")
    public void checksThreeSkipsOneThenCompletes(String first, String second, String third, String skipped) {
        String token = this.api.staffToken(1L, this.ctx.staffLogin("\u5c0f\u7f8e"));
        this.ctx.put("sopActorId", this.staffIdOf(this.ctx.staffLogin("\u5c0f\u7f8e")));
        for (String label : List.of(first, second, third)) {
            ScenarioContext.HttpResult checked = this.sop(this.ctx.taskId(), this.ctx.sopItemKey(label), Map.of("checked", true), token);
            Assertions.assertEquals((int)200, (int)checked.status(), (String)("\u52fe\u9078\u300c" + label + "\u300d\u8a72\u6210\u529f: " + checked.body()));
        }
        ScenarioContext.HttpResult skippedResult = this.sop(this.ctx.taskId(), this.ctx.sopItemKey(skipped), Map.of("skipped", true), token);
        Assertions.assertEquals((int)200, (int)skippedResult.status(), (String)("\u8df3\u904e\u300c" + skipped + "\u300d\u8a72\u6210\u529f: " + skippedResult.body()));
        this.ctx.put("skippedLabel", skipped);
        this.ctx.lastResponse(this.api.post("/staff/tasks/" + String.valueOf(this.ctx.taskId()) + "/complete", token, "bdd-" + String.valueOf(UUID.randomUUID())));
    }

    @When(value="^\u300c\u5c0f\u7f8e\u300d\u5c0d\u4efb\u52d9 \"([^\"]+)\" \u7684\u300c([^\u300d]+)\u300d\u540c\u6642\u9001\u51fa\u52fe\u9078\u8207\u8df3\u904e$")
    public void bothCheckedAndSkipped(String taskSymbol, String label) {
        this.ctx.lastResponse(this.sop(this.ctx.entityId(taskSymbol), this.ctx.sopItemKey(label), Map.of("checked", true, "skipped", true), this.api.staffToken(1L, this.ctx.staffLogin("\u5c0f\u7f8e"))));
    }

    @When(value="^\u300c\u5c0f\u7f8e\u300d\u5c0d\u4efb\u52d9 \"([^\"]+)\" \u7684\u300c([^\u300d]+)\u300d\u9001\u51fa\u7a7a\u767d\u52d5\u4f5c$")
    public void neitherCheckedNorSkipped(String taskSymbol, String label) {
        this.ctx.lastResponse(this.sop(this.ctx.entityId(taskSymbol), this.ctx.sopItemKey(label), Map.of(), this.api.staffToken(1L, this.ctx.staffLogin("\u5c0f\u7f8e"))));
    }

    @When(value="^\u300c\u5c0f\u7f8e\u300d\u5c0d\u4efb\u52d9 \"([^\"]+)\" \u7684\u672a\u77e5\u9805\u76ee \"([^\"]+)\" \u9001\u51fa\u52fe\u9078$")
    public void unknownSopItemKey(String taskSymbol, String itemKey) {
        this.ctx.lastResponse(this.sop(this.ctx.entityId(taskSymbol), itemKey, Map.of("checked", true), this.api.staffToken(1L, this.ctx.staffLogin("\u5c0f\u7f8e"))));
    }

    @When(value="^\u300c(\u5c0f\u7f8e|\u963f\u5f37)\u300d\u67e5\u8a62\u5f85\u8a8d\u9818$")
    public void staffListsPool(String name) {
        ScenarioContext.HttpResult pool = this.api.get("/staff/pool", this.api.staffToken(1L, this.ctx.staffLogin(name)));
        Assertions.assertEquals((int)200, (int)pool.status(), (String)("\u67e5\u6c60\u8a72\u6210\u529f: " + pool.body()));
        this.ctx.put("poolBody:" + name, pool.body());
        this.ctx.lastResponse(pool);
    }

    @When(value="^\u300c\u5c0f\u7f8e\u300d\u8a8d\u9818\u4e00\u500b\u4e0d\u5b58\u5728\u7684\u4efb\u52d9$")
    public void claimUnknownTask() {
        this.ctx.lastResponse(this.claim(UUID.randomUUID(), this.ctx.staffLogin("\u5c0f\u7f8e")));
    }

    @When(value="^\u300c\u5c0f\u7f8e\u300d\u5c0d\u4e0d\u5b58\u5728\u7684\u4efb\u52d9\u52fe\u9078 SOP \u9805\u76ee$")
    public void sopCheckUnknownTask() {
        this.ctx.lastResponse(this.sop(UUID.randomUUID(), "bed", Map.of("checked", true), this.api.staffToken(1L, this.ctx.staffLogin("\u5c0f\u7f8e"))));
    }

    @When(value="^\u6ac3\u6aaf\u4ee5 dept \"([a-z_]+)\" \u5224\u8b80\u4efb\u52d9 \"([^\"]+)\"$")
    public void deskAssignsDept(String dept, String taskSymbol) {
        this.decideOn(taskSymbol, Map.of("action", "assign", "dept", dept));
    }

    @When(value="^\u6ac3\u6aaf\u4ee5 action \"([a-z_]+)\" \u5224\u8b80\u4efb\u52d9 \"([^\"]+)\"$")
    public void deskDecidesActionOnly(String action, String taskSymbol) {
        this.decideOn(taskSymbol, Map.of("action", action));
    }

    @When(value="^\u6ac3\u6aaf\u4ee5 action \"answer\" \u4f46\u4e0d\u5e36 note \u5224\u8b80\u4efb\u52d9 \"([^\"]+)\"$")
    public void deskAnswersWithoutNote(String taskSymbol) {
        this.decideOn(taskSymbol, Map.of("action", "answer"));
    }

    @When(value="^\u6ac3\u6aaf\u4ee5 action \"assign\"\u3001dept \"([a-z_]+)\" \u5224\u8b80\u4efb\u52d9 \"([^\"]+)\"$")
    public void deskAssignsToDept(String dept, String taskSymbol) {
        this.decideOn(taskSymbol, Map.of("action", "assign", "dept", dept));
    }

    @When(value="^\u6ac3\u6aaf\u4ee5 action \"answer\"\u3001note \"([^\"]+)\" \u5224\u8b80\u4efb\u52d9 \"([^\"]+)\"$")
    public void deskAnswersWithNote(String note, String taskSymbol) {
        this.ctx.put("answerNote", note);
        this.decideOn(taskSymbol, Map.of("action", "answer", "note", note));
    }

    @When(value="^\u6ac3\u6aaf\u67e5\u8a62\u4efb\u52d9\u6e05\u55ae$")
    public void deskListsAllTasks() {
        ScenarioContext.HttpResult tasks = this.api.get("/desk/tasks", this.api.staffToken(1L, this.ctx.staffLogin("\u6ac3\u6aaf")));
        Assertions.assertEquals((int)200, (int)tasks.status(), (String)("\u5168\u55ae\u8996\u5716\u8a72\u6210\u529f: " + tasks.body()));
        this.ctx.put("deskTasksBody", tasks.body());
        this.ctx.lastResponse(tasks);
    }

    @When(value="^\u6ac3\u6aaf\u5224\u8b80\u4e00\u500b\u4e0d\u5b58\u5728\u7684\u4efb\u52d9$")
    public void deskDecidesUnknownTask() {
        this.ctx.lastResponse(this.decide(UUID.randomUUID(), Map.of("action", "answer", "note", "x")));
    }

    private void decideOn(String taskSymbol, Map<String, Object> body) {
        UUID taskId = this.ctx.entityId(taskSymbol);
        this.ctx.taskId(taskId);
        this.ctx.lastResponse(this.decide(taskId, body));
    }

    private ScenarioContext.HttpResult claim(UUID taskId, String loginId) {
        return this.api.post("/staff/tasks/" + String.valueOf(taskId) + "/claim", this.api.staffToken(1L, loginId), "bdd-" + String.valueOf(UUID.randomUUID()));
    }

    private ScenarioContext.HttpResult sop(UUID taskId, String itemKey, Map<String, Object> body, String token) {
        return this.api.postJson("/staff/tasks/" + String.valueOf(taskId) + "/sop/" + itemKey, token, body, null);
    }

    private ScenarioContext.HttpResult decide(UUID taskId, Map<String, Object> body) {
        return this.api.postJson("/desk/triage/" + String.valueOf(taskId), this.api.staffToken(1L, this.ctx.staffLogin("\u6ac3\u6aaf")), body, null);
    }

    private UUID staffIdOf(String loginId) {
        return (UUID)this.db.admin().queryForObject("SELECT id FROM staff_accounts WHERE tenant_id = ? AND login_id = ?", UUID.class, new Object[]{1L, loginId});
    }
}
