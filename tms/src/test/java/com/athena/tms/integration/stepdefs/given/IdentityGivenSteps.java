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

    @Given(value="^「阿強」的帳號 active 為 false$")
    public void deactivateMt1AfterCapturingOldToken() {
        ScenarioContext.HttpResult login = this.api.loginRaw(1L, this.ctx.staffLogin("阿強"), "demo1234", null, null);
        Assertions.assertEquals((int)200, (int)login.status(), (String)("停用前的登入該成功: " + login.body()));
        this.ctx.put("oldToken", this.objectMapper.readTree(login.body()).get("token").asText());
        this.db.admin().update("UPDATE staff_accounts SET active = false WHERE tenant_id = 1 AND login_id = ?", new Object[]{this.ctx.staffLogin("阿強")});
    }
}
