package com.athena.tms.integration.stepdefs.given;

import com.athena.tms.common.codes.ActorRole;
import com.athena.tms.common.codes.Dept;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;
import com.athena.tms.integration.stepdefs.ScenarioContext.TaskSnapshot;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.Awaits;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import com.athena.tms.integration.stepdefs.utils.TaskFixtures;
import com.athena.tms.integration.stepdefs.utils.Tenants;
import com.athena.tms.dto.Actor;
import io.cucumber.java.en.Given;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.athena.tms.integration.stepdefs.utils.TestData.TENANT_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 核心骨幹 feature 的前置步驟（任務與待送匣表格由 EntityTableGivenSteps 處理）。 */
public class BackboneGivenSteps {

    private final ScenarioContext ctx;
    private final TaskFixtures fixtures;
    private final ApiClient api;
    private final DbSupport db;
    private final SseSupport sse;

    public BackboneGivenSteps(ScenarioContext ctx, TaskFixtures fixtures, ApiClient api,
                              DbSupport db, SseSupport sse) {
        this.ctx = ctx;
        this.fixtures = fixtures;
        this.api = api;
        this.db = db;
        this.sse = sse;
    }

    @Given("^「小美」在電梯間對任務 \"([^\"]+)\" 按「完成」且請求帶 Idempotency-Key \"([^\"]+)\"$")
    public void completesWithIdempotencyKey(String taskSymbol, String opId) {
        UUID taskId = ctx.entityId(taskSymbol);
        String token = api.staffToken(TENANT_1, ctx.staffLogin("小美"));
        HttpResult first = api.post("/staff/tasks/" + taskId + "/complete", token, opId);
        ctx.taskId(taskId);
        ctx.opId(opId);
        ctx.firstResponse(first);
    }

    @Given("^第一次請求已成功、回應尚未送達$")
    public void firstRequestSucceeded() {
        assertEquals(200, ctx.firstResponse().status(),
                "第一次完成請求該 200: " + ctx.firstResponse().body());
        assertNull(ctx.firstResponse().replayedHeader(), "第一次是真執行，不是回放");
    }

    @Given("^任務 \"([^\"]+)\" 已走完 認領、開工、完成 且事件已全數上 Kafka$")
    public void completedChainFullySent(String taskSymbol) {
        UUID id = ctx.entityId(taskSymbol);
        Actor housekeeper = Actor.staff(ActorRole.HOUSEKEEPING,
                db.admin().queryForObject(
                        "SELECT id FROM staff_accounts WHERE tenant_id = ? AND login_id = ?",
                        UUID.class, TENANT_1, ctx.staffLogin("小美")));
        Tenants.runAsTenant(TENANT_1, () -> {
            fixtures.engine().claim(TENANT_1, id, Dept.HOUSEKEEPING, housekeeper);
            fixtures.engine().start(TENANT_1, id, housekeeper);
            fixtures.engine().complete(TENANT_1, id, housekeeper);
        });
        ctx.taskId(id);
        // 快照取在 relay 轉送之前：之後任何消費者若回寫 tasks，比對一定露餡
        Map<String, Object> row = db.taskRow(id);
        ctx.taskSnapshot(new TaskSnapshot((String) row.get("state"),
                ((Number) row.get("event_seq")).intValue(),
                row.get("updated_at"),
                db.eventTypes(id).size()));
        Awaits.waitUntil(Duration.ofSeconds(20), "該任務的 outbox 全數送出",
                () -> db.outboxUnsentCount("task:" + id) == 0);
    }

    /**
     * 模擬「relay 上一輪真的把這筆送到 Kafka 了，但送完、標記 sent 之前當機」
     * 這個縫隙：直接把 outbox 這一列現在的內容原樣送一次 Kafka，資料庫裡的
     * 列保持 pending 不動——跟 relay 真正重啟後看到的情況一樣。
     */
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
