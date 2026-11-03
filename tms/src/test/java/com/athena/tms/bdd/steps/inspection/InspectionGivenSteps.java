package com.athena.tms.bdd.steps.inspection;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.StaffFixtures;
import com.athena.tms.bdd.support.TaskFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 查房模組的 Given（查房/dsl.md Given 句型區）。只建立/確認權威狀態，不含任何斷言——
 * 「必查」清單在 {@link InspectionThenSteps}。
 *
 * <p>本套件兩個設定鍵（{@code inspection_waived_enabled}、{@code pms.writeback.room_status}）
 * 與既有 {@code CommonGivenSteps#slaEscalationConfig} 同樣的「以主管身分呼叫
 * updateTenantConfig」慣例：臨時建一個不對外曝光姓名的主管帳號純粹用來滿足認證要求。
 */
public class InspectionGivenSteps {

    private final ScenarioWorld world;
    private final StaffFixtures staffFixtures;
    private final TaskFixtures taskFixtures;
    private final RoomFixtures roomFixtures;
    private final TenantFixtures tenantFixtures;
    private final BddApiClient apiClient;
    private final JdbcClient jdbcClient;

    public InspectionGivenSteps(
            ScenarioWorld world,
            StaffFixtures staffFixtures,
            TaskFixtures taskFixtures,
            RoomFixtures roomFixtures,
            TenantFixtures tenantFixtures,
            BddApiClient apiClient,
            JdbcClient jdbcClient) {
        this.world = world;
        this.staffFixtures = staffFixtures;
        this.taskFixtures = taskFixtures;
        this.roomFixtures = roomFixtures;
        this.tenantFixtures = tenantFixtures;
        this.apiClient = apiClient;
        this.jdbcClient = jdbcClient;
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
        // dsl.md 沒有指名鍵值；沿用姊妹鍵「pms.writeback.room_status」的命名慣例另開一鍵，
        // 兩者都還不在 tenant_config.key 的 CHECK 封閉鍵集內（見 data-model.dbml Table
        // tenant_config 的 note），呼叫 updateTenantConfig 目前必然打不到任何已實作的路由。
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
                // dsl.md：「退回重做是 accepted 且 redo 1」。
                taskId = builder.state("accepted").redoCount(1).create();
                appendResultEvent(tenantId, taskId, "inspection_failed");
            } else {
                // dsl.md：「通過與免查是 inspected」。
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
        // 讓「開工當下鎖定 inspection_waived」這段產品邏輯（尚未實作）成為後續 Then 的 RED 訊號。
        apiClient.post("/tasks/" + taskId + "/start", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }

    @假設("{string} 的角色沒有查房判定（inspect_submit）權限")
    public void staffLacksInspectSubmitPermission(String staffName) {
        // specs/truth/contracts/components/schemas/員工帳號與登入.yaml 明寫「角色×動作矩陣在
        // 程式/設定，不在表裡」：staff_accounts 沒有任何欄位或關聯表可以承載「這個角色的
        // inspect_submit 權限」，現在沒有地方可以落這個設定。等 Feature GREEN 建立權限設定
        // 後再接上真正的落地方式。
        throw new UnsupportedOperationException(
                "目前沒有角色權限矩陣可調整，無法把「" + staffName + "」的 inspect_submit 權限設為未勾選。");
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
