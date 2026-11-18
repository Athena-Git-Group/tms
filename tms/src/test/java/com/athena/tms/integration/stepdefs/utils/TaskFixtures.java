package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.dto.TaskCreateCommand;
import com.athena.tms.entity.Task;
import com.athena.tms.service.TaskEngine;
import java.util.UUID;

public class TaskFixtures {

    private final TaskEngine engine;

    public TaskFixtures(TaskEngine engine) {
        this.engine = engine;
    }

    public TaskEngine engine() {
        return engine;
    }

    public Task newPoolTask(String title) {
        return Tenants.asTenant(1L, () -> engine.create(new TaskCreateCommand(
                1L, "amenity", "desk", TestData.ROOM_305, TestData.RESERVATION_B001, "housekeeping", "pool",
                null, title, null, null, null, null, TestData.FD1)));
    }

    public void claimAndStartAsHk1(UUID taskId) {
        Tenants.runAsTenant(1L, () -> {
            engine.claim(1L, taskId, "housekeeping", TestData.HK1);
            engine.start(1L, taskId, TestData.HK1);
        });
    }

    public UUID newDoneTask(String title) {
        Task task = newPoolTask(title);
        Tenants.runAsTenant(1L, () -> {
            engine.claim(1L, task.getId(), "housekeeping", TestData.HK1);
            engine.start(1L, task.getId(), TestData.HK1);
            engine.complete(1L, task.getId(), TestData.HK1);
        });
        return task.getId();
    }
}
