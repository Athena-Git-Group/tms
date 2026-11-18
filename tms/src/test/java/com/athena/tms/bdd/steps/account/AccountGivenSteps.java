package com.athena.tms.bdd.steps.account;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.StaffFixtures;
import com.athena.tms.bdd.support.TenantFixtures;
import io.cucumber.java.zh_tw.假設;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

public class AccountGivenSteps {

    private static final String WRONG_PASSWORD = "錯誤密碼-account-given";

    private static final String LOGIN_PATH = "/staff-accounts/login";

    private static final String CHANGE_PASSWORD_PATH = "/staff-accounts/change-password";

    private static final String FRONTDESK_WEB_CLIENT = "frontdesk_web";

    private final ScenarioWorld world;
    private final StaffFixtures staffFixtures;
    private final TenantFixtures tenantFixtures;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final PasswordEncoder passwordEncoder;

    public AccountGivenSteps(
            ScenarioWorld world,
            StaffFixtures staffFixtures,
            TenantFixtures tenantFixtures,
            BddApiClient apiClient,
            DbProbe dbProbe,
            PasswordEncoder passwordEncoder) {
        this.world = world;
        this.staffFixtures = staffFixtures;
        this.tenantFixtures = tenantFixtures;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.passwordEncoder = passwordEncoder;
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
        world.rememberStaffPassword(staffName, password);
    }

    @假設("{string} 已連續輸入錯誤密碼 {int} 次")
    public void staffFailedLoginCount(String staffName, int times) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        String loginId = staffFixtures.loginId(staffId);
        Map<String, Object> body = Map.of("loginId", loginId, "password", WRONG_PASSWORD);
        for (int i = 0; i < times; i++) {
            apiClient.post(LOGIN_PATH, BddApiClient.Auth.none(), body);
        }
    }

    @假設("{string} 已用臨時密碼從櫃檯畫面登入，還沒設定新密碼")
    public void loggedInFromFrontdeskWithTemporaryPassword(String staffName) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        String temporaryPassword = currentPassword(staffName);
        Map<String, Object> body = Map.of(
                "loginId", staffFixtures.loginId(staffId),
                "password", temporaryPassword,
                "client", FRONTDESK_WEB_CLIENT);

        ScenarioWorld.ApiResponse resp = apiClient.post(LOGIN_PATH, BddApiClient.Auth.none(), body);
        String token = requireTemporaryLoginToken(staffName, resp);
        if (!mustChangePasswordInDb(staffId)) {
            throw new AssertionError(
                    "「" + staffName + "」用臨時密碼登入之後，staff_accounts.must_change_password 預期仍為 true，實際為 false");
        }

        world.rememberStaff(staffName, staffId.toString(), token);
        world.rememberTemporaryPassword(staffName, temporaryPassword);
    }

    @假設("{string} 已把臨時密碼改成 {string}")
    public void changedTemporaryPassword(String staffName, String newPassword) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        String temporaryPassword = currentPassword(staffName);
        Map<String, Object> loginBody = Map.of(
                "loginId", staffFixtures.loginId(staffId),
                "password", temporaryPassword);
        ScenarioWorld.ApiResponse login = apiClient.post(LOGIN_PATH, BddApiClient.Auth.none(), loginBody);
        String temporaryToken = requireTemporaryLoginToken(staffName, login);

        ScenarioWorld.ApiResponse changed = apiClient.post(
                CHANGE_PASSWORD_PATH,
                BddApiClient.Auth.staff(temporaryToken),
                Map.of("newPassword", newPassword));
        if (changed.status() != 200) {
            throw new AssertionError(
                    "「" + staffName + "」設定新密碼預期成功（200），實際 status=" + changed.status()
                            + "，body=" + changed.body());
        }
        String issuedToken = textOrNull(changed.json(), "token");
        if (issuedToken == null || issuedToken.isBlank()) {
            throw new AssertionError("「" + staffName + "」設定新密碼的回應預期帶換發的 token，實際 body=" + changed.body());
        }

        Map<String, Object> account = staffAccountRow(staffId);
        if (!passwordEncoder.matches(newPassword, (String) account.get("password_hash"))) {
            throw new AssertionError("「" + staffName + "」設定新密碼之後，password_hash 預期對得上「" + newPassword + "」");
        }
        if (Boolean.TRUE.equals(account.get("must_change_password"))) {
            throw new AssertionError(
                    "「" + staffName + "」設定新密碼之後，staff_accounts.must_change_password 預期為 false，實際為 true");
        }

        world.rememberStaff(staffName, staffId.toString(), issuedToken);
        world.rememberTemporaryPassword(staffName, temporaryPassword);
        world.rememberStaffPassword(staffName, newPassword);
    }

    @假設("有一家飯店的識別代碼是 {string}，名稱是 {string}")
    public void hotelWithCode(String code, String name) {
        long tenantId = tenantFixtures.createTenant(name, code);
        world.rememberTenant(code, tenantId);
    }

    @假設("飯店 {string} 有房務員工 {string}，員工編號密碼為 {string} \\/ {string}")
    public void housekeeperInHotel(String code, String staffName, String loginId, String password) {
        StaffFixtures.CreatedStaff created =
                staffFixtures.createHousekeeperInTenant(world.tenantId(code), staffName, loginId, password);
        world.rememberStaff(staffName, created.id().toString(), created.jwt());
        world.rememberStaffPassword(staffName, password);
    }

    private String currentPassword(String staffName) {
        return world.staffPassword(staffName).orElse(StaffFixtures.DEFAULT_PASSWORD);
    }

    private static String requireTemporaryLoginToken(String staffName, ScenarioWorld.ApiResponse resp) {
        if (resp.status() != 200) {
            throw new AssertionError(
                    "「" + staffName + "」用臨時密碼登入預期回 200，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        JsonNode body = resp.json();
        if (body == null || !body.path("mustChangePassword").asBoolean(false)) {
            throw new AssertionError(
                    "「" + staffName + "」用臨時密碼登入的回應預期 mustChangePassword=true，實際 body=" + resp.body());
        }
        String token = textOrNull(body, "token");
        if (token == null || token.isBlank()) {
            throw new AssertionError("「" + staffName + "」用臨時密碼登入的回應預期帶 token，實際 body=" + resp.body());
        }
        return token;
    }

    private boolean mustChangePasswordInDb(UUID staffId) {
        return Boolean.TRUE.equals(staffAccountRow(staffId).get("must_change_password"));
    }

    private Map<String, Object> staffAccountRow(UUID staffId) {
        return dbProbe
                .queryOne(
                        "SELECT password_hash, must_change_password FROM staff_accounts WHERE id = ?", staffId)
                .orElseThrow(() -> new IllegalStateException("staff_accounts 找不到 id=" + staffId));
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
