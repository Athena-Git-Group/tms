package com.athena.tms.bdd.steps.account;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.StaffFixtures;
import io.cucumber.java.zh_tw.假設;
import java.util.Map;
import java.util.UUID;

public class AccountGivenSteps {

    private static final String WRONG_PASSWORD = "錯誤密碼-account-given";

    private final ScenarioWorld world;
    private final StaffFixtures staffFixtures;
    private final BddApiClient apiClient;

    public AccountGivenSteps(ScenarioWorld world, StaffFixtures staffFixtures, BddApiClient apiClient) {
        this.world = world;
        this.staffFixtures = staffFixtures;
        this.apiClient = apiClient;
    }

    @假設("{string} 的帳號是臨時密碼")
    public void staffAccountIsTemporaryPassword(String staffName) {
        staffFixtures.markMustChangePassword(UUID.fromString(world.staffId(staffName)));
    }

    // Cucumber Expression 裡裸 / 是 alternation 語法，照字面比對斜線必須寫成 \/
    @假設("{string} 的員工編號密碼為 {string} \\/ {string}")
    public void staffHasCredentials(String staffName, String loginId, String password) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        staffFixtures.setCredentials(staffId, loginId, password);
    }

    @假設("{string} 已連續輸入錯誤密碼 {int} 次")
    public void staffFailedLoginCount(String staffName, int times) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        String loginId = staffFixtures.loginId(staffId);
        Map<String, Object> body = Map.of("loginId", loginId, "password", WRONG_PASSWORD);
        for (int i = 0; i < times; i++) {
            apiClient.post("/staff-accounts/login", BddApiClient.Auth.none(), body);
        }
    }
}
