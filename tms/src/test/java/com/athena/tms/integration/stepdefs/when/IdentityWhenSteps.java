package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.dto.GuestSessionTokens;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.TestData;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
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
    private static final Map<String, String> ROLE_NAMES = Map.of("房務", "小美", "工務", "阿強", "櫃檯", "林櫃檯", "主管", "王主管", "老闆", "張老闆");
    private final ScenarioContext ctx;
    private final ApiClient api;

    public IdentityWhenSteps(ScenarioContext ctx, ApiClient api) {
        this.ctx = ctx;
        this.api = api;
    }

    @When(value="^「小美」以正確帳密自同一台手機登入兩次$")
    public void xiaomeiLogsInTwiceFromSameDevice() {
        String fingerprint = "bdd-iphone-" + String.valueOf(UUID.randomUUID());
        ScenarioContext.HttpResult first = this.api.loginRaw(1L, this.ctx.staffLogin("小美"), "demo1234", fingerprint, "apns-token-1");
        Assertions.assertEquals((int)200, (int)first.status(), (String)("第一次登入該成功: " + first.body()));
        ScenarioContext.HttpResult second = this.api.loginRaw(1L, this.ctx.staffLogin("小美"), "demo1234", fingerprint, "apns-token-2");
        this.ctx.put("fingerprint", fingerprint);
        this.ctx.lastResponse(second);
    }

    @When(value="^王主管以正確帳密登入$")
    public void managerLogsIn() {
        this.ctx.lastResponse(this.api.loginRaw(1L, this.ctx.staffLogin("王主管"), "demo1234", null, null));
    }

    @When(value="^以 login_id \"hk1\" 與錯誤密碼登入$")
    public void loginWithWrongPassword() {
        this.ctx.lastResponse(this.api.loginRaw(1L, "hk1", "wrong-password", null, null));
    }

    @When(value="^分別以 錯誤密碼、不存在的帳號、不存在的租戶 登入$")
    public void threeIndistinguishableLoginFailures() {
        this.ctx.put("loginAttempts", List.of(this.api.loginRaw(1L, "hk1", "wrong-password", null, null), this.api.loginRaw(1L, "no-such-user", "demo1234", null, null), this.api.loginRaw(99L, "hk1", "demo1234", null, null)));
    }

    @When(value="^他登入並以舊憑證呼叫員工 API$")
    public void deactivatedLoginAndOldTokenCall() {
        this.ctx.put("reloginResult", this.api.loginRaw(1L, this.ctx.staffLogin("阿強"), "demo1234", null, null));
        this.ctx.put("oldTokenResult", this.api.get("/staff/probe", (String)this.ctx.get("oldToken")));
    }

    @When(value="^分別以 無憑證、垃圾字串、偽造簽章的 JWT 呼叫員工 API$")
    public void anonymousGarbageAndForgedCalls() throws Exception {
        MACSigner forger = new MACSigner("attacker-secret-attacker-secret-attacker!".getBytes());
        SignedJWT forged = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder().claim("tenant_id", (Object)1L).claim("staff_id", (Object)TestData.STAFF_MG1.toString()).claim("role", (Object)"manager").expirationTime(new Date(System.currentTimeMillis() + 3600000L)).build());
        forged.sign((JWSSigner)forger);
        this.ctx.put("credentialAttempts", List.of(this.api.get("/staff/probe", null), this.api.get("/staff/probe", "not-a-jwt-at-all"), this.api.get("/staff/probe", forged.serialize())));
    }

    @When(value="^(房務 小美|老闆帳號|櫃檯) 呼叫 (主管查房提交|任務認領|房間 QR 輪替)$")
    public void semanticOverreach(String actor, String action) {
        String loginId = switch (actor) {
            case "房務 小美" -> this.ctx.staffLogin("小美");
            case "老闆帳號" -> this.ctx.staffLogin("張老闆");
            default -> this.ctx.staffLogin("林櫃檯");
        };
        String path = switch (action) {
            case "主管查房提交" -> "/admin/inspections/" + String.valueOf(UUID.randomUUID());
            case "任務認領" -> "/staff/tasks/" + String.valueOf(UUID.randomUUID()) + "/claim";
            default -> "/admin/rooms/305/qr/rotate";
        };
        this.ctx.lastResponse(this.api.post(path, this.api.staffToken(1L, loginId), null));
    }

    @When(value="^老闆帳號讀取儀表板$")
    public void ownerReadsDashboard() {
        this.ctx.lastResponse(this.api.get("/owner/probe", this.api.staffToken(1L, this.ctx.staffLogin("張老闆"))));
    }

    @When(value="^(房務|工務|櫃檯|主管|老闆) 以 (GET|POST) 呼叫 (\\S+)$")
    public void roleCallsPrefixCell(String role, String method, String path) {
        String token = this.api.staffToken(1L, this.ctx.staffLogin(ROLE_NAMES.get(role)));
        this.ctx.lastResponse("GET".equals(method) ? this.api.get(path, token) : this.api.post(path, token, null));
    }

    @When(value="^以 session \"([^\"]+)\" 讀取住客上下文$")
    public void readGuestContextWithSession(String sessionSymbol) {
        String token = this.ctx.guestSession(sessionSymbol);
        this.ctx.put("sessionToken", token);
        this.ctx.lastResponse(this.api.get("/guest/context", token));
    }

    @When(value="^以一枚格式正確但資料庫查無此列的 session token 呼叫住客 API$")
    public void wellFormedButAbsentSession() {
        this.ctx.lastResponse(this.api.get("/guest/context", GuestSessionTokens.issue((long)1L)));
    }

    @When(value="^王主管以自家房間 \"([^\"]+)\" 的 id 查詢房間$")
    public void managerReadsOwnRoom(String roomSymbol) {
        this.ctx.lastResponse(this.api.get("/staff/rooms/" + String.valueOf(this.ctx.entityId(roomSymbol)), this.api.staffToken(1L, this.ctx.staffLogin("王主管"))));
    }

    @When(value="^王主管以租戶 2 房間 \"([^\"]+)\" 的 id 查詢房間$")
    public void managerReadsOtherTenantsRoom(String roomSymbol) {
        this.ctx.lastResponse(this.api.get("/staff/rooms/" + String.valueOf(this.ctx.entityId(roomSymbol)), this.api.staffToken(1L, this.ctx.staffLogin("王主管"))));
    }

    @When(value="^租戶 2 的小芳以租戶 1 房間 \"([^\"]+)\" 的 id 查詢房間$")
    public void tenant2StaffReadsTenant1Room(String roomSymbol) {
        this.ctx.lastResponse(this.api.get("/staff/rooms/" + String.valueOf(this.ctx.entityId(roomSymbol)), this.api.staffToken(2L, this.ctx.staffLogin("小芳"))));
    }
}
