package com.athena.tms.bdd.steps.account;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.StaffFixtures;
import io.cucumber.java.zh_tw.假設;
import java.util.Map;
import java.util.UUID;

/**
 * 帳號與登入句型的 Given（dsl.md Given 句型區）。只建立/確認權威狀態，不含任何斷言——
 * 「必查」清單在 {@link AccountThenSteps}。
 */
public class AccountGivenSteps {

    /**
     * dsl.md「"{員工}" 已連續輸入錯誤密碼 {次數} 次」用的固定錯誤密碼：只要保證跟這位員工
     * 真正的密碼不同即可，不需要每次隨機——這個 Given 的「怎麼做」是真的呼叫
     * {@code staffAccountsLogin} N 次累積失敗次數，不是直接改資料庫欄位。
     */
    private static final String WRONG_PASSWORD = "錯誤密碼-account-given";

    private final ScenarioWorld world;
    private final StaffFixtures staffFixtures;
    private final BddApiClient apiClient;

    public AccountGivenSteps(ScenarioWorld world, StaffFixtures staffFixtures, BddApiClient apiClient) {
        this.world = world;
        this.staffFixtures = staffFixtures;
        this.apiClient = apiClient;
    }

    /**
     * dsl.md「"{員工}" 的帳號是臨時密碼」：怎麼做＝把該員工帳號標記為需要強制改密。
     * 權威狀態落地在 {@code staff_accounts.must_change_password}（V15 新增欄位），
     * 沒有對應的 admin API，直接落地權威狀態（同檔其他 Given 的慣例）。
     */
    @假設("{string} 的帳號是臨時密碼")
    public void staffAccountIsTemporaryPassword(String staffName) {
        staffFixtures.markMustChangePassword(UUID.fromString(world.staffId(staffName)));
    }

    /**
     * dsl.md「"{員工}" 的員工編號密碼為 "{員工編號}" / "{密碼}"」：把該員工帳號的
     * {@code login_id} 與密碼雜湊設定為指定值，供後續用這組帳密呼叫 {@code staffAccountsLogin}。
     */
    // Cucumber Expression 裡裸 / 是 alternation 語法，照字面比對斜線必須寫成 \/
    @假設("{string} 的員工編號密碼為 {string} \\/ {string}")
    public void staffHasCredentials(String staffName, String loginId, String password) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        staffFixtures.setCredentials(staffId, loginId, password);
    }

    /**
     * dsl.md「"{員工}" 已連續輸入錯誤密碼 {次數} 次」：怎麼做＝用錯誤密碼呼叫
     * {@code staffAccountsLogin} {次數} 次，讓系統依正常登入路徑自己累積失敗次數
     * （{@code staff_accounts.failed_login_count}，V15 新增欄位），不是直接改資料庫欄位。
     * 用這位員工目前的 {@code login_id}（通常由同模組的「的員工編號密碼為」Given 先落地）。
     */
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
