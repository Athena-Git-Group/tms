package com.athena.tms.bdd.steps.account;

import com.athena.tms.bdd.support.BddApiClient;
import io.cucumber.java.zh_tw.當;
import java.util.Map;

public class AccountWhenSteps {

    private final BddApiClient apiClient;

    public AccountWhenSteps(BddApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @當("{string} 用員工編號 {string} 密碼 {string} 登入")
    public void login(String staffName, String loginId, String password) {
        Map<String, Object> body = Map.of("loginId", loginId, "password", password);
        apiClient.post("/staff-accounts/login", BddApiClient.Auth.none(), body);
    }
}
