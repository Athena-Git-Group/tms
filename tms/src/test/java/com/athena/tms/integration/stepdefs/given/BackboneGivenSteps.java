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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.springframework.core.env.Environment;

public class BackboneGivenSteps {

    private final ScenarioContext ctx;
    private final TaskFixtures fixtures;
    private final ApiClient api;
    private final DbSupport db;
    private final SseSupport sse;
    private final Environment environment;

    public BackboneGivenSteps(ScenarioContext ctx, TaskFixtures fixtures, ApiClient api, DbSupport db, SseSupport sse, Environment environment) {
        this.ctx = ctx;
        this.fixtures = fixtures;
        this.api = api;
        this.db = db;
        this.sse = sse;
        this.environment = environment;
    }

    @Given("^「小美」在電梯間對任務 \"([^\"]+)\" 按「完成」且請求帶 Idempotency-Key \"([^\"]+)\"$")
    public void completesWithIdempotencyKey(String taskSymbol, String opId) {
        UUID taskId = ctx.entityId(taskSymbol);
        String token = api.staffToken(1L, ctx.staffLogin("小美"));
        ScenarioContext.HttpResult first = api.post("/api/v1/tasks/" + taskId + "/complete", token, opId);
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

    @Given("^租戶 1 的鍵 \"([^\"]+)\" 依序排著一筆超過 Kafka 單筆上限的 \"([^\"]+)\" 與一筆一般的 \"([^\"]+)\"$")
    public void keyHeadedByOversizedMessage(String aggregateKey, String headKey, String tailKey) {
        // producer 單筆上限預設 1MB，超過就在送出當下失敗，用來穩定重現「這個鍵送不出去」。
        String oversized = "{\"note\":\"" + "x".repeat(1_100_000) + "\"}";
        ctx.put("outbox:" + headKey, insertPendingPush(1, aggregateKey, oversized, headKey));
        ctx.put("outbox:" + tailKey, insertPendingPush(1, aggregateKey, "{\"note\":\"tail\"}", tailKey));
    }

    @Given("^租戶 1 積壓了 (\\d+) 個鍵、每鍵 (\\d+) 筆的待送訊息，多於每輪撈取上限$")
    public void backlogBeyondBatchSize(int keys, int perKey) {
        int batchSize = environment.getRequiredProperty("tms.outbox.relay.batch-size", Integer.class);
        Assertions.assertTrue(keys * perKey > batchSize, "積壓筆數要多於每輪撈取上限 " + batchSize + "，才驗得到分輪送完");
        List<Long> ids = new ArrayList<>();
        for (int nth = 0; nth < perKey; nth++) {
            for (int key = 0; key < keys; key++) {
                ids.add(insertPendingPush(1, "task:t-backlog-" + key, "{\"note\":\"" + nth + "\"}", "ob-backlog-" + key + "-" + nth));
            }
        }
        ctx.put("outboxFreeIds", ids);
        ctx.put("backlogKeys", keys);
        ctx.put("backlogPerKey", perKey);
    }

    private long insertPendingPush(long tenantId, String aggregateKey, String payload, String idempotencyKey) {
        return db.admin().queryForObject("""
                INSERT INTO outbox (tenant_id, aggregate_key, kind, payload, idempotency_key, status, attempts, created_at)
                VALUES (?, ?, 'push', ?::jsonb, ?, 'pending', 0, now())
                RETURNING id
                """, Long.class, tenantId, aggregateKey, payload, idempotencyKey);
    }
}
