package com.athena.tms.bdd.support;

import com.athena.tms.bdd.ScenarioWorld;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 查房送出的測試入口（contracts {@code createInspections}）。
 *
 * <p>只做一件事：以某位員工的身分對一張單送出逐項判定，請求形狀照
 * {@code CreateInspectionRequest}——只帶 {@code taskId} 與 {@code items[{key, pass}]}，
 * 租戶與查房者由權杖推導、通過或退回由後端算，這裡不送也不算。每次送出都帶
 * 一把新的 {@code Idempotency-Key}；要驗證重送回放的情境請用帶 key 的多載。
 *
 * <p>回傳整個 HTTP 回應、不做任何斷言：Given 句只關心單有沒有被送過去，When／Then 句
 * 才需要看狀態碼與 {@code InspectionOutcome}，各自決定要不要存進 {@link ScenarioWorld}。
 */
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
