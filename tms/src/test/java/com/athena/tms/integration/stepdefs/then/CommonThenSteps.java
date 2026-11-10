package com.athena.tms.integration.stepdefs.then;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import io.cucumber.java.en.Then;
import org.junit.jupiter.api.Assertions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class CommonThenSteps {
    private final ScenarioContext ctx;
    private final ObjectMapper objectMapper;

    public CommonThenSteps(ScenarioContext ctx, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.objectMapper = objectMapper;
    }

    @Then(value="^回應為 (\\d+)$")
    public void responseStatusIs(int expected) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)expected, (int)last.status(), (String)("回應 body: " + last.body()));
    }

    @Then(value="^請求應該失敗，回應為 (\\d+)$")
    public void requestFailedWithStatus(int expected) {
        this.responseStatusIs(expected);
    }

    @Then(value="^請求應該失敗，回應為 (\\d+) 且原因是 \"([^\"]+)\"$")
    public void requestFailedWithStatusAndCode(int expected, String code) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)expected, (int)last.status(), (String)("回應 body: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Assertions.assertNotNull((Object)body.get("code"), (String)("錯誤 body 該有 code 欄: " + last.body()));
        Assertions.assertEquals((Object)code, (Object)body.get("code").asText());
    }

    @Then(value="^回應訊息含「([^」]+)」$")
    public void responseMessageContains(String fragment) {
        JsonNode body = this.objectMapper.readTree(this.ctx.lastResponse().body());
        Assertions.assertNotNull((Object)body.get("message"), (String)("錯誤 body 該有 message 欄: " + this.ctx.lastResponse().body()));
        Assertions.assertTrue((boolean)body.get("message").asText().contains(fragment), (String)("訊息該含「" + fragment + "」: " + body.get("message").asText()));
    }
}
