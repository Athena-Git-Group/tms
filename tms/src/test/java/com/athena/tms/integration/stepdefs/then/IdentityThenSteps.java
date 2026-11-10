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

    @Then(value="^\u53d6\u5f97\u542b tenant_id/staff_id/role/dept \u7684\u6191\u8b49\uff0c\u6709\u6548\u671f\u70ba 12 \u5c0f\u6642$")
    public void credentialCarriesSpecClaims() throws Exception {
        ScenarioContext.HttpResult login = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)login.status(), (String)("\u7b2c\u4e8c\u6b21\u767b\u5165\u8a72\u6210\u529f: " + login.body()));
        JsonNode body = this.objectMapper.readTree(login.body());
        Assertions.assertEquals((Object)"Bearer", (Object)body.get("tokenType").asText());
        Assertions.assertNotNull((Object)body.get("expiresAt"));
        JsonNode staff = body.get("staff");
        Assertions.assertEquals((Object)"\u5c0f\u7f8e", (Object)staff.get("name").asText());
        Assertions.assertEquals((Object)"housekeeping", (Object)staff.get("role").asText());
        Assertions.assertEquals((int)1, (int)staff.get("tenantId").asInt());
        Assertions.assertTrue((staff.get("passwordHash") == null || staff.get("passwordHash").isNull() ? 1 : 0) != 0, (String)"\u5bc6\u78bc\u96dc\u6e4a\u7d55\u4e0d\u5f97\u51fa\u73fe\u5728\u56de\u61c9");
        JWTClaimsSet claims = SignedJWT.parse((String)body.get("token").asText()).getJWTClaimsSet();
        Assertions.assertEquals((long)1L, (Long)claims.getLongClaim("tenant_id"));
        Assertions.assertEquals((Object)TestData.STAFF_HK1.toString(), (Object)claims.getStringClaim("staff_id"));
        Assertions.assertEquals((Object)"housekeeping", (Object)claims.getStringClaim("role"));
        Assertions.assertEquals((Object)"housekeeping", (Object)claims.getStringClaim("dept"));
        long ttlSeconds = (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000L;
        Assertions.assertEquals((long)43200L, (long)ttlSeconds, (String)"TTL \u5fc5\u9808\u6070\u70ba 12 \u5c0f\u6642\uff08gherkin \u767b\u5165\u6210\u529f\u5834\u666f\uff09");
    }

    @Then(value="^staff_devices \u5c0d\u540c\u4e00\u88dd\u7f6e\u53bb\u91cd\u3001\u50c5\u767b\u8a18\u4e00\u5217\uff0c\u4e14\u767b\u5165\u4e0d\u7d81\u88dd\u7f6e$")
    public void deviceDedupAndLoginNotBoundToDevice() {
        String fingerprint = (String)this.ctx.get("fingerprint");
        Integer sameDevice = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM staff_devices WHERE tenant_id = 1 AND staff_id = ? AND device_fingerprint = ?", Integer.class, new Object[]{TestData.STAFF_HK1, fingerprint});
        Assertions.assertEquals((int)1, (int)sameDevice, (String)"\u540c\u4e00\u53f0\u624b\u6a5f\u767b\u5165\u5169\u6b21\uff0cstaff_devices \u50c5\u767b\u8a18\u4e00\u5217");
        String pushToken = (String)this.db.admin().queryForObject("SELECT push_token FROM staff_devices WHERE tenant_id = 1 AND staff_id = ? AND device_fingerprint = ?", String.class, new Object[]{TestData.STAFF_HK1, fingerprint});
        Assertions.assertEquals((Object)"apns-token-2", (Object)pushToken, (String)"\u91cd\u8907\u767b\u5165\u8981\u66f4\u65b0 push_token\uff08\u63db token \u662f APNS/FCM \u5e38\u614b\uff09");
        Integer before = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM staff_devices WHERE tenant_id = 1 AND staff_id = ?", Integer.class, new Object[]{TestData.STAFF_HK1});
        ScenarioContext.HttpResult noDevice = this.api.loginRaw(1L, this.ctx.staffLogin("\u5c0f\u7f8e"), "demo1234", null, null);
        Assertions.assertEquals((int)200, (int)noDevice.status(), (String)"\u767b\u5165\u4e0d\u7d81\u88dd\u7f6e\uff1a\u6c92\u5e36 fingerprint \u7167\u6a23\u767b\u5165");
        Integer after = (Integer)this.db.admin().queryForObject("SELECT count(*) FROM staff_devices WHERE tenant_id = 1 AND staff_id = ?", Integer.class, new Object[]{TestData.STAFF_HK1});
        Assertions.assertEquals((Integer)before, (Integer)after, (String)"\u6c92\u5e36 fingerprint \u4e0d\u5f97\u591a\u767b\u8a18\u88dd\u7f6e");
    }

    @Then(value="^\u6191\u8b49 role \u70ba \"manager\" \u4e14\u4e0d\u542b dept\uff0c\u56de\u61c9\u4e5f\u4e0d\u542b\u5bc6\u78bc\u96dc\u6e4a$")
    public void managerCredentialHasNoDept() throws Exception {
        ScenarioContext.HttpResult login = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)login.status(), (String)("\u767b\u5165\u8a72\u6210\u529f: " + login.body()));
        JsonNode staff = this.objectMapper.readTree(login.body()).get("staff");
        Assertions.assertEquals((Object)"\u738b\u4e3b\u7ba1", (Object)staff.get("name").asText());
        Assertions.assertEquals((Object)"manager", (Object)staff.get("role").asText());
        Assertions.assertTrue((staff.get("dept") == null || staff.get("dept").isNull() ? 1 : 0) != 0, (String)"manager \u7121\u90e8\u9580\uff0c\u4e0d\u5f97\u7de8\u9020");
        Assertions.assertTrue((staff.get("passwordHash") == null || staff.get("passwordHash").isNull() ? 1 : 0) != 0, (String)"\u5bc6\u78bc\u96dc\u6e4a\u7d55\u4e0d\u5f97\u51fa\u73fe\u5728\u56de\u61c9");
        JWTClaimsSet claims = SignedJWT.parse((String)this.objectMapper.readTree(login.body()).get("token").asText()).getJWTClaimsSet();
        Assertions.assertEquals((Object)"manager", (Object)claims.getStringClaim("role"));
        Assertions.assertNull((Object)claims.getStringClaim("dept"));
    }

    @Then(value="^\u4e0d\u7c3d\u767c\u4efb\u4f55\u6191\u8b49$")
    public void noCredentialIssued() {
        JsonNode body = this.objectMapper.readTree(this.ctx.lastResponse().body());
        Assertions.assertEquals((Object)"unauthorized", (Object)body.get("code").asText());
        Assertions.assertTrue((body.get("token") == null || body.get("token").isNull() ? 1 : 0) != 0, (String)"401 \u56de\u61c9\u4e0d\u5f97\u593e\u5e36\u4efb\u4f55\u6191\u8b49");
    }

    @Then(value="^\u4e09\u8005\u8acb\u6c42\u61c9\u8a72\u90fd\u5931\u6557\uff0c\u56de\u61c9\u7686\u70ba 401 \u4e14\u932f\u8aa4\u78bc\u4e00\u81f4$")
    public void threeFailuresIndistinguishable() {
        List<ScenarioContext.HttpResult> attempts = this.ctx.get("loginAttempts");
        String firstCode = null;
        for (ScenarioContext.HttpResult attempt : attempts) {
            Assertions.assertEquals((int)401, (int)attempt.status(), (String)("\u56de\u61c9 body: " + attempt.body()));
            String code = this.objectMapper.readTree(attempt.body()).get("code").asText();
            if (firstCode == null) {
                firstCode = code;
                continue;
            }
            Assertions.assertEquals((Object)firstCode, (Object)code, (String)"\u932f\u8aa4\u78bc\u5fc5\u9808\u4e0d\u53ef\u5340\u5206");
        }
    }

    @Then(value="^\u7f3a\u5bc6\u78bc\u6b04\u4f4d\u7684\u767b\u5165\u8acb\u6c42\u61c9\u8a72\u5931\u6557\uff0c\u56de\u61c9\u70ba (\\d+)$")
    public void missingPasswordFailsWith(int expected) {
        ScenarioContext.HttpResult missing = this.api.loginRaw(1L, this.ctx.staffLogin("\u5c0f\u7f8e"), null, null, null);
        Assertions.assertEquals((int)expected, (int)missing.status(), (String)("\u7f3a\u5fc5\u586b\u6b04\u4f4d\u662f\u5ba2\u6236\u7aef bug\uff0c\u8207\u6191\u8b49\u932f\u8aa4\u7684 401 \u8981\u5206\u6d41: " + missing.body()));
    }

    @Then(value="^\u5169\u8005\u8acb\u6c42\u61c9\u8a72\u90fd\u5931\u6557\uff0c\u56de\u61c9\u7686\u70ba 401$")
    public void bothDeactivatedCallsAre401() {
        ScenarioContext.HttpResult relogin = (ScenarioContext.HttpResult)this.ctx.get("reloginResult");
        ScenarioContext.HttpResult oldToken = (ScenarioContext.HttpResult)this.ctx.get("oldTokenResult");
        Assertions.assertEquals((int)401, (int)relogin.status(), (String)("\u300c\u963f\u5f37\u300dactive=false \u2192 \u767b\u5165 401: " + relogin.body()));
        Assertions.assertEquals((int)401, (int)oldToken.status(), (String)"\u505c\u7528\u5fc5\u9808\u7acb\u5373\u751f\u6548\uff0c\u4e0d\u5f97\u7b49 JWT TTL \u5230\u671f\u2014\u2014\u9019\u5c31\u662f\u6bcf\u8acb\u6c42\u6253 DB \u9a57 active \u7684\u7406\u7531");
    }

    @Then(value="^\u4e09\u8005\u8acb\u6c42\u61c9\u8a72\u90fd\u5931\u6557\uff0c\u56de\u61c9\u7686\u70ba 401$")
    public void allThreeCredentialAttemptsAre401() {
        List<ScenarioContext.HttpResult> attempts = this.ctx.get("credentialAttempts");
        for (ScenarioContext.HttpResult attempt : attempts) {
            Assertions.assertEquals((int)401, (int)attempt.status(), (String)("\u56de\u61c9 body: " + attempt.body()));
        }
    }

    @Then(value="^\u4e0a\u4e0b\u6587\u5e36\u623f (\\d+) \u8207\u8a72\u8a02\u55ae\u7684\u7de8\u865f$")
    public void contextCarriesRoomAndReservation(String roomNo) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)200, (int)last.status(), (String)("\u56de\u61c9 body: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Map session = this.db.admin().queryForMap("SELECT room_id, reservation_id FROM guest_sessions WHERE session_token = ?", new Object[]{this.ctx.get("sessionToken")});
        String roomInDb = (String)this.db.admin().queryForObject("SELECT room_no FROM rooms WHERE id = ?", String.class, new Object[]{session.get("room_id")});
        Assertions.assertEquals((Object)roomNo, (Object)roomInDb, (String)"session \u7d81\u7684\u5c31\u662f\u9019\u9593\u623f");
        Assertions.assertEquals((Object)session.get("room_id").toString(), (Object)body.get("roomId").asText(), (String)"\u9a57\u904e session \u8981\u628a\u623f\u7d81\u9032\u8acb\u6c42\u4e0a\u4e0b\u6587");
        Assertions.assertEquals((Object)session.get("reservation_id").toString(), (Object)body.get("reservationId").asText(), (String)"\u8a02\u55ae\u4e5f\u8981\u7d81\u2014\u2014\u4f4f\u5ba2\u67e5\u8a62\u4e00\u5f8b\u4ee5\u8a02\u55ae\u904e\u6ffe\uff0c\u4e0d\u6309\u623f\u865f");
    }

    @Then(value="^\u4ee5\u8a72 session \u547c\u53eb\u4f4f\u5ba2\u5beb\u5165\u52d5\u4f5c\u56de (\\d+)$")
    public void sessionGuestWriteStatus(int expected) {
        ScenarioContext.HttpResult result = this.api.post("/guest/probe", (String)this.ctx.get("sessionToken"), null);
        Assertions.assertEquals((int)expected, (int)result.status(), (String)("\u56de\u61c9 body: " + result.body()));
    }

    @Then(value="^\u4ee5\u8a72 session \u547c\u53eb\u54e1\u5de5 API \u7684\u8acb\u6c42\u61c9\u8a72\u5931\u6557\uff0c\u56de\u61c9\u70ba (\\d+)$")
    public void sessionStaffCallStatus(int expected) {
        ScenarioContext.HttpResult result = this.api.get("/staff/probe", (String)this.ctx.get("sessionToken"));
        Assertions.assertEquals((int)expected, (int)result.status(), (String)"\u4f4f\u5ba2\u6191\u8b49\u6709\u6548\u4e5f\u9032\u4e0d\u4e86\u54e1\u5de5\u9580\u2014\u2014401/403 \u5206\u91ce\u5728\u300c\u8eab\u5206\u5047\u300d\u5c0d\u300c\u6b0a\u9650\u4e0d\u5920\u300d");
    }
}
