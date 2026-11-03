/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.cucumber.java.en.Then
 *  org.junit.jupiter.api.Assertions
 *  tools.jackson.databind.JsonNode
 *  tools.jackson.databind.ObjectMapper
 */
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

    @Then(value="^\u56de\u61c9\u70ba (\\d+)$")
    public void responseStatusIs(int expected) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)expected, (int)last.status(), (String)("\u56de\u61c9 body: " + last.body()));
    }

    @Then(value="^\u8acb\u6c42\u61c9\u8a72\u5931\u6557\uff0c\u56de\u61c9\u70ba (\\d+)$")
    public void requestFailedWithStatus(int expected) {
        this.responseStatusIs(expected);
    }

    @Then(value="^\u8acb\u6c42\u61c9\u8a72\u5931\u6557\uff0c\u56de\u61c9\u70ba (\\d+) \u4e14\u539f\u56e0\u662f \"([^\"]+)\"$")
    public void requestFailedWithStatusAndCode(int expected, String code) {
        ScenarioContext.HttpResult last = this.ctx.lastResponse();
        Assertions.assertEquals((int)expected, (int)last.status(), (String)("\u56de\u61c9 body: " + last.body()));
        JsonNode body = this.objectMapper.readTree(last.body());
        Assertions.assertNotNull((Object)body.get("code"), (String)("\u932f\u8aa4 body \u8a72\u6709 code \u6b04: " + last.body()));
        Assertions.assertEquals((Object)code, (Object)body.get("code").asText());
    }

    @Then(value="^\u56de\u61c9\u8a0a\u606f\u542b\u300c([^\u300d]+)\u300d$")
    public void responseMessageContains(String fragment) {
        JsonNode body = this.objectMapper.readTree(this.ctx.lastResponse().body());
        Assertions.assertNotNull((Object)body.get("message"), (String)("\u932f\u8aa4 body \u8a72\u6709 message \u6b04: " + this.ctx.lastResponse().body()));
        Assertions.assertTrue((boolean)body.get("message").asText().contains(fragment), (String)("\u8a0a\u606f\u8a72\u542b\u300c" + fragment + "\u300d: " + body.get("message").asText()));
    }
}
