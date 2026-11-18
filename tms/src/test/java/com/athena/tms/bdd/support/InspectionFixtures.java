package com.athena.tms.bdd.support;

import com.athena.tms.bdd.ScenarioWorld;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InspectionFixtures {

    private final BddApiClient apiClient;

    public InspectionFixtures(BddApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ScenarioWorld.ApiResponse submit(
            ScenarioWorld world, String inspectorName, UUID taskId, List<Map<String, Object>> items) {
        return submit(world, inspectorName, taskId, items, UUID.randomUUID().toString());
    }

    public ScenarioWorld.ApiResponse submit(
            ScenarioWorld world,
            String inspectorName,
            UUID taskId,
            List<Map<String, Object>> items,
            String idempotencyKey) {
        Map<String, Object> body = Map.of("taskId", taskId.toString(), "items", items);
        return apiClient.post(
                "/inspections", BddApiClient.Auth.staff(world.jwt(inspectorName)), body, idempotencyKey);
    }
}
