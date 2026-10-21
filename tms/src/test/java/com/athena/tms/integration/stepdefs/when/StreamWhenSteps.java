package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.common.codes.Dept;
import com.athena.tms.common.codes.TaskEventType;
import com.athena.tms.common.codes.TaskSource;
import com.athena.tms.common.codes.TaskState;
import com.athena.tms.common.codes.TaskType;
import com.athena.tms.entity.Task;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import com.athena.tms.integration.stepdefs.utils.TaskFixtures;
import com.athena.tms.integration.stepdefs.utils.Tenants;
import com.athena.tms.integration.stepdefs.utils.TestData;
import com.athena.tms.dto.TaskCreateCommand;
import io.cucumber.java.en.When;

import java.util.UUID;

import static com.athena.tms.integration.stepdefs.utils.TestData.FD1;
import static com.athena.tms.integration.stepdefs.utils.TestData.HK1;
import static com.athena.tms.integration.stepdefs.utils.TestData.TENANT_1;

/** 14-sse-stream feature 的動作步驟（建單走 TaskEngine、重複與延遲事件直送 Kafka）。 */
public class StreamWhenSteps {

    private final ScenarioContext ctx;
    private final TaskFixtures fixtures;
    private final SseSupport sse;
    private final DbSupport db;

    public StreamWhenSteps(ScenarioContext ctx, TaskFixtures fixtures, SseSupport sse,
                           DbSupport db) {
        this.ctx = ctx;
        this.fixtures = fixtures;
        this.sse = sse;
        this.db = db;
    }

    @When("^訂單 \"([^\"]+)\" 與訂單 \"([^\"]+)\" 各建立一張任務$")
    public void eachReservationGetsOneTask(String firstReservation, String secondReservation) {
        ctx.put("sseTask:" + guestLabelOf(firstReservation),
                newTask(TaskType.AMENITY, firstReservation, Dept.HOUSEKEEPING, "毛巾×2（A 房）"));
        ctx.put("sseTask:" + guestLabelOf(secondReservation),
                newTask(TaskType.REPAIR, secondReservation, Dept.ENGINEERING, "冷氣不冷（B 房）"));
    }

    /** 開流步驟記了「住客標籤 → 訂單」，反查這張單是 A 還是 B 的。 */
    private String guestLabelOf(String reservationSymbol) {
        UUID reservationId = ctx.entityId(reservationSymbol);
        for (String label : java.util.List.of("A", "B")) {
            if (reservationId.equals(ctx.get("reservation:" + label))) {
                return label;
            }
        }
        throw new IllegalStateException("沒有住客開著綁訂單 " + reservationSymbol + " 的串流");
    }

    @When("^房務池建立一張新單並由「小美」認領、開工$")
    public void housekeepingTaskCreatedClaimedStarted() {
        Task task = fixtures.newPoolTask("房務池新單");
        fixtures.claimAndStartAsHk1(task.getId());
        ctx.put("sseTask:hk", task.getId());
    }

    @When("^工務池建立一張新單$")
    public void engineeringTaskCreated() {
        Task task = Tenants.asTenant(TENANT_1, () -> fixtures.engine().create(new TaskCreateCommand(
                TENANT_1, TaskType.REPAIR, TaskSource.DESK, TestData.ROOM_301,
                TestData.RESERVATION_B002, Dept.ENGINEERING, TaskState.POOL, null,
                "工務池新單", null, null, null, null, FD1)));
        ctx.put("sseTask:eng", task.getId());
    }

    @When("^訂單 \"([^\"]+)\" 上的一張任務走完 認領、開工、完成 全鏈$")
    public void fullChainOnReservation(String reservationSymbol) {
        UUID taskId = newTask(TaskType.AMENITY, reservationSymbol, Dept.HOUSEKEEPING, "整鏈推送");
        Tenants.runAsTenant(TENANT_1, () -> {
            fixtures.engine().claim(TENANT_1, taskId, Dept.HOUSEKEEPING, HK1);
            fixtures.engine().start(TENANT_1, taskId, HK1);
            fixtures.engine().complete(TENANT_1, taskId, HK1);
        });
        ctx.taskId(taskId);
    }

    @When("^同 \\(task_id, seq\\) 的事件被重複直送 Kafka 兩筆$")
    public void duplicateEventsResentToKafka() throws Exception {
        UUID taskId = ctx.taskId();
        sse.produceRaw(System.currentTimeMillis(), "task:" + taskId,
                SseSupport.envelope(TENANT_1, taskId, 4, TaskEventType.COMPLETED));
        sse.produceRaw(System.currentTimeMillis(), "task:" + taskId,
                SseSupport.envelope(TENANT_1, taskId, 2, TaskEventType.CLAIMED));
        // 留一拍讓重複事件（若有 bug）有時間推到客戶端——斷言的是「不會來」
        Thread.sleep(2000);
    }

    @When("^一筆 record 時間戳落後 (\\d+) 秒的事件進入 task-events topic$")
    public void staleRecordEntersTopic(int lagSeconds) throws Exception {
        // 先送一筆新鮮事件把 degraded 旗標歸位（前面場景的殘留不影響本場景的轉換沿）
        sse.produceRaw(System.currentTimeMillis(), "task:" + UUID.randomUUID(),
                SseSupport.envelope(TENANT_1, UUID.randomUUID(), 1, TaskEventType.CREATED));
        Thread.sleep(1000);
        // 消費延遲靠「舊時間戳」模擬：record CreateTime 落後 lagSeconds，遠超測試門檻 5s
        sse.produceRaw(System.currentTimeMillis() - lagSeconds * 1000L, "task:" + UUID.randomUUID(),
                SseSupport.envelope(TENANT_1, UUID.randomUUID(), 1, TaskEventType.CREATED));
    }

    /** amenity 而非 cleaning：cleaning 必填 work_date 且一房一天僅一筆未完成，SSE 場景只關心推送鏈。 */
    private UUID newTask(String type, String reservationSymbol, String dept, String title) {
        UUID reservationId = ctx.entityId(reservationSymbol);
        UUID roomId = db.admin().queryForObject(
                "SELECT room_id FROM reservations_cache WHERE id = ?", UUID.class, reservationId);
        return Tenants.asTenant(TENANT_1, () -> fixtures.engine().create(new TaskCreateCommand(
                TENANT_1, type, TaskSource.DESK, roomId, reservationId,
                dept, TaskState.POOL, null, title, null, null, null, null, FD1))).getId();
    }
}
