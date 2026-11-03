package com.athena.tms.bdd.steps.pool;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.PmsProbe;
import com.athena.tms.bdd.support.Terms;
import io.cucumber.java.zh_tw.當;
import java.time.LocalDate;
import java.util.List;

/** 待接池模組的 When（待接池/dsl.md When 句型區）。 */
public class PoolWhenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final MutableClock clock;
    private final PmsProbe pmsProbe;

    public PoolWhenSteps(ScenarioWorld world, BddApiClient apiClient, MutableClock clock, PmsProbe pmsProbe) {
        this.world = world;
        this.apiClient = apiClient;
        this.clock = clock;
        this.pmsProbe = pmsProbe;
    }

    @當("{string} 查看待接池")
    public void staffViewsPool(String staffName) {
        apiClient.get("/pool", BddApiClient.Auth.staff(world.jwt(staffName)));
    }

    @當("{string} 只看 {string} 類的待接池")
    public void staffViewsPoolByCategory(String staffName, String categoryChinese) {
        String category = Terms.poolCategory(categoryChinese);
        apiClient.get("/pool?category=" + category, BddApiClient.Auth.staff(world.jwt(staffName)));
    }

    @當("匯入今日房態 CSV，{string} 的訂房帶 {string} 屬性")
    public void importTodayRoomStateCsv(String roomNo, String attribute) {
        LocalDate checkinDate = clock.instant().atZone(clock.getZone()).toLocalDate();
        // dsl.md 點名這句「走與介面根『從 PMS 拉今日房態』同一套批次比對與冪等鍵」，
        // 而兩者在 main 目前都卡在同一個缺口（見 PmsProbe class Javadoc）：沒有
        // PmsAdapter 介面可以注入，所以直接重用同一個 stub，不另外杜撰一條假的呼叫路徑。
        pmsProbe.pushTodayRoomState(roomNo, List.of(attribute), checkinDate);
    }

    @當("{string} 刪除 {string} 規則")
    public void managerDeletesRule(String managerName, String ruleName) {
        // contracts 與 main 都還沒有自動化規則的寫入端點，沒有「哪個現有呼叫可以重用」
        // 這種退路（不像 importTodayRoomStateCsv 能借 PmsProbe）；與其自己猜一個不存在
        // 的路由去打 404，不如直接標明這是尚待補的產品缺口。
        throw new UnsupportedOperationException(
                "main／contracts 尚未提供刪除自動化規則的 API，等 Feature GREEN 補上後再接上：規則名="
                        + ruleName);
    }

    @當("{string} 停用 {string} 規則")
    public void managerDisablesRule(String managerName, String ruleName) {
        throw new UnsupportedOperationException(
                "main／contracts 尚未提供停用自動化規則的 API，等 Feature GREEN 補上後再接上：規則名="
                        + ruleName);
    }
}
