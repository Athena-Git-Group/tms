package com.athena.tms.bdd.steps.repair;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.LastOperationGuard;
import com.athena.tms.bdd.support.RepairCountGuard;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.RoomFixtures;
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
 * {@link RepairCountGuard#capture}（全租戶筆數快照，見其 Javadoc）。
 */
public class RepairWhenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final LastOperationGuard lastOperationGuard;
    private final RepairCountGuard repairCountGuard;
    private final MutableClock clock;
    private final RoomFixtures roomFixtures;

    public RepairWhenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            LastOperationGuard lastOperationGuard,
            RepairCountGuard repairCountGuard,
            MutableClock clock,
            RoomFixtures roomFixtures) {
        this.world = world;
        this.apiClient = apiClient;
        this.lastOperationGuard = lastOperationGuard;
        this.repairCountGuard = repairCountGuard;
        this.clock = clock;
        this.roomFixtures = roomFixtures;
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
        lastOperationGuard.captureFor(sourceTaskId);
        repairCountGuard.capture();

        Map<String, Object> body = Map.of(
                "sourceTaskId", sourceTaskId.toString(),
                "category", Terms.repairCategory("水電"),
                "photoIds", world.photoIds(),
                "interruptSourceTask", true);
        ScenarioWorld.ApiResponse resp =
                apiClient.post("/repair-tickets", BddApiClient.Auth.staff(world.jwt(staffName)), body);
        if (resp.status() == 201 && resp.json() != null) {
            String ticketNo = resp.json().path("ticketNo").asText(null);
            String taskId = resp.json().path("taskId").asText(null);
            if (ticketNo != null && taskId != null) {
                world.rememberRepairTicket(ticketNo, UUID.fromString(taskId).toString());
            }
        }
    }

    /**
     * dsl.md：工務不經過任何房務任務直接開單（`sourceTaskId` 留空，改帶 `roomId`／`isPublicArea`）。
     * 「處理人」留空則進工務待認領（pool），有填時直接 accepted 給該人。
     */
    @當("{string} 為 {string} 開立以下工務單，不經過任何房務任務：")
    public void engineeringStaffOpensTicketDirectly(String engineerName, String roomNo, DataTable table) {
        Map<String, String> row = table.asMaps(String.class, String.class).get(0);
        String categoryChinese = requireCell(row, "類別");
        String description = blankToNull(row.get("說明"));
        int photoCount = Integer.parseInt(requireCell(row, "照片張數"));
        String assigneeName = blankToNull(row.get("處理人"));

        repairCountGuard.capture();

        String jwt = world.jwt(engineerName);
        List<String> photoIds = uploadPhotos(jwt, "report", photoCount);

        Map<String, Object> body = new LinkedHashMap<>();
        if ("公共區域".equals(roomNo)) {
            body.put("isPublicArea", true);
        } else {
            body.put("roomId", roomFixtures.roomId(roomNo).toString());
        }
        body.put("category", Terms.repairCategory(categoryChinese));
        if (description != null) {
            body.put("description", description);
        }
        body.put("photoIds", photoIds);
        if (assigneeName != null) {
            body.put("assigneeId", world.staffId(assigneeName));
        }

        ScenarioWorld.ApiResponse resp = apiClient.post("/repair-tickets", BddApiClient.Auth.staff(jwt), body);
        if (resp.status() == 201 && resp.json() != null) {
            String ticketNo = resp.json().path("ticketNo").asText(null);
            String taskId = resp.json().path("taskId").asText(null);
            if (ticketNo != null && taskId != null) {
                world.rememberRepairTicket(ticketNo, UUID.fromString(taskId).toString());
            }
        }
    }

    /**
     * dsl.md：櫃檯交辦工務部門走同一個 {@code createRepairTicket} 路徑，`sourceTaskId` 留空，
     * 改帶 `roomId`／`isPublicArea`；「處理人」留空則進工務待認領，有填時直接 accepted 給該人。
     * 請求不帶急件欄位——急件沿用既有 {@code UrgentJudgement} 系統判定。來源（`tasks.source`=desk）
     * 由後端依呼叫者（櫃檯員工）部門決定，這裡不需要另外傳欄位。
     */
    @當("{string} 為 {string} 交辦工務部門，開立以下工務單：")
    public void deskStaffForwardsTicketToEngineering(String deskStaffName, String roomNo, DataTable table) {
        Map<String, String> row = table.asMaps(String.class, String.class).get(0);
        String categoryChinese = requireCell(row, "類別");
        String description = blankToNull(row.get("說明"));
        int photoCount = Integer.parseInt(requireCell(row, "照片張數"));
        String assigneeName = blankToNull(row.get("處理人"));

        repairCountGuard.capture();

        String jwt = world.jwt(deskStaffName);
        List<String> photoIds = uploadPhotos(jwt, "report", photoCount);

        Map<String, Object> body = new LinkedHashMap<>();
        if ("公共區域".equals(roomNo)) {
            body.put("isPublicArea", true);
        } else {
            roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
            body.put("roomId", roomFixtures.roomId(roomNo).toString());
        }
        body.put("category", Terms.repairCategory(categoryChinese));
        if (description != null) {
            body.put("description", description);
        }
        body.put("photoIds", photoIds);
        if (assigneeName != null) {
            body.put("assigneeId", world.staffId(assigneeName));
        }

        ScenarioWorld.ApiResponse resp = apiClient.post("/repair-tickets", BddApiClient.Auth.staff(jwt), body);
        if (resp.status() == 201 && resp.json() != null) {
            String ticketNo = resp.json().path("ticketNo").asText(null);
            String taskId = resp.json().path("taskId").asText(null);
            if (ticketNo != null && taskId != null) {
                world.rememberRepairTicket(ticketNo, UUID.fromString(taskId).toString());
            }
        }
    }

    /**
     * dsl.md：以該工務身分查詢今日進度統計，沿用 {@code GET /me/today}（呼叫者是
     * dept=engineering 時回應另外帶 engineeringSummary）；不是獨立端點。
     */
    @當("{string} 查看工務視角今日進度")
    public void engineeringStaffViewsToday(String staffName) {
        apiClient.get("/me/today", BddApiClient.Auth.staff(world.jwt(staffName)));
    }

    @當("{string} 以原因 {string} 將 {string} 降級為一般件")
    public void downgradeTicketUrgency(String managerName, String reason, String ticketNo) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        lastOperationGuard.captureFor(ticketTaskId);

        Map<String, Object> body = Map.of("note", reason);
        apiClient.post(
                "/repair-tickets/" + ticketTaskId + "/downgrade-urgency",
                BddApiClient.Auth.staff(world.jwt(managerName)),
                body);
    }

    @當("{string} 核准 {string}")
    public void approveTicket(String managerName, String ticketNo) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        lastOperationGuard.captureFor(ticketTaskId);
        apiClient.post(
                "/repair-tickets/" + ticketTaskId + "/approve",
                BddApiClient.Auth.staff(world.jwt(managerName)),
                null);
    }

    @當("{string} 對 {string} 選擇工務單類別 {string}")
    public void selectCategoryForPrecheck(String staffName, String roomNo, String categoryChinese) {
        UUID sourceTaskId = UUID.fromString(world.taskId(roomNo));
        // precheck 本身不建單；T6／T8 要驗「不該發生：沒有建立工務單」，在這裡先留一份筆數快照。
        repairCountGuard.capture();

        Map<String, Object> body =
                Map.of("sourceTaskId", sourceTaskId.toString(), "category", Terms.repairCategory(categoryChinese));
        apiClient.post("/repair-tickets/precheck", BddApiClient.Auth.staff(world.jwt(staffName)), body);
    }

    @當("{string} 補 {int} 張照片到 {string}")
    public void appendPhotosToTicket(String staffName, int count, String ticketNo) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        lastOperationGuard.captureFor(ticketTaskId);

        String jwt = world.jwt(staffName);
        List<String> photoIds = uploadPhotos(jwt, "report", count);

        Map<String, Object> body = Map.of("photoIds", photoIds);
        apiClient.post("/repair-tickets/" + ticketTaskId + "/photos", BddApiClient.Auth.staff(jwt), body);
    }

    @當("{string} 以原因 {string} 退回 {string}")
    public void rejectTicket(String managerName, String reason, String ticketNo) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        lastOperationGuard.captureFor(ticketTaskId);

        Map<String, Object> body = Map.of("reason", reason);
        apiClient.post(
                "/repair-tickets/" + ticketTaskId + "/reject", BddApiClient.Auth.staff(world.jwt(managerName)), body);
    }

    @當("{string} 驗收通過 {string}")
    public void verifyTicket(String managerName, String ticketNo) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        lastOperationGuard.captureFor(ticketTaskId);
        apiClient.post(
                "/repair-tickets/" + ticketTaskId + "/verify", BddApiClient.Auth.staff(world.jwt(managerName)), null);
    }

    @當("{string} 把 {string} 放回工務待認領")
    public void releaseTicketToPool(String staffName, String ticketNo) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        lastOperationGuard.captureFor(ticketTaskId);
        apiClient.post(
                "/repair-tickets/" + ticketTaskId + "/release-to-pool",
                BddApiClient.Auth.staff(world.jwt(staffName)),
                null);
    }

    @當("{string} 只看 {string} 的工務待認領")
    public void staffViewsEngineeringPoolByTriage(String staffName, String triageChinese) {
        apiClient.get(
                "/pool?category=repair" + triageQueryString(triageChinese),
                BddApiClient.Auth.staff(world.jwt(staffName)));
    }

    /**
     * dsl.md「只看…的工務待認領」的分流對照 listDeptPool 的 urgent／source 篩選參數（見
     * specs/truth/contracts/groups/現場作業/員工工作台.yaml）：全部＝不加篩選；急件＝
     * urgent=true；房務報修＝source=staff_report；櫃檯＝source=desk_forward。「住客」分流磚是
     * dsl.md 點名的過時敘述，不建 truth，不收進這個對照。
     */
    private static String triageQueryString(String triageChinese) {
        return switch (triageChinese) {
            case "全部" -> "";
            case "急件" -> "&urgent=true";
            case "房務報修" -> "&source=staff_report";
            case "櫃檯" -> "&source=desk_forward";
            default -> throw new IllegalArgumentException("「只看…的工務待認領」不支援的分流: " + triageChinese);
        };
    }

    @當("{string} 撤回 {string}")
    public void withdrawTicket(String staffName, String ticketNo) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        lastOperationGuard.captureFor(ticketTaskId);
        apiClient.post(
                "/repair-tickets/" + ticketTaskId + "/withdraw", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }

    @當("{string} 補資料再送出 {string}")
    public void resubmitTicket(String staffName, String ticketNo) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        lastOperationGuard.captureFor(ticketTaskId);
        apiClient.post(
                "/repair-tickets/" + ticketTaskId + "/resubmit", BddApiClient.Auth.staff(world.jwt(staffName)), null);
    }

    @當("{string} 附 {int} 張完修照片、處理說明 {string} 完成 {string}")
    public void completeWithPhotosAndNote(String staffName, int photoCount, String note, String ticketNo) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        lastOperationGuard.captureFor(ticketTaskId);

        String jwt = world.jwt(staffName);
        List<String> photoIds = uploadPhotos(jwt, "completion", photoCount);

        Map<String, Object> body = Map.of("photoIds", photoIds, "resolutionNote", note);
        apiClient.post("/tasks/" + ticketTaskId + "/complete", BddApiClient.Auth.staff(jwt), body);
    }

    @當("{string} 附 {int} 張完修照片，回報 {string} 暫時處理，說明 {string}")
    public void completeWithTemporaryResolution(String staffName, int photoCount, String ticketNo, String note) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        lastOperationGuard.captureFor(ticketTaskId);
        repairCountGuard.capture();

        String jwt = world.jwt(staffName);
        List<String> photoIds = uploadPhotos(jwt, "completion", photoCount);

        Map<String, Object> body = Map.of(
                "photoIds", photoIds,
                "resolutionType", "temporary",
                "resolutionNote", note);
        apiClient.post("/tasks/" + ticketTaskId + "/complete", BddApiClient.Auth.staff(jwt), body);
    }

    @當("{string} 附 {int} 張完修照片，回報 {string} 無法修復，說明 {string}")
    public void completeWithCannotFixResolution(String staffName, int photoCount, String ticketNo, String note) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        lastOperationGuard.captureFor(ticketTaskId);

        String jwt = world.jwt(staffName);
        List<String> photoIds = uploadPhotos(jwt, "completion", photoCount);

        Map<String, Object> body = Map.of(
                "photoIds", photoIds,
                "resolutionType", "cannot_fix",
                "resolutionNote", note);
        apiClient.post("/tasks/" + ticketTaskId + "/complete", BddApiClient.Auth.staff(jwt), body);
    }

    @當("{string} 不附完修照片完成 {string}")
    public void completeWithoutPhotos(String staffName, String ticketNo) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        lastOperationGuard.captureFor(ticketTaskId);

        Map<String, Object> body = Map.of("photoIds", List.of());
        apiClient.post("/tasks/" + ticketTaskId + "/complete", BddApiClient.Auth.staff(world.jwt(staffName)), body);
    }

    private void createTicket(
            String staffName, String taskKey, DataTable table, boolean interrupt, String idempotencyKey) {
        Map<String, String> row = table.asMaps(String.class, String.class).get(0);
        String categoryChinese = requireCell(row, "類別");
        String description = blankToNull(row.get("說明"));
        int photoCount = Integer.parseInt(requireCell(row, "照片張數"));

        UUID sourceTaskId = UUID.fromString(world.taskId(taskKey));
        lastOperationGuard.captureFor(sourceTaskId);
        repairCountGuard.capture();

        String jwt = world.jwt(staffName);
        List<String> photoIds = uploadPhotos(jwt, "report", photoCount);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceTaskId", sourceTaskId.toString());
        body.put("category", Terms.repairCategory(categoryChinese));
        if (description != null) {
            body.put("description", description);
        }
        body.put("photoIds", photoIds);
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
