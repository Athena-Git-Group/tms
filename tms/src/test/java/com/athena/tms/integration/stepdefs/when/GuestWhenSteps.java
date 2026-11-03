/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.cucumber.java.en.When
 *  tools.jackson.databind.JsonNode
 *  tools.jackson.databind.ObjectMapper
 */
package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.GuestFixtures;
import io.cucumber.java.en.When;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class GuestWhenSteps {
    private final ScenarioContext ctx;
    private final ApiClient api;
    private final DbSupport db;
    private final GuestFixtures guest;
    private final ObjectMapper objectMapper;

    public GuestWhenSteps(ScenarioContext ctx, ApiClient api, DbSupport db, GuestFixtures guest, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.api = api;
        this.db = db;
        this.guest = guest;
        this.objectMapper = objectMapper;
    }

    @When(value="^\u4ee5 token \"([^\"]+)\" \u547c\u53eb\u6383\u78bc$")
    public void scanWithToken(String token) {
        this.scanAndSnapshot(token);
    }

    @When(value="^\u4efb\u4f55\u4eba\u4ee5 token \"([^\"]+)\" \u6383\u78bc$")
    public void anyoneScansToken(String token) {
        this.scanAndSnapshot(token);
    }

    @When(value="^\u4ee5 session \"([^\"]+)\" \u67e5\u8a62\u672c\u6b21\u4f4f\u5bbf\u7684\u9700\u6c42$")
    public void listRequestsWithSession(String sessionSymbol) {
        this.ctx.lastResponse(this.api.get("/guest/requests", this.ctx.guestSession(sessionSymbol)));
    }

    @When(value="^\u65b0\u4f4f\u5ba2\u4ee5 session \"([^\"]+)\" \u67e5\u8a62\u672c\u6b21\u4f4f\u5bbf\u7684\u9700\u6c42$")
    public void newGuestListsRequests(String sessionSymbol) {
        this.listRequestsWithSession(sessionSymbol);
    }

    @When(value="^\u4ee5 session \"([^\"]+)\" \u8b80\u53d6\u8cc7\u8a0a\u9801$")
    public void readInfoPages(String sessionSymbol) {
        this.ctx.put("tasksBeforeHome", this.db.countAll("tasks"));
        this.ctx.lastResponse(this.api.get("/guest/home", this.ctx.guestSession(sessionSymbol)));
    }

    @When(value="^\u4f4f\u5ba2\u4ee5 session \"([^\"]+)\" \u7528\u81ea\u7531\u6587\u5b57\u300c([^\u300d]+)\u300d\u9001\u51fa\u9700\u6c42$")
    public void createFreeTextRequest(String sessionSymbol, String text) {
        this.ctx.put("freeText", text);
        ScenarioContext.HttpResult created = this.guest.createRequest(this.ctx.guestSession(sessionSymbol), Map.of("text", text, "lang", "zh-TW"));
        this.ctx.lastResponse(created);
        this.rememberTaskIdIfCreated(created);
    }

    @When(value="^\u4f4f\u5ba2\u4ee5 session \"([^\"]+)\" \u9001\u51fa (\u9078\u55ae\u9805|\u81ea\u7531\u6587\u5b57)\u300c([^\u300d]+)\u300d$")
    public void createRequestFromOutline(String sessionSymbol, String kind, String content) {
        if ("\u9078\u55ae\u9805".equals(kind)) {
            ScenarioContext.HttpResult created = this.guest.createRequest(this.ctx.guestSession(sessionSymbol), Map.of("itemId", GuestFixtures.menuKeyOf(content)));
            this.ctx.lastResponse(created);
            this.rememberTaskIdIfCreated(created);
        } else {
            this.createFreeTextRequest(sessionSymbol, content);
        }
    }

    @When(value="^\u518d\u4ee5 session \"([^\"]+)\" \u9001\u51fa\u7b2c 4 \u7b46\u9700\u6c42$")
    public void createFourthRequest(String sessionSymbol) {
        this.ctx.lastResponse(this.guest.createRequest(this.ctx.guestSession(sessionSymbol), Map.of("itemId", "towel")));
    }

    @When(value="^\u4f4f\u5ba2\u4ee5 session \"([^\"]+)\" \u5c0d\u4efb\u52d9 \"([^\"]+)\" \u547c\u53eb\u6536\u56de$")
    public void cancelTask(String sessionSymbol, String taskSymbol) {
        UUID taskId = this.ctx.entityId(taskSymbol);
        this.ctx.taskId(taskId);
        this.ctx.lastResponse(this.guest.cancelRequest(this.ctx.guestSession(sessionSymbol), taskId));
    }

    @When(value="^\u4f4f\u5ba2\u4ee5 session \"([^\"]+)\" \u5c0d\u4efb\u52d9 \"([^\"]+)\" \u8a55 (\\d+) \u661f$")
    public void rateTask(String sessionSymbol, String taskSymbol, int rating) {
        UUID taskId = this.ctx.entityId(taskSymbol);
        this.ctx.taskId(taskId);
        this.ctx.lastResponse(this.guest.createRating(this.ctx.guestSession(sessionSymbol), taskId, rating));
    }

    @When(value="^\u8a72 IP \u4ee5 token \"([^\"]+)\" \u518d\u6b21\u6383\u78bc$")
    public void blockedIpScansAgain(String token) {
        this.ctx.lastResponse(this.guest.scan(token, (String)this.ctx.get("attackerIp")));
    }

    @When(value="^\u8a72 IP \u518d\u4ee5\u4e0d\u5b58\u5728\u7684 token \u6383\u78bc$")
    public void blockedIpScansUnknownToken() {
        this.ctx.lastResponse(this.guest.scan("NoSuchTokenAnywhere999", (String)this.ctx.get("attackerIp")));
    }

    private void scanAndSnapshot(String token) {
        this.ctx.put("sessionsBefore", this.db.countAll("guest_sessions"));
        this.ctx.put("tasksBefore", this.db.countAll("tasks"));
        this.ctx.lastResponse(this.guest.scan(token, null));
    }

    private void rememberTaskIdIfCreated(ScenarioContext.HttpResult created) {
        if (created.status() == 201) {
            JsonNode body = this.objectMapper.readTree(created.body());
            this.ctx.taskId(UUID.fromString(body.get("id").asText()));
        }
    }
}
