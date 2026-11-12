package com.athena.tms.bdd.steps.repair;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.ReservationFixtures;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.TaskFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import com.athena.tms.bdd.support.VerifyFixtures;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.UuidV7;
import com.athena.tms.dto.Actor;
import com.athena.tms.service.TaskEngine;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
    private final VerifyFixtures verifyFixtures;
    private final ObjectMapper objectMapper;

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
            TaskEngine taskEngine,
            VerifyFixtures verifyFixtures,
            ObjectMapper objectMapper) {
        this.world = world;
        this.apiClient = apiClient;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
        this.taskFixtures = taskFixtures;
        this.reservationFixtures = reservationFixtures;
        this.clock = clock;
        this.jdbcClient = jdbcClient;
        this.taskEngine = taskEngine;
        this.verifyFixtures = verifyFixtures;
        this.objectMapper = objectMapper;
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
            // 根 dsl.md：報修人選填——來源為定期保養時留空，其餘必填（下面依來源檢查）。
            String reporterName = blankToNull(row.get("報修人"));
            String description = blankToNull(row.get("說明"));
            String assigneeName = blankToNull(row.get("處理人"));
            int photoCount = parseIntOrDefault(row.get("照片張數"), 1);
            boolean urgent = "是".equals(blankToNull(row.get("急件")));
            String linkedRoom = blankToNull(row.get("連結任務"));
            String sourceChinese = row.get("來源") == null || row.get("來源").isBlank() ? "房務報修" : row.get("來源");
            String verifierName = blankToNull(row.get("驗收人"));
            String resolutionChinese = blankToNull(row.get("處理結果"));
            String resolutionNote = blankToNull(row.get("處理說明"));
            String completionPhotoCell = blankToNull(row.get("完修照片張數"));
            String doneAtChinese = blankToNull(row.get("完修時間"));
            String failedReason = blankToNull(row.get("不通過原因"));
            String failedByName = blankToNull(row.get("不通過判定人"));
            String failedAtChinese = blankToNull(row.get("不通過時間"));

            String state = repairTaskState(stateChinese);
            // 已完修與已結案的單才有完修結果；其他狀態（含退回重修中的單）完修欄位一律為空。
            boolean completed = "done".equals(state) || "closed".equals(state);
            String category = Terms.repairCategory(categoryChinese);
            boolean scheduled = "定期保養".equals(sourceChinese);
            // 根 dsl.md：來源決定 tasks.source（房務報修＝staff、櫃檯＝desk、定期保養＝system）。
            // dsl.md 另提到 repair_tickets.origin（reported／scheduled），目前資料模型沒有這個欄位，
            // 待 /data-plan 補；這裡只落 tasks.source=system，不假造欄位。
            String taskSource = scheduled ? "system" : Terms.repairTicketSource(sourceChinese);

            // 房號為「公共區域」時掛在公共區域佔位房（與產品 RepairService.ensurePublicAreaRoom
            // 同一個房號、floor=0、room_type=public_area），不用一般客房的 ensureRoom。
            boolean publicArea = "公共區域".equals(roomNo);
            UUID roomId = publicArea
                    ? ensurePublicAreaRoom(tenantId)
                    : roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));

            UUID assigneeId = assigneeName != null ? UUID.fromString(world.staffId(assigneeName)) : null;
            UUID verifierId = verifierName != null ? UUID.fromString(world.staffId(verifierName)) : null;
            if (failedReason == null && (failedByName != null || failedAtChinese != null)) {
                throw new IllegalArgumentException("「不通過判定人」「不通過時間」只用於有「不通過原因」的單：" + row);
            }
            if (failedReason != null && failedByName == null) {
                throw new IllegalArgumentException("有「不通過原因」時「不通過判定人」必填：" + row);
            }
            UUID failedById = failedByName != null ? UUID.fromString(world.staffId(failedByName)) : null;
            // 報修人：來源為定期保養時可留空，其餘必填。repair_tickets.reporter_id 目前是 NOT NULL，
            // 定期保養沒有報修人時先以處理人（沒有處理人就任一在職員工）頂住，待 /data-plan 放寬。
            UUID reporterId;
            if (reporterName != null) {
                reporterId = UUID.fromString(world.staffId(reporterName));
            } else if (scheduled) {
                reporterId = assigneeId != null ? assigneeId : anyActiveStaffId(tenantId);
            } else {
                throw new IllegalArgumentException("「報修人」只有來源為定期保養時可留空：" + row);
            }

            if (!completed && (resolutionChinese != null || resolutionNote != null || doneAtChinese != null)) {
                throw new IllegalArgumentException(
                        "「處理結果」「處理說明」「完修時間」只用於已完修與已結案的單，收到狀態「" + stateChinese + "」：" + row);
            }
            String resolutionType = resolutionChinese != null
                    ? Terms.resolutionType(resolutionChinese)
                    : (completed ? "fixed" : null);
            boolean noActionNeeded = "no_action_needed".equals(resolutionType);
            if ("done".equals(state) && noActionNeeded) {
                throw new IllegalArgumentException("已完修的單不會是「不需處理」（不需處理在完成當下就結案）：" + row);
            }
            if ("closed".equals(state) && !noActionNeeded && verifierId == null) {
                throw new IllegalArgumentException("已結案且處理結果不是不需處理的單，「驗收人」必填：" + row);
            }
            if (noActionNeeded && verifierId != null) {
                throw new IllegalArgumentException("以不需處理結案的單沒有驗收人：" + row);
            }
            if (!"closed".equals(state) && verifierId != null) {
                throw new IllegalArgumentException("「驗收人」只用於已結案的單，收到狀態「" + stateChinese + "」：" + row);
            }
            int completionPhotoCount = completionPhotoCell != null
                    ? Integer.parseInt(completionPhotoCell.trim())
                    : (completed && !noActionNeeded ? 1 : 0);

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
            if ("pool".equals(state) && failedReason != null) {
                // 根 dsl.md：驗收不通過後退回工務待認領的單，進池來源是 verification_return。
                builder.poolSource("verification_return");
            }
            OffsetDateTime doneAt = completed
                    ? (doneAtChinese != null ? relativeTime(doneAtChinese, "完修時間") : OffsetDateTime.now(clock))
                    : null;
            if (doneAtChinese != null) {
                // 根 dsl.md：已完修與已結案的單寫入 tasks.done_at；TaskFixtures 會把 completed 事件錨在這個時刻。
                builder.doneAt(doneAt.toInstant());
            }
            // 已完修／已結案的單重演時會經過一次開工（doing）。處理人手上若已有背景鋪好的維修中單，
            // 會撞 uq_tasks_one_doing_per_staff；重演期間先把那幾張暫放成 accepted，建完原樣放回，
            // 背景的維修中單不受影響。
            List<UUID> parked = completed && assigneeId != null
                    ? parkDoingTasks(tenantId, assigneeId)
                    : List.of();
            UUID taskId;
            try {
                taskId = builder.create();
            } finally {
                restoreDoingTasks(tenantId, parked);
            }

            jdbcClient
                    .sql("UPDATE tasks SET urgent = ? WHERE tenant_id = ? AND id = ?")
                    .params(urgent, tenantId, taskId)
                    .update();

            UUID sourceTaskId = linkedRoom != null ? UUID.fromString(world.taskId(linkedRoom)) : null;
            OffsetDateTime verifiedAt = verifierId != null ? OffsetDateTime.now(clock) : null;
            OffsetDateTime failedAt = null;
            if (failedReason != null) {
                // 根 dsl.md 預設：已完修的單是完修時間前 30 分鐘（重修後再完修），其他狀態是目前 Clock 前 30 分鐘。
                failedAt = failedAtChinese != null
                        ? relativeTime(failedAtChinese, "不通過時間")
                        : ("done".equals(state) ? doneAt : OffsetDateTime.now(clock)).minusMinutes(30);
            }

            jdbcClient
                    .sql(
                            """
                            INSERT INTO repair_tickets
                                (task_id, tenant_id, ticket_no, room_id, category, description,
                                 reporter_id, source_task_id, created_at, is_urgent,
                                 verified_by, verified_at, resolution_type, resolution_note,
                                 last_verify_failed_reason, last_verify_failed_by, last_verify_failed_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """)
                    .params(
                            taskId, tenantId, ticketNo, roomId, category, description, reporterId, sourceTaskId,
                            OffsetDateTime.now(clock), urgent, verifierId, verifiedAt,
                            resolutionType, resolutionNote, failedReason, failedById, failedAt)
                    .update();

            if (failedReason != null) {
                // 根 dsl.md：補一筆 repair_verification_failed，destination 待認領是 pool、其他是 assignee。
                String destination = "pool".equals(state) ? "pool" : "assignee";
                appendEvent(tenantId, taskId, "repair_verification_failed", failedById,
                        "{\"reason\":\"" + escapeJson(failedReason) + "\",\"destination\":\"" + destination + "\"}",
                        failedAt);
            }
            if (verifierId != null) {
                // 根 dsl.md：已結案（非不需處理）的單補一筆 repair_verified；不需處理時 completed 事件即結案事件。
                appendEvent(tenantId, taskId, "repair_verified", verifierId, null, verifiedAt);
            }

            if ("cannot_fix".equals(resolutionType)) {
                // 根 dsl.md：處理結果為無法修復時，該房 rooms.hk_state 一併寫成 out_of_order。
                jdbcClient
                        .sql("UPDATE rooms SET hk_state = 'out_of_order' WHERE tenant_id = ? AND id = ?")
                        .params(tenantId, roomId)
                        .update();
            }

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

            UUID completionUploader = assigneeId != null ? assigneeId : reporterId;
            for (int i = 0; i < completionPhotoCount; i++) {
                UUID photoId = UuidV7.generate();
                jdbcClient
                        .sql(
                                """
                                INSERT INTO repair_photos
                                    (id, tenant_id, ticket_task_id, purpose, status, storage_ref,
                                     taken_at, uploaded_by, created_at, uploaded_at)
                                VALUES (?, ?, ?, 'completion', 'uploaded', ?, ?, ?, ?, ?)
                                """)
                        .params(
                                photoId, tenantId, taskId, "fixture:" + photoId, OffsetDateTime.now(clock),
                                completionUploader, OffsetDateTime.now(clock), OffsetDateTime.now(clock))
                        .update();
            }

            world.rememberRepairTicket(ticketNo, taskId.toString());
        }
    }

    /** 公共區域佔位房：與產品 {@code RepairService.ensurePublicAreaRoom} 同一個房號與落地欄位。 */
    private UUID ensurePublicAreaRoom(long tenantId) {
        String roomNo = "公共區域";
        return jdbcClient
                .sql("SELECT id FROM rooms WHERE tenant_id = ? AND room_no = ?")
                .params(tenantId, roomNo)
                .query(UUID.class)
                .optional()
                .orElseGet(() -> {
                    UUID id = UuidV7.generate();
                    jdbcClient
                            .sql(
                                    """
                                    INSERT INTO rooms (id, tenant_id, room_no, floor, room_type, hk_state, occupancy, updated_at)
                                    VALUES (?, ?, ?, 0, 'public_area', 'cleaned', 'vacant', ?)
                                    """)
                            .params(id, tenantId, roomNo, OffsetDateTime.now(clock))
                            .update();
                    return id;
                });
    }

    private UUID anyActiveStaffId(long tenantId) {
        return jdbcClient
                .sql("SELECT id FROM staff_accounts WHERE tenant_id = ? AND active = true ORDER BY created_at LIMIT 1")
                .param(tenantId)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException("定期保養的工務單沒有報修人，租戶內也找不到任何在職員工可頂替"));
    }

    /**
     * 在該任務事件歷程尾端補一筆事件：取 {@code tasks.event_seq + 1} 當 seq（與 TaskEngine 取號同一個計數器），
     * 事件時間用呼叫端給的時刻。驗收通過與驗收不通過在測試資料裡沒有對應的狀態轉移可重演，
     * 直接落地權威事件，讓查事件歷程的 Then 看到與正式流程相同的事件類型與 payload。
     */
    private void appendEvent(
            long tenantId, UUID taskId, String type, UUID actorId, String payloadJson, OffsetDateTime at) {
        int seq = jdbcClient
                .sql("UPDATE tasks SET event_seq = event_seq + 1 WHERE tenant_id = ? AND id = ? RETURNING event_seq")
                .params(tenantId, taskId)
                .query(Integer.class)
                .single();
        jdbcClient
                .sql(
                        """
                        INSERT INTO task_events (tenant_id, task_id, seq, type, actor_role, actor_id, payload, created_at)
                        VALUES (?, ?, ?, ?, 'manager', ?, ?::jsonb, ?)
                        """)
                .params(tenantId, taskId, seq, type, actorId, payloadJson, at)
                .update();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** `今天 HH:mm`／`昨天 HH:mm` → 目前 Clock 時區的時刻。 */
    private OffsetDateTime relativeTime(String chinese, String column) {
        String[] parts = chinese.trim().split("\\s+");
        if (parts.length != 2 || !("今天".equals(parts[0]) || "昨天".equals(parts[0]))) {
            throw new IllegalArgumentException("「" + column + "」格式須為 `今天 HH:mm` 或 `昨天 HH:mm`，收到「" + chinese + "」");
        }
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        LocalDate date = "昨天".equals(parts[0]) ? today.minusDays(1) : today;
        return ZonedDateTime.of(date, LocalTime.parse(parts[1]), clock.getZone()).toOffsetDateTime();
    }


    @假設("{string} 今天是 VIP 房，住客 {string} 入住")
    public void roomIsVipCheckinToday(String roomNo, String checkinTime) {
        roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        reservationFixtures.upsertReservation(roomNo, List.of("vip"), today, LocalTime.parse(checkinTime), null);
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
     * dsl.md：以該主管身分呼叫一次 {@code verifyRepairTicket}（result=passed），當作 Arrange——該單已是
     * closed，有驗收人與驗收時間、repair_verified 事件一筆。用在「另一位主管先驗收」的撞單案例；
     * 這句不斷言，「先到的那一次有沒有記下」由 Then 驗。
     */
    @假設("{string} 已先驗收通過 {string}")
    public void managerAlreadyVerifiedTicket(String managerName, String ticketNo) {
        ScenarioWorld.ApiResponse response = verifyFixtures.send(world, managerName, ticketNo, "passed", null);
        requireArranged(response, managerName + " 先驗收通過 " + ticketNo);
    }

    /**
     * dsl.md：以該主管身分呼叫一次 {@code verifyRepairTicket}（result=failed、reason＝原因），當作 Arrange。
     * 用在「另一位主管先送出不通過」的撞單案例；結果與推播由 Then 驗。
     */
    @假設("{string} 已先以原因 {string} 判定 {string} 驗收不通過")
    public void managerAlreadyFailedVerification(String managerName, String reason, String ticketNo) {
        ScenarioWorld.ApiResponse response = verifyFixtures.send(world, managerName, ticketNo, "failed", reason);
        requireArranged(response, managerName + " 先判定 " + ticketNo + " 驗收不通過");
    }

    /** 示範資料（scripts/seed-demo.sh）的班別設定：早班 08:00–16:00、午班 16:00–23:00。 */
    private static final String DEFAULT_SHIFT_TIMES =
            "{\"morning\": {\"start\": \"08:00\", \"end\": \"16:00\"}, "
                    + "\"afternoon\": {\"start\": \"16:00\", \"end\": \"23:00\"}}";

    /**
     * dsl.md：把測試 Clock 撥到今天、落在飯店所有班別時段（tenant_config.shift_times）之外的時刻。
     * 工務人員沒有排房計畫班次，換人判定對工務只看請假與飯店班別時段，所以「班已經結束」用這個時刻表達。
     * 先從現在往後找；往後找到的已經跨到明天時，改從今天零點開始找（今天開班前也算班別之外）。
     * 不寫任何資料，只確認這位工務人員此刻沒有請假，免得情境其實是在測請假分支。
     */
    @假設("{string} 今天的班已經結束")
    public void engineerShiftAlreadyEnded(String engineerName) {
        long tenantId = tenantFixtures.testTenantId();
        // 測試租戶預設沒有班別設定；沒有時產品把任何時刻都當成在班內，這句就表達不出「班已結束」。
        // 先補一份與示範資料相同的班別設定（已有就沿用），再找班別之外的時刻。
        jdbcClient
                .sql("""
                        INSERT INTO tenant_config (tenant_id, key, value, updated_at)
                        VALUES (?, 'shift_times', ?::jsonb, now())
                        ON CONFLICT (tenant_id, key) DO NOTHING
                        """)
                .params(tenantId, DEFAULT_SHIFT_TIMES)
                .update();
        String shiftTimesJson = jdbcClient
                .sql("SELECT value::text FROM tenant_config WHERE tenant_id = ? AND key = 'shift_times'")
                .param(tenantId)
                .query(String.class)
                .single();
        JsonNode shiftTimes = objectMapper.readTree(shiftTimesJson);

        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        Instant target = clock.nextInstantOutsideShifts(shiftTimes);
        if (!target.atZone(clock.getZone()).toLocalDate().equals(today)) {
            Instant now = clock.instant();
            clock.setInstant(today.atStartOfDay(clock.getZone()).toInstant());
            target = clock.nextInstantOutsideShifts(shiftTimes);
            if (!target.atZone(clock.getZone()).toLocalDate().equals(today)) {
                clock.setInstant(now);
                throw new IllegalStateException("今天找不到落在所有班別時段之外的時刻：shift_times=" + shiftTimesJson);
            }
        }
        clock.setInstant(target);

        UUID engineerId = UUID.fromString(world.staffId(engineerName));
        long onLeave = jdbcClient
                .sql("SELECT count(*) FROM leaves WHERE tenant_id = ? AND staff_id = ? AND from_at <= ? AND to_at > ?")
                .params(tenantId, engineerId, OffsetDateTime.now(clock), OffsetDateTime.now(clock))
                .query(Long.class)
                .single();
        if (onLeave > 0) {
            throw new IllegalStateException("「" + engineerName + "今天的班已經結束」要求他不在請假中，但此刻有請假紀錄");
        }
    }

    /**
     * dsl.md：以工務身分呼叫 {@code tasksComplete}，body {@code {resolutionType: no_action_needed}}、不帶照片，
     * 當作 Arrange——工務單完成當下直接結案，沒有驗收人。
     */
    @假設("{string} 已由 {string} 以不需處理結案")
    public void ticketClosedAsNoActionNeeded(String ticketNo, String engineerName) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        ScenarioWorld.ApiResponse response = apiClient.post(
                "/tasks/" + ticketTaskId + "/complete",
                BddApiClient.Auth.staff(world.jwt(engineerName)),
                Map.of("resolutionType", "no_action_needed"));
        requireArranged(response, ticketNo + " 由 " + engineerName + " 以不需處理結案");
    }

    /** Arrange 句的呼叫沒成功就是前提沒鋪起來，在這裡就把原因說清楚，不留到 Then 才失焦。 */
    private static void requireArranged(ScenarioWorld.ApiResponse response, String what) {
        if (response.status() < 200 || response.status() >= 300) {
            throw new IllegalStateException("前提「" + what + "」沒鋪起來：HTTP " + response.status()
                    + "，body=" + response.body());
        }
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

    /** 把該員工手上 doing 的任務暫放成 accepted，回傳被暫放的 id；由 {@link #restoreDoingTasks} 放回。 */
    private List<UUID> parkDoingTasks(long tenantId, UUID assigneeId) {
        List<UUID> ids = jdbcClient
                .sql("SELECT id FROM tasks WHERE tenant_id = ? AND assignee_id = ? AND state = 'doing'")
                .params(tenantId, assigneeId)
                .query(UUID.class)
                .list();
        for (UUID id : ids) {
            jdbcClient.sql("UPDATE tasks SET state = 'accepted' WHERE tenant_id = ? AND id = ?")
                    .params(tenantId, id)
                    .update();
        }
        return ids;
    }

    private void restoreDoingTasks(long tenantId, List<UUID> ids) {
        for (UUID id : ids) {
            jdbcClient.sql("UPDATE tasks SET state = 'doing' WHERE tenant_id = ? AND id = ?")
                    .params(tenantId, id)
                    .update();
        }
    }

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
