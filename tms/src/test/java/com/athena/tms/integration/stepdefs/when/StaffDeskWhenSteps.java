package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;
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

import static com.athena.tms.integration.stepdefs.utils.TestData.TENANT_1;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 02-staff-pool／03-desk-triage feature 的動作步驟（一律真 HTTP＋JWT）。 */
public class StaffDeskWhenSteps {

    private final ScenarioContext ctx;
    private final ApiClient api;
    private final DbSupport db;

    public StaffDeskWhenSteps(ScenarioContext ctx, ApiClient api, DbSupport db) {
        this.ctx = ctx;
        this.api = api;
        this.db = db;
    }

    @Given("^「([^」]+)」已認領任務 \"([^\"]+)\"$")
    public void taskAlreadyClaimed(String name, String taskSymbol) {
        UUID taskId = ctx.entityId(taskSymbol);
        HttpResult claimed = claim(taskId, ctx.staffLogin(name));
        assertEquals(200, claimed.status(), "前置認領必須成功: " + claimed.body());
        ctx.taskId(taskId);
    }

    @When("^(?:另一位)?(?:房務|工務)?「(小美|小華|阿強)」認領任務 \"([^\"]+)\"$")
    public void staffClaimsTask(String name, String taskSymbol) {
        UUID taskId = ctx.entityId(taskSymbol);
        ctx.taskId(taskId);
        ctx.lastResponse(claim(taskId, ctx.staffLogin(name)));
    }

    @When("^「小美」與「小華」同秒對任務 \"([^\"]+)\" 送出認領$")
    public void concurrentClaims(String taskSymbol) throws Exception {
        UUID taskId = ctx.entityId(taskSymbol);
        // token 先各自備好：登入不能擠在閘門後，否則「同秒」變「先後」
        List<String> tokens = List.of(
                api.staffToken(TENANT_1, ctx.staffLogin("小美")),
                api.staffToken(TENANT_1, ctx.staffLogin("小華")));
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (String token : tokens) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return api.post("/staff/tasks/" + taskId + "/claim", token,
                            "bdd-" + UUID.randomUUID()).status();
                }));
            }
            go.countDown();
            for (Future<Integer> future : futures) {
                ctx.rejectionStatuses().add(future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        ctx.taskId(taskId);
    }

    @When("^「(小美|小華|阿強)」對任務 \"([^\"]+)\" 執行 (claim|start|complete)$")
    public void staffPerformsAction(String name, String taskSymbol, String action) {
        UUID taskId = ctx.entityId(taskSymbol);
        ctx.taskId(taskId);
        ctx.lastResponse(api.post("/staff/tasks/" + taskId + "/" + action,
                api.staffToken(TENANT_1, ctx.staffLogin(name)), "bdd-" + UUID.randomUUID()));
    }

    @When("^「小美」按「開始」再按「完成」$")
    public void startsThenCompletes() {
        String token = api.staffToken(TENANT_1, ctx.staffLogin("小美"));
        HttpResult started = api.post("/staff/tasks/" + ctx.taskId() + "/start",
                token, "bdd-" + UUID.randomUUID());
        assertEquals(200, started.status(), "開始該成功: " + started.body());
        ctx.put("startedResponse", started);
        HttpResult completed = api.post("/staff/tasks/" + ctx.taskId() + "/complete",
                token, "bdd-" + UUID.randomUUID());
        ctx.put("completedResponse", completed);
        ctx.lastResponse(completed);
    }

    @When("^「小美」勾選「([^」]+)」「([^」]+)」「([^」]+)」並跳過「([^」]+)」再按完成$")
    public void checksThreeSkipsOneThenCompletes(String first, String second, String third,
                                                 String skipped) {
        String token = api.staffToken(TENANT_1, ctx.staffLogin("小美"));
        ctx.put("sopActorId", staffIdOf(ctx.staffLogin("小美")));
        for (String label : List.of(first, second, third)) {
            HttpResult checked = sop(ctx.taskId(), ctx.sopItemKey(label),
                    Map.of("checked", true), token);
            assertEquals(200, checked.status(), "勾選「" + label + "」該成功: " + checked.body());
        }
        HttpResult skippedResult = sop(ctx.taskId(), ctx.sopItemKey(skipped),
                Map.of("skipped", true), token);
        assertEquals(200, skippedResult.status(), "跳過「" + skipped + "」該成功: "
                + skippedResult.body());
        ctx.put("skippedLabel", skipped);
        ctx.lastResponse(api.post("/staff/tasks/" + ctx.taskId() + "/complete",
                token, "bdd-" + UUID.randomUUID()));
    }

    @When("^「小美」對任務 \"([^\"]+)\" 的「([^」]+)」同時送出勾選與跳過$")
    public void bothCheckedAndSkipped(String taskSymbol, String label) {
        ctx.lastResponse(sop(ctx.entityId(taskSymbol), ctx.sopItemKey(label),
                Map.of("checked", true, "skipped", true),
                api.staffToken(TENANT_1, ctx.staffLogin("小美"))));
    }

    @When("^「小美」對任務 \"([^\"]+)\" 的「([^」]+)」送出空白動作$")
    public void neitherCheckedNorSkipped(String taskSymbol, String label) {
        ctx.lastResponse(sop(ctx.entityId(taskSymbol), ctx.sopItemKey(label), Map.of(),
                api.staffToken(TENANT_1, ctx.staffLogin("小美"))));
    }

    @When("^「小美」對任務 \"([^\"]+)\" 的未知項目 \"([^\"]+)\" 送出勾選$")
    public void unknownSopItemKey(String taskSymbol, String itemKey) {
        ctx.lastResponse(sop(ctx.entityId(taskSymbol), itemKey, Map.of("checked", true),
                api.staffToken(TENANT_1, ctx.staffLogin("小美"))));
    }

    @When("^「(小美|阿強)」查詢待接池$")
    public void staffListsPool(String name) {
        HttpResult pool = api.get("/staff/pool", api.staffToken(TENANT_1, ctx.staffLogin(name)));
        assertEquals(200, pool.status(), "查池該成功: " + pool.body());
        ctx.put("poolBody:" + name, pool.body());
        ctx.lastResponse(pool);
    }

    @When("^「小美」認領一個不存在的任務$")
    public void claimUnknownTask() {
        ctx.lastResponse(claim(UUID.randomUUID(), ctx.staffLogin("小美")));
    }

    @When("^「小美」對不存在的任務勾選 SOP 項目$")
    public void sopCheckUnknownTask() {
        ctx.lastResponse(sop(UUID.randomUUID(), "bed", Map.of("checked", true),
                api.staffToken(TENANT_1, ctx.staffLogin("小美"))));
    }

    @When("^櫃檯以 dept \"([a-z_]+)\" 判讀任務 \"([^\"]+)\"$")
    public void deskAssignsDept(String dept, String taskSymbol) {
        decideOn(taskSymbol, Map.of("action", "assign", "dept", dept));
    }

    @When("^櫃檯以 action \"([a-z_]+)\" 判讀任務 \"([^\"]+)\"$")
    public void deskDecidesActionOnly(String action, String taskSymbol) {
        decideOn(taskSymbol, Map.of("action", action));
    }

    @When("^櫃檯以 action \"answer\" 但不帶 note 判讀任務 \"([^\"]+)\"$")
    public void deskAnswersWithoutNote(String taskSymbol) {
        decideOn(taskSymbol, Map.of("action", "answer"));
    }

    @When("^櫃檯以 action \"assign\"、dept \"([a-z_]+)\" 判讀任務 \"([^\"]+)\"$")
    public void deskAssignsToDept(String dept, String taskSymbol) {
        decideOn(taskSymbol, Map.of("action", "assign", "dept", dept));
    }

    @When("^櫃檯以 action \"answer\"、note \"([^\"]+)\" 判讀任務 \"([^\"]+)\"$")
    public void deskAnswersWithNote(String note, String taskSymbol) {
        ctx.put("answerNote", note);
        decideOn(taskSymbol, Map.of("action", "answer", "note", note));
    }

    @When("^櫃檯查詢任務清單$")
    public void deskListsAllTasks() {
        HttpResult tasks = api.get("/desk/tasks", api.staffToken(TENANT_1, ctx.staffLogin("櫃檯")));
        assertEquals(200, tasks.status(), "全單視圖該成功: " + tasks.body());
        ctx.put("deskTasksBody", tasks.body());
        ctx.lastResponse(tasks);
    }

    @When("^櫃檯判讀一個不存在的任務$")
    public void deskDecidesUnknownTask() {
        ctx.lastResponse(decide(UUID.randomUUID(), Map.of("action", "answer", "note", "x")));
    }

    private void decideOn(String taskSymbol, Map<String, Object> body) {
        UUID taskId = ctx.entityId(taskSymbol);
        ctx.taskId(taskId);
        ctx.lastResponse(decide(taskId, body));
    }

    private HttpResult claim(UUID taskId, String loginId) {
        return api.post("/staff/tasks/" + taskId + "/claim",
                api.staffToken(TENANT_1, loginId), "bdd-" + UUID.randomUUID());
    }

    private HttpResult sop(UUID taskId, String itemKey, Map<String, Object> body, String token) {
        return api.postJson("/staff/tasks/" + taskId + "/sop/" + itemKey, token, body, null);
    }

    private HttpResult decide(UUID taskId, Map<String, Object> body) {
        return api.postJson("/desk/triage/" + taskId,
                api.staffToken(TENANT_1, ctx.staffLogin("櫃檯")), body, null);
    }

    private UUID staffIdOf(String loginId) {
        return db.admin().queryForObject(
                "SELECT id FROM staff_accounts WHERE tenant_id = ? AND login_id = ?",
                UUID.class, TENANT_1, loginId);
    }
}
