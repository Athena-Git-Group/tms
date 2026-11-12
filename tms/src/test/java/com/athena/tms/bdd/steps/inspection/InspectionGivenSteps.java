package com.athena.tms.bdd.steps.inspection;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.InspectionFixtures;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.StaffFixtures;
import com.athena.tms.bdd.support.TaskFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.common.UuidV7;
import com.athena.tms.common.codes.RolePermissions;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 查房模組的 Given（查房/dsl.md Given 句型區）。只建立/確認權威狀態，不含任何斷言——
 * 「必查」清單在 {@link InspectionThenSteps}。
 *
 * <p>本套件兩個設定鍵（{@code inspection_waived_enabled}、{@code pms.writeback.room_status}）
 * 沿用「以主管身分呼叫 updateTenantConfig」慣例：臨時建一個不對外曝光姓名的主管帳號
 * 純粹用來滿足認證要求。
 */
public class InspectionGivenSteps {

    private final ScenarioWorld world;
    private final StaffFixtures staffFixtures;
    private final TaskFixtures taskFixtures;
    private final RoomFixtures roomFixtures;
    private final TenantFixtures tenantFixtures;
    private final BddApiClient apiClient;
    private final JdbcClient jdbcClient;
    private final InspectionFixtures inspectionFixtures;

    public InspectionGivenSteps(
            ScenarioWorld world,
            StaffFixtures staffFixtures,
            TaskFixtures taskFixtures,
            RoomFixtures roomFixtures,
            TenantFixtures tenantFixtures,
            BddApiClient apiClient,
            JdbcClient jdbcClient,
            InspectionFixtures inspectionFixtures) {
        this.world = world;
        this.staffFixtures = staffFixtures;
        this.taskFixtures = taskFixtures;
        this.roomFixtures = roomFixtures;
        this.tenantFixtures = tenantFixtures;
        this.apiClient = apiClient;
        this.jdbcClient = jdbcClient;
        this.inspectionFixtures = inspectionFixtures;
    }

    @假設("飯店設定免查房開關為 {string}")
    public void hotelSetsInspectionWaivedSwitch(String onOff) {
        updateTenantConfig("inspection_waived_enabled", parseSwitch(onOff));
    }

    @假設("{string} 在免查房開關開啟時開始了 {string} 的續住整理")
    public void staffStartsStayoverWithWaiverEnabled(String staffName, String roomNo) {
        startStayoverWithWaiver(staffName, roomNo, true);
    }

    @假設("{string} 在免查房開關關閉時開始了 {string} 的續住整理")
    public void staffStartsStayoverWithWaiverDisabled(String staffName, String roomNo) {
        startStayoverWithWaiver(staffName, roomNo, false);
    }

    @假設("飯店的「回寫房態」開關為 {string}")
    public void hotelSetsPmsWritebackSwitch(String onOff) {
        updateTenantConfig("pms.writeback.room_status", parseSwitch(onOff));
    }

    @假設("飯店的 PMS 為手動匯入模式")
    public void hotelPmsIsManualImportMode() {
        // dsl.md 沒有指名鍵值；沿用姊妹鍵「pms.writeback.room_status」的命名慣例，
        // 供應商記在 pms.provider，值 manual 表示沒有可回寫的 PMS 對象。
        updateTenantConfig("pms.provider", "manual");
    }

    @假設("今日的打掃查房結果如下：")
    public void todaysInspectionResultsAre(DataTable table) {
        long tenantId = tenantFixtures.testTenantId();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String roomNo = requireCell(row, "房號");
            String content = requireCell(row, "內容");
            String staffName = requireCell(row, "負責人");
            String result = Terms.inspection(requireCell(row, "查房結果"));

            roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
            UUID assigneeId = UUID.fromString(world.staffId(staffName));

            TaskFixtures.Builder builder = taskFixtures.builder()
                    .roomNo(roomNo)
                    .title(content)
                    .type("cleaning")
                    .dept("housekeeping")
                    .source("desk")
                    .assignee(assigneeId);

            UUID taskId;
            if ("failed".equals(result)) {
                taskId = builder.state("accepted").redoCount(1).create();
                appendResultEvent(tenantId, taskId, "inspection_failed");
            } else {
                taskId = builder.state("inspected").create();
                if ("waived".equals(result)) {
                    // TaskFixtures 的 inspected 狀態固定走 engine.passInspection，事件型別
                    // 寫死 inspection_passed；免查這支改標成 inspection_waived 才跟結果一致。
                    renameLastInspectionEvent(taskId, "inspection_waived");
                }
            }

            jdbcClient
                    .sql("UPDATE tasks SET inspection = ? WHERE tenant_id = ? AND id = ?")
                    .params(result, tenantId, taskId)
                    .update();
            world.rememberTask(roomNo, taskId.toString());
        }
    }

    /**
     * 查房/dsl.md「飯店目前設定的查房項目如下：」——把租戶 applies_to=inspection 的啟用集合
     * 整批換成表格列出的這些：先把既有的全部停用（下架用停用不刪，與 sop_items 的設計一致），
     * 再依表格順序寫入／重新啟用，sort_order 就是列序。item key 由 label 產生，
     * 規則集中在 {@link InspectionItemKeys}，When 句解析不到 label 時用同一條規則對回來。
     */
    @假設("飯店目前設定的查房項目如下：")
    public void hotelInspectionItemsAre(DataTable table) {
        long tenantId = tenantFixtures.testTenantId();
        deactivateAllInspectionItems(tenantId);
        int sortOrder = 1;
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String label = requireCell(row, "查房項目");
            upsertInspectionItem(tenantId, label, sortOrder++);
        }
    }

    @假設("飯店停用了全部查房項目")
    public void hotelDeactivatesAllInspectionItems() {
        deactivateAllInspectionItems(tenantFixtures.testTenantId());
    }

    /** 在現行集合之後再加一項：sort_order 取目前最大值加一，確保「依飯店排序」時排在最後。 */
    @假設("飯店新增了查房項目 {string}")
    public void hotelAddsInspectionItem(String label) {
        long tenantId = tenantFixtures.testTenantId();
        Integer maxOrder = jdbcClient
                .sql("SELECT COALESCE(MAX(sort_order), 0) FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection'")
                .params(tenantId)
                .query(Integer.class)
                .single();
        upsertInspectionItem(tenantId, label, maxOrder + 1);
    }

    /**
     * 查房/dsl.md「"{主管}" 已判定 "{任務}" 查房全部項目通過」——Arrange 用的「另一位主管先送出」。
     * 走 {@link InspectionFixtures#submit} 真的打 createInspections，items 為現行全集且全部通過。
     * 這是前提不是待驗證的操作，所以送出沒成功直接視為情境建不起來；回應也不存進
     * {@code lastInspectionResponse}，留給後面的 When 句。
     */
    @假設("{string} 已判定 {string} 查房全部項目通過")
    public void managerAlreadyPassedFullInspection(String managerName, String roomNo) {
        long tenantId = tenantFixtures.testTenantId();
        UUID taskId = UUID.fromString(world.taskId(roomNo));
        List<Map<String, Object>> items = activeInspectionItemKeys(tenantId).stream()
                .map(key -> Map.<String, Object>of("key", key, "pass", true))
                .toList();
        ScenarioWorld.ApiResponse resp = inspectionFixtures.submit(world, managerName, taskId, items);
        if (resp.status() < 200 || resp.status() > 299) {
            throw new IllegalStateException(
                    "前提「" + managerName + " 已判定 " + roomNo + " 查房全部項目通過」建不起來：createInspections 回 status="
                            + resp.status() + "，body=" + resp.body());
        }
    }

    private List<String> activeInspectionItemKeys(long tenantId) {
        return jdbcClient
                .sql(
                        "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection' AND active = true ORDER BY sort_order")
                .params(tenantId)
                .query(String.class)
                .list();
    }

    private void deactivateAllInspectionItems(long tenantId) {
        jdbcClient
                .sql("UPDATE sop_items SET active = false WHERE tenant_id = ? AND applies_to = 'inspection'")
                .params(tenantId)
                .update();
    }

    private void upsertInspectionItem(long tenantId, String label, int sortOrder) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO sop_items (id, tenant_id, applies_to, item_key, label, sort_order, active, created_at)
                        VALUES (?, ?, 'inspection', ?, ?, ?, true, ?)
                        ON CONFLICT (tenant_id, applies_to, item_key)
                        DO UPDATE SET label = EXCLUDED.label, sort_order = EXCLUDED.sort_order, active = true
                        """)
                .params(UuidV7.generate(), tenantId, InspectionItemKeys.fromLabel(label), label, sortOrder, OffsetDateTime.now())
                .update();
    }

    private void startStayoverWithWaiver(String staffName, String roomNo, boolean waived) {
        updateTenantConfig("inspection_waived_enabled", waived);

        UUID assigneeId = UUID.fromString(world.staffId(staffName));
        roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        UUID taskId = taskFixtures.builder()
                .roomNo(roomNo)
                .title("續住整理")
                .type("cleaning")
                .dept("housekeeping")
                .source("desk")
                .estMin(20)
                .state("accepted")
                .assignee(assigneeId)
                .create();
        world.rememberTask(roomNo, taskId.toString());

        // dsl.md：「再呼叫 tasksStart」——走真正的 API 路徑而非直接呼叫 TaskEngine，
        // 讓「開工當下鎖定 inspection_waived」這段產品邏輯經過和 production 相同的入口被驗證。
        apiClient.post("/tasks/" + taskId + "/start", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }

    /**
     * 確認這位員工的查房判定會被系統拒絕。角色對動作的權限由程式碼
     * （{@link RolePermissions}）決定，資料庫沒有任何列可以把單一員工的
     * inspect_submit 設成未勾選。這個前提成立與否取決於員工的角色，
     * 所以這裡查出角色後交給同一份權限程式碼判斷。角色其實有權限時若
     * 當作成立，後面的「操作被拒絕」斷言驗的是系統裡不存在的行為。
     */
    @假設("{string} 的角色沒有查房判定（inspect_submit）權限")
    public void staffLacksInspectSubmitPermission(String staffName) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        String role = jdbcClient
                .sql("SELECT role FROM staff_accounts WHERE id = ?")
                .params(staffId)
                .query(String.class)
                .single();
        if (RolePermissions.canInspectSubmit(role)) {
            throw new IllegalStateException(
                    "「" + staffName + "」的角色 " + role + " 具備 inspect_submit 權限，"
                            + "無法把這個情境建成「沒有查房判定權限」。");
        }
    }

    private void updateTenantConfig(String key, Object value) {
        StaffFixtures.CreatedStaff manager = staffFixtures.createManagerLikeStaff(
                "系統管理者-" + UUID.randomUUID(), "manager", "active");
        long tenantId = tenantFixtures.testTenantId();
        Map<String, Object> body = new HashMap<>();
        body.put("tenantId", tenantId);
        body.put("key", key);
        body.put("value", value);
        apiClient.exchange(
                HttpMethod.PUT, "/tenant-configs/" + key, BddApiClient.Auth.staff(manager.jwt()), body, null);
    }

    private void renameLastInspectionEvent(UUID taskId, String newType) {
        jdbcClient
                .sql("UPDATE task_events SET type = ? WHERE task_id = ? AND type = 'inspection_passed'")
                .params(newType, taskId)
                .update();
    }

    private void appendResultEvent(long tenantId, UUID taskId, String type) {
        Integer seq = jdbcClient
                .sql("UPDATE tasks SET event_seq = event_seq + 1 WHERE tenant_id = ? AND id = ? RETURNING event_seq")
                .params(tenantId, taskId)
                .query(Integer.class)
                .single();
        jdbcClient
                .sql(
                        """
                        INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
                        VALUES (?, ?, ?, ?, 'manager', NULL, NULL, ?)
                        """)
                .params(tenantId, taskId, seq, type, OffsetDateTime.now())
                .update();
    }

    private static boolean parseSwitch(String chinese) {
        if ("開".equals(chinese)) {
            return true;
        }
        if ("關".equals(chinese)) {
            return false;
        }
        throw new IllegalArgumentException("開關只支援「開」「關」，收到: " + chinese);
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v;
    }
}
