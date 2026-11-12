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

/**
 * 查房模組的 When（查房/dsl.md When 句型區）。逐項判定一律走
 * {@link InspectionFixtures#submit}（新 body、帶 Idempotency-Key），回應存進
 * {@link ScenarioWorld#setLastInspectionResponse}；送出前先用 {@link LastOperationGuard}
 * 快照，讓「被拒絕、權威資料不變」的 Then 句有東西可比。
 */
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

    /**
     * 查房/dsl.md「"{主管}" 判定 "{任務}" 查房如下：」——items 剛好等於表格，不補不刪：
     * 表格少於或多於現行全集就照送，讓後端自己回 422。顯示名稱先查 sop_items（含已停用的，
     * 「判完才被加項」的案例表格裡列的是舊集合）；查不到就依 {@link InspectionItemKeys}
     * 的同一條規則推回 key，表格多送一個飯店根本沒有的項目時仍送得出去。
     */
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

    /**
     * 查房/dsl.md「同一請求共送出 {次數} 次」——同一把 Idempotency-Key、同一份 body 連送；
     * 次數含第一次。每次回應都會蓋過 lastResponse，最後一次另外存成 lastInspectionResponse，
     * 冪等回放時它和第一次的內容應該相同。
     */
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
