package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.common.codes.ActorRole;
import com.athena.tms.common.codes.Dept;
import com.athena.tms.common.codes.OutboxStatus;
import com.athena.tms.common.codes.TaskSource;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.common.codes.TaskType;
import com.athena.tms.common.error.StateConflictException;
import com.athena.tms.entity.Task;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.Awaits;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.KafkaProbe;
import com.athena.tms.integration.stepdefs.utils.TaskFixtures;
import com.athena.tms.integration.stepdefs.utils.Tenants;
import com.athena.tms.dto.Actor;
import com.athena.tms.dto.TaskCreateCommand;
import io.cucumber.java.en.When;
import org.springframework.dao.DataAccessException;

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

import static com.athena.tms.integration.stepdefs.utils.TestData.HK1;
import static com.athena.tms.integration.stepdefs.utils.TestData.MG1;
import static com.athena.tms.integration.stepdefs.utils.TestData.STAFF_HK1;
import static com.athena.tms.integration.stepdefs.utils.TestData.STAFF_MG1;
import static com.athena.tms.integration.stepdefs.utils.TestData.ROOM_502;
import static com.athena.tms.integration.stepdefs.utils.TestData.TENANT_1;
import static org.junit.jupiter.api.Assertions.fail;

/** 核心骨幹 feature 的動作步驟。 */
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

    @When("^客戶端以同一 Idempotency-Key 重送 (\\d+) 次$")
    public void resendWithSameKey(int times) {
        String token = api.staffToken(TENANT_1, "hk1");
        for (int i = 0; i < times; i++) {
            ctx.replays().add(api.post(
                    "/staff/tasks/" + ctx.taskId() + "/complete", token, ctx.opId()));
        }
    }

    @When("^任務 \"([^\"]+)\" 由 \"done\" 轉 \"inspected\"$")
    public void doneToInspected(String taskSymbol) {
        ctx.taskId(ctx.entityId(taskSymbol));
        ctx.returnedSeq(Tenants.asTenant(TENANT_1,
                () -> fixtures.engine().passInspection(TENANT_1, ctx.taskId(), MG1)));
    }

    @When("^兩個併發請求同秒認領任務 \"([^\"]+)\"$")
    public void twoConcurrentClaims(String taskSymbol) throws Exception {
        UUID taskId = ctx.entityId(taskSymbol);
        ctx.taskId(taskId);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        Tenants.runAsTenant(TENANT_1, () ->
                                fixtures.engine().claim(TENANT_1, taskId, Dept.HOUSEKEEPING, HK1));
                        return "ok";
                    } catch (StateConflictException e) {
                        return e.getCode();
                    }
                }));
            }
            go.countDown();
            for (Future<String> future : futures) {
                ctx.claimOutcomes().add(future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @When("^依序嘗試 待接池直接開工、跨部門認領、認領後住客收回 三種非法轉移$")
    public void threeIllegalTransitions() {
        UUID taskId = ctx.taskId();
        // pool 不能直接 start
        attemptIllegal(() -> fixtures.engine().start(TENANT_1, taskId, HK1));
        // 跨部門認領也走同一句條件式更新，被擋成 0 列
        attemptIllegal(() -> fixtures.engine().claim(TENANT_1, taskId, Dept.ENGINEERING,
                Actor.staff(ActorRole.ENGINEERING, STAFF_MG1)));
        // 合法認領墊場，讓「收回只限還沒人認領」有 accepted 狀態可擋
        Tenants.runAsTenant(TENANT_1,
                () -> fixtures.engine().claim(TENANT_1, taskId, Dept.HOUSEKEEPING, HK1));
        attemptIllegal(() -> fixtures.engine().cancelByGuest(TENANT_1, taskId));
    }

    private void attemptIllegal(Runnable work) {
        try {
            Tenants.runAsTenant(TENANT_1, work);
            fail("非法轉移竟然成功了");
        } catch (StateConflictException e) {
            ctx.rejectionCodes().add(e.getCode());
            ctx.rejectionStatuses().add(e.getStatus());
        }
    }

    @When("^任務 \"([^\"]+)\" 走完 認領、開工、完成、查房退回、重新開工、再完成、查房通過 的全鏈歷程$")
    public void fullChain(String taskSymbol) {
        UUID id = ctx.entityId(taskSymbol);
        ctx.taskId(id);
        int lastSeq = Tenants.asTenant(TENANT_1, () -> {
            fixtures.engine().claim(TENANT_1, id, Dept.HOUSEKEEPING, HK1);
            fixtures.engine().start(TENANT_1, id, HK1);
            fixtures.engine().complete(TENANT_1, id, HK1);
            fixtures.engine().failInspection(TENANT_1, id, "床單沒換", MG1);
            fixtures.engine().start(TENANT_1, id, HK1);
            fixtures.engine().complete(TENANT_1, id, HK1);
            return fixtures.engine().passInspection(TENANT_1, id, MG1);
        });
        ctx.returnedSeq(lastSeq);
    }

    @When("^系統以排房起點直接建立一張指派給「小美」的打掃單$")
    public void createAtAccepted() {
        Task task = Tenants.asTenant(TENANT_1, () -> fixtures.engine().create(new TaskCreateCommand(
                TENANT_1, TaskType.CLEANING, TaskSource.SYSTEM, ROOM_502, null,
                Dept.HOUSEKEEPING, TaskState.ACCEPTED, STAFF_HK1, "退房清",
                null, 30, null, LocalDate.now(), Actor.system())));
        ctx.taskId(task.getId());
        ctx.returnedSeq(task.getEventSeq());
    }

    @When("^relay 輪詢 outbox$")
    public void relayPollsOutbox() throws InterruptedException {
        awaitFreeRowsSent();
        Thread.sleep(1000); // 再放 relay 跑幾輪，確認被封鎖的鍵真的原地不動
    }

    @When("^relay 下一輪輪詢$")
    public void relayNextRound() {
        awaitFreeRowsSent();
    }

    /**
     * awaitFreeRowsSent 認的「free」是靠 attempts/next_retry_at 猜出來的
     * pending 退避狀態，表格裡直接插一列 status=failed 不在這套猜測範圍內，
     * 等它變 sent 只會白等到逾時。這種案例沒有「該變成 sent 的列」可等，
     * 固定等過一個 poll 週期即可，實際斷言交給後面的 Then 步驟自己查。
     */
    @When("^relay 跑過一輪$")
    public void relayRunsOneRound() throws InterruptedException {
        Thread.sleep(3000);
    }

    /** 待送匣表格裡沒被同鍵失敗列擋住的那些列，等 relay 把它們全送出。 */
    private void awaitFreeRowsSent() {
        List<Long> freeIds = ctx.get("outboxFreeIds");
        for (Long freeId : freeIds) {
            Awaits.waitUntil(Duration.ofSeconds(20), "待送匣列 " + freeId + " 該被照常送出",
                    () -> OutboxStatus.SENT.equals(db.outboxStatus(freeId)));
        }
    }

    @When("^Kafka 消費者處理完這些事件$")
    public void consumersProcessEvents() throws InterruptedException {
        Thread.sleep(1500); // 消費者（SSE 推送）在背景執行緒 poll，給它幾輪處理時間
    }

    @When("^消費者從頭讀取 task-events topic$")
    public void readTaskEventsTopic() {
        ctx.put("records", KafkaProbe.consumeAll("tms.task-events", Duration.ofSeconds(3)));
    }

    @When("^應用連線嘗試 UPDATE 或 DELETE task_events 的列$")
    public void appConnectionMutatesEvents() {
        ctx.put("updateState", sqlStateOf(() -> db.app().update("UPDATE task_events SET type = type")));
        ctx.put("deleteState", sqlStateOf(() -> db.app().update("DELETE FROM task_events")));
    }

    /** 42501＝insufficient_privilege；權限檢查在規劃期就發生，與 RLS 可見列數無關。 */
    private String sqlStateOf(Runnable work) {
        try {
            Tenants.runAsTenant(TENANT_1, work);
            return "no-error";
        } catch (DataAccessException e) {
            Throwable cause = e.getMostSpecificCause();
            return (cause instanceof SQLException sql) ? sql.getSQLState() : "non-sql: " + cause;
        }
    }
}
