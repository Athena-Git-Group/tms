package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.entity.Task;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.SseSupport;
import com.athena.tms.integration.stepdefs.utils.TaskFixtures;
import com.athena.tms.integration.stepdefs.utils.Tenants;
import com.athena.tms.integration.stepdefs.utils.TestData;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.UUID;

public class StreamWhenSteps {
    private final ScenarioContext ctx;
    private final TaskFixtures fixtures;
    private final SseSupport sse;
    private final DbSupport db;

    public StreamWhenSteps(ScenarioContext ctx, TaskFixtures fixtures, SseSupport sse, DbSupport db) {
        this.ctx = ctx;
        this.fixtures = fixtures;
        this.sse = sse;
        this.db = db;
    }

    @When(value="^訂單 \"([^\"]+)\" 與訂單 \"([^\"]+)\" 各建立一張任務$")
    public void eachReservationGetsOneTask(String firstReservation, String secondReservation) {
        this.ctx.put("sseTask:" + this.guestLabelOf(firstReservation), this.newTask("amenity", firstReservation, "housekeeping", "毛巾×2（A 房）"));
        this.ctx.put("sseTask:" + this.guestLabelOf(secondReservation), this.newTask("repair", secondReservation, "engineering", "冷氣不冷（B 房）"));
    }

    private String guestLabelOf(String reservationSymbol) {
        UUID reservationId = this.ctx.entityId(reservationSymbol);
        for (String label : List.of("A", "B")) {
            if (!reservationId.equals(this.ctx.get("reservation:" + label))) continue;
            return label;
        }
        throw new IllegalStateException("沒有住客開著綁訂單 " + reservationSymbol + " 的串流");
    }

    @When(value="^房務池建立一張新單並由「小美」認領、開工$")
    public void housekeepingTaskCreatedClaimedStarted() {
        Task task = this.fixtures.newPoolTask("房務池新單");
        this.fixtures.claimAndStartAsHk1(task.getId());
        this.ctx.put("sseTask:hk", task.getId());
    }

    @When(value="^工務池建立一張新單$")
    public void engineeringTaskCreated() {
        Task task = Tenants.asTenant(1L, () -> this.fixtures.engine().create(new TaskCreateCommand(1L, "repair", "desk", TestData.ROOM_301, TestData.RESERVATION_B002, "engineering", "pool", null, "工務池新單", null, null, null, null, TestData.FD1)));
        this.ctx.put("sseTask:eng", task.getId());
    }

    @When(value="^訂單 \"([^\"]+)\" 上的一張任務走完 認領、開工、完成 全鏈$")
    public void fullChainOnReservation(String reservationSymbol) {
        UUID taskId = this.newTask("amenity", reservationSymbol, "housekeeping", "整鏈推送");
        Tenants.runAsTenant(1L, () -> {
            this.fixtures.engine().claim(1L, taskId, "housekeeping", TestData.HK1);
            this.fixtures.engine().start(1L, taskId, TestData.HK1);
            this.fixtures.engine().complete(1L, taskId, TestData.HK1);
        });
        this.ctx.taskId(taskId);
    }

    @When(value="^同 \\(task_id, seq\\) 的事件被重複直送 Kafka 兩筆$")
    public void duplicateEventsResentToKafka() throws Exception {
        UUID taskId = this.ctx.taskId();
        this.sse.produceRaw(System.currentTimeMillis(), "task:" + String.valueOf(taskId), SseSupport.envelope(1L, taskId, 4, "completed"));
        this.sse.produceRaw(System.currentTimeMillis(), "task:" + String.valueOf(taskId), SseSupport.envelope(1L, taskId, 2, "claimed"));
        Thread.sleep(2000L);
    }

    @When(value="^一筆 record 時間戳落後 (\\d+) 秒的事件進入 task-events topic$")
    public void staleRecordEntersTopic(int lagSeconds) throws Exception {
        this.sse.produceRaw(System.currentTimeMillis(), "task:" + String.valueOf(UUID.randomUUID()), SseSupport.envelope(1L, UUID.randomUUID(), 1, "created"));
        Thread.sleep(1000L);
        this.sse.produceRaw(System.currentTimeMillis() - (long)lagSeconds * 1000L, "task:" + String.valueOf(UUID.randomUUID()), SseSupport.envelope(1L, UUID.randomUUID(), 1, "created"));
    }

    private UUID newTask(String type, String reservationSymbol, String dept, String title) {
        UUID reservationId = this.ctx.entityId(reservationSymbol);
        UUID roomId = (UUID)this.db.admin().queryForObject("SELECT room_id FROM reservations_cache WHERE id = ?", UUID.class, new Object[]{reservationId});
        return Tenants.asTenant(1L, () -> this.fixtures.engine().create(new TaskCreateCommand(1L, type, "desk", roomId, reservationId, dept, "pool", null, title, null, null, null, null, TestData.FD1))).getId();
    }
}
