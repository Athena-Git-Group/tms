package com.athena.tms.integration.stepdefs.given;

import com.athena.tms.dto.Actor;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.Awaits;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import com.athena.tms.integration.stepdefs.utils.TaskFixtures;
import com.athena.tms.integration.stepdefs.utils.Tenants;
import io.cucumber.java.en.Given;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;

public class BackboneGivenSteps {

    private final ScenarioContext ctx;
    private final TaskFixtures fixtures;
    private final ApiClient api;
    private final DbSupport db;
    private final SseSupport sse;

    public BackboneGivenSteps(ScenarioContext ctx, TaskFixtures fixtures, ApiClient api, DbSupport db, SseSupport sse) {
        this.ctx = ctx;
        this.fixtures = fixtures;
        this.api = api;
        this.db = db;
        this.sse = sse;
    }

    @Given("^「小美」在電梯間對任務 \"([^\"]+)\" 按「完成」且請求帶 Idempotency-Key \"([^\"]+)\"$")
    public void completesWithIdempotencyKey(String taskSymbol, String opId) {
        UUID taskId = ctx.entityId(taskSymbol);
        String token = api.staffToken(1L, ctx.staffLogin("小美"));
        ScenarioContext.HttpResult first = api.post("/staff/tasks/" + taskId + "/complete", token, opId);
        ctx.taskId(taskId);
        ctx.opId(opId);
        ctx.firstResponse(first);
    }

    @Given("^第一次請求已成功、回應尚未送達$")
    public void firstRequestSucceeded() {
        Assertions.assertEquals(200, ctx.firstResponse().status(),
                "第一次完成請求該 200: " + ctx.firstResponse().body());
        Assertions.assertNull(ctx.firstResponse().replayedHeader(), "第一次是真執行，不是回放");
    }

    @Given("^任務 \"([^\"]+)\" 已走完 認領、開工、完成 且事件已全數上 Kafka$")
    public void completedChainFullySent(String taskSymbol) {
        UUID id = ctx.entityId(taskSymbol);
        Actor housekeeper = Actor.staff("housekeeping", db.admin().queryForObject(
                "SELECT id FROM staff_accounts WHERE tenant_id = ? AND login_id = ?",
                UUID.class, 1L, ctx.staffLogin("小美")));
        Tenants.runAsTenant(1L, () -> {
            fixtures.engine().claim(1L, id, "housekeeping", housekeeper);
            fixtures.engine().start(1L, id, housekeeper);
            fixtures.engine().complete(1L, id, housekeeper);
        });
        ctx.taskId(id);
        Map<String, Object> row = db.taskRow(id);
        ctx.taskSnapshot(new ScenarioContext.TaskSnapshot(
                (String) row.get("state"),
                ((Number) row.get("event_seq")).intValue(),
                row.get("updated_at"),
                db.eventTypes(id).size()));
        Awaits.waitUntil(Duration.ofSeconds(20), "該任務的 outbox 全數送出",
                () -> db.outboxUnsentCount("task:" + id) == 0);
    }

    @Given("^待送匣 \"([^\"]+)\" 其實已經送過一次 Kafka（模擬 relay 上一輪送出成功、標記前當機）$")
    public void outboxAlreadyDeliveredOnce(String idempotencyKey) throws Exception {
        Long outboxId = ctx.get("outbox:" + idempotencyKey);
        String aggregateKey = db.admin().queryForObject(
                "SELECT aggregate_key FROM outbox WHERE id = ?", String.class, outboxId);
        String payload = db.admin().queryForObject(
                "SELECT payload FROM outbox WHERE id = ?", String.class, outboxId);
        sse.produceRaw(System.currentTimeMillis(), aggregateKey, payload);
    }
}
