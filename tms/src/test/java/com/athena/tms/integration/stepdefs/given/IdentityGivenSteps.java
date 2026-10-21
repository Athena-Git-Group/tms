package com.athena.tms.integration.stepdefs.given;

import com.athena.tms.integration.stepdefs.ScenarioContext;
import com.athena.tms.integration.stepdefs.ScenarioContext.HttpResult;
import com.athena.tms.integration.stepdefs.utils.ApiClient;
import com.athena.tms.integration.stepdefs.utils.DbSupport;
import com.athena.tms.integration.stepdefs.utils.TestData;
import io.cucumber.java.en.Given;
import tools.jackson.databind.ObjectMapper;

import static com.athena.tms.integration.stepdefs.utils.TestData.TENANT_1;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 05-identity-tenancy feature 的前置步驟（帳號與房間表格由 EntityTableGivenSteps 處理）。 */
public class IdentityGivenSteps {

    private final ScenarioContext ctx;
    private final ApiClient api;
    private final DbSupport db;
    private final ObjectMapper objectMapper;

    public IdentityGivenSteps(ScenarioContext ctx, ApiClient api, DbSupport db,
                              ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.api = api;
        this.db = db;
        this.objectMapper = objectMapper;
    }

    @Given("^「阿強」的帳號 active 為 false$")
    public void deactivateMt1AfterCapturingOldToken() {
        // 先拿「停用前簽發」的舊憑證（loginRaw 不進 token 快取，不污染其他場景）
        HttpResult login = api.loginRaw(TENANT_1, ctx.staffLogin("阿強"), TestData.PASSWORD,
                null, null);
        assertEquals(200, login.status(), "停用前的登入該成功: " + login.body());
        ctx.put("oldToken", objectMapper.readTree(login.body()).get("token").asText());
        db.admin().update("UPDATE staff_accounts SET active = false"
                + " WHERE tenant_id = 1 AND login_id = ?", ctx.staffLogin("阿強"));
        // 還原由 hooks 的開場清理負責（UPDATE active=true），場景中途炸掉也不污染下一景
    }
}
