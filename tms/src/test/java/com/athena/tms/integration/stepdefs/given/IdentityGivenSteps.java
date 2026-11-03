/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.cucumber.java.en.Given
 *  org.junit.jupiter.api.Assertions
 *  tools.jackson.databind.ObjectMapper
 */
package com.athena.tms.integration.stepdefs.given;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import io.cucumber.java.en.Given;
import org.junit.jupiter.api.Assertions;
import tools.jackson.databind.ObjectMapper;

public class IdentityGivenSteps {
    private final ScenarioContext ctx;
    private final ApiClient api;
    private final DbSupport db;
    private final ObjectMapper objectMapper;

    public IdentityGivenSteps(ScenarioContext ctx, ApiClient api, DbSupport db, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.api = api;
        this.db = db;
        this.objectMapper = objectMapper;
    }

    @Given(value="^\u300c\u963f\u5f37\u300d\u7684\u5e33\u865f active \u70ba false$")
    public void deactivateMt1AfterCapturingOldToken() {
        ScenarioContext.HttpResult login = this.api.loginRaw(1L, this.ctx.staffLogin("\u963f\u5f37"), "demo1234", null, null);
        Assertions.assertEquals((int)200, (int)login.status(), (String)("\u505c\u7528\u524d\u7684\u767b\u5165\u8a72\u6210\u529f: " + login.body()));
        this.ctx.put("oldToken", this.objectMapper.readTree(login.body()).get("token").asText());
        this.db.admin().update("UPDATE staff_accounts SET active = false WHERE tenant_id = 1 AND login_id = ?", new Object[]{this.ctx.staffLogin("\u963f\u5f37")});
    }
}
