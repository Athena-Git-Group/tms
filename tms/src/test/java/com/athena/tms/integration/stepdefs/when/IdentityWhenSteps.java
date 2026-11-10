package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.dto.GuestSessionTokens;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.TestData;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.cucumber.java.en.When;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;

public class IdentityWhenSteps {

    private static final Map<String, String> ROLE_NAMES = Map.of(
            "房務", "小美",
            "工務", "阿強",
            "櫃檯", "林櫃檯",
            "主管", "王主管",
            "老闆", "張老闆");

    private final ScenarioContext ctx;
    private final ApiClient api;

    public IdentityWhenSteps(ScenarioContext ctx, ApiClient api) {
        this.ctx = ctx;
        this.api = api;
    }

    @When("^「小美」以正確帳密自同一台手機登入兩次$")
    public void xiaomeiLogsInTwiceFromSameDevice() {
        String fingerprint = "bdd-iphone-" + UUID.randomUUID();
        ScenarioContext.HttpResult first = api.loginRaw(1L, ctx.staffLogin("小美"), "demo1234", fingerprint, "apns-token-1");
        Assertions.assertEquals(200, first.status(), "第一次登入該成功: " + first.body());
        ScenarioContext.HttpResult second = api.loginRaw(1L, ctx.staffLogin("小美"), "demo1234", fingerprint, "apns-token-2");
        ctx.put("fingerprint", fingerprint);
        ctx.lastResponse(second);
    }

    @When("^王主管以正確帳密登入$")
    public void managerLogsIn() {
        ctx.lastResponse(api.loginRaw(1L, ctx.staffLogin("王主管"), "demo1234", null, null));
    }

    @When("^以 login_id \"hk1\" 與錯誤密碼登入$")
    public void loginWithWrongPassword() {
        ctx.lastResponse(api.loginRaw(1L, "hk1", "wrong-password", null, null));
    }

    @When("^分別以 錯誤密碼、不存在的帳號、不存在的租戶 登入$")
    public void threeIndistinguishableLoginFailures() {
        ctx.put("loginAttempts", List.of(
                api.loginRaw(1L, "hk1", "wrong-password", null, null),
                api.loginRaw(1L, "no-such-user", "demo1234", null, null),
                api.loginRaw(99L, "hk1", "demo1234", null, null)));
    }

    @When("^他登入並以舊憑證呼叫員工 API$")
    public void deactivatedLoginAndOldTokenCall() {
        ctx.put("reloginResult", api.loginRaw(1L, ctx.staffLogin("阿強"), "demo1234", null, null));
        ctx.put("oldTokenResult", api.get("/staff/probe", ctx.get("oldToken")));
    }

    @When("^分別以 無憑證、垃圾字串、偽造簽章的 JWT 呼叫員工 API$")
    public void anonymousGarbageAndForgedCalls() throws Exception {
        MACSigner forger = new MACSigner("attacker-secret-attacker-secret-attacker!".getBytes());
        SignedJWT forged = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .claim("tenant_id", 1L)
                        .claim("staff_id", TestData.STAFF_MG1.toString())
                        .claim("role", "manager")
                        .expirationTime(new Date(System.currentTimeMillis() + 3600_000L))
                        .build());
        forged.sign(forger);
        ctx.put("credentialAttempts", List.of(
                api.get("/staff/probe", null),
                api.get("/staff/probe", "not-a-jwt-at-all"),
                api.get("/staff/probe", forged.serialize())));
    }

    @When("^(房務 小美|老闆帳號|櫃檯) 呼叫 (主管查房提交|任務認領|房間 QR 輪替)$")
    public void semanticOverreach(String actor, String action) {
        String loginId = switch (actor) {
            case "房務 小美" -> ctx.staffLogin("小美");
            case "老闆帳號" -> ctx.staffLogin("張老闆");
            default -> ctx.staffLogin("林櫃檯");
        };
        String path = switch (action) {
            case "主管查房提交" -> "/admin/inspections/" + UUID.randomUUID();
            case "任務認領" -> "/api/v1/tasks/" + UUID.randomUUID() + "/claim";
            default -> "/admin/rooms/305/qr/rotate";
        };
        ctx.lastResponse(api.post(path, api.staffToken(1L, loginId), null));
    }

    @When("^老闆帳號讀取儀表板$")
    public void ownerReadsDashboard() {
        ctx.lastResponse(api.get("/owner/probe", api.staffToken(1L, ctx.staffLogin("張老闆"))));
    }

    @When("^(房務|工務|櫃檯|主管|老闆) 以 (GET|POST) 呼叫 (\\S+)$")
    public void roleCallsPrefixCell(String role, String method, String path) {
        String token = api.staffToken(1L, ctx.staffLogin(ROLE_NAMES.get(role)));
        ctx.lastResponse("GET".equals(method) ? api.get(path, token) : api.post(path, token, null));
    }

    @When("^以 session \"([^\"]+)\" 讀取住客上下文$")
    public void readGuestContextWithSession(String sessionSymbol) {
        String token = ctx.guestSession(sessionSymbol);
        ctx.put("sessionToken", token);
        ctx.lastResponse(api.get("/guest/context", token));
    }

    @When("^以一枚格式正確但資料庫查無此列的 session token 呼叫住客 API$")
    public void wellFormedButAbsentSession() {
        ctx.lastResponse(api.get("/guest/context", GuestSessionTokens.issue(1L)));
    }

    @When("^王主管以自家房間 \"([^\"]+)\" 的 id 查詢房間$")
    public void managerReadsOwnRoom(String roomSymbol) {
        ctx.lastResponse(api.get("/staff/rooms/" + ctx.entityId(roomSymbol), api.staffToken(1L, ctx.staffLogin("王主管"))));
    }

    @When("^王主管以租戶 2 房間 \"([^\"]+)\" 的 id 查詢房間$")
    public void managerReadsOtherTenantsRoom(String roomSymbol) {
        ctx.lastResponse(api.get("/staff/rooms/" + ctx.entityId(roomSymbol), api.staffToken(1L, ctx.staffLogin("王主管"))));
    }

    @When("^租戶 2 的小芳以租戶 1 房間 \"([^\"]+)\" 的 id 查詢房間$")
    public void tenant2StaffReadsTenant1Room(String roomSymbol) {
        ctx.lastResponse(api.get("/staff/rooms/" + ctx.entityId(roomSymbol), api.staffToken(2L, ctx.staffLogin("小芳"))));
    }
}
