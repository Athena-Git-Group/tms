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

    @When(value="^以 token \"([^\"]+)\" 呼叫掃碼$")
    public void scanWithToken(String token) {
        this.scanAndSnapshot(token);
    }

    @When(value="^任何人以 token \"([^\"]+)\" 掃碼$")
    public void anyoneScansToken(String token) {
        this.scanAndSnapshot(token);
    }

    @When(value="^以 session \"([^\"]+)\" 查詢本次住宿的需求$")
    public void listRequestsWithSession(String sessionSymbol) {
        this.ctx.lastResponse(this.api.get("/guest/requests", this.ctx.guestSession(sessionSymbol)));
    }

    @When(value="^新住客以 session \"([^\"]+)\" 查詢本次住宿的需求$")
    public void newGuestListsRequests(String sessionSymbol) {
        this.listRequestsWithSession(sessionSymbol);
    }

    @When(value="^以 session \"([^\"]+)\" 讀取資訊頁$")
    public void readInfoPages(String sessionSymbol) {
        this.ctx.put("tasksBeforeHome", this.db.countAll("tasks"));
        this.ctx.lastResponse(this.api.get("/guest/home", this.ctx.guestSession(sessionSymbol)));
    }

    @When(value="^住客以 session \"([^\"]+)\" 用自由文字「([^」]+)」送出需求$")
    public void createFreeTextRequest(String sessionSymbol, String text) {
        this.ctx.put("freeText", text);
        ScenarioContext.HttpResult created = this.guest.createRequest(this.ctx.guestSession(sessionSymbol), Map.of("text", text, "lang", "zh-TW"));
        this.ctx.lastResponse(created);
        this.rememberTaskIdIfCreated(created);
    }

    @When(value="^住客以 session \"([^\"]+)\" 送出 (選單項|自由文字)「([^」]+)」$")
    public void createRequestFromOutline(String sessionSymbol, String kind, String content) {
        if ("選單項".equals(kind)) {
            ScenarioContext.HttpResult created = this.guest.createRequest(this.ctx.guestSession(sessionSymbol), Map.of("itemId", GuestFixtures.menuKeyOf(content)));
            this.ctx.lastResponse(created);
            this.rememberTaskIdIfCreated(created);
        } else {
            this.createFreeTextRequest(sessionSymbol, content);
        }
    }

    @When(value="^再以 session \"([^\"]+)\" 送出第 4 筆需求$")
    public void createFourthRequest(String sessionSymbol) {
        this.ctx.lastResponse(this.guest.createRequest(this.ctx.guestSession(sessionSymbol), Map.of("itemId", "towel")));
    }

    @When(value="^住客以 session \"([^\"]+)\" 對任務 \"([^\"]+)\" 呼叫收回$")
    public void cancelTask(String sessionSymbol, String taskSymbol) {
        UUID taskId = this.ctx.entityId(taskSymbol);
        this.ctx.taskId(taskId);
        this.ctx.lastResponse(this.guest.cancelRequest(this.ctx.guestSession(sessionSymbol), taskId));
    }

    @When(value="^住客以 session \"([^\"]+)\" 對任務 \"([^\"]+)\" 評 (\\d+) 星$")
    public void rateTask(String sessionSymbol, String taskSymbol, int rating) {
        UUID taskId = this.ctx.entityId(taskSymbol);
        this.ctx.taskId(taskId);
        this.ctx.lastResponse(this.guest.createRating(this.ctx.guestSession(sessionSymbol), taskId, rating));
    }

    @When(value="^該 IP 以 token \"([^\"]+)\" 再次掃碼$")
    public void blockedIpScansAgain(String token) {
        this.ctx.lastResponse(this.guest.scan(token, (String)this.ctx.get("attackerIp")));
    }

    @When(value="^該 IP 再以不存在的 token 掃碼$")
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
