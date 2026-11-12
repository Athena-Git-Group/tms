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
