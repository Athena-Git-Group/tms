package com.athena.tms.bdd.support;

import com.athena.tms.bdd.ScenarioWorld;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 櫃檯看板與派單的句型共用的呼叫入口：六支 {@code /api/v1/desk/**}，以及以住客身分送單、收回、看進度。
 *
 * <p>路徑、標頭與請求內容集中在這裡，契約改動時只改一處，不必逐句追。
 * 每個方法只負責把請求送出去並回傳回應，不判斷成功或失敗——同一支呼叫有的句型要它成功、
 * 有的要它被拒絕，判斷留給句型自己做。
 *
 * <p>員工一律用姓名指定，憑證取自 {@link ScenarioWorld#jwt(String)}；
 * 部門與派單方式傳契約的值（{@code housekeeping}、{@code button}…），中文用語的換算是句型的事。
 */
public class DeskApi {

    /**
     * 空房的住客 session 掛的那筆訂房，住期放在這麼多天以前。
     * 要夠早，才不會和案例裡「現在有住客」的訂房住期重疊（同房住期重疊會被資料庫拒絕）。
     */
    private static final int VACANT_ROOM_STAY_DAYS_AGO = 30;

    private final BddApiClient apiClient;
    private final ScenarioWorld world;
    private final DeskContext deskContext;
    private final DbProbe dbProbe;
    private final TenantFixtures tenantFixtures;
    private final RoomFixtures roomFixtures;
    private final ReservationFixtures reservationFixtures;
    private final MutableClock clock;
    private final StaffFixtures staffFixtures;

    public DeskApi(
            BddApiClient apiClient,
            ScenarioWorld world,
            DeskContext deskContext,
            DbProbe dbProbe,
            TenantFixtures tenantFixtures,
            RoomFixtures roomFixtures,
            ReservationFixtures reservationFixtures,
            MutableClock clock,
            StaffFixtures staffFixtures) {
        this.apiClient = apiClient;
        this.world = world;
        this.deskContext = deskContext;
        this.dbProbe = dbProbe;
        this.tenantFixtures = tenantFixtures;
        this.roomFixtures = roomFixtures;
        this.reservationFixtures = reservationFixtures;
        this.clock = clock;
        this.staffFixtures = staffFixtures;
    }

    /** getDeskBoard。看板的 Then 常隔著別的呼叫才檢查，所以回應另外記進 {@link DeskContext}。 */
    public ScenarioWorld.ApiResponse getBoard(String staffName) {
        ScenarioWorld.ApiResponse response = apiClient.get("/desk/board", staff(staffName));
        deskContext.rememberBoardResponse(response);
        deskContext.rememberBoardReader(staffName);
        return response;
    }

    /** readDeskTask。詳情的 Then 常隔著別的呼叫才檢查，所以回應另外記進 {@link DeskContext}。 */
    public ScenarioWorld.ApiResponse readTask(String staffName, UUID taskId) {
        ScenarioWorld.ApiResponse response = apiClient.get("/desk/tasks/" + taskId, staff(staffName));
        deskContext.rememberDetailResponse(response);
        return response;
    }

    /** listDeskQuickReplies。 */
    public ScenarioWorld.ApiResponse listQuickReplies(String staffName) {
        return apiClient.get("/desk/quick-replies", staff(staffName));
    }

    /** deskAssignTask，每次一把新的防重複鍵，因為畫面每按一次或拖一次就是一把新的鍵。 */
    public ScenarioWorld.ApiResponse assign(String staffName, UUID taskId, String dept, String via) {
        return assign(staffName, taskId, dept, via, UUID.randomUUID().toString());
    }

    /**
     * deskAssignTask，由呼叫的句型指定防重複鍵，用來模擬網路逾時後拿同一把鍵重送。
     * 重送要拿得回前一次的鍵，所以每次帶鍵派單都把那把鍵記進 {@link DeskContext}。
     */
    public ScenarioWorld.ApiResponse assign(
            String staffName, UUID taskId, String dept, String via, String idempotencyKey) {
        deskContext.rememberAssignIdempotencyKey(idempotencyKey);
        ScenarioWorld.ApiResponse response =
                apiClient.post(assignPath(taskId), staff(staffName), assignBody(dept, via), idempotencyKey);
        deskContext.rememberAssignResponse(response);
        return response;
    }

    /** deskAssignTask，不帶 {@code Idempotency-Key} 標頭，用來驗後端會拒絕沒帶鍵的寫入。 */
    public ScenarioWorld.ApiResponse assignWithoutKey(String staffName, UUID taskId, String dept, String via) {
        return apiClient.post(assignPath(taskId), staff(staffName), assignBody(dept, via), null);
    }

    /** deskUndoAssign，每次一把新的防重複鍵；契約規定這支沒有請求內容。 */
    public ScenarioWorld.ApiResponse undoAssign(String staffName, UUID taskId) {
        return undoAssign(staffName, taskId, UUID.randomUUID().toString());
    }

    /** deskUndoAssign，由呼叫的句型指定防重複鍵，用來模擬拿同一把鍵重送。 */
    public ScenarioWorld.ApiResponse undoAssign(String staffName, UUID taskId, String idempotencyKey) {
        return apiClient.post(undoPath(taskId), staff(staffName), null, idempotencyKey);
    }

    /** deskUndoAssign，不帶 {@code Idempotency-Key} 標頭，用來驗後端會拒絕沒帶鍵的寫入。 */
    public ScenarioWorld.ApiResponse undoAssignWithoutKey(String staffName, UUID taskId) {
        return apiClient.post(undoPath(taskId), staff(staffName), null, null);
    }

    /**
     * deskAnswerTask，每次一把新的防重複鍵。
     * 回覆照傳入的字面送出，因為去掉頭尾空白與長度檢查是後端的規則，測試要能把前後空白原樣送到。
     */
    public ScenarioWorld.ApiResponse answer(String staffName, UUID taskId, String answer) {
        return answer(staffName, taskId, answer, UUID.randomUUID().toString());
    }

    /** deskAnswerTask，由呼叫的句型指定防重複鍵，用來模擬拿同一把鍵重送。 */
    public ScenarioWorld.ApiResponse answer(String staffName, UUID taskId, String answer, String idempotencyKey) {
        return apiClient.post(answerPath(taskId), staff(staffName), answerBody(answer), idempotencyKey);
    }

    /** deskAnswerTask，不帶 {@code Idempotency-Key} 標頭，用來驗後端會拒絕沒帶鍵的寫入。 */
    public ScenarioWorld.ApiResponse answerWithoutKey(String staffName, UUID taskId, String answer) {
        return apiClient.post(answerPath(taskId), staff(staffName), answerBody(answer), null);
    }

    /**
     * createGuestRequest：待派單的來源是住客打字送出的一段話，所以只送文字、不送選單品項。
     * {@code lang} 傳 null 時請求不帶這一欄，讓後端走沒有指定語言的預設。
     */
    public ScenarioWorld.ApiResponse createGuestRequest(String roomNo, String text, String lang) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        if (lang != null) {
            body.put("lang", lang);
        }
        return apiClient.post("/guest-requests", guest(roomNo), body);
    }

    /** cancelGuestRequest：住客自己收回需求，是櫃檯派單與復原會撞上的情況之一。 */
    public ScenarioWorld.ApiResponse cancelGuestRequest(String roomNo, UUID requestId) {
        return apiClient.post("/guest-requests/" + requestId + "/cancel", guest(roomNo), null);
    }

    /** listGuestRequests：住客進度頁的資料來源，用來確認住客讀得到櫃檯的回覆。 */
    public ScenarioWorld.ApiResponse listGuestRequests(String roomNo) {
        return apiClient.get("/guest-requests", guest(roomNo));
    }

    /**
     * 住客端點靠 session 認人，句型只知道房號，這裡負責把房號換成該房住客的 session token。
     *
     * <p>住客 session 一定要掛在一筆訂房上。這間房現在有住客時，照正式流程由櫃檯開 session，到期時間由後端定成那筆訂房的退房時刻。
     * 沒有住客時掛一筆住期早已結束的訂房，這樣房間仍是空房，
     * 之後案例再替同一間房建立入住中的訂房也不會因住期重疊被資料庫拒絕。
     * 後端不替已經退房的訂房開 session，所以這種 session 直接寫進資料庫，到期時間放在一天後，案例進行中不會失效。
     *
     * <p>住客只看得到自己那筆訂房名下的單，所以同一間房、同一筆訂房沿用同一個 session。
     */
    public String guestSessionToken(String roomNo) {
        long tenantId = tenantFixtures.testTenantId();
        UUID roomId = roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID stayingReservationId = currentReservationId(tenantId, roomId, now);
        UUID reservationId = stayingReservationId;
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

        if (stayingReservationId == null) {
            return reservationFixtures.insertGuestSession(roomId, reservationId, now.plusDays(1));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roomId", roomId.toString());
        body.put("reservationId", reservationId.toString());
        ScenarioWorld.ApiResponse scan = apiClient.post(
                "/guest-sessions/scan", BddApiClient.Auth.staff(staffFixtures.fixtureFrontdeskJwt()), body);
        if (scan.status() < 200 || scan.status() > 299) {
            // 這裡失敗代表測試資料沒備好，不是受測行為；當場停下，免得後面的住客呼叫全部變成難懂的 401。
            throw new IllegalStateException(
                    "建立「" + roomNo + "」的住客 session 失敗，狀態碼=" + scan.status() + "，body=" + scan.body());
        }
        return scan.json().get("sessionToken").asText();
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

    // 兩欄都照傳入的值送，null 也送：不合法的部門、沒給派單方式這些情況要能原樣送到後端。
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
