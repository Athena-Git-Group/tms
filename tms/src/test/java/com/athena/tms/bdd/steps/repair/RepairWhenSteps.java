package com.athena.tms.bdd.steps.repair;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.當;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpMethod;

/**
 * 工務單模組的 When（dsl.md When 句型區）。每句呼叫 {@link BddApiClient}，不含任何斷言——
 * 「必查」清單在 {@link RepairThenSteps}。
 *
 * <p>destructive 操作（可能接 common「這次操作被拒絕」）前都呼叫
 * {@link LastOperationGuard#captureFor}；會接 {@link RepairThenSteps} 的
 * 「系統沒有建立工務單」「不該發生：沒有建立工務單」的操作，另外呼叫
 * {@link RepairCountGuard#captureFor}（本模組專屬的全租戶筆數快照，見其 Javadoc）。
 */
public class RepairWhenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final MutableClock clock;
    private final TenantFixtures tenantFixtures;

    public RepairWhenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            MutableClock clock,
            TenantFixtures tenantFixtures) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.clock = clock;
        this.tenantFixtures = tenantFixtures;
    }

    @當("{string} 中斷 {string} 並開立以下工務單：")
    public void interruptAndOpenTicket(String staffName, String taskKey, DataTable table) {
        createTicket(staffName, taskKey, table, true, null);
    }

    @當("{string} 從 {string} 開立以下工務單：")
    public void openTicketWithoutInterrupt(String staffName, String taskKey, DataTable table) {
        createTicket(staffName, taskKey, table, false, null);
    }

    @當("{string} 從 {string} 開立以下工務單，同一請求共送出 {int} 次：")
    public void openTicketRepeatedly(String staffName, String taskKey, int times, DataTable table) {
        // dsl.md：同一個 Idempotency-Key 連續呼叫，第二次起由 processed_ops 回放第一次的回應；
        // key 必須在回合外先固定一次，否則每次呼叫都會各自拿到 BddApiClient 自動產生的新 key，
        // 就驗不到冪等回放。
        String idempotencyKey = UUID.randomUUID().toString();
        for (int i = 0; i < times; i++) {
            createTicket(staffName, taskKey, table, false, idempotencyKey);
        }
    }

    @當("{string} 中斷 {string} 並以已準備的照片開立水電工務單")
    public void interruptAndOpenTicketWithPreparedPhotos(String staffName, String taskKey) {
        UUID sourceTaskId = UUID.fromString(world.taskId(taskKey));
        LastOperationGuard.captureFor(dbProbe, sourceTaskId);
        RepairCountGuard.captureFor(dbProbe, tenantFixtures.testTenantId());

        Map<String, Object> body = Map.of(
                "sourceTaskId", sourceTaskId.toString(),
                "category", Terms.repairCategory("水電"),
                "photoIds", world.photoIds(),
                "urgent", false,
                "interruptSourceTask", true);
        apiClient.post("/repair-tickets", BddApiClient.Auth.staff(world.jwt(staffName)), body);
    }

    @當("{string} 對 {string} 選擇工務單類別 {string}")
    public void selectCategoryForPrecheck(String staffName, String roomNo, String categoryChinese) {
        UUID sourceTaskId = UUID.fromString(world.taskId(roomNo));
        // precheck 本身不建單；T6／T8 要驗「不該發生：沒有建立工務單」，在這裡先留一份筆數快照。
        RepairCountGuard.captureFor(dbProbe, tenantFixtures.testTenantId());

        Map<String, Object> body =
                Map.of("sourceTaskId", sourceTaskId.toString(), "category", Terms.repairCategory(categoryChinese));
        apiClient.post("/repair-tickets/precheck", BddApiClient.Auth.staff(world.jwt(staffName)), body);
    }

    @當("{string} 補 {int} 張照片到 {string}")
    public void appendPhotosToTicket(String staffName, int count, String ticketNo) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        LastOperationGuard.captureFor(dbProbe, ticketTaskId);

        String jwt = world.jwt(staffName);
        List<String> photoIds = uploadPhotos(jwt, "report", count);

        Map<String, Object> body = Map.of("photoIds", photoIds);
        apiClient.post("/repair-tickets/" + ticketTaskId + "/photos", BddApiClient.Auth.staff(jwt), body);
    }

    @當("{string} 撤回 {string}")
    public void withdrawTicket(String staffName, String ticketNo) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        LastOperationGuard.captureFor(dbProbe, ticketTaskId);
        apiClient.post(
                "/repair-tickets/" + ticketTaskId + "/withdraw", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }

    @當("{string} 附 {int} 張完修照片、處理說明 {string} 完成 {string}")
    public void completeWithPhotosAndNote(String staffName, int photoCount, String note, String ticketNo) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        LastOperationGuard.captureFor(dbProbe, ticketTaskId);

        String jwt = world.jwt(staffName);
        List<String> photoIds = uploadPhotos(jwt, "completion", photoCount);

        Map<String, Object> body = Map.of("photoIds", photoIds, "resolutionNote", note);
        apiClient.post("/tasks/" + ticketTaskId + "/complete", BddApiClient.Auth.staff(jwt), body);
    }

    @當("{string} 不附完修照片完成 {string}")
    public void completeWithoutPhotos(String staffName, String ticketNo) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        LastOperationGuard.captureFor(dbProbe, ticketTaskId);

        Map<String, Object> body = Map.of("photoIds", List.of());
        apiClient.post("/tasks/" + ticketTaskId + "/complete", BddApiClient.Auth.staff(world.jwt(staffName)), body);
    }

    /** W1／W2／W3 共用：準備照片（若 Data Table 要求）後呼叫 createRepairTicket。 */
    private void createTicket(
            String staffName, String taskKey, DataTable table, boolean interrupt, String idempotencyKey) {
        Map<String, String> row = table.asMaps(String.class, String.class).get(0);
        String categoryChinese = requireCell(row, "類別");
        String description = blankToNull(row.get("說明"));
        int photoCount = Integer.parseInt(requireCell(row, "照片張數"));
        boolean urgent = "是".equals(requireCell(row, "急件"));

        UUID sourceTaskId = UUID.fromString(world.taskId(taskKey));
        LastOperationGuard.captureFor(dbProbe, sourceTaskId);
        RepairCountGuard.captureFor(dbProbe, tenantFixtures.testTenantId());

        String jwt = world.jwt(staffName);
        List<String> photoIds = uploadPhotos(jwt, "report", photoCount);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceTaskId", sourceTaskId.toString());
        body.put("category", Terms.repairCategory(categoryChinese));
        if (description != null) {
            body.put("description", description);
        }
        body.put("photoIds", photoIds);
        body.put("urgent", urgent);
        body.put("interruptSourceTask", interrupt);

        ScenarioWorld.ApiResponse resp = idempotencyKey != null
                ? apiClient.post("/repair-tickets", BddApiClient.Auth.staff(jwt), body, idempotencyKey)
                : apiClient.post("/repair-tickets", BddApiClient.Auth.staff(jwt), body);

        if (resp.status() == 201 && resp.json() != null) {
            String ticketNo = resp.json().path("ticketNo").asText(null);
            String taskId = resp.json().path("taskId").asText(null);
            if (ticketNo != null && taskId != null) {
                world.rememberRepairTicket(ticketNo, UUID.fromString(taskId).toString());
            }
        }
    }

    /**
     * 「有照片沒上傳成功時不建立工務單」這條規則，靠的是送到 createRepairTicket／
     * appendRepairTicketPhotos／tasksComplete 的 photoIds 數量天然就比預期少（上傳失敗的 id
     * 不放進清單），不是在這裡先做業務驗證搶著擋下來——驗證與拒絕的語意屬於產品，不屬於測試準備。
     */
    private List<String> uploadPhotos(String jwt, String purpose, int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> photoBody = Map.of("purpose", purpose, "takenAt", OffsetDateTime.now(clock).toString());
            ScenarioWorld.ApiResponse createResp =
                    apiClient.post("/repair-photos", BddApiClient.Auth.staff(jwt), photoBody);
            String photoId = createResp.json() != null ? createResp.json().path("id").asText(null) : null;
            if (photoId != null) {
                apiClient.exchange(
                        HttpMethod.PUT,
                        "/repair-photos/" + photoId + "/content",
                        BddApiClient.Auth.staff(jwt),
                        "fixture-jpeg-bytes",
                        null);
                ids.add(photoId);
            }
        }
        return ids;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String requireCell(Map<String, String> row, String column) {
        String v = row.get(column);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return v;
    }
}
