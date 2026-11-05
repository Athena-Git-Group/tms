package com.athena.tms.bdd.steps.account;

import com.athena.tms.bdd.support.BddApiClient;
import io.cucumber.java.zh_tw.當;
import java.util.Map;

/**
 * 帳號與登入句型的 When（dsl.md When 句型區）。只發動作、記錄回應，不含任何斷言——
 * 「必查」清單在 {@link AccountThenSteps}。
 */
public class AccountWhenSteps {

    private final BddApiClient apiClient;

    public AccountWhenSteps(BddApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * dsl.md：{@code 員工} 只供案例辨識，不是請求欄位——登入前還沒有身分，呼叫
     * {@code staffAccountsLogin} 不帶任何認證 header（{@link BddApiClient.Auth#none()}）。
     */
    @當("{string} 用員工編號 {string} 密碼 {string} 登入")
    public void login(String staffName, String loginId, String password) {
        Map<String, Object> body = Map.of("loginId", loginId, "password", password);
        apiClient.post("/staff-accounts/login", BddApiClient.Auth.none(), body);
    }
}
