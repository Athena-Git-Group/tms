package com.athena.tms.bdd.steps.pool;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.PmsProbe;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.service.AutomationRuleService;
import io.cucumber.java.zh_tw.當;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 待認領模組的 When（待認領/dsl.md When 句型區）。 */
public class PoolWhenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final MutableClock clock;
    private final PmsProbe pmsProbe;
    private final AutomationRuleService automationRuleService;
    private final TenantFixtures tenantFixtures;

    public PoolWhenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            MutableClock clock,
            PmsProbe pmsProbe,
            AutomationRuleService automationRuleService,
            TenantFixtures tenantFixtures) {
        this.world = world;
        this.apiClient = apiClient;
        this.clock = clock;
        this.pmsProbe = pmsProbe;
        this.automationRuleService = automationRuleService;
        this.tenantFixtures = tenantFixtures;
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

    @當("{string} 刪除 {string} 規則")
    public void managerDeletesRule(String managerName, String ruleName) {
        // contracts 還沒有自動化規則的寫入端點，比照 PmsProbe 的接線方式直接呼叫
        // production service；等 contracts 補上路由後，這裡改走 HTTP 即可。
        automationRuleService.deleteRule(tenantFixtures.testTenantId(), ruleName);
    }

    @當("{string} 停用 {string} 規則")
    public void managerDisablesRule(String managerName, String ruleName) {
        automationRuleService.disableRule(tenantFixtures.testTenantId(), ruleName);
    }

    @當("{string} 認領 {string}")
    public void staffClaimsTask(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
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
