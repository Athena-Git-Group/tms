package com.athena.tms.bdd.steps.common;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.InspectionFixtures;
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

public class CommonWhenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final LastOperationGuard lastOperationGuard;
    private final MutableClock clock;
    private final JobTrigger jobTrigger;
    private final PmsProbe pmsProbe;
    private final TenantFixtures tenantFixtures;
    private final JdbcClient jdbcClient;
    private final InspectionFixtures inspectionFixtures;

    public CommonWhenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            LastOperationGuard lastOperationGuard,
            MutableClock clock,
            JobTrigger jobTrigger,
            PmsProbe pmsProbe,
            TenantFixtures tenantFixtures,
            JdbcClient jdbcClient,
            InspectionFixtures inspectionFixtures) {
        this.world = world;
        this.apiClient = apiClient;
        this.lastOperationGuard = lastOperationGuard;
        this.clock = clock;
        this.jobTrigger = jobTrigger;
        this.pmsProbe = pmsProbe;
        this.tenantFixtures = tenantFixtures;
        this.jdbcClient = jdbcClient;
        this.inspectionFixtures = inspectionFixtures;
    }

    @當("{string} 開始 {string}")
    public void staffStartsTask(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        apiClient.post("/tasks/" + taskId + "/start", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }

    @當("{string} 恢復 {string}")
    public void staffResumesTask(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        apiClient.post("/tasks/" + taskId + "/resume", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }

    @當("{string} 完成 {string}")
    public void staffCompletesTask(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);
        apiClient.post("/tasks/" + taskId + "/complete", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }

    @當("{string} 判定 {string} 查房全部項目通過")
    public void staffPassesFullInspection(String staffName, String taskKey) {
        UUID taskId = resolveTaskId(taskKey);
        lastOperationGuard.captureFor(taskId);

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

        ScenarioWorld.ApiResponse response = inspectionFixtures.submit(world, staffName, taskId, items);
        world.setLastInspectionResponse(response);
        world.setLastInspectionItems(items);
    }

    @當("時間來到 {string}")
    public void timeAdvancesTo(String hhmm) {
        clock.setTodayAt(hhmm);
        jobTrigger.run("unclaimed-reminder-scan");
        jobTrigger.run("schedule-rule-scan");
        registerRuleCreatedTasks();
    }

    @當("從 PMS 拉今日房態，{string} 的訂房帶 {string} 屬性")
    public void pullTodayRoomStateFromPms(String roomNo, String attribute) {
        LocalDate checkinDate = clock.instant().atZone(clock.getZone()).toLocalDate();
        pmsProbe.pushTodayRoomState(roomNo, List.of(attribute), checkinDate);
    }

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
