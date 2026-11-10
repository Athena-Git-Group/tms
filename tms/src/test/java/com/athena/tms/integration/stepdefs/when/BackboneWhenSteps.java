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

    @When(value="^客戶端以同一 Idempotency-Key 重送 (\\d+) 次$")
    public void resendWithSameKey(int times) {
        String token = this.api.staffToken(1L, "hk1");
        for (int i = 0; i < times; ++i) {
            this.ctx.replays().add(this.api.post("/staff/tasks/" + String.valueOf(this.ctx.taskId()) + "/complete", token, this.ctx.opId()));
        }
    }

    @When(value="^任務 \"([^\"]+)\" 由 \"done\" 轉 \"inspected\"$")
    public void doneToInspected(String taskSymbol) {
        this.ctx.taskId(this.ctx.entityId(taskSymbol));
        this.ctx.returnedSeq(Tenants.asTenant(1L, () -> this.fixtures.engine().passInspection(1L, this.ctx.taskId(), TestData.MG1)));
    }

    @When(value="^兩個併發請求同秒認領任務 \"([^\"]+)\"$")
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

    @When(value="^依序嘗試 待認領直接開工、跨部門認領、認領後住客收回 三種非法轉移$")
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
            Assertions.fail((String)"非法轉移竟然成功了");
        }
        catch (StateConflictException e) {
            this.ctx.rejectionCodes().add(e.getCode());
            this.ctx.rejectionStatuses().add(e.getStatus());
        }
    }

    @When(value="^任務 \"([^\"]+)\" 走完 認領、開工、完成、查房退回、重新開工、再完成、查房通過 的全鏈歷程$")
    public void fullChain(String taskSymbol) {
        UUID id = this.ctx.entityId(taskSymbol);
        this.ctx.taskId(id);
        int lastSeq = Tenants.asTenant(1L, () -> {
            this.fixtures.engine().claim(1L, id, "housekeeping", TestData.HK1);
            this.fixtures.engine().start(1L, id, TestData.HK1);
            this.fixtures.engine().complete(1L, id, TestData.HK1);
            this.fixtures.engine().failInspection(1L, id, "床單沒換", TestData.MG1);
            this.fixtures.engine().start(1L, id, TestData.HK1);
            this.fixtures.engine().complete(1L, id, TestData.HK1);
            return this.fixtures.engine().passInspection(1L, id, TestData.MG1);
        });
        this.ctx.returnedSeq(lastSeq);
    }

    @When(value="^系統以排房起點直接建立一張指派給「小美」的打掃單$")
    public void createAtAccepted() {
        Task task = Tenants.asTenant(1L, () -> this.fixtures.engine().create(new TaskCreateCommand(1L, "cleaning", "system", TestData.ROOM_502, null, "housekeeping", "accepted", TestData.STAFF_HK1, "退房清", null, Integer.valueOf(30), null, LocalDate.now(), Actor.system())));
        this.ctx.taskId(task.getId());
        this.ctx.returnedSeq(task.getEventSeq());
    }

    @When(value="^relay 輪詢 outbox$")
    public void relayPollsOutbox() throws InterruptedException {
        this.awaitFreeRowsSent();
        Thread.sleep(1000L);
    }

    @When(value="^relay 下一輪輪詢$")
    public void relayNextRound() {
        this.awaitFreeRowsSent();
    }

    @When(value="^relay 跑過一輪$")
    public void relayRunsOneRound() throws InterruptedException {
        Thread.sleep(3000L);
    }

    private void awaitFreeRowsSent() {
        List<Long> freeIds = this.ctx.get("outboxFreeIds");
        for (Long freeId : freeIds) {
            Awaits.waitUntil(Duration.ofSeconds(20L), "待送匣列 " + freeId + " 該被照常送出", () -> "sent".equals(this.db.outboxStatus(freeId)));
        }
    }

    @When(value="^Kafka 消費者處理完這些事件$")
    public void consumersProcessEvents() throws InterruptedException {
        Thread.sleep(1500L);
    }

    @When(value="^消費者從頭讀取 task-events topic$")
    public void readTaskEventsTopic() {
        this.ctx.put("records", KafkaProbe.consumeAll("tms.task-events", Duration.ofSeconds(3L)));
    }

    @When(value="^應用連線嘗試 UPDATE 或 DELETE task_events 的列$")
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
