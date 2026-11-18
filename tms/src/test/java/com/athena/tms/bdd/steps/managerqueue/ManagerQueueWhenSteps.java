package com.athena.tms.bdd.steps.managerqueue;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import io.cucumber.java.zh_tw.當;
import java.util.UUID;

public class ManagerQueueWhenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private UUID lastCallerStaffId;

    public ManagerQueueWhenSteps(ScenarioWorld world, BddApiClient apiClient) {
        this.world = world;
        this.apiClient = apiClient;
    }

    @當("{string} 查看主管待辦")
    public void staffViewsManagerQueues(String staffName) {
        lastCallerStaffId = UUID.fromString(world.staffId(staffName));
        ScenarioWorld.ApiResponse response =
                apiClient.get("/me/manager-queues", BddApiClient.Auth.staff(world.jwt(staffName)));
        world.setLastManagerQueuesResponse(response);
    }

    public UUID lastCallerStaffId() {
        if (lastCallerStaffId == null) {
            throw new IllegalStateException("本 scenario 尚未查看主管待辦");
        }
        return lastCallerStaffId;
    }
}
