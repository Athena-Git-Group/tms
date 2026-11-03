package com.athena.tms.bdd.steps.repair;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.ReservationFixtures;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.TaskFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.dto.Actor;
import com.athena.tms.service.TaskEngine;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * 工務單模組的 Given（dsl.md Given 句型區，{@code specs/truth/features/backend/工務單/dsl.md}）。
 * 只建立/確認權威狀態（或執行 dsl.md 點名的查詢式呼叫），不含「必查」斷言——那是
 * {@link RepairThenSteps} 的事；G4 是例外：dsl.md 本身就把「確認 duplicate.ticketNo」
 * 寫在這句 Given 的語意裡，所以直接在這裡比對。
 *
 * <p>repair_tickets／repair_photos／repair_ticket_counters 沒有對應的 TestFixtures 入口
 * （{@code bdd/support} 範圍不包含工務單專屬資料），依任務指示直接注入 {@link JdbcClient} 落地。
 */
public class RepairGivenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;
    private final TaskFixtures taskFixtures;
    private final ReservationFixtures reservationFixtures;
    private final MutableClock clock;
    private final JdbcClient jdbcClient;
    private final TaskEngine taskEngine;

    /** G5／G6 共用：最近一次「準備照片」批次裡，上傳失敗的那張照片 id（給 G6 重試）。 */
    private String lastFailedPhotoId;

    public RepairGivenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures,
            TaskFixtures taskFixtures,
            ReservationFixtures reservationFixtures,
            MutableClock clock,
            JdbcClient jdbcClient,
            TaskEngine taskEngine) {
        this.world = world;
        this.apiClient = apiClient;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
        this.taskFixtures = taskFixtures;
        this.reservationFixtures = reservationFixtures;
        this.clock = clock;
        this.jdbcClient = jdbcClient;
        this.taskEngine = taskEngine;
    }

    @假設("本飯店下一張工務單號為 {string}")
    public void nextRepairTicketNoIs(String ticketNo) {
        int n = parseTicketNumber(ticketNo);
        long tenantId = tenantFixtures.testTenantId();
        jdbcClient
                .sql(
                        """
                        INSERT INTO repair_ticket_counters (tenant_id, last_no) VALUES (?, ?)
                        ON CONFLICT (tenant_id) DO UPDATE SET last_no = excluded.last_no
                        """)
                .params(tenantId, n - 1)
                .update();
    }

    @假設("已有以下工務單：")
    public void existingRepairTickets(DataTable table) {
        long tenantId = tenantFixtures.testTenantId();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String ticketNo = requireCell(row, "單號");
            String roomNo = requireCell(row, "房號");
            String categoryChinese = requireCell(row, "類別");
            String stateChinese = requireCell(row, "狀態");
            String reporterName = requireCell(row, "報修人");
            String description = blankToNull(row.get("說明"));
            String assigneeName = blankToNull(row.get("處理人"));
            int photoCount = parseIntOrDefault(row.get("照片張數"), 1);
            boolean urgent = "是".equals(blankToNull(row.get("急件")));
            String linkedRoom = blankToNull(row.get("連結任務"));

            roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
            UUID roomId = roomFixtures.roomId(roomNo);
            UUID reporterId = UUID.fromString(world.staffId(reporterName));
            UUID assigneeId = assigneeName != null ? UUID.fromString(world.staffId(assigneeName)) : null;

            String state = repairTaskState(stateChinese);
            String category = Terms.repairCategory(categoryChinese);

            // uq_tasks_one_doing_per_staff：一位員工同時最多一筆 doing。Background 可能已讓
            // 同一位處理人有處理中的單（例如 W-0412），這裡要再建他的處理中新單時，先把他
            // 手上既有的 doing 單完修結案（「已完修」＝done，工務單狀態對照表裡合法的非處理
            // 中狀態，事件歷程也由 TaskEngine 完整留下）；直接硬插會撞唯一索引噴
            // DuplicateKeyException，那不是場景要驗的事。
            if ("doing".equals(state) && assigneeId != null) {
                completeExistingDoingTasks(tenantId, assigneeId);
            }

            TaskFixtures.Builder builder = taskFixtures
                    .builder()
                    .roomNo(roomNo)
                    .title(categoryChinese + " 工務單")
                    .type("repair")
                    .dept("engineering")
                    .source("staff")
                    .state(state);
            if (assigneeId != null) {
                builder.assignee(assigneeId);
            }
            if ("cancelled".equals(state)) {
                builder.cancelReason(Terms.repairTicketWithdrawnReason());
            }
            UUID taskId = builder.create();

            jdbcClient
                    .sql("UPDATE tasks SET urgent = ? WHERE tenant_id = ? AND id = ?")
                    .params(urgent, tenantId, taskId)
                    .update();

            UUID sourceTaskId = linkedRoom != null ? UUID.fromString(world.taskId(linkedRoom)) : null;

            jdbcClient
                    .sql(
                            """
                            INSERT INTO repair_tickets
                                (task_id, tenant_id, ticket_no, room_id, category, description,
                                 reporter_id, source_task_id, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """)
                    .params(
                            taskId, tenantId, ticketNo, roomId, category, description, reporterId, sourceTaskId,
                            OffsetDateTime.now(clock))
                    .update();

            if (sourceTaskId != null) {
                jdbcClient
                        .sql("UPDATE tasks SET linked_repair_task_id = ? WHERE tenant_id = ? AND id = ?")
                        .params(taskId, tenantId, sourceTaskId)
                        .update();
            }

            for (int i = 0; i < photoCount; i++) {
                UUID photoId = UuidV7.generate();
                jdbcClient
                        .sql(
                                """
                                INSERT INTO repair_photos
                                    (id, tenant_id, ticket_task_id, purpose, status, storage_ref,
                                     taken_at, uploaded_by, created_at, uploaded_at)
                                VALUES (?, ?, ?, 'report', 'uploaded', ?, ?, ?, ?, ?)
                                """)
                        .params(
                                photoId, tenantId, taskId, "fixture:" + photoId, OffsetDateTime.now(clock),
                                reporterId, OffsetDateTime.now(clock), OffsetDateTime.now(clock))
                        .update();
            }

            world.rememberRepairTicket(ticketNo, taskId.toString());
        }
    }

    @假設("{string} 今天是 VIP 房，住客 {string} 入住")
    public void roomIsVipCheckinToday(String roomNo, String checkinTime) {
        roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        // ReservationFixtures 的入住時間固定 15:00（見其 Javadoc），這裡的 {時刻} 參數目前無法
        // 透過既有入口客製化；dsl.md 要求的「reservations_cache 已有該訂單」本身仍有落地。
        reservationFixtures.upsertReservation(roomNo, List.of("vip"), today, null);
    }

    @假設("{string} 已對 {string} 選擇工務單類別 {string} 並看到 {string} 的重複提示")
    public void staffSawDuplicateHint(String staffName, String roomNo, String categoryChinese, String ticketNo) {
        UUID sourceTaskId = UUID.fromString(world.taskId(roomNo));
        Map<String, Object> body =
                Map.of("sourceTaskId", sourceTaskId.toString(), "category", Terms.repairCategory(categoryChinese));
        ScenarioWorld.ApiResponse resp =
                apiClient.post("/repair-tickets/precheck", BddApiClient.Auth.staff(world.jwt(staffName)), body);
        JsonNode json = resp.json();
        String actualTicketNo = json == null ? null : json.path("duplicate").path("ticketNo").asText(null);
        if (!ticketNo.equals(actualTicketNo)) {
            throw new AssertionError(
                    "預期 precheck 回報重複單號 " + ticketNo + "，實際 status=" + resp.status()
                            + " duplicate.ticketNo=" + actualTicketNo + "，body=" + resp.body());
        }
    }

    @假設("{string} 為 {string} 的工務單準備了 {int} 張照片，其中 {int} 張上傳失敗")
    public void preparePhotosWithFailures(String staffName, String roomNo, int total, int failCount) {
        UUID roomId = roomFixtures.roomId(roomNo);
        String jwt = world.jwt(staffName);
        lastFailedPhotoId = null;

        for (int i = 0; i < total; i++) {
            Map<String, Object> photoBody =
                    Map.of("purpose", "report", "roomId", roomId.toString(), "takenAt", nowIso());
            ScenarioWorld.ApiResponse createResp =
                    apiClient.post("/repair-photos", BddApiClient.Auth.staff(jwt), photoBody);
            String photoId = createResp.json() != null ? createResp.json().path("id").asText(null) : null;
            if (photoId == null) {
                throw new AssertionError(
                        "createRepairPhoto 預期回傳 id，實際 status=" + createResp.status() + "，body=" + createResp.body());
            }

            boolean shouldFail = i >= total - failCount;
            if (!shouldFail) {
                apiClient.exchange(
                        HttpMethod.PUT,
                        "/repair-photos/" + photoId + "/content",
                        BddApiClient.Auth.staff(jwt),
                        "fixture-jpeg-bytes",
                        null);
            } else {
                lastFailedPhotoId = photoId;
            }
            world.photoIds().add(photoId);
        }
    }

    @假設("{string} 已重新上傳失敗的那張照片並成功")
    public void retryFailedPhoto(String staffName) {
        if (lastFailedPhotoId == null) {
            throw new IllegalStateException("沒有記錄上傳失敗的照片，請先執行「...的工務單準備了 N 張照片，其中 M 張上傳失敗」");
        }
        apiClient.exchange(
                HttpMethod.PUT,
                "/repair-photos/" + lastFailedPhotoId + "/content",
                BddApiClient.Auth.staff(world.jwt(staffName)),
                "fixture-jpeg-bytes-retry",
                null);
        lastFailedPhotoId = null;
    }

    @假設("{string} 已由 {string} 完修")
    public void ticketAlreadyCompletedByEngineer(String ticketNo, String staffName) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        String jwt = world.jwt(staffName);

        Map<String, Object> photoBody = Map.of("purpose", "completion", "takenAt", nowIso());
        ScenarioWorld.ApiResponse createResp = apiClient.post("/repair-photos", BddApiClient.Auth.staff(jwt), photoBody);
        String photoId = createResp.json() != null ? createResp.json().path("id").asText(null) : null;
        if (photoId == null) {
            throw new AssertionError(
                    "createRepairPhoto(completion) 預期回傳 id，實際 status=" + createResp.status()
                            + "，body=" + createResp.body());
        }
        apiClient.exchange(
                HttpMethod.PUT,
                "/repair-photos/" + photoId + "/content",
                BddApiClient.Auth.staff(jwt),
                "fixture-jpeg-bytes",
                null);

        Map<String, Object> completeBody = Map.of("photoIds", List.of(photoId));
        apiClient.post("/tasks/" + ticketTaskId + "/complete", BddApiClient.Auth.staff(jwt), completeBody);
    }

    /** 見 {@link #existingRepairTickets}：把該員工名下所有 doing 任務透過 TaskEngine 完修結案。 */
    private void completeExistingDoingTasks(long tenantId, UUID assigneeId) {
        List<Map<String, Object>> doingRows = jdbcClient
                .sql("SELECT id, dept FROM tasks WHERE tenant_id = ? AND assignee_id = ? AND state = 'doing'")
                .params(tenantId, assigneeId)
                .query()
                .listOfRows();
        if (doingRows.isEmpty()) {
            return;
        }
        TenantContext.set(tenantId);
        try {
            for (Map<String, Object> row : doingRows) {
                taskEngine.complete(
                        tenantId, (UUID) row.get("id"), Actor.staff(String.valueOf(row.get("dept")), assigneeId));
            }
        } finally {
            TenantContext.clear();
        }
    }

    private String nowIso() {
        return OffsetDateTime.now(clock).toString();
    }

    private static String repairTaskState(String chinese) {
        if ("待接".equals(chinese)) {
            return "pool";
        }
        return Terms.repairTicketState(chinese);
    }

    private static int parseTicketNumber(String ticketNo) {
        if (!ticketNo.matches("W-\\d{4}")) {
            throw new IllegalArgumentException("工務單號格式必須是 W- 加 4 位數字，收到: " + ticketNo);
        }
        return Integer.parseInt(ticketNo.substring(2));
    }

    private static int parseIntOrDefault(String s, int def) {
        return (s == null || s.isBlank()) ? def : Integer.parseInt(s.trim());
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
