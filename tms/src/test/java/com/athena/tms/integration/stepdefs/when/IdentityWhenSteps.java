package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.dto.GuestSessionTokens;
import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;
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

import static com.athena.tms.integration.stepdefs.utils.TestData.TENANT_1;
import static com.athena.tms.integration.stepdefs.utils.TestData.TENANT_2;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 05-identity-tenancy feature 的動作步驟。 */
public class IdentityWhenSteps {

    /** 矩陣表格的中文角色名 → 員工中文名（login 再經 ScenarioContext 的員工表對照）。 */
    private static final Map<String, String> ROLE_NAMES = Map.of(
            "房務", "小美", "工務", "阿強", "櫃檯", "林櫃檯", "主管", "王主管", "老闆", "張老闆");

    private final ScenarioContext ctx;
    private final ApiClient api;

    public IdentityWhenSteps(ScenarioContext ctx, ApiClient api) {
        this.ctx = ctx;
        this.api = api;
    }

    @When("^「小美」以正確帳密自同一台手機登入兩次$")
    public void xiaomeiLogsInTwiceFromSameDevice() {
        String fingerprint = "bdd-iphone-" + UUID.randomUUID();
        HttpResult first = api.loginRaw(TENANT_1, ctx.staffLogin("小美"), TestData.PASSWORD, fingerprint, "apns-token-1");
        assertEquals(200, first.status(), "第一次登入該成功: " + first.body());
        HttpResult second = api.loginRaw(TENANT_1, ctx.staffLogin("小美"), TestData.PASSWORD, fingerprint, "apns-token-2");
        ctx.put("fingerprint", fingerprint);
        ctx.lastResponse(second);
    }

    @When("^王主管以正確帳密登入$")
    public void managerLogsIn() {
        ctx.lastResponse(api.loginRaw(TENANT_1, ctx.staffLogin("王主管"), TestData.PASSWORD, null, null));
    }

    @When("^以 login_id \"hk1\" 與錯誤密碼登入$")
    public void loginWithWrongPassword() {
        ctx.lastResponse(api.loginRaw(TENANT_1, "hk1", "wrong-password", null, null));
    }

    @When("^分別以 錯誤密碼、不存在的帳號、不存在的租戶 登入$")
    public void threeIndistinguishableLoginFailures() {
        ctx.put("loginAttempts", List.of(
                api.loginRaw(TENANT_1, "hk1", "wrong-password", null, null),
                api.loginRaw(TENANT_1, "no-such-user", TestData.PASSWORD, null, null),
                api.loginRaw(99L, "hk1", TestData.PASSWORD, null, null)));
    }

    @When("^他登入並以舊憑證呼叫員工 API$")
    public void deactivatedLoginAndOldTokenCall() {
        ctx.put("reloginResult", api.loginRaw(TENANT_1, ctx.staffLogin("阿強"), TestData.PASSWORD, null, null));
        ctx.put("oldTokenResult", api.get("/staff/probe", ctx.get("oldToken")));
    }

    @When("^分別以 無憑證、垃圾字串、偽造簽章的 JWT 呼叫員工 API$")
    public void anonymousGarbageAndForgedCalls() throws Exception {
        // 偽造者知道 claims 形狀但沒有 secret：簽章驗不過就不該走到任何 DB 查詢
        MACSigner forger = new MACSigner("attacker-secret-attacker-secret-attacker!".getBytes());
        SignedJWT forged = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .claim("tenant_id", 1L)
                        .claim("staff_id", TestData.STAFF_MG1.toString())
                        .claim("role", "manager")
                        .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
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
        // 真端點：授權判定在 dispatch 之前，403 時不會碰到 body 驗證或資料列
        String path = switch (action) {
            case "主管查房提交" -> "/admin/inspections/" + UUID.randomUUID();
            case "任務認領" -> "/staff/tasks/" + UUID.randomUUID() + "/claim";
            default -> "/admin/rooms/305/qr/rotate";
        };
        ctx.lastResponse(api.post(path, api.staffToken(TENANT_1, loginId), null));
    }

    @When("^老闆帳號讀取儀表板$")
    public void ownerReadsDashboard() {
        // MVP 尚無真的 /owner 業務端點，儀表板以 owner 前綴探針代位（矩陣放行語意相同）
        ctx.lastResponse(api.get("/owner/probe", api.staffToken(TENANT_1, ctx.staffLogin("張老闆"))));
    }

    @When("^(房務|工務|櫃檯|主管|老闆) 以 (GET|POST) 呼叫 (\\S+)$")
    public void roleCallsPrefixCell(String role, String method, String path) {
        String token = api.staffToken(TENANT_1, ctx.staffLogin(ROLE_NAMES.get(role)));
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
        ctx.lastResponse(api.get("/guest/context", GuestSessionTokens.issue(TENANT_1)));
    }

    @When("^王主管以自家房間 \"([^\"]+)\" 的 id 查詢房間$")
    public void managerReadsOwnRoom(String roomSymbol) {
        ctx.lastResponse(api.get("/staff/rooms/" + ctx.entityId(roomSymbol),
                api.staffToken(TENANT_1, ctx.staffLogin("王主管"))));
    }

    @When("^王主管以租戶 2 房間 \"([^\"]+)\" 的 id 查詢房間$")
    public void managerReadsOtherTenantsRoom(String roomSymbol) {
        ctx.lastResponse(api.get("/staff/rooms/" + ctx.entityId(roomSymbol),
                api.staffToken(TENANT_1, ctx.staffLogin("王主管"))));
    }

    @When("^租戶 2 的小芳以租戶 1 房間 \"([^\"]+)\" 的 id 查詢房間$")
    public void tenant2StaffReadsTenant1Room(String roomSymbol) {
        ctx.lastResponse(api.get("/staff/rooms/" + ctx.entityId(roomSymbol),
                api.staffToken(TENANT_2, ctx.staffLogin("小芳"))));
    }
}
