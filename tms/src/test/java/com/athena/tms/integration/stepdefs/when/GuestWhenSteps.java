package com.athena.tms.integration.stepdefs.when;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.GuestFixtures;
import io.cucumber.java.en.When;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

/** 01-guest-requests feature 的動作步驟（token 字面值直接打 API，session 代號換真權杖）。 */
public class GuestWhenSteps {

    private final ScenarioContext ctx;
    private final ApiClient api;
    private final DbSupport db;
    private final GuestFixtures guest;
    private final ObjectMapper objectMapper;

    public GuestWhenSteps(ScenarioContext ctx, ApiClient api, DbSupport db,
                          GuestFixtures guest, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.api = api;
        this.db = db;
        this.guest = guest;
        this.objectMapper = objectMapper;
    }

    @When("^以 token \"([^\"]+)\" 呼叫掃碼$")
    public void scanWithToken(String token) {
        scanAndSnapshot(token);
    }

    @When("^任何人以 token \"([^\"]+)\" 掃碼$")
    public void anyoneScansToken(String token) {
        scanAndSnapshot(token);
    }

    @When("^以 session \"([^\"]+)\" 查詢本次住宿的需求$")
    public void listRequestsWithSession(String sessionSymbol) {
        ctx.lastResponse(api.get("/guest/requests", ctx.guestSession(sessionSymbol)));
    }

    @When("^新住客以 session \"([^\"]+)\" 查詢本次住宿的需求$")
    public void newGuestListsRequests(String sessionSymbol) {
        listRequestsWithSession(sessionSymbol);
    }

    @When("^以 session \"([^\"]+)\" 讀取資訊頁$")
    public void readInfoPages(String sessionSymbol) {
        // 「不產生任務」的基準取在讀取之前
        ctx.put("tasksBeforeHome", db.countAll("tasks"));
        ctx.lastResponse(api.get("/guest/home", ctx.guestSession(sessionSymbol)));
    }

    @When("^住客以 session \"([^\"]+)\" 用自由文字「([^」]+)」送出需求$")
    public void createFreeTextRequest(String sessionSymbol, String text) {
        ctx.put("freeText", text);
        HttpResult created = guest.createRequest(ctx.guestSession(sessionSymbol),
                Map.of("text", text, "lang", "zh-TW"));
        ctx.lastResponse(created);
        rememberTaskIdIfCreated(created);
    }

    @When("^住客以 session \"([^\"]+)\" 送出 (選單項|自由文字)「([^」]+)」$")
    public void createRequestFromOutline(String sessionSymbol, String kind, String content) {
        if ("選單項".equals(kind)) {
            HttpResult created = guest.createRequest(ctx.guestSession(sessionSymbol),
                    Map.of("itemId", GuestFixtures.menuKeyOf(content)));
            ctx.lastResponse(created);
            rememberTaskIdIfCreated(created);
        } else {
            createFreeTextRequest(sessionSymbol, content);
        }
    }

    @When("^再以 session \"([^\"]+)\" 送出第 4 筆需求$")
    public void createFourthRequest(String sessionSymbol) {
        ctx.lastResponse(guest.createRequest(ctx.guestSession(sessionSymbol),
                Map.of("itemId", "towel")));
    }

    @When("^住客以 session \"([^\"]+)\" 對任務 \"([^\"]+)\" 呼叫收回$")
    public void cancelTask(String sessionSymbol, String taskSymbol) {
        UUID taskId = ctx.entityId(taskSymbol);
        ctx.taskId(taskId);
        ctx.lastResponse(guest.cancelRequest(ctx.guestSession(sessionSymbol), taskId));
    }

    @When("^住客以 session \"([^\"]+)\" 對任務 \"([^\"]+)\" 評 (\\d+) 星$")
    public void rateTask(String sessionSymbol, String taskSymbol, int rating) {
        UUID taskId = ctx.entityId(taskSymbol);
        ctx.taskId(taskId);
        ctx.lastResponse(guest.createRating(ctx.guestSession(sessionSymbol), taskId, rating));
    }

    @When("^該 IP 以 token \"([^\"]+)\" 再次掃碼$")
    public void blockedIpScansAgain(String token) {
        ctx.lastResponse(guest.scan(token, ctx.get("attackerIp")));
    }

    @When("^該 IP 再以不存在的 token 掃碼$")
    public void blockedIpScansUnknownToken() {
        ctx.lastResponse(guest.scan("NoSuchTokenAnywhere999", ctx.get("attackerIp")));
    }

    /** 掃碼前先記 session／任務筆數，「不簽 session、不建單」的零增量斷言靠它。 */
    private void scanAndSnapshot(String token) {
        ctx.put("sessionsBefore", db.countAll("guest_sessions"));
        ctx.put("tasksBefore", db.countAll("tasks"));
        ctx.lastResponse(guest.scan(token, null));
    }

    private void rememberTaskIdIfCreated(HttpResult created) {
        if (created.status() == 201) {
            JsonNode body = objectMapper.readTree(created.body());
            ctx.taskId(UUID.fromString(body.get("id").asText()));
        }
    }
}
