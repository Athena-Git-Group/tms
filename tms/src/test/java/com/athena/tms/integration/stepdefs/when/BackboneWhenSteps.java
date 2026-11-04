/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.athena.tms.common.error.StateConflictException
 *  com.athena.tms.dto.Actor
 *  com.athena.tms.dto.TaskCreateCommand
 *  com.athena.tms.entity.Task
 *  io.cucumber.java.en.When
 *  org.junit.jupiter.api.Assertions
 *  org.springframework.dao.DataAccessException
 */
package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.entity.Task;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.Awaits;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.KafkaProbe;
import com.athena.tms.integration.stepdefs.utils.TaskFixtures;
import com.athena.tms.integration.stepdefs.utils.Tenants;
import com.athena.tms.integration.stepdefs.utils.TestData;
import io.cucumber.java.en.When;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.springframework.dao.DataAccessException;

public class BackboneWhenSteps {
    private final ScenarioContext ctx;
    private final TaskFixtures fixtures;
    private final ApiClient api;
    private final DbSupport db;

    public BackboneWhenSteps(ScenarioContext ctx, TaskFixtures fixtures, ApiClient api, DbSupport db) {
        this.ctx = ctx;
        this.fixtures = fixtures;
        this.api = api;
        this.db = db;
    }

    @When(value="^\u5ba2\u6236\u7aef\u4ee5\u540c\u4e00 Idempotency-Key \u91cd\u9001 (\\d+) \u6b21$")
    public void resendWithSameKey(int times) {
        String token = this.api.staffToken(1L, "hk1");
        for (int i = 0; i < times; ++i) {
            this.ctx.replays().add(this.api.post("/staff/tasks/" + String.valueOf(this.ctx.taskId()) + "/complete", token, this.ctx.opId()));
        }
    }

    @When(value="^\u4efb\u52d9 \"([^\"]+)\" \u7531 \"done\" \u8f49 \"inspected\"$")
    public void doneToInspected(String taskSymbol) {
        this.ctx.taskId(this.ctx.entityId(taskSymbol));
        this.ctx.returnedSeq(Tenants.asTenant(1L, () -> this.fixtures.engine().passInspection(1L, this.ctx.taskId(), TestData.MG1)));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @When(value="^\u5169\u500b\u4f75\u767c\u8acb\u6c42\u540c\u79d2\u8a8d\u9818\u4efb\u52d9 \"([^\"]+)\"$")
    public void twoConcurrentClaims(String taskSymbol) throws Exception {
        UUID taskId = this.ctx.entityId(taskSymbol);
        this.ctx.taskId(taskId);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            ArrayList<Future<String>> futures = new ArrayList<Future<String>>();
            for (int i = 0; i < 2; ++i) {
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        Tenants.runAsTenant(1L, () -> this.fixtures.engine().claim(1L, taskId, "housekeeping", TestData.HK1));
                        return "ok";
                    }
                    catch (StateConflictException e) {
                        return e.getCode();
                    }
                }));
            }
            go.countDown();
            for (Future future : futures) {
                this.ctx.claimOutcomes().add((String)future.get(60L, TimeUnit.SECONDS));
            }
        }
        finally {
            pool.shutdownNow();
        }
    }

    @When(value="^\u4f9d\u5e8f\u5617\u8a66 \u5f85\u8a8d\u9818\u76f4\u63a5\u958b\u5de5\u3001\u8de8\u90e8\u9580\u8a8d\u9818\u3001\u8a8d\u9818\u5f8c\u4f4f\u5ba2\u6536\u56de \u4e09\u7a2e\u975e\u6cd5\u8f49\u79fb$")
    public void threeIllegalTransitions() {
        UUID taskId = this.ctx.taskId();
        this.attemptIllegal(() -> this.fixtures.engine().start(1L, taskId, TestData.HK1));
        this.attemptIllegal(() -> this.fixtures.engine().claim(1L, taskId, "engineering", Actor.staff((String)"engineering", (UUID)TestData.STAFF_MG1)));
        Tenants.runAsTenant(1L, () -> this.fixtures.engine().claim(1L, taskId, "housekeeping", TestData.HK1));
        this.attemptIllegal(() -> this.fixtures.engine().cancelByGuest(1L, taskId));
    }

    private void attemptIllegal(Runnable work) {
        try {
            Tenants.runAsTenant(1L, work);
            Assertions.fail((String)"\u975e\u6cd5\u8f49\u79fb\u7adf\u7136\u6210\u529f\u4e86");
        }
        catch (StateConflictException e) {
            this.ctx.rejectionCodes().add(e.getCode());
            this.ctx.rejectionStatuses().add(e.getStatus());
        }
    }

    @When(value="^\u4efb\u52d9 \"([^\"]+)\" \u8d70\u5b8c \u8a8d\u9818\u3001\u958b\u5de5\u3001\u5b8c\u6210\u3001\u67e5\u623f\u9000\u56de\u3001\u91cd\u65b0\u958b\u5de5\u3001\u518d\u5b8c\u6210\u3001\u67e5\u623f\u901a\u904e \u7684\u5168\u93c8\u6b77\u7a0b$")
    public void fullChain(String taskSymbol) {
        UUID id = this.ctx.entityId(taskSymbol);
        this.ctx.taskId(id);
        int lastSeq = Tenants.asTenant(1L, () -> {
            this.fixtures.engine().claim(1L, id, "housekeeping", TestData.HK1);
            this.fixtures.engine().start(1L, id, TestData.HK1);
            this.fixtures.engine().complete(1L, id, TestData.HK1);
            this.fixtures.engine().failInspection(1L, id, "\u5e8a\u55ae\u6c92\u63db", TestData.MG1);
            this.fixtures.engine().start(1L, id, TestData.HK1);
            this.fixtures.engine().complete(1L, id, TestData.HK1);
            return this.fixtures.engine().passInspection(1L, id, TestData.MG1);
        });
        this.ctx.returnedSeq(lastSeq);
    }

    @When(value="^\u7cfb\u7d71\u4ee5\u6392\u623f\u8d77\u9ede\u76f4\u63a5\u5efa\u7acb\u4e00\u5f35\u6307\u6d3e\u7d66\u300c\u5c0f\u7f8e\u300d\u7684\u6253\u6383\u55ae$")
    public void createAtAccepted() {
        Task task = Tenants.asTenant(1L, () -> this.fixtures.engine().create(new TaskCreateCommand(1L, "cleaning", "system", TestData.ROOM_502, null, "housekeeping", "accepted", TestData.STAFF_HK1, "\u9000\u623f\u6e05", null, Integer.valueOf(30), null, LocalDate.now(), Actor.system())));
        this.ctx.taskId(task.getId());
        this.ctx.returnedSeq(task.getEventSeq());
    }

    @When(value="^relay \u8f2a\u8a62 outbox$")
    public void relayPollsOutbox() throws InterruptedException {
        this.awaitFreeRowsSent();
        Thread.sleep(1000L);
    }

    @When(value="^relay \u4e0b\u4e00\u8f2a\u8f2a\u8a62$")
    public void relayNextRound() {
        this.awaitFreeRowsSent();
    }

    @When(value="^relay \u8dd1\u904e\u4e00\u8f2a$")
    public void relayRunsOneRound() throws InterruptedException {
        Thread.sleep(3000L);
    }

    private void awaitFreeRowsSent() {
        List<Long> freeIds = this.ctx.get("outboxFreeIds");
        for (Long freeId : freeIds) {
            Awaits.waitUntil(Duration.ofSeconds(20L), "\u5f85\u9001\u5323\u5217 " + freeId + " \u8a72\u88ab\u7167\u5e38\u9001\u51fa", () -> "sent".equals(this.db.outboxStatus(freeId)));
        }
    }

    @When(value="^Kafka \u6d88\u8cbb\u8005\u8655\u7406\u5b8c\u9019\u4e9b\u4e8b\u4ef6$")
    public void consumersProcessEvents() throws InterruptedException {
        Thread.sleep(1500L);
    }

    @When(value="^\u6d88\u8cbb\u8005\u5f9e\u982d\u8b80\u53d6 task-events topic$")
    public void readTaskEventsTopic() {
        this.ctx.put("records", KafkaProbe.consumeAll("tms.task-events", Duration.ofSeconds(3L)));
    }

    @When(value="^\u61c9\u7528\u9023\u7dda\u5617\u8a66 UPDATE \u6216 DELETE task_events \u7684\u5217$")
    public void appConnectionMutatesEvents() {
        this.ctx.put("updateState", this.sqlStateOf(() -> this.db.app().update("UPDATE task_events SET type = type")));
        this.ctx.put("deleteState", this.sqlStateOf(() -> this.db.app().update("DELETE FROM task_events")));
    }

    private String sqlStateOf(Runnable work) {
        try {
            Tenants.runAsTenant(1L, work);
            return "no-error";
        }
        catch (DataAccessException e) {
            String result;
            Throwable cause = e.getMostSpecificCause();
            if (cause instanceof SQLException) {
                SQLException sql = (SQLException)cause;
                result = sql.getSQLState();
            } else {
                result = "non-sql: " + String.valueOf(cause);
            }
            return result;
        }
    }
}
