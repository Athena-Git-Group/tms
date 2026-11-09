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
 * <p>例外：{@link #existingRepairTickets} 的權威列不在本模組 dsl.md，已搬到介面根
 * {@code specs/truth/features/backend/dsl.md}（跨模組共用句，語意不變，只是收斂到單一權威
 * 位置），glue code 留在這個類別純粹是沿用既有檔案落點，不代表權威歸屬。
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

    /**
     * 權威列：介面根 {@code specs/truth/features/backend/dsl.md} Given
     * {@code 已有以下工務單：}（非本模組 dsl.md）。
     */
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
            String sourceChinese = row.get("來源") == null || row.get("來源").isBlank() ? "房務報修" : row.get("來源");
            String rejectReason = blankToNull(row.get("退回原因"));
            String verifierName = blankToNull(row.get("驗收人"));

            roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
            UUID roomId = roomFixtures.roomId(roomNo);
            UUID reporterId = UUID.fromString(world.staffId(reporterName));
            UUID assigneeId = assigneeName != null ? UUID.fromString(world.staffId(assigneeName)) : null;
            UUID verifierId = verifierName != null ? UUID.fromString(world.staffId(verifierName)) : null;

            String state = repairTaskState(stateChinese);
            String category = Terms.repairCategory(categoryChinese);
            String taskSource = Terms.repairTicketSource(sourceChinese);

            // uq_tasks_one_doing_per_staff：一位員工同時最多一筆 doing。Background 可能已讓
            // 同一位處理人有維修中的單（例如 W-0412），這裡要再建他的維修中新單時，先把他
            // 手上既有的 doing 單完修結案（「已完修」＝done，工務單狀態對照表裡合法的非維修
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
                    .source(taskSource)
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
            OffsetDateTime verifiedAt = verifierId != null ? OffsetDateTime.now(clock) : null;

            jdbcClient
                    .sql(
                            """
                            INSERT INTO repair_tickets
                                (task_id, tenant_id, ticket_no, room_id, category, description,
                                 reporter_id, source_task_id, created_at, is_urgent,
                                 reject_reason, verified_by, verified_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """)
                    .params(
                            taskId, tenantId, ticketNo, roomId, category, description, reporterId, sourceTaskId,
                            OffsetDateTime.now(clock), urgent, rejectReason, verifierId, verifiedAt)
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

    /**
     * 工務視角今日進度的統計背景資料。dsl.md：`怎麼做` 透過後端測試資料建立符合統計數字的工務單
     * （doing 指派給該工務；今天 done 的；is_urgent 的）。
     *
     * <p>「急件數」是「手上張數」（doing）裡 isUrgent 的子集，不是「今天已完修」的子集——
     * 依據 {@code specs/truth/contracts/components/schemas/員工工作台.yaml}
     * {@code engineeringSummary.urgentCount}：「手上單（accepted／doing）中 isUrgent=true 的筆數」。
     * 今天已完修的那批單不影響這個統計，urgent 一律設 false，避免誤導後續斷言。
     */
    @假設("{string} 手上有 {int} 張維修中的工務單，今天已完修 {int} 張，其中 {int} 張是急件")
    public void engineerWorkloadToday(String staffName, int doingCount, int doneTodayCount, int urgentCount) {
        if (urgentCount > doingCount) {
            throw new IllegalArgumentException(
                    "急件數不能超過手上張數（維修中）：" + urgentCount + " > " + doingCount);
        }
        long tenantId = tenantFixtures.testTenantId();
        UUID assigneeId = UUID.fromString(world.staffId(staffName));

        // 建「今天已完修」要先建：它的狀態重演是 claim→start→complete，start() 那一步
        // 會短暫佔用這個人唯一的 doing 名額（uq_tasks_one_doing_per_staff），complete()
        // 後才釋放。先建完修單時這個人手上還沒有任何未結案的單，佔用後立刻釋放不會撞別的單；
        // 若反過來先建「手上張數」那批（其中一張要停在 doing 不結案），完修單重演到 start()
        // 就會跟那張還沒結案的 doing 撞唯一索引。
        for (int i = 0; i < doneTodayCount; i++) {
            createEngineeringTicketFor(tenantId, assigneeId, "done", false);
        }
        // 「手上張數」＝contracts engineeringSummary.openCount 的 accepted＋doing；
        // 同一人同時只能有一張 doing（uq_tasks_one_doing_per_staff），其餘手上單落 accepted。
        for (int i = 0; i < doingCount; i++) {
            createEngineeringTicketFor(tenantId, assigneeId, i == 0 ? "doing" : "accepted", i < urgentCount);
        }
    }

    /**
     * {@link #engineerWorkloadToday} 專用：建一張最小可用的工務單（房號、單號均為合成值，
     * 情境不依賴這兩者），落地 tasks 與 repair_tickets 兩張權威表。
     */
    private void createEngineeringTicketFor(long tenantId, UUID assigneeId, String state, boolean urgent) {
        int n = allocateNextTicketNo(tenantId);
        String ticketNo = String.format("W-%04d", n);
        String roomNo = "ENG-" + ticketNo;
        roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        UUID roomId = roomFixtures.roomId(roomNo);

        UUID taskId = taskFixtures
                .builder()
                .roomNo(roomNo)
                .title("其他 工務單")
                .type("repair")
                .dept("engineering")
                .source("staff")
                .state(state)
                .assignee(assigneeId)
                .create();

        jdbcClient
                .sql("UPDATE tasks SET urgent = ? WHERE tenant_id = ? AND id = ?")
                .params(urgent, tenantId, taskId)
                .update();

        jdbcClient
                .sql(
                        """
                        INSERT INTO repair_tickets
                            (task_id, tenant_id, ticket_no, room_id, category, reporter_id, created_at, is_urgent)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        taskId, tenantId, ticketNo, roomId, Terms.repairCategory("其他"), assigneeId,
                        OffsetDateTime.now(clock), urgent)
                .update();
    }

    /** {@link #createEngineeringTicketFor}：租戶內遞增工務單號，與 {@code createRepairTicket} 取號語意一致。 */
    private int allocateNextTicketNo(long tenantId) {
        return jdbcClient
                .sql(
                        """
                        INSERT INTO repair_ticket_counters (tenant_id, last_no) VALUES (?, 1)
                        ON CONFLICT (tenant_id) DO UPDATE SET last_no = repair_ticket_counters.last_no + 1
                        RETURNING last_no
                        """)
                .param(tenantId)
                .query(Integer.class)
                .single();
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
        if ("待認領".equals(chinese)) {
            return "pool";
        }
        if ("待開始".equals(chinese)) {
            // 狀態用語對照的通用列：待開始 = accepted。工務單寫「待開始」時等同「已接單」，
            // 差別只在措辭（有處理人但還沒動工），不是另一個狀態。
            return "accepted";
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
