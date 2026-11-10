package com.athena.tms.bdd.steps.pool;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.PmsProbe;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.service.AutomationRuleService;
import io.cucumber.java.zh_tw.當;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class PoolWhenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final MutableClock clock;
    private final PmsProbe pmsProbe;
    private final AutomationRuleService automationRuleService;
    private final TenantFixtures tenantFixtures;
    private final LastOperationGuard lastOperationGuard;

    public PoolWhenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            MutableClock clock,
            PmsProbe pmsProbe,
            AutomationRuleService automationRuleService,
            TenantFixtures tenantFixtures,
            LastOperationGuard lastOperationGuard) {
        this.world = world;
        this.apiClient = apiClient;
        this.clock = clock;
        this.pmsProbe = pmsProbe;
        this.automationRuleService = automationRuleService;
        this.tenantFixtures = tenantFixtures;
        this.lastOperationGuard = lastOperationGuard;
    }

    @當("{string} 查看待認領")
    public void staffViewsPool(String staffName) {
        apiClient.get("/pool", BddApiClient.Auth.staff(world.jwt(staffName)));
    }

    @當("{string} 只看 {string} 類的待認領")
    public void staffViewsPoolByCategory(String staffName, String categoryChinese) {
        String category = Terms.poolCategory(categoryChinese);
        apiClient.get("/pool?category=" + category, BddApiClient.Auth.staff(world.jwt(staffName)));
    }

    @當("匯入今日房態 CSV，{string} 的訂房帶 {string} 屬性")
    public void importTodayRoomStateCsv(String roomNo, String attribute) {
        LocalDate checkinDate = clock.instant().atZone(clock.getZone()).toLocalDate();
        // dsl.md 點名這句「走與介面根『從 PMS 拉今日房態』同一套批次比對與冪等鍵」，
        // 所以直接重用同一個入口（見 PmsProbe），兩條路徑自然共用同一套冪等鍵。
        pmsProbe.pushTodayRoomState(roomNo, List.of(attribute), checkinDate);
    }

    @當("{string} 規則已被刪除")
    public void ruleDeleted(String ruleName) {
        automationRuleService.deleteRule(tenantFixtures.testTenantId(), ruleName);
    }

    @當("{string} 停用 {string} 規則")
    public void managerDisablesRule(String managerName, String ruleName) {
        automationRuleService.disableRule(tenantFixtures.testTenantId(), ruleName);
    }

    @當("{string} 認領 {string}")
    public void staffClaimsTask(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        // 認領可能撞單（任務已被他人認領），接下來的「這次操作被拒絕」Then 句要比對
        // 操作前後的任務列、事件數與 outbox 數有沒有變動，靠的是這裡先留一份快照。
        lastOperationGuard.captureFor(taskId);
        apiClient.post("/tasks/" + taskId + "/claim", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }

    /** 任務：房號（對應 {@link ScenarioWorld#taskId}）或工務單號 W-NNNN（對應 {@link ScenarioWorld#repairTicketTaskId}）。 */
    private UUID resolveTaskId(String key) {
        if (key.startsWith("W-")) {
            return UUID.fromString(world.repairTicketTaskId(key));
        }
        return UUID.fromString(world.taskId(key));
    }
}
