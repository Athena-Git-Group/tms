package com.athena.tms.bdd.support;

import com.athena.tms.bdd.ScenarioWorld;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeskApi {

    private static final int VACANT_ROOM_STAY_DAYS_AGO = 30;

    private final BddApiClient apiClient;
    private final ScenarioWorld world;
    private final DeskContext deskContext;
    private final DbProbe dbProbe;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;
    private final ReservationFixtures reservationFixtures;
    private final MutableClock clock;

    public DeskApi(
            BddApiClient apiClient,
            ScenarioWorld world,
            DeskContext deskContext,
            DbProbe dbProbe,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures,
            ReservationFixtures reservationFixtures,
            MutableClock clock) {
        this.apiClient = apiClient;
        this.world = world;
        this.deskContext = deskContext;
        this.dbProbe = dbProbe;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
        this.reservationFixtures = reservationFixtures;
        this.clock = clock;
    }

    public ScenarioWorld.ApiResponse getBoard(String staffName) {
        ScenarioWorld.ApiResponse response = apiClient.get("/desk/board", staff(staffName));
        deskContext.rememberBoardResponse(response);
        deskContext.rememberBoardReader(staffName);
        return response;
    }

    public ScenarioWorld.ApiResponse readTask(String staffName, UUID taskId) {
        ScenarioWorld.ApiResponse response = apiClient.get("/desk/tasks/" + taskId, staff(staffName));
        deskContext.rememberDetailResponse(response);
        return response;
    }

    public ScenarioWorld.ApiResponse listQuickReplies(String staffName) {
        return apiClient.get("/desk/quick-replies", staff(staffName));
    }

    public ScenarioWorld.ApiResponse assign(String staffName, UUID taskId, String dept, String via) {
        return assign(staffName, taskId, dept, via, UUID.randomUUID().toString());
    }

    public ScenarioWorld.ApiResponse assign(
            String staffName, UUID taskId, String dept, String via, String idempotencyKey) {
        deskContext.rememberAssignIdempotencyKey(idempotencyKey);
        ScenarioWorld.ApiResponse response =
                apiClient.post(assignPath(taskId), staff(staffName), assignBody(dept, via), idempotencyKey);
        deskContext.rememberAssignResponse(response);
        return response;
    }

    public ScenarioWorld.ApiResponse assignWithoutKey(String staffName, UUID taskId, String dept, String via) {
        return apiClient.post(assignPath(taskId), staff(staffName), assignBody(dept, via), null);
    }

    public ScenarioWorld.ApiResponse undoAssign(String staffName, UUID taskId) {
        return undoAssign(staffName, taskId, UUID.randomUUID().toString());
    }

    public ScenarioWorld.ApiResponse undoAssign(String staffName, UUID taskId, String idempotencyKey) {
        return apiClient.post(undoPath(taskId), staff(staffName), null, idempotencyKey);
    }

    public ScenarioWorld.ApiResponse undoAssignWithoutKey(String staffName, UUID taskId) {
        return apiClient.post(undoPath(taskId), staff(staffName), null, null);
    }

    public ScenarioWorld.ApiResponse answer(String staffName, UUID taskId, String answer) {
        return answer(staffName, taskId, answer, UUID.randomUUID().toString());
    }

    public ScenarioWorld.ApiResponse answer(String staffName, UUID taskId, String answer, String idempotencyKey) {
        return apiClient.post(answerPath(taskId), staff(staffName), answerBody(answer), idempotencyKey);
    }

    public ScenarioWorld.ApiResponse answerWithoutKey(String staffName, UUID taskId, String answer) {
        return apiClient.post(answerPath(taskId), staff(staffName), answerBody(answer), null);
    }

    public ScenarioWorld.ApiResponse createGuestRequest(String roomNo, String text, String lang) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        if (lang != null) {
            body.put("lang", lang);
        }
        return apiClient.post("/guest-requests", guest(roomNo), body);
    }

    public ScenarioWorld.ApiResponse cancelGuestRequest(String roomNo, UUID requestId) {
        return apiClient.post("/guest-requests/" + requestId + "/cancel", guest(roomNo), null);
    }

    public ScenarioWorld.ApiResponse listGuestRequests(String roomNo) {
        return apiClient.get("/guest-requests", guest(roomNo));
    }

    public String guestSessionToken(String roomNo) {
        long tenantId = tenantFixtures.testTenantId();
        UUID roomId = roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID reservationId = currentReservationId(tenantId, roomId, now);
        if (reservationId == null) {
            LocalDate longAgo = now.toLocalDate().minusDays(VACANT_ROOM_STAY_DAYS_AGO);
            reservationId = reservationFixtures.upsertReservation(roomNo, List.of(), longAgo, null);
        }

        Map<String, Object> existing = dbProbe
                .queryOne(
                        """
                        SELECT session_token
                          FROM guest_sessions
                         WHERE tenant_id = ? AND room_id = ? AND reservation_id = ?
                           AND revoked_at IS NULL AND expires_at > ?
                         ORDER BY created_at DESC
                         LIMIT 1
                        """,
                        tenantId, roomId, reservationId, now)
                .orElse(null);
        if (existing != null) {
            return String.valueOf(existing.get("session_token"));
        }

        String sessionToken = "fx-desk-guest-session-" + UUID.randomUUID();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("sessionToken", sessionToken);
        body.put("roomId", roomId.toString());
        body.put("reservationId", reservationId.toString());
        body.put("expiresAt", now.plusDays(1).toString());
        ScenarioWorld.ApiResponse scan = apiClient.post("/guest-sessions/scan", BddApiClient.Auth.none(), body);
        if (scan.status() < 200 || scan.status() > 299) {
            throw new IllegalStateException(
                    "建立「" + roomNo + "」的住客 session 失敗，狀態碼=" + scan.status() + "，body=" + scan.body());
        }
        return sessionToken;
    }

    private UUID currentReservationId(long tenantId, UUID roomId, OffsetDateTime now) {
        return dbProbe
                .queryOne(
                        """
                        SELECT id
                          FROM reservations_cache
                         WHERE tenant_id = ? AND room_id = ? AND superseded_at IS NULL
                           AND checkin_at <= ? AND checkout_at > ?
                        """,
                        tenantId, roomId, now, now)
                .map(row -> (UUID) row.get("id"))
                .orElse(null);
    }

    private BddApiClient.Auth staff(String staffName) {
        return BddApiClient.Auth.staff(world.jwt(staffName));
    }

    private BddApiClient.Auth guest(String roomNo) {
        return BddApiClient.Auth.guest(guestSessionToken(roomNo));
    }

    private static String assignPath(UUID taskId) {
        return "/desk/tasks/" + taskId + "/assign";
    }

    private static String undoPath(UUID taskId) {
        return "/desk/tasks/" + taskId + "/assign/undo";
    }

    private static String answerPath(UUID taskId) {
        return "/desk/tasks/" + taskId + "/answer";
    }

    private static Map<String, Object> assignBody(String dept, String via) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dept", dept);
        body.put("via", via);
        return body;
    }

    private static Map<String, Object> answerBody(String answer) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("answer", answer);
        return body;
    }
}
