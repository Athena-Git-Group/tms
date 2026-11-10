package com.athena.tms.bdd.steps.push;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.MutableClock;
import com.athena.tms.bdd.support.ReservationFixtures;
import com.athena.tms.bdd.support.RoomFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import io.cucumber.java.zh_tw.假設;
import io.cucumber.java.zh_tw.當;
import io.cucumber.java.zh_tw.那麼;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * 推播模組（dsl.md: specs/truth/features/backend/推播/dsl.md）住客觸發的句型：
 * 住客掃碼建需求、住客撤回需求、節流與被擋下。共用句型（收到推播／收到以下推播／
 * 沒有收到任何推播）在 steps/common，本類別不重複定義。
 *
 * <p>住客身分一律走 {@code X-Guest-Session} header（{@link BddApiClient.Auth#guest}）。
 * guest_sessionsRequest 的形狀是 guest_sessions 整列欄位，sessionToken 由呼叫端自己
 * 產生、連同 roomId／reservationId 一起寫進去註冊，不是先呼叫一個端點、再從回應裡
 * 讀系統配發的 token；所以本類別一律先準備好 sessionToken 再送出註冊請求。
 */
public class PushGuestSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final RoomFixtures roomFixtures;
    private final ReservationFixtures reservationFixtures;
    private final TenantFixtures tenantFixtures;
    private final MutableClock clock;

    public PushGuestSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            RoomFixtures roomFixtures,
            ReservationFixtures reservationFixtures,
            TenantFixtures tenantFixtures,
            MutableClock clock) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.roomFixtures = roomFixtures;
        this.reservationFixtures = reservationFixtures;
        this.tenantFixtures = tenantFixtures;
        this.clock = clock;
    }

    /**
     * dsl.md 怎麼做：以該房的住客 session 在目前 Clock 的 10 分鐘內送出該筆數的需求。
     * 權威狀態落地：這些需求已建單。每筆用不同的 itemId——節流只看這 10 分鐘內的
     * 筆數，品項內容本身不影響這個 Given 要落地的狀態。
     */
    @假設("住客 {int} 分鐘內已在 {string} 送出 {int} 筆需求")
    public void guestAlreadySentRequestsWithinMinutes(int minutesIgnored, String roomNo, int count) {
        String sessionToken = ensureGuestSession(roomNo);
        for (int i = 1; i <= count; i++) {
            createGuestRequest(sessionToken, "節流測試需求" + i, 1);
        }
    }

    @當("住客在 {string} 掃碼要 {string}")
    public void guestScansForItem(String roomNo, String itemWithQuantity) {
        String sessionToken = ensureGuestSession(roomNo);
        ParsedItem item = ParsedItem.parse(itemWithQuantity);
        ScenarioWorld.ApiResponse resp = createGuestRequest(sessionToken, item.name(), item.quantity());
        String taskId = textOrNull(resp.json(), "id");
        if (taskId != null) {
            world.rememberTask(itemWithQuantity, taskId);
        }
    }

    @當("住客撤回 {string} 的需求")
    public void guestWithdrawsRequest(String roomNo) {
        String sessionToken = ensureGuestSession(roomNo);
        UUID requestId = UUID.fromString(world.taskId(roomNo));
        apiClient.post(
                "/guest-requests/" + requestId + "/cancel",
                BddApiClient.Auth.guest(sessionToken),
                null);
    }

    /**
     * dsl.md 必查：呈現結果——回應為 4xx，錯誤代碼 rate_limited，訊息提示住客洽櫃檯。
     * 權威狀態（tasks、outbox 與這次請求前一樣）與「同次送出多項只回一次提示」由
     * 場景裡另外的「今天沒有…需求單」與「沒有收到任何推播」兩句驗證，不在本句重查。
     */
    @那麼("這次請求被擋下，提示住客洽櫃檯")
    public void requestBlockedHintFrontDesk() {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        if (resp.status() < 400 || resp.status() > 499) {
            throw new AssertionError("預期 4xx 回應，實際狀態碼=" + resp.status() + "，body=" + resp.body());
        }
        JsonNode json = resp.json();
        String code = textOrNull(json, "code");
        if (!"rate_limited".equals(code)) {
            throw new AssertionError("預期錯誤代碼 rate_limited，實際 code=" + code + "，body=" + resp.body());
        }
        String message = textOrNull(json, "message");
        if (message == null || !(message.contains("櫃檯") || message.contains("前台"))) {
            throw new AssertionError("預期錯誤訊息提示住客洽櫃檯，實際 message=" + message);
        }
    }

    @那麼("{string} 今天沒有 {string} 的需求單")
    public void roomHasNoRequestToday(String roomNo, String item) {
        long tenantId = tenantFixtures.testTenantId();
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        long count = dbProbe.count(
                """
                SELECT count(*) FROM tasks t
                  JOIN rooms r ON r.id = t.room_id AND r.tenant_id = t.tenant_id
                 WHERE t.tenant_id = ? AND r.room_no = ? AND t.title = ?
                   AND t.created_at::date = ?
                """,
                tenantId, roomNo, item, today);
        if (count > 0) {
            throw new AssertionError(
                    "預期「" + roomNo + "」今天沒有「" + item + "」的需求單，實際找到 " + count + " 筆");
        }
    }

    /**
     * 同一房的 guest_sessions.session_token 全域 UNIQUE，且 QR 輪替會把該房其他
     * 未撤銷 session 連坐撤銷（見 dbml 注記），所以重用既有、未撤銷未過期的 session
     * 而不是每次都重新註冊一個，避免同一場景裡後面的呼叫把前面建立的 session 撤銷掉。
     */
    private String ensureGuestSession(String roomNo) {
        roomFixtures.ensureRoom(roomNo, RoomFixtures.floorFromRoomNo(roomNo));
        long tenantId = tenantFixtures.testTenantId();

        Map<String, Object> existing = dbProbe
                .queryOne(
                        """
                        SELECT gs.session_token AS session_token
                          FROM guest_sessions gs
                          JOIN rooms r ON r.id = gs.room_id AND r.tenant_id = gs.tenant_id
                         WHERE gs.tenant_id = ? AND r.room_no = ?
                           AND gs.revoked_at IS NULL AND gs.expires_at > ?
                         ORDER BY gs.created_at DESC
                         LIMIT 1
                        """,
                        tenantId, roomNo, OffsetDateTime.now(clock))
                .orElse(null);
        if (existing != null) {
            return String.valueOf(existing.get("session_token"));
        }

        LocalDate checkinDate = clock.instant().atZone(clock.getZone()).toLocalDate();
        UUID reservationId = reservationFixtures.upsertReservation(roomNo, List.of(), checkinDate, null);
        UUID roomId = roomFixtures.roomId(roomNo);
        String sessionToken = "fx-guest-session-" + UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plusDays(1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("sessionToken", sessionToken);
        body.put("roomId", roomId.toString());
        body.put("reservationId", reservationId.toString());
        body.put("expiresAt", expiresAt.toString());
        apiClient.post("/guest-sessions/scan", BddApiClient.Auth.none(), body);
        return sessionToken;
    }

    private ScenarioWorld.ApiResponse createGuestRequest(String sessionToken, String itemId, int quantity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("itemId", itemId);
        body.put("quantity", quantity);
        return apiClient.post("/guest-requests", BddApiClient.Auth.guest(sessionToken), body);
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    /** 把 dsl.md「勾選單品項與數量，例如 衣架 ×4」這個格式拆成品項名稱與數量。 */
    private record ParsedItem(String name, int quantity) {
        private static final java.util.regex.Pattern PATTERN =
                java.util.regex.Pattern.compile("^(.+?)\\s*×\\s*(\\d+)$");

        static ParsedItem parse(String raw) {
            java.util.regex.Matcher m = PATTERN.matcher(raw.trim());
            if (m.matches()) {
                return new ParsedItem(m.group(1), Integer.parseInt(m.group(2)));
            }
            return new ParsedItem(raw.trim(), 1);
        }
    }
}
