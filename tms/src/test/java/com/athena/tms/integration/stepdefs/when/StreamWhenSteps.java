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

    @When(value="^\u8a02\u55ae \"([^\"]+)\" \u8207\u8a02\u55ae \"([^\"]+)\" \u5404\u5efa\u7acb\u4e00\u5f35\u4efb\u52d9$")
    public void eachReservationGetsOneTask(String firstReservation, String secondReservation) {
        this.ctx.put("sseTask:" + this.guestLabelOf(firstReservation), this.newTask("amenity", firstReservation, "housekeeping", "\u6bdb\u5dfe\u00d72\uff08A \u623f\uff09"));
        this.ctx.put("sseTask:" + this.guestLabelOf(secondReservation), this.newTask("repair", secondReservation, "engineering", "\u51b7\u6c23\u4e0d\u51b7\uff08B \u623f\uff09"));
    }

    private String guestLabelOf(String reservationSymbol) {
        UUID reservationId = this.ctx.entityId(reservationSymbol);
        for (String label : List.of("A", "B")) {
            if (!reservationId.equals(this.ctx.get("reservation:" + label))) continue;
            return label;
        }
        throw new IllegalStateException("\u6c92\u6709\u4f4f\u5ba2\u958b\u8457\u7d81\u8a02\u55ae " + reservationSymbol + " \u7684\u4e32\u6d41");
    }

    @When(value="^\u623f\u52d9\u6c60\u5efa\u7acb\u4e00\u5f35\u65b0\u55ae\u4e26\u7531\u300c\u5c0f\u7f8e\u300d\u8a8d\u9818\u3001\u958b\u5de5$")
    public void housekeepingTaskCreatedClaimedStarted() {
        Task task = this.fixtures.newPoolTask("\u623f\u52d9\u6c60\u65b0\u55ae");
        this.fixtures.claimAndStartAsHk1(task.getId());
        this.ctx.put("sseTask:hk", task.getId());
    }

    @When(value="^\u5de5\u52d9\u6c60\u5efa\u7acb\u4e00\u5f35\u65b0\u55ae$")
    public void engineeringTaskCreated() {
        Task task = Tenants.asTenant(1L, () -> this.fixtures.engine().create(new TaskCreateCommand(1L, "repair", "desk", TestData.ROOM_301, TestData.RESERVATION_B002, "engineering", "pool", null, "\u5de5\u52d9\u6c60\u65b0\u55ae", null, null, null, null, TestData.FD1)));
        this.ctx.put("sseTask:eng", task.getId());
    }

    @When(value="^\u8a02\u55ae \"([^\"]+)\" \u4e0a\u7684\u4e00\u5f35\u4efb\u52d9\u8d70\u5b8c \u8a8d\u9818\u3001\u958b\u5de5\u3001\u5b8c\u6210 \u5168\u93c8$")
    public void fullChainOnReservation(String reservationSymbol) {
        UUID taskId = this.newTask("amenity", reservationSymbol, "housekeeping", "\u6574\u93c8\u63a8\u9001");
        Tenants.runAsTenant(1L, () -> {
            this.fixtures.engine().claim(1L, taskId, "housekeeping", TestData.HK1);
            this.fixtures.engine().start(1L, taskId, TestData.HK1);
            this.fixtures.engine().complete(1L, taskId, TestData.HK1);
        });
        this.ctx.taskId(taskId);
    }

    @When(value="^\u540c \\(task_id, seq\\) \u7684\u4e8b\u4ef6\u88ab\u91cd\u8907\u76f4\u9001 Kafka \u5169\u7b46$")
    public void duplicateEventsResentToKafka() throws Exception {
        UUID taskId = this.ctx.taskId();
        this.sse.produceRaw(System.currentTimeMillis(), "task:" + String.valueOf(taskId), SseSupport.envelope(1L, taskId, 4, "completed"));
        this.sse.produceRaw(System.currentTimeMillis(), "task:" + String.valueOf(taskId), SseSupport.envelope(1L, taskId, 2, "claimed"));
        Thread.sleep(2000L);
    }

    @When(value="^\u4e00\u7b46 record \u6642\u9593\u6233\u843d\u5f8c (\\d+) \u79d2\u7684\u4e8b\u4ef6\u9032\u5165 task-events topic$")
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
