package com.athena.tms.bdd.steps.inspection;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.InspectionFixtures;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.TenantFixtures;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.當;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public class InspectionWhenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final LastOperationGuard lastOperationGuard;
    private final InspectionFixtures inspectionFixtures;
    private final TenantFixtures tenantFixtures;
    private final JdbcClient jdbcClient;

    public InspectionWhenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            LastOperationGuard lastOperationGuard,
            InspectionFixtures inspectionFixtures,
            TenantFixtures tenantFixtures,
            JdbcClient jdbcClient) {
        this.world = world;
        this.apiClient = apiClient;
        this.lastOperationGuard = lastOperationGuard;
        this.inspectionFixtures = inspectionFixtures;
        this.tenantFixtures = tenantFixtures;
        this.jdbcClient = jdbcClient;
    }

    @當("{string} 抽查 {string}，判定 {string} 不合格")
    public void managerSpotChecksFailing(String managerName, String roomNo, String item) {
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        lastOperationGuard.captureFor(taskId);

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
        apiClient.get("/staff-accounts", BddApiClient.Auth.staff(world.jwt(managerName)));
    }

    @當("{string} 判定 {string} 查房如下：")
    public void managerJudgesInspectionAs(String managerName, String roomNo, DataTable table) {
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        Map<String, String> keyByLabel = inspectionItemKeyByLabel();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String label = requireCell(row, "查房項目");
            String key = keyByLabel.getOrDefault(label, InspectionItemKeys.fromLabel(label));
            items.add(Map.of("key", key, "pass", parseVerdict(requireCell(row, "判定"))));
        }
        submitAndRemember(managerName, taskId, items);
    }

    @當("{string} 以空的項目清單判定 {string} 查房")
    public void managerJudgesInspectionWithEmptyItems(String managerName, String roomNo) {
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        submitAndRemember(managerName, taskId, List.of());
    }

    @當("{string} 判定 {string} 查房全部項目通過，同一請求共送出 {int} 次")
    public void managerPassesFullInspectionResentTimes(String managerName, String roomNo, int times) {
        if (times < 1) {
            throw new IllegalArgumentException("送出次數必須至少 1 次（含第一次），收到: " + times);
        }
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        lastOperationGuard.captureFor(taskId);
        List<Map<String, Object>> items = activeInspectionItemKeys().stream()
                .map(key -> Map.<String, Object>of("key", key, "pass", true))
                .toList();
        world.setLastInspectionItems(items);
        String idempotencyKey = UUID.randomUUID().toString();
        ScenarioWorld.ApiResponse last = null;
        for (int i = 0; i < times; i++) {
            last = inspectionFixtures.submit(world, managerName, taskId, items, idempotencyKey);
        }
        world.setLastInspectionResponse(last);
    }

    private void submitAndRemember(String managerName, UUID taskId, List<Map<String, Object>> items) {
        lastOperationGuard.captureFor(taskId);
        world.setLastInspectionItems(items);
        world.setLastInspectionResponse(inspectionFixtures.submit(world, managerName, taskId, items));
    }

    private Map<String, String> inspectionItemKeyByLabel() {
        Map<String, String> map = new HashMap<>();
        jdbcClient
                .sql("SELECT item_key, label FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection' ORDER BY sort_order")
                .params(tenantFixtures.testTenantId())
                .query()
                .listOfRows()
                .forEach(r -> map.put(String.valueOf(r.get("label")), String.valueOf(r.get("item_key"))));
        return map;
    }

    private List<String> activeInspectionItemKeys() {
        return jdbcClient
                .sql(
                        "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection' AND active = true ORDER BY sort_order")
                .params(tenantFixtures.testTenantId())
                .query(String.class)
                .list();
    }

    private static boolean parseVerdict(String chinese) {
        if ("通過".equals(chinese)) {
            return true;
        }
        if ("不通過".equals(chinese)) {
            return false;
        }
        throw new IllegalArgumentException("判定只支援「通過」「不通過」，收到: " + chinese);
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v;
    }
}
