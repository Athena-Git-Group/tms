package com.athena.tms.bdd.support;

import com.athena.tms.bdd.ScenarioWorld;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class VerifyFixtures {

    private final BddApiClient apiClient;

    public VerifyFixtures(BddApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ScenarioWorld.ApiResponse send(
            ScenarioWorld world, String managerName, String ticketNo, String result, String reason) {
        return send(world, managerName, ticketNo, result, reason, UUID.randomUUID().toString());
    }

    public ScenarioWorld.ApiResponse send(
            ScenarioWorld world,
            String managerName,
            String ticketNo,
            String result,
            String reason,
            String idempotencyKey) {
        String ticketTaskId = world.repairTicketTaskId(ticketNo);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("result", result);
        if (reason != null) {
            body.put("reason", reason);
        }
        return apiClient.post(
                "/repair-tickets/" + ticketTaskId + "/verify",
                BddApiClient.Auth.staff(world.jwt(managerName)),
                body,
                idempotencyKey);
    }
}
