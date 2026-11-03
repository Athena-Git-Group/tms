package com.athena.tms.bdd.steps.common;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.JobTrigger;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.PmsProbe;
import com.athena.tms.bdd.support.TenantFixtures;
import io.cucumber.java.zh_tw.當;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 介面根共通句的 When（dsl.md When 句型區）。 */
public class CommonWhenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final MutableClock clock;
    private final JobTrigger jobTrigger;
    private final PmsProbe pmsProbe;
    private final TenantFixtures tenantFixtures;
    private final JdbcClient jdbcClient;

    public CommonWhenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            MutableClock clock,
            JobTrigger jobTrigger,
            PmsProbe pmsProbe,
            TenantFixtures tenantFixtures,
            JdbcClient jdbcClient) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.clock = clock;
        this.jobTrigger = jobTrigger;
        this.pmsProbe = pmsProbe;
        this.tenantFixtures = tenantFixtures;
        this.jdbcClient = jdbcClient;
    }

    @當("{string} 開始 {string}")
    public void staffStartsTask(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        // 後面可能接「這次操作被拒絕」，需要操作前的任務列快照才比得出「沒有被改動」。
        LastOperationGuard.captureFor(dbProbe, taskId);
        apiClient.post("/tasks/" + taskId + "/start", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }

    @當("{string} 恢復 {string}")
    public void staffResumesTask(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        LastOperationGuard.captureFor(dbProbe, taskId);
        apiClient.post("/tasks/" + taskId + "/resume", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }

    @當("{string} 完成 {string}")
    public void staffCompletesTask(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        LastOperationGuard.captureFor(dbProbe, taskId);
        apiClient.post("/tasks/" + taskId + "/complete", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }

    @當("{string} 判定 {string} 查房全部項目通過")
    public void staffPassesFullInspection(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        LastOperationGuard.captureFor(dbProbe, taskId);

        long tenantId = tenantFixtures.testTenantId();
        List<String> itemKeys = jdbcClient
                .sql(
                        "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection' AND active = true ORDER BY sort_order")
                .params(tenantId)
                .query(String.class)
                .list();
        List<Map<String, Object>> items = itemKeys.stream()
                .map(key -> Map.<String, Object>of("key", key, "pass", true))
                .toList();

        Map<String, Object> body = Map.of(
                "tenantId", tenantId,
                "taskId", taskId.toString(),
                "inspectorId", world.staffId(staffName),
                "result", "passed",
                "items", items);
        apiClient.post("/inspections", BddApiClient.Auth.staff(world.jwt(staffName)), body);
    }

    @當("時間來到 {string}")
    public void timeAdvancesTo(String hhmm) {
        clock.setTodayAt(hhmm);
        // 時間推進後，所有依賴時間的週期工作都該有機會跑一輪：逾時升級與排程建單
        // 在正式環境各自定時掃描，測試裡同步觸發，情境才不用等真實排程間隔。
        jobTrigger.run("escalation-scan");
        jobTrigger.run("schedule-rule-scan");
        registerRuleCreatedTasks();
    }

    @當("從 PMS 拉今日房態，{string} 的訂房帶 {string} 屬性")
    public void pullTodayRoomStateFromPms(String roomNo, String attribute) {
        LocalDate checkinDate = clock.instant().atZone(clock.getZone()).toLocalDate();
        // PmsProbe 固定丟 UnsupportedOperationException（main 還沒有 PmsAdapter），見其類別註解。
        pmsProbe.pushTodayRoomState(roomNo, List.of(attribute), checkinDate);
    }

    /**
     * 把排程規則剛建出的工單登記進 {@link ScenarioWorld}：後續 Then 句以房號（公共
     * 區域的單以內容）取任務 id，規則引擎建的單沒有任何 Given 登記過，不補登記
     * 就找不到。鍵相同時重複登記的是同一張單，覆寫無害。
     */
    private void registerRuleCreatedTasks() {
        long tenantId = tenantFixtures.testTenantId();
        List<Map<String, Object>> rows = jdbcClient
                .sql(
                        """
                        SELECT t.id, t.title, r.room_no
                          FROM tasks t
                          JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                         WHERE t.tenant_id = ? AND t.rule_hit IS NOT NULL AND t.pool_source = 'system'
                        """)
                .params(tenantId)
                .query()
                .listOfRows();
        for (Map<String, Object> row : rows) {
            String roomNo = String.valueOf(row.get("room_no"));
            String key = "公共區域".equals(roomNo) ? String.valueOf(row.get("title")) : roomNo;
            world.rememberTask(key, String.valueOf(row.get("id")));
        }
    }

    private UUID resolveTaskId(String key) {
        if (key.startsWith("W-")) {
            return UUID.fromString(world.repairTicketTaskId(key));
        }
        return UUID.fromString(world.taskId(key));
    }
}
