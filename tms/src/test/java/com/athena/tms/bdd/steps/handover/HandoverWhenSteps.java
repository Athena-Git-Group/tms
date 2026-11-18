package com.athena.tms.bdd.steps.handover;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import io.cucumber.java.zh_tw.當;
import java.util.UUID;

public class HandoverWhenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;

    public HandoverWhenSteps(ScenarioWorld world, BddApiClient apiClient) {
        this.world = world;
        this.apiClient = apiClient;
    }

    @當("{string} 打開 {string} 的任務")
    public void staffOpensTask(String staffName, String taskKey) {
        UUID taskId = UUID.fromString(world.taskId(taskKey));
        apiClient.get("/tasks/" + taskId, BddApiClient.Auth.staff(world.jwt(staffName)));
    }

    @當("{string} 展開 {string} 的交辦")
    public void staffExpandsHandover(String staffName, String taskKey) {
        UUID taskId = UUID.fromString(world.taskId(taskKey));
        apiClient.post("/tasks/" + taskId + "/handover-read", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }
}
