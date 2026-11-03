/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.athena.tms.dto.GuestSessionTokens
 *  com.nimbusds.jose.JWSAlgorithm
 *  com.nimbusds.jose.JWSHeader
 *  com.nimbusds.jose.JWSSigner
 *  com.nimbusds.jose.crypto.MACSigner
 *  com.nimbusds.jwt.JWTClaimsSet$Builder
 *  com.nimbusds.jwt.SignedJWT
 *  io.cucumber.java.en.When
 *  org.junit.jupiter.api.Assertions
 */
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
    private static final Map<String, String> ROLE_NAMES = Map.of("\u623f\u52d9", "\u5c0f\u7f8e", "\u5de5\u52d9", "\u963f\u5f37", "\u6ac3\u6aaf", "\u6797\u6ac3\u6aaf", "\u4e3b\u7ba1", "\u738b\u4e3b\u7ba1", "\u8001\u95c6", "\u5f35\u8001\u95c6");
    private final ScenarioContext ctx;
    private final ApiClient api;

    public IdentityWhenSteps(ScenarioContext ctx, ApiClient api) {
        this.ctx = ctx;
        this.api = api;
    }

    @When(value="^\u300c\u5c0f\u7f8e\u300d\u4ee5\u6b63\u78ba\u5e33\u5bc6\u81ea\u540c\u4e00\u53f0\u624b\u6a5f\u767b\u5165\u5169\u6b21$")
    public void xiaomeiLogsInTwiceFromSameDevice() {
        String fingerprint = "bdd-iphone-" + String.valueOf(UUID.randomUUID());
        ScenarioContext.HttpResult first = this.api.loginRaw(1L, this.ctx.staffLogin("\u5c0f\u7f8e"), "demo1234", fingerprint, "apns-token-1");
        Assertions.assertEquals((int)200, (int)first.status(), (String)("\u7b2c\u4e00\u6b21\u767b\u5165\u8a72\u6210\u529f: " + first.body()));
        ScenarioContext.HttpResult second = this.api.loginRaw(1L, this.ctx.staffLogin("\u5c0f\u7f8e"), "demo1234", fingerprint, "apns-token-2");
        this.ctx.put("fingerprint", fingerprint);
        this.ctx.lastResponse(second);
    }

    @When(value="^\u738b\u4e3b\u7ba1\u4ee5\u6b63\u78ba\u5e33\u5bc6\u767b\u5165$")
    public void managerLogsIn() {
        this.ctx.lastResponse(this.api.loginRaw(1L, this.ctx.staffLogin("\u738b\u4e3b\u7ba1"), "demo1234", null, null));
    }

    @When(value="^\u4ee5 login_id \"hk1\" \u8207\u932f\u8aa4\u5bc6\u78bc\u767b\u5165$")
    public void loginWithWrongPassword() {
        this.ctx.lastResponse(this.api.loginRaw(1L, "hk1", "wrong-password", null, null));
    }

    @When(value="^\u5206\u5225\u4ee5 \u932f\u8aa4\u5bc6\u78bc\u3001\u4e0d\u5b58\u5728\u7684\u5e33\u865f\u3001\u4e0d\u5b58\u5728\u7684\u79df\u6236 \u767b\u5165$")
    public void threeIndistinguishableLoginFailures() {
        this.ctx.put("loginAttempts", List.of(this.api.loginRaw(1L, "hk1", "wrong-password", null, null), this.api.loginRaw(1L, "no-such-user", "demo1234", null, null), this.api.loginRaw(99L, "hk1", "demo1234", null, null)));
    }

    @When(value="^\u4ed6\u767b\u5165\u4e26\u4ee5\u820a\u6191\u8b49\u547c\u53eb\u54e1\u5de5 API$")
    public void deactivatedLoginAndOldTokenCall() {
        this.ctx.put("reloginResult", this.api.loginRaw(1L, this.ctx.staffLogin("\u963f\u5f37"), "demo1234", null, null));
        this.ctx.put("oldTokenResult", this.api.get("/staff/probe", (String)this.ctx.get("oldToken")));
    }

    @When(value="^\u5206\u5225\u4ee5 \u7121\u6191\u8b49\u3001\u5783\u573e\u5b57\u4e32\u3001\u507d\u9020\u7c3d\u7ae0\u7684 JWT \u547c\u53eb\u54e1\u5de5 API$")
    public void anonymousGarbageAndForgedCalls() throws Exception {
        MACSigner forger = new MACSigner("attacker-secret-attacker-secret-attacker!".getBytes());
        SignedJWT forged = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder().claim("tenant_id", (Object)1L).claim("staff_id", (Object)TestData.STAFF_MG1.toString()).claim("role", (Object)"manager").expirationTime(new Date(System.currentTimeMillis() + 3600000L)).build());
        forged.sign((JWSSigner)forger);
        this.ctx.put("credentialAttempts", List.of(this.api.get("/staff/probe", null), this.api.get("/staff/probe", "not-a-jwt-at-all"), this.api.get("/staff/probe", forged.serialize())));
    }

    @When(value="^(\u623f\u52d9 \u5c0f\u7f8e|\u8001\u95c6\u5e33\u865f|\u6ac3\u6aaf) \u547c\u53eb (\u4e3b\u7ba1\u67e5\u623f\u63d0\u4ea4|\u4efb\u52d9\u8a8d\u9818|\u623f\u9593 QR \u8f2a\u66ff)$")
    public void semanticOverreach(String actor, String action) {
        String loginId = switch (actor) {
            case "\u623f\u52d9 \u5c0f\u7f8e" -> this.ctx.staffLogin("\u5c0f\u7f8e");
            case "\u8001\u95c6\u5e33\u865f" -> this.ctx.staffLogin("\u5f35\u8001\u95c6");
            default -> this.ctx.staffLogin("\u6797\u6ac3\u6aaf");
        };
        String path = switch (action) {
            case "\u4e3b\u7ba1\u67e5\u623f\u63d0\u4ea4" -> "/admin/inspections/" + String.valueOf(UUID.randomUUID());
            case "\u4efb\u52d9\u8a8d\u9818" -> "/staff/tasks/" + String.valueOf(UUID.randomUUID()) + "/claim";
            default -> "/admin/rooms/305/qr/rotate";
        };
        this.ctx.lastResponse(this.api.post(path, this.api.staffToken(1L, loginId), null));
    }

    @When(value="^\u8001\u95c6\u5e33\u865f\u8b80\u53d6\u5100\u8868\u677f$")
    public void ownerReadsDashboard() {
        this.ctx.lastResponse(this.api.get("/owner/probe", this.api.staffToken(1L, this.ctx.staffLogin("\u5f35\u8001\u95c6"))));
    }

    @When(value="^(\u623f\u52d9|\u5de5\u52d9|\u6ac3\u6aaf|\u4e3b\u7ba1|\u8001\u95c6) \u4ee5 (GET|POST) \u547c\u53eb (\\S+)$")
    public void roleCallsPrefixCell(String role, String method, String path) {
        String token = this.api.staffToken(1L, this.ctx.staffLogin(ROLE_NAMES.get(role)));
        this.ctx.lastResponse("GET".equals(method) ? this.api.get(path, token) : this.api.post(path, token, null));
    }

    @When(value="^\u4ee5 session \"([^\"]+)\" \u8b80\u53d6\u4f4f\u5ba2\u4e0a\u4e0b\u6587$")
    public void readGuestContextWithSession(String sessionSymbol) {
        String token = this.ctx.guestSession(sessionSymbol);
        this.ctx.put("sessionToken", token);
        this.ctx.lastResponse(this.api.get("/guest/context", token));
    }

    @When(value="^\u4ee5\u4e00\u679a\u683c\u5f0f\u6b63\u78ba\u4f46\u8cc7\u6599\u5eab\u67e5\u7121\u6b64\u5217\u7684 session token \u547c\u53eb\u4f4f\u5ba2 API$")
    public void wellFormedButAbsentSession() {
        this.ctx.lastResponse(this.api.get("/guest/context", GuestSessionTokens.issue((long)1L)));
    }

    @When(value="^\u738b\u4e3b\u7ba1\u4ee5\u81ea\u5bb6\u623f\u9593 \"([^\"]+)\" \u7684 id \u67e5\u8a62\u623f\u9593$")
    public void managerReadsOwnRoom(String roomSymbol) {
        this.ctx.lastResponse(this.api.get("/staff/rooms/" + String.valueOf(this.ctx.entityId(roomSymbol)), this.api.staffToken(1L, this.ctx.staffLogin("\u738b\u4e3b\u7ba1"))));
    }

    @When(value="^\u738b\u4e3b\u7ba1\u4ee5\u79df\u6236 2 \u623f\u9593 \"([^\"]+)\" \u7684 id \u67e5\u8a62\u623f\u9593$")
    public void managerReadsOtherTenantsRoom(String roomSymbol) {
        this.ctx.lastResponse(this.api.get("/staff/rooms/" + String.valueOf(this.ctx.entityId(roomSymbol)), this.api.staffToken(1L, this.ctx.staffLogin("\u738b\u4e3b\u7ba1"))));
    }

    @When(value="^\u79df\u6236 2 \u7684\u5c0f\u82b3\u4ee5\u79df\u6236 1 \u623f\u9593 \"([^\"]+)\" \u7684 id \u67e5\u8a62\u623f\u9593$")
    public void tenant2StaffReadsTenant1Room(String roomSymbol) {
        this.ctx.lastResponse(this.api.get("/staff/rooms/" + String.valueOf(this.ctx.entityId(roomSymbol)), this.api.staffToken(2L, this.ctx.staffLogin("\u5c0f\u82b3"))));
    }
}
