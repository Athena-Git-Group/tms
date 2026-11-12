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

    @假設("已有以下工務單：")
    public void existingRepairTickets(DataTable table) {
        long tenantId = tenantFixtures.testTenantId();
        for (Map<String, String> row : table.asMaps(String.class, String.class)) {
            String ticketNo = requireCell(row, "單號");
            String roomNo = requireCell(row, "房號");
            String categoryChinese = requireCell(row, "類別");
            String stateChinese = requireCell(row, "狀態");
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
            boolean completed = "done".equals(state) || "closed".equals(state);
            String category = Terms.repairCategory(categoryChinese);
            boolean scheduled = "定期保養".equals(sourceChinese);
            String taskSource = scheduled ? "system" : Terms.repairTicketSource(sourceChinese);

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
                builder.poolSource("verification_return");
            }
            OffsetDateTime doneAt = completed
                    ? (doneAtChinese != null ? relativeTime(doneAtChinese, "完修時間") : OffsetDateTime.now(clock))
                    : null;
            if (doneAtChinese != null) {
                builder.doneAt(doneAt.toInstant());
            }
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
                String destination = "pool".equals(state) ? "pool" : "assignee";
                appendEvent(tenantId, taskId, "repair_verification_failed", failedById,
                        "{\"reason\":\"" + escapeJson(failedReason) + "\",\"destination\":\"" + destination + "\"}",
                        failedAt);
            }
            if (verifierId != null) {
                appendEvent(tenantId, taskId, "repair_verified", verifierId, null, verifiedAt);
            }

            if ("cannot_fix".equals(resolutionType)) {
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

    @假設("{string} 已先驗收通過 {string}")
    public void managerAlreadyVerifiedTicket(String managerName, String ticketNo) {
        ScenarioWorld.ApiResponse response = verifyFixtures.send(world, managerName, ticketNo, "passed", null);
        requireArranged(response, managerName + " 先驗收通過 " + ticketNo);
    }

    @假設("{string} 已先以原因 {string} 判定 {string} 驗收不通過")
    public void managerAlreadyFailedVerification(String managerName, String reason, String ticketNo) {
        ScenarioWorld.ApiResponse response = verifyFixtures.send(world, managerName, ticketNo, "failed", reason);
        requireArranged(response, managerName + " 先判定 " + ticketNo + " 驗收不通過");
    }

    private static final String DEFAULT_SHIFT_TIMES =
            "{\"morning\": {\"start\": \"08:00\", \"end\": \"16:00\"}, "
                    + "\"afternoon\": {\"start\": \"16:00\", \"end\": \"23:00\"}}";

    @假設("{string} 今天的班已經結束")
    public void engineerShiftAlreadyEnded(String engineerName) {
        long tenantId = tenantFixtures.testTenantId();
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

    @假設("{string} 已由 {string} 以不需處理結案")
    public void ticketClosedAsNoActionNeeded(String ticketNo, String engineerName) {
        UUID ticketTaskId = UUID.fromString(world.repairTicketTaskId(ticketNo));
        ScenarioWorld.ApiResponse response = apiClient.post(
                "/tasks/" + ticketTaskId + "/complete",
                BddApiClient.Auth.staff(world.jwt(engineerName)),
                Map.of("resolutionType", "no_action_needed"));
        requireArranged(response, ticketNo + " 由 " + engineerName + " 以不需處理結案");
    }

    private static void requireArranged(ScenarioWorld.ApiResponse response, String what) {
        if (response.status() < 200 || response.status() >= 300) {
            throw new IllegalStateException("前提「" + what + "」沒鋪起來：HTTP " + response.status()
                    + "，body=" + response.body());
        }
    }

    @假設("{string} 手上有 {int} 張維修中的工務單，今天已完修 {int} 張，其中 {int} 張是急件")
    public void engineerWorkloadToday(String staffName, int doingCount, int doneTodayCount, int urgentCount) {
        if (urgentCount > doingCount) {
            throw new IllegalArgumentException(
                    "急件數不能超過手上張數（維修中）：" + urgentCount + " > " + doingCount);
        }
        long tenantId = tenantFixtures.testTenantId();
        UUID assigneeId = UUID.fromString(world.staffId(staffName));

        for (int i = 0; i < doneTodayCount; i++) {
            createEngineeringTicketFor(tenantId, assigneeId, "done", false);
        }
        for (int i = 0; i < doingCount; i++) {
            createEngineeringTicketFor(tenantId, assigneeId, i == 0 ? "doing" : "accepted", i < urgentCount);
        }
    }

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
