package com.athena.tms.bdd.steps.managerqueue;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import io.cucumber.java.zh_tw.當;
import java.util.UUID;

/**
 * 主管工作台模組的 When（dsl.md When 句型區）：以某位員工身分查看主管待辦。
 *
 * <p>查詢不寫資料，所以不走 {@code LastOperationGuard}；回應除了 {@link BddApiClient}
 * 例行存的 lastResponse，另外存進 {@link ScenarioWorld#setLastManagerQueuesResponse}，
 * 讓本模組的 Then 不受後續呼叫覆蓋。呼叫者是誰也記下來：待查房的「負責人不是呼叫者本人」
 * 這條公式要拿它重算。
 */
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

    /** 最近一次「查看主管待辦」的呼叫者 staff id；尚未呼叫時拋 IllegalStateException。 */
    public UUID lastCallerStaffId() {
        if (lastCallerStaffId == null) {
            throw new IllegalStateException("本 scenario 尚未查看主管待辦");
        }
        return lastCallerStaffId;
    }
}
