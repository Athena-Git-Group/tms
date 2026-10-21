package com.athena.tms.integration.stepdefs.then;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.TestData;

import java.util.Map;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.cucumber.java.en.Then;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static com.athena.tms.integration.stepdefs.utils.TestData.STAFF_HK1;
import static com.athena.tms.integration.stepdefs.utils.TestData.TENANT_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IdentityThenSteps {

    private final ScenarioContext ctx;
    private final ApiClient api;
    private final DbSupport db;
    private final ObjectMapper objectMapper;

    public IdentityThenSteps(ScenarioContext ctx, ApiClient api, DbSupport db,
                             ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.api = api;
        this.db = db;
        this.objectMapper = objectMapper;
    }

    @Then("^取得含 tenant_id/staff_id/role/dept 的憑證，有效期為 12 小時$")
    public void credentialCarriesSpecClaims() throws Exception {
        HttpResult login = ctx.lastResponse();
        assertEquals(200, login.status(), "第二次登入該成功: " + login.body());
        JsonNode body = objectMapper.readTree(login.body());
        assertEquals("Bearer", body.get("tokenType").asText());
        assertNotNull(body.get("expiresAt"));
        JsonNode staff = body.get("staff");
        assertEquals("小美", staff.get("name").asText());
        assertEquals("housekeeping", staff.get("role").asText());
        assertEquals(1, staff.get("tenantId").asInt());
        assertTrue(staff.get("passwordHash") == null || staff.get("passwordHash").isNull(),
                "密碼雜湊絕不得出現在回應");

        // claims 名照 spec 字面 snake_case——它是跨前後端的 wire format
        JWTClaimsSet claims = SignedJWT.parse(body.get("token").asText()).getJWTClaimsSet();
        assertEquals(1L, claims.getLongClaim("tenant_id"));
        assertEquals(STAFF_HK1.toString(), claims.getStringClaim("staff_id"));
        assertEquals("housekeeping", claims.getStringClaim("role"));
        assertEquals("housekeeping", claims.getStringClaim("dept"));
        long ttlSeconds =
                (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000;
        assertEquals(12 * 3600, ttlSeconds, "TTL 必須恰為 12 小時（gherkin 登入成功場景）");
    }

    @Then("^staff_devices 對同一裝置去重、僅登記一列，且登入不綁裝置$")
    public void deviceDedupAndLoginNotBoundToDevice() {
        String fingerprint = ctx.get("fingerprint");
        Integer sameDevice = db.admin().queryForObject(
                "SELECT count(*) FROM staff_devices WHERE tenant_id = 1 AND staff_id = ?"
                        + " AND device_fingerprint = ?",
                Integer.class, STAFF_HK1, fingerprint);
        assertEquals(1, sameDevice.intValue(), "同一台手機登入兩次，staff_devices 僅登記一列");
        String pushToken = db.admin().queryForObject(
                "SELECT push_token FROM staff_devices WHERE tenant_id = 1 AND staff_id = ?"
                        + " AND device_fingerprint = ?",
                String.class, STAFF_HK1, fingerprint);
        assertEquals("apns-token-2", pushToken, "重複登入要更新 push_token（換 token 是 APNS/FCM 常態）");

        Integer before = db.admin().queryForObject(
                "SELECT count(*) FROM staff_devices WHERE tenant_id = 1 AND staff_id = ?",
                Integer.class, STAFF_HK1);
        HttpResult noDevice = api.loginRaw(TENANT_1, ctx.staffLogin("小美"), TestData.PASSWORD, null, null);
        assertEquals(200, noDevice.status(), "登入不綁裝置：沒帶 fingerprint 照樣登入");
        Integer after = db.admin().queryForObject(
                "SELECT count(*) FROM staff_devices WHERE tenant_id = 1 AND staff_id = ?",
                Integer.class, STAFF_HK1);
        assertEquals(before, after, "沒帶 fingerprint 不得多登記裝置");
    }

    @Then("^憑證 role 為 \"manager\" 且不含 dept，回應也不含密碼雜湊$")
    public void managerCredentialHasNoDept() throws Exception {
        HttpResult login = ctx.lastResponse();
        assertEquals(200, login.status(), "登入該成功: " + login.body());
        JsonNode staff = objectMapper.readTree(login.body()).get("staff");
        assertEquals("王主管", staff.get("name").asText());
        assertEquals("manager", staff.get("role").asText());
        assertTrue(staff.get("dept") == null || staff.get("dept").isNull(), "manager 無部門，不得編造");
        assertTrue(staff.get("passwordHash") == null || staff.get("passwordHash").isNull(),
                "密碼雜湊絕不得出現在回應");
        JWTClaimsSet claims = SignedJWT.parse(
                objectMapper.readTree(login.body()).get("token").asText()).getJWTClaimsSet();
        assertEquals("manager", claims.getStringClaim("role"));
        assertNull(claims.getStringClaim("dept"));
    }

    @Then("^不簽發任何憑證$")
    public void noCredentialIssued() {
        JsonNode body = objectMapper.readTree(ctx.lastResponse().body());
        assertEquals("unauthorized", body.get("code").asText());
        assertTrue(body.get("token") == null || body.get("token").isNull(),
                "401 回應不得夾帶任何憑證");
    }

    @Then("^三者請求應該都失敗，回應皆為 401 且錯誤碼一致$")
    public void threeFailuresIndistinguishable() {
        List<HttpResult> attempts = ctx.get("loginAttempts");
        String firstCode = null;
        for (HttpResult attempt : attempts) {
            assertEquals(401, attempt.status(), "回應 body: " + attempt.body());
            String code = objectMapper.readTree(attempt.body()).get("code").asText();
            if (firstCode == null) {
                firstCode = code;
            } else {
                assertEquals(firstCode, code, "錯誤碼必須不可區分");
            }
        }
    }

    @Then("^缺密碼欄位的登入請求應該失敗，回應為 (\\d+)$")
    public void missingPasswordFailsWith(int expected) {
        HttpResult missing = api.loginRaw(TENANT_1, ctx.staffLogin("小美"), null, null, null);
        assertEquals(expected, missing.status(),
                "缺必填欄位是客戶端 bug，與憑證錯誤的 401 要分流: " + missing.body());
    }

    @Then("^兩者請求應該都失敗，回應皆為 401$")
    public void bothDeactivatedCallsAre401() {
        HttpResult relogin = ctx.get("reloginResult");
        HttpResult oldToken = ctx.get("oldTokenResult");
        assertEquals(401, relogin.status(), "「阿強」active=false → 登入 401: " + relogin.body());
        assertEquals(401, oldToken.status(),
                "停用必須立即生效，不得等 JWT TTL 到期——這就是每請求打 DB 驗 active 的理由");
    }

    @Then("^三者請求應該都失敗，回應皆為 401$")
    public void allThreeCredentialAttemptsAre401() {
        List<HttpResult> attempts = ctx.get("credentialAttempts");
        for (HttpResult attempt : attempts) {
            assertEquals(401, attempt.status(), "回應 body: " + attempt.body());
        }
    }

    @Then("^上下文帶房 (\\d+) 與該訂單的編號$")
    public void contextCarriesRoomAndReservation(String roomNo) {
        HttpResult last = ctx.lastResponse();
        assertEquals(200, last.status(), "回應 body: " + last.body());
        JsonNode body = objectMapper.readTree(last.body());
        // 以 session 那一列為準：上下文帶的房與訂單必須就是 session 綁的那組
        Map<String, Object> session = db.admin().queryForMap(
                "SELECT room_id, reservation_id FROM guest_sessions WHERE session_token = ?",
                ctx.<String>get("sessionToken"));
        String roomInDb = db.admin().queryForObject(
                "SELECT room_no FROM rooms WHERE id = ?", String.class, session.get("room_id"));
        assertEquals(roomNo, roomInDb, "session 綁的就是這間房");
        assertEquals(session.get("room_id").toString(), body.get("roomId").asText(),
                "驗過 session 要把房綁進請求上下文");
        assertEquals(session.get("reservation_id").toString(), body.get("reservationId").asText(),
                "訂單也要綁——住客查詢一律以訂單過濾，不按房號");
    }

    @Then("^以該 session 呼叫住客寫入動作回 (\\d+)$")
    public void sessionGuestWriteStatus(int expected) {
        HttpResult result = api.post("/guest/probe", ctx.get("sessionToken"), null);
        assertEquals(expected, result.status(), "回應 body: " + result.body());
    }

    @Then("^以該 session 呼叫員工 API 的請求應該失敗，回應為 (\\d+)$")
    public void sessionStaffCallStatus(int expected) {
        HttpResult result = api.get("/staff/probe", ctx.get("sessionToken"));
        assertEquals(expected, result.status(),
                "住客憑證有效也進不了員工門——401/403 分野在「身分假」對「權限不夠」");
    }
}
