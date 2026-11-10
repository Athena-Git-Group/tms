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

    @Given(value="^「([^」]+)」已認領任務 \"([^\"]+)\"$")
    public void taskAlreadyClaimed(String name, String taskSymbol) {
        UUID taskId = this.ctx.entityId(taskSymbol);
        ScenarioContext.HttpResult claimed = this.claim(taskId, this.ctx.staffLogin(name));
        Assertions.assertEquals((int)200, (int)claimed.status(), (String)("前置認領必須成功: " + claimed.body()));
        this.ctx.taskId(taskId);
    }

    @When(value="^(?:另一位)?(?:房務|工務)?「(小美|小華|阿強)」認領任務 \"([^\"]+)\"$")
    public void staffClaimsTask(String name, String taskSymbol) {
        UUID taskId = this.ctx.entityId(taskSymbol);
        this.ctx.taskId(taskId);
        this.ctx.lastResponse(this.claim(taskId, this.ctx.staffLogin(name)));
    }

    @When(value="^「小美」與「小華」同秒對任務 \"([^\"]+)\" 送出認領$")
    public void concurrentClaims(String taskSymbol) throws Exception {
        UUID taskId = this.ctx.entityId(taskSymbol);
        List<String> tokens = List.of(this.api.staffToken(1L, this.ctx.staffLogin("小美")), this.api.staffToken(1L, this.ctx.staffLogin("小華")));
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

    @When(value="^「(小美|小華|阿強)」對任務 \"([^\"]+)\" 執行 (claim|start|complete)$")
    public void staffPerformsAction(String name, String taskSymbol, String action) {
        UUID taskId = this.ctx.entityId(taskSymbol);
        this.ctx.taskId(taskId);
        this.ctx.lastResponse(this.api.post("/staff/tasks/" + String.valueOf(taskId) + "/" + action, this.api.staffToken(1L, this.ctx.staffLogin(name)), "bdd-" + String.valueOf(UUID.randomUUID())));
    }

    @When(value="^「小美」按「開始」再按「完成」$")
    public void startsThenCompletes() {
        String token = this.api.staffToken(1L, this.ctx.staffLogin("小美"));
        ScenarioContext.HttpResult started = this.api.post("/staff/tasks/" + String.valueOf(this.ctx.taskId()) + "/start", token, "bdd-" + String.valueOf(UUID.randomUUID()));
        Assertions.assertEquals((int)200, (int)started.status(), (String)("開始該成功: " + started.body()));
        this.ctx.put("startedResponse", started);
        ScenarioContext.HttpResult completed = this.api.post("/staff/tasks/" + String.valueOf(this.ctx.taskId()) + "/complete", token, "bdd-" + String.valueOf(UUID.randomUUID()));
        this.ctx.put("completedResponse", completed);
        this.ctx.lastResponse(completed);
    }

    @When(value="^「小美」勾選「([^」]+)」「([^」]+)」「([^」]+)」並跳過「([^」]+)」再按完成$")
    public void checksThreeSkipsOneThenCompletes(String first, String second, String third, String skipped) {
        String token = this.api.staffToken(1L, this.ctx.staffLogin("小美"));
        this.ctx.put("sopActorId", this.staffIdOf(this.ctx.staffLogin("小美")));
        for (String label : List.of(first, second, third)) {
            ScenarioContext.HttpResult checked = this.sop(this.ctx.taskId(), this.ctx.sopItemKey(label), Map.of("checked", true), token);
            Assertions.assertEquals((int)200, (int)checked.status(), (String)("勾選「" + label + "」該成功: " + checked.body()));
        }
        ScenarioContext.HttpResult skippedResult = this.sop(this.ctx.taskId(), this.ctx.sopItemKey(skipped), Map.of("skipped", true), token);
        Assertions.assertEquals((int)200, (int)skippedResult.status(), (String)("跳過「" + skipped + "」該成功: " + skippedResult.body()));
        this.ctx.put("skippedLabel", skipped);
        this.ctx.lastResponse(this.api.post("/staff/tasks/" + String.valueOf(this.ctx.taskId()) + "/complete", token, "bdd-" + String.valueOf(UUID.randomUUID())));
    }

    @When(value="^「小美」對任務 \"([^\"]+)\" 的「([^」]+)」同時送出勾選與跳過$")
    public void bothCheckedAndSkipped(String taskSymbol, String label) {
        this.ctx.lastResponse(this.sop(this.ctx.entityId(taskSymbol), this.ctx.sopItemKey(label), Map.of("checked", true, "skipped", true), this.api.staffToken(1L, this.ctx.staffLogin("小美"))));
    }

    @When(value="^「小美」對任務 \"([^\"]+)\" 的「([^」]+)」送出空白動作$")
    public void neitherCheckedNorSkipped(String taskSymbol, String label) {
        this.ctx.lastResponse(this.sop(this.ctx.entityId(taskSymbol), this.ctx.sopItemKey(label), Map.of(), this.api.staffToken(1L, this.ctx.staffLogin("小美"))));
    }

    @When(value="^「小美」對任務 \"([^\"]+)\" 的未知項目 \"([^\"]+)\" 送出勾選$")
    public void unknownSopItemKey(String taskSymbol, String itemKey) {
        this.ctx.lastResponse(this.sop(this.ctx.entityId(taskSymbol), itemKey, Map.of("checked", true), this.api.staffToken(1L, this.ctx.staffLogin("小美"))));
    }

    @When(value="^「(小美|阿強)」查詢待認領$")
    public void staffListsPool(String name) {
        ScenarioContext.HttpResult pool = this.api.get("/staff/pool", this.api.staffToken(1L, this.ctx.staffLogin(name)));
        Assertions.assertEquals((int)200, (int)pool.status(), (String)("查池該成功: " + pool.body()));
        this.ctx.put("poolBody:" + name, pool.body());
        this.ctx.lastResponse(pool);
    }

    @When(value="^「小美」認領一個不存在的任務$")
    public void claimUnknownTask() {
        this.ctx.lastResponse(this.claim(UUID.randomUUID(), this.ctx.staffLogin("小美")));
    }

    @When(value="^「小美」對不存在的任務勾選 SOP 項目$")
    public void sopCheckUnknownTask() {
        this.ctx.lastResponse(this.sop(UUID.randomUUID(), "bed", Map.of("checked", true), this.api.staffToken(1L, this.ctx.staffLogin("小美"))));
    }

    @When(value="^櫃檯以 dept \"([a-z_]+)\" 判讀任務 \"([^\"]+)\"$")
    public void deskAssignsDept(String dept, String taskSymbol) {
        this.decideOn(taskSymbol, Map.of("action", "assign", "dept", dept));
    }

    @When(value="^櫃檯以 action \"([a-z_]+)\" 判讀任務 \"([^\"]+)\"$")
    public void deskDecidesActionOnly(String action, String taskSymbol) {
        this.decideOn(taskSymbol, Map.of("action", action));
    }

    @When(value="^櫃檯以 action \"answer\" 但不帶 note 判讀任務 \"([^\"]+)\"$")
    public void deskAnswersWithoutNote(String taskSymbol) {
        this.decideOn(taskSymbol, Map.of("action", "answer"));
    }

    @When(value="^櫃檯以 action \"assign\"、dept \"([a-z_]+)\" 判讀任務 \"([^\"]+)\"$")
    public void deskAssignsToDept(String dept, String taskSymbol) {
        this.decideOn(taskSymbol, Map.of("action", "assign", "dept", dept));
    }

    @When(value="^櫃檯以 action \"answer\"、note \"([^\"]+)\" 判讀任務 \"([^\"]+)\"$")
    public void deskAnswersWithNote(String note, String taskSymbol) {
        this.ctx.put("answerNote", note);
        this.decideOn(taskSymbol, Map.of("action", "answer", "note", note));
    }

    @When(value="^櫃檯查詢任務清單$")
    public void deskListsAllTasks() {
        ScenarioContext.HttpResult tasks = this.api.get("/desk/tasks", this.api.staffToken(1L, this.ctx.staffLogin("櫃檯")));
        Assertions.assertEquals((int)200, (int)tasks.status(), (String)("全單視圖該成功: " + tasks.body()));
        this.ctx.put("deskTasksBody", tasks.body());
        this.ctx.lastResponse(tasks);
    }

    @When(value="^櫃檯判讀一個不存在的任務$")
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
        return this.api.postJson("/desk/triage/" + String.valueOf(taskId), this.api.staffToken(1L, this.ctx.staffLogin("櫃檯")), body, null);
    }

    private UUID staffIdOf(String loginId) {
        return (UUID)this.db.admin().queryForObject("SELECT id FROM staff_accounts WHERE tenant_id = ? AND login_id = ?", UUID.class, new Object[]{1L, loginId});
    }
}
