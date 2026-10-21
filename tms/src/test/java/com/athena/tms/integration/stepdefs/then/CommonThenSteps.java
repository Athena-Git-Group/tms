package com.athena.tms.integration.stepdefs.then;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;
import io.cucumber.java.en.Then;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨 feature 共用的 HTTP 回應斷言（吃 {@link ScenarioContext#lastResponse()}）。
 * 錯誤 body 一律 common ApiError 形狀 {@code {code,message}}。
 */
public class CommonThenSteps {

    private final ScenarioContext ctx;
    private final ObjectMapper objectMapper;

    public CommonThenSteps(ScenarioContext ctx, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.objectMapper = objectMapper;
    }

    @Then("^回應為 (\\d+)$")
    public void responseStatusIs(int expected) {
        HttpResult last = ctx.lastResponse();
        assertEquals(expected, last.status(), "回應 body: " + last.body());
    }

    @Then("^請求應該失敗，回應為 (\\d+)$")
    public void requestFailedWithStatus(int expected) {
        responseStatusIs(expected);
    }

    @Then("^請求應該失敗，回應為 (\\d+) 且原因是 \"([^\"]+)\"$")
    public void requestFailedWithStatusAndCode(int expected, String code) {
        HttpResult last = ctx.lastResponse();
        assertEquals(expected, last.status(), "回應 body: " + last.body());
        JsonNode body = objectMapper.readTree(last.body());
        assertNotNull(body.get("code"), "錯誤 body 該有 code 欄: " + last.body());
        assertEquals(code, body.get("code").asText());
    }

    @Then("^回應訊息含「([^」]+)」$")
    public void responseMessageContains(String fragment) {
        JsonNode body = objectMapper.readTree(ctx.lastResponse().body());
        assertNotNull(body.get("message"), "錯誤 body 該有 message 欄: " + ctx.lastResponse().body());
        assertTrue(body.get("message").asText().contains(fragment),
                "訊息該含「" + fragment + "」: " + body.get("message").asText());
    }
}
