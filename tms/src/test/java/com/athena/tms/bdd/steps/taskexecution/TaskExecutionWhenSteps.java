package com.athena.tms.bdd.steps.taskexecution;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.LastOperationGuard;
import io.cucumber.java.zh_tw.當;
import java.util.Map;
import java.util.UUID;

public class TaskExecutionWhenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final LastOperationGuard lastOperationGuard;

    public TaskExecutionWhenSteps(ScenarioWorld world, BddApiClient apiClient, LastOperationGuard lastOperationGuard) {
        this.world = world;
        this.apiClient = apiClient;
        this.lastOperationGuard = lastOperationGuard;
    }

    @當("{string} 以原因 {string} 延後 {string}")
    public void staffDefersTaskWithReason(String staffName, String reason, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        apiClient.post(
                "/tasks/" + taskId + "/defer",
                BddApiClient.Auth.staff(world.jwt(staffName)),
                Map.of("reason", reason));
    }

    @當("{string} 以 {int} 字的自填原因延後 {string}")
    public void staffDefersTaskWithGeneratedReason(String staffName, int charCount, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        String reason = "住".repeat(charCount);
        apiClient.post(
                "/tasks/" + taskId + "/defer",
                BddApiClient.Auth.staff(world.jwt(staffName)),
                Map.of("reason", reason));
    }

    @當("{string} 以原因 {string} 中斷 {string}")
    public void staffInterruptsTaskWithReason(String staffName, String reason, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        apiClient.post(
                "/tasks/" + taskId + "/interrupt",
                BddApiClient.Auth.staff(world.jwt(staffName)),
                Map.of("reason", reason));
    }

    private UUID resolveTaskId(String key) {
        if (key.startsWith("W-")) {
            return UUID.fromString(world.repairTicketTaskId(key));
        }
        return UUID.fromString(world.taskId(key));
    }
}
