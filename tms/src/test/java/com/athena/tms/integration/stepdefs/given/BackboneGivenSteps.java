/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.athena.tms.dto.Actor
 *  io.cucumber.java.en.Given
 *  org.junit.jupiter.api.Assertions
 */
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

    @Given(value="^\u300c\u5c0f\u7f8e\u300d\u5728\u96fb\u68af\u9593\u5c0d\u4efb\u52d9 \"([^\"]+)\" \u6309\u300c\u5b8c\u6210\u300d\u4e14\u8acb\u6c42\u5e36 Idempotency-Key \"([^\"]+)\"$")
    public void completesWithIdempotencyKey(String taskSymbol, String opId) {
        UUID taskId = this.ctx.entityId(taskSymbol);
        String token = this.api.staffToken(1L, this.ctx.staffLogin("\u5c0f\u7f8e"));
        ScenarioContext.HttpResult first = this.api.post("/staff/tasks/" + String.valueOf(taskId) + "/complete", token, opId);
        this.ctx.taskId(taskId);
        this.ctx.opId(opId);
        this.ctx.firstResponse(first);
    }

    @Given(value="^\u7b2c\u4e00\u6b21\u8acb\u6c42\u5df2\u6210\u529f\u3001\u56de\u61c9\u5c1a\u672a\u9001\u9054$")
    public void firstRequestSucceeded() {
        Assertions.assertEquals((int)200, (int)this.ctx.firstResponse().status(), (String)("\u7b2c\u4e00\u6b21\u5b8c\u6210\u8acb\u6c42\u8a72 200: " + this.ctx.firstResponse().body()));
        Assertions.assertNull((Object)this.ctx.firstResponse().replayedHeader(), (String)"\u7b2c\u4e00\u6b21\u662f\u771f\u57f7\u884c\uff0c\u4e0d\u662f\u56de\u653e");
    }

    @Given(value="^\u4efb\u52d9 \"([^\"]+)\" \u5df2\u8d70\u5b8c \u8a8d\u9818\u3001\u958b\u5de5\u3001\u5b8c\u6210 \u4e14\u4e8b\u4ef6\u5df2\u5168\u6578\u4e0a Kafka$")
    public void completedChainFullySent(String taskSymbol) {
        UUID id = this.ctx.entityId(taskSymbol);
        Actor housekeeper = Actor.staff((String)"housekeeping", (UUID)((UUID)this.db.admin().queryForObject("SELECT id FROM staff_accounts WHERE tenant_id = ? AND login_id = ?", UUID.class, new Object[]{1L, this.ctx.staffLogin("\u5c0f\u7f8e")})));
        Tenants.runAsTenant(1L, () -> {
            this.fixtures.engine().claim(1L, id, "housekeeping", housekeeper);
            this.fixtures.engine().start(1L, id, housekeeper);
            this.fixtures.engine().complete(1L, id, housekeeper);
        });
        this.ctx.taskId(id);
        Map<String, Object> row = this.db.taskRow(id);
        this.ctx.taskSnapshot(new ScenarioContext.TaskSnapshot((String)row.get("state"), ((Number)row.get("event_seq")).intValue(), row.get("updated_at"), this.db.eventTypes(id).size()));
        Awaits.waitUntil(Duration.ofSeconds(20L), "\u8a72\u4efb\u52d9\u7684 outbox \u5168\u6578\u9001\u51fa", () -> this.db.outboxUnsentCount("task:" + String.valueOf(id)) == 0);
    }

    @Given(value="^\u5f85\u9001\u5323 \"([^\"]+)\" \u5176\u5be6\u5df2\u7d93\u9001\u904e\u4e00\u6b21 Kafka\uff08\u6a21\u64ec relay \u4e0a\u4e00\u8f2a\u9001\u51fa\u6210\u529f\u3001\u6a19\u8a18\u524d\u7576\u6a5f\uff09$")
    public void outboxAlreadyDeliveredOnce(String idempotencyKey) throws Exception {
        Long outboxId = (Long)this.ctx.get("outbox:" + idempotencyKey);
        String aggregateKey = (String)this.db.admin().queryForObject("SELECT aggregate_key FROM outbox WHERE id = ?", String.class, new Object[]{outboxId});
        String payload = (String)this.db.admin().queryForObject("SELECT payload FROM outbox WHERE id = ?", String.class, new Object[]{outboxId});
        this.sse.produceRaw(System.currentTimeMillis(), aggregateKey, payload);
    }
}
