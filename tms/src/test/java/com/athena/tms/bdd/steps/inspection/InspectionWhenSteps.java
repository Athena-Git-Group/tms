package com.athena.tms.bdd.steps.inspection;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.LastOperationGuard;
import io.cucumber.java.zh_tw.當;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InspectionWhenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final LastOperationGuard lastOperationGuard;

    public InspectionWhenSteps(ScenarioWorld world, BddApiClient apiClient, LastOperationGuard lastOperationGuard) {
        this.world = world;
        this.apiClient = apiClient;
        this.lastOperationGuard = lastOperationGuard;
    }

    @當("{string} 抽查 {string}，判定 {string} 不合格")
    public void managerSpotChecksFailing(String managerName, String roomNo, String item) {
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        lastOperationGuard.captureFor(taskId);

        // dsl.md 沒有指明「項目」中文如何對照 sop_items.item_key，本情境也沒有先建立查房
        // 項目的 Given，這裡直接把中文項目名當 key 送出，不杜撰一份未經授權的對照表。
        Map<String, Object> body = Map.of(
                "taskId", taskId.toString(),
                "items", List.of(Map.of("key", item, "pass", false)));
        apiClient.post(
                "/inspections/spot-checks", BddApiClient.Auth.staff(world.jwt(managerName)), body);
    }

    @當("{string} 查看今日查房合格率")
    public void managerViewsTodaysPassRate(String managerName) {
        apiClient.get("/inspections/pass-rate", BddApiClient.Auth.staff(world.jwt(managerName)));
    }

    @當("{string} 查看查房者名單")
    public void managerViewsInspectorList(String managerName) {
        // 契約沒有專門的「查房者名單」端點，沿用 listStaffAccounts（GET /staff-accounts）
        // 當作「可選的查房者名單」查詢，由後續 Then 斷言過濾/排除是否正確。
        apiClient.get("/staff-accounts", BddApiClient.Auth.staff(world.jwt(managerName)));
    }
}
