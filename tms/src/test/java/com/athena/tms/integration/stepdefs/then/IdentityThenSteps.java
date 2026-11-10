package com.athena.tms.integration.stepdefs.then;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.TestData;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.cucumber.java.en.Then;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class IdentityThenSteps {
    private final ScenarioContext ctx;
    private final ApiClient api;
    private final DbSupport db;
    private final ObjectMapper objectMapper;

    public IdentityThenSteps(ScenarioContext ctx, ApiClient api, DbSupport db, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.api = api;
        this.db = db;
        this.objectMapper = objectMapper;
    }

    @Then(value="^取得含 tenant_id/staff_id/role/dept 的憑證，有效期為 12 小時$")
    public void credentialCarriesSpecClaims() throws Exception {
        ScenarioContext.HttpResult login = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)login.status(), (String)("第二次登入該成功: " + login.body()));
        JsonNode body = this.objectMapper.readTree(login.body());
        Assertions.assertEquals((Object)"Bearer", (Object)body.get("tokenType").asText());
        Assertions.assertNotNull((Object)body.get("expiresAt"));
        JsonNode staff = body.get("staff");
        Assertions.assertEquals((Object)"小美", (Object)staff.get("name").asText());
        Assertions.assertEquals((Object)"housekeeping", (Object)staff.get("role").asText());
        Assertions.assertEquals((int)1, (int)staff.get("tenantId").asInt());
        Assertions.assertTrue((staff.get("passwordHash") == null || staff.get("passwordHash").isNull() ? 1 : 0) != 0, (String)"密碼雜湊絕不得出現在回應");
        JWTClaimsSet claims = SignedJWT.parse((String)body.get("token").asText()).getJWTClaimsSet();
        Assertions.assertEquals((long)1L, (Long)claims.getLongClaim("tenant_id"));
        Assertions.assertEquals((Object)TestData.STAFF_HK1.toString(), (Object)claims.getStringClaim("staff_id"));
        Assertions.assertEquals((Object)"housekeeping", (Object)claims.getStringClaim("role"));
        Assertions.assertEquals((Object)"housekeeping", (Object)claims.getStringClaim("dept"));
        long ttlSeconds = (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000L;
        Assertions.assertEquals((long)43200L, (long)ttlSeconds, (String)"TTL 必須恰為 12 小時（gherkin 登入成功場景）");
    }

    @Then(value="^staff_devices 對同一裝置去重、僅登記一列，且登入不綁裝置$")
    public void deviceDedupAndLoginNotBoundToDevice() {
        String fingerprint = (String)this.ctx.get("fingerprint");
        Integer sameDevice = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM staff_devices WHERE tenant_id = 1 AND staff_id = ? AND device_fingerprint = ?", Integer.class, new Object[]{TestData.STAFF_HK1, fingerprint});
        Assertions.assertEquals((int)1, (int)sameDevice, (String)"同一台手機登入兩次，staff_devices 僅登記一列");
        String pushToken = (String)this.db.admin().queryForObject("SELECT push_token FROM staff_devices WHERE tenant_id = 1 AND staff_id = ? AND device_fingerprint = ?", String.class, new Object[]{TestData.STAFF_HK1, fingerprint});
        Assertions.assertEquals((Object)"apns-token-2", (Object)pushToken, (String)"重複登入要更新 push_token（換 token 是 APNS/FCM 常態）");
        Integer before = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM staff_devices WHERE tenant_id = 1 AND staff_id = ?", Integer.class, new Object[]{TestData.STAFF_HK1});
        ScenarioContext.HttpResult noDevice = this.api.loginRaw(1L, this.ctx.staffLogin("小美"), "demo1234", null, null);
        Assertions.assertEquals((int)200, (int)noDevice.status(), (String)"登入不綁裝置：沒帶 fingerprint 照樣登入");
        Integer after = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM staff_devices WHERE tenant_id = 1 AND staff_id = ?", Integer.class, new Object[]{TestData.STAFF_HK1});
        Assertions.assertEquals((Integer)before, (Integer)after, (String)"沒帶 fingerprint 不得多登記裝置");
    }

    @Then(value="^憑證 role 為 \"manager\" 且不含 dept，回應也不含密碼雜湊$")
    public void managerCredentialHasNoDept() throws Exception {
        ScenarioContext.HttpResult login = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)login.status(), (String)("登入該成功: " + login.body()));
        JsonNode staff = this.objectMapper.readTree(login.body()).get("staff");
        Assertions.assertEquals((Object)"王主管", (Object)staff.get("name").asText());
        Assertions.assertEquals((Object)"manager", (Object)staff.get("role").asText());
        Assertions.assertTrue((staff.get("dept") == null || staff.get("dept").isNull() ? 1 : 0) != 0, (String)"manager 無部門，不得編造");
        Assertions.assertTrue((staff.get("passwordHash") == null || staff.get("passwordHash").isNull() ? 1 : 0) != 0, (String)"密碼雜湊絕不得出現在回應");
        JWTClaimsSet claims = SignedJWT.parse((String)this.objectMapper.readTree(login.body()).get("token").asText()).getJWTClaimsSet();
        Assertions.assertEquals((Object)"manager", (Object)claims.getStringClaim("role"));
        Assertions.assertNull((Object)claims.getStringClaim("dept"));
    }

    @Then(value="^不簽發任何憑證$")
    public void noCredentialIssued() {
        JsonNode body = this.objectMapper.readTree(this.ctx.lastResponse().body());
        Assertions.assertEquals((Object)"unauthorized", (Object)body.get("code").asText());
        Assertions.assertTrue((body.get("token") == null || body.get("token").isNull() ? 1 : 0) != 0, (String)"401 回應不得夾帶任何憑證");
    }

    @Then(value="^三者請求應該都失敗，回應皆為 401 且錯誤碼一致$")
    public void threeFailuresIndistinguishable() {
        List<ScenarioContext.HttpResult> attempts = this.ctx.get("loginAttempts");
        String firstCode = null;
        for (ScenarioContext.HttpResult attempt : attempts) {
            Assertions.assertEquals((int)401, (int)attempt.status(), (String)("回應 body: " + attempt.body()));
            String code = this.objectMapper.readTree(attempt.body()).get("code").asText();
            if (firstCode == null) {
                firstCode = code;
                continue;
            }
            Assertions.assertEquals((Object)firstCode, (Object)code, (String)"錯誤碼必須不可區分");
        }
    }

    @Then(value="^缺密碼欄位的登入請求應該失敗，回應為 (\\d+)$")
    public void missingPasswordFailsWith(int expected) {
        ScenarioContext.HttpResult missing = this.api.loginRaw(1L, this.ctx.staffLogin("小美"), null, null, null);
        Assertions.assertEquals((int)expected, (int)missing.status(), (String)("缺必填欄位是客戶端 bug，與憑證錯誤的 401 要分流: " + missing.body()));
    }

    @Then(value="^兩者請求應該都失敗，回應皆為 401$")
    public void bothDeactivatedCallsAre401() {
        ScenarioContext.HttpResult relogin = (ScenarioContext.HttpResult)this.ctx.get("reloginResult");
        ScenarioContext.HttpResult oldToken = (ScenarioContext.HttpResult)this.ctx.get("oldTokenResult");
        Assertions.assertEquals((int)401, (int)relogin.status(), (String)("「阿強」active=false → 登入 401: " + relogin.body()));
        Assertions.assertEquals((int)401, (int)oldToken.status(), (String)"停用必須立即生效，不得等 JWT TTL 到期——這就是每請求打 DB 驗 active 的理由");
    }

    @Then(value="^三者請求應該都失敗，回應皆為 401$")
    public void allThreeCredentialAttemptsAre401() {
        List<ScenarioContext.HttpResult> attempts = this.ctx.get("credentialAttempts");
        for (ScenarioContext.HttpResult attempt : attempts) {
            Assertions.assertEquals((int)401, (int)attempt.status(), (String)("回應 body: " + attempt.body()));
        }
    }

    @Then(value="^上下文帶房 (\\d+) 與該訂單的編號$")
    public void contextCarriesRoomAndReservation(String roomNo) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("回應 body: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Map session = this.db.admin().queryForMap("SELECT room_id, reservation_id FROM guest_sessions WHERE session_token = ?", new Object[]{this.ctx.get("sessionToken")});
        String roomInDb = (String)this.db.admin().queryForObject("SELECT room_no FROM rooms WHERE id = ?", String.class, new Object[]{session.get("room_id")});
        Assertions.assertEquals((Object)roomNo, (Object)roomInDb, (String)"session 綁的就是這間房");
        Assertions.assertEquals((Object)session.get("room_id").toString(), (Object)body.get("roomId").asText(), (String)"驗過 session 要把房綁進請求上下文");
        Assertions.assertEquals((Object)session.get("reservation_id").toString(), (Object)body.get("reservationId").asText(), (String)"訂單也要綁——住客查詢一律以訂單過濾，不按房號");
    }

    @Then(value="^以該 session 呼叫住客寫入動作回 (\\d+)$")
    public void sessionGuestWriteStatus(int expected) {
        ScenarioContext.HttpResult result = this.api.post("/guest/probe", (String)this.ctx.get("sessionToken"), null);
        Assertions.assertEquals((int)expected, (int)result.status(), (String)("回應 body: " + result.body()));
    }

    @Then(value="^以該 session 呼叫員工 API 的請求應該失敗，回應為 (\\d+)$")
    public void sessionStaffCallStatus(int expected) {
        ScenarioContext.HttpResult result = this.api.get("/staff/probe", (String)this.ctx.get("sessionToken"));
        Assertions.assertEquals((int)expected, (int)result.status(), (String)"住客憑證有效也進不了員工門——401/403 分野在「身分假」對「權限不夠」");
    }
}
