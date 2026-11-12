package com.athena.tms.bdd.support;

import com.athena.tms.bdd.ScenarioWorld;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 主管驗收工務單的測試入口（contracts {@code verifyRepairTicket}）。
 *
 * <p>只做一件事：以某位主管的身分對一張工務單送出驗收結果，請求形狀照
 * {@code VerifyRepairTicketRequest}，帶 {@code result}（{@code passed}｜{@code failed}）與
 * {@code reason}。原因是 null 時整個欄位不送，讓「通過不必填原因」與「不通過卻沒給原因」
 * 都能照原樣打到後端。每次送出都帶一把新的 {@code Idempotency-Key}；要驗證重送回放的
 * 情境請用帶 key 的多載。
 *
 * <p>回傳整個 HTTP 回應、不做任何斷言：Given 句只關心單有沒有被驗收過，When／Then 句
 * 才需要看狀態碼與 {@code RepairVerificationOutcome}，各自決定要不要存進 {@link ScenarioWorld}。
 */
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
