package com.athena.tms.bdd.steps.account;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import com.athena.tms.bdd.support.TenantFixtures;
import com.athena.tms.bdd.support.Terms;
import io.cucumber.java.zh_tw.那麼;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

public class AccountThenSteps {

    private static final String LOGIN_PATH = "/staff-accounts/login";
    private static final String PUBLIC_HOTEL_PATH_PREFIX = "/public/hotels/";
    private static final String DESK_BOARD_PATH = "/desk/board";
    private static final String STAFF_TODAY_PATH = "/me/today";

    /** 和「密碼錯誤」比對時故意送錯的密碼；只要不可能是任何案例真的設過的密碼就好。 */
    private static final String PROBE_WRONG_PASSWORD = "錯誤密碼-account-then";

    /** 和「查不到」比對時用的代碼：格式合法、不是保留字，但不會有飯店用它。 */
    private static final String PROBE_UNKNOWN_HOTEL_CODE = "nohotel-zz9";

    private static final Map<String, String> PASSWORD_RULE = Map.of(
            "至少 8 碼", "min_length",
            "包含英文字母", "letter_required",
            "包含數字", "digit_required");

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;
    private final TenantFixtures tenantFixtures;
    private final PasswordEncoder passwordEncoder;

    public AccountThenSteps(
            ScenarioWorld world,
            BddApiClient apiClient,
            DbProbe dbProbe,
            TenantFixtures tenantFixtures,
            PasswordEncoder passwordEncoder) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
        this.tenantFixtures = tenantFixtures;
        this.passwordEncoder = passwordEncoder;
    }

    @那麼("登入成功，取得 {string} 的身分與可用憑證")
    public void loginSucceedsWithIdentityAndCredential(String staffName) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode body = requireStatusWithBody(resp, 200, "登入成功");

        UUID staffId = UUID.fromString(world.staffId(staffName));
        Map<String, Object> staffRow = staffRow(staffId, staffName);

        check("LoginResponse.staffId", staffId.toString(), textOrNull(body, "staffId"));
        check("LoginResponse.name", staffRow.get("name"), textOrNull(body, "name"));
        check("LoginResponse.role", staffRow.get("role"), textOrNull(body, "role"));
        check("LoginResponse.mustChangePassword", false, body.path("mustChangePassword").asBoolean(true));
        requireCredential(body, "登入成功");

        // 登入只換發憑證，不建帳號、不改帳號：員工編號仍只對到這一個人，臨時密碼標記與啟用狀態都維持原樣。
        long sameLoginId = dbProbe.count(
                "SELECT count(*) FROM staff_accounts WHERE tenant_id = ? AND login_id = ?",
                staffRow.get("tenant_id"), staffRow.get("login_id"));
        check("staff_accounts 同一員工編號的帳號數", 1L, sameLoginId);
        check("staff_accounts.must_change_password", false, staffRow.get("must_change_password"));
        check("staff_accounts.active", true, staffRow.get("active"));
    }

    @那麼("登入失敗，提示 {string}")
    public void loginFailedWithHint(String hint) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode json = requireError(resp, 401, "credentials_invalid", "登入失敗");
        check("登入失敗的提示 message", hint, textOrNull(json, "message"));
        requireNoCredential(json, "登入失敗");

        // 員工編號對得到帳號時，這次錯誤要記進連續錯誤次數；對不到帳號時沒有地方可記，不檢查。
        // 登入帶了飯店代碼就看那一家的帳號，沒帶就看「測試飯店」的。
        String loginId = lastLoginField("loginId");
        Optional<Long> tenantId = loginTargetTenantId();
        Optional<Map<String, Object>> account = tenantId.flatMap(id -> dbProbe.queryOne(
                "SELECT failed_login_count FROM staff_accounts WHERE tenant_id = ? AND login_id = ?",
                id, loginId));
        if (account.isPresent()) {
            int failed = ((Number) account.get().get("failed_login_count")).intValue();
            if (failed < 1) {
                throw new AssertionError("員工編號「" + loginId + "」密碼錯誤後連續錯誤次數預期至少 1，實際=" + failed);
            }
        }
    }

    @那麼("{string} 的帳號已鎖定 {int} 分鐘")
    public void accountLocked(String staffName, int minutes) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode json = requireError(resp, 429, "account_locked", "「" + staffName + "」帳號鎖定期間登入");
        requireNoCredential(json, "帳號鎖定期間");

        JsonNode context = json.path("context");
        String lockedUntilText = textOrNull(context, "lockedUntil");
        if (lockedUntilText == null) {
            throw new AssertionError("鎖定回應的 context 預期帶 lockedUntil，實際 body=" + resp.body());
        }
        JsonNode remaining = context.path("remainingMinutes");
        if (!remaining.isNumber() || remaining.asInt() < 1 || remaining.asInt() > minutes) {
            throw new AssertionError(
                    "鎖定回應的 context.remainingMinutes 預期介於 1 到 " + minutes + "，實際 body=" + resp.body());
        }

        UUID staffId = UUID.fromString(world.staffId(staffName));
        Object lockedUntilValue = staffRow(staffId, staffName).get("locked_until");
        if (lockedUntilValue == null) {
            throw new AssertionError("預期「" + staffName + "」staff_accounts.locked_until 非 NULL，實際=null");
        }
        Instant lockedUntil = toInstant(lockedUntilValue);

        // 鎖定到期時間是最後一次錯誤後的指定分鐘數。登入用的是真實時間，所以和「現在」比、容許兩分鐘誤差。
        Instant expected = Instant.now().plus(Duration.ofMinutes(minutes));
        if (Duration.between(expected, lockedUntil).abs().compareTo(Duration.ofMinutes(2)) > 0) {
            throw new AssertionError(
                    "預期「" + staffName + "」staff_accounts.locked_until≈" + expected + "（±2 分鐘），實際=" + lockedUntil);
        }
        Instant reported = OffsetDateTime.parse(lockedUntilText).toInstant();
        if (Duration.between(reported, lockedUntil).abs().compareTo(Duration.ofSeconds(1)) > 0) {
            throw new AssertionError(
                    "鎖定回應的 context.lockedUntil=" + reported + " 與 staff_accounts.locked_until=" + lockedUntil + " 不一致");
        }
    }

    @那麼("{string} 的帳號已停用，登入被拒")
    public void accountDeactivatedLoginRejected(String staffName) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode json = requireError(resp, 403, "account_disabled", "「" + staffName + "」帳號已停用時登入");
        requireNoCredential(json, "帳號已停用");

        UUID staffId = UUID.fromString(world.staffId(staffName));
        Object activeValue = staffRow(staffId, staffName).get("active");
        if (Boolean.TRUE.equals(activeValue)) {
            throw new AssertionError("預期「" + staffName + "」staff_accounts.active=false，實際=" + activeValue);
        }
    }

    @那麼("{string} 必須先設定新密碼才能繼續")
    public void mustChangePasswordBeforeProceeding(String staffName) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode body = requireStatusWithBody(resp, 200, "「" + staffName + "」臨時密碼登入");
        check("LoginResponse.mustChangePassword", true, body.path("mustChangePassword").asBoolean(false));
        String token = requireCredential(body, "「" + staffName + "」臨時密碼登入");

        // 改密之前這把憑證只能拿去改密碼，其他要身分的端點一律 403。櫃檯看板與員工端點各打一支，兩種介面都涵蓋到。
        for (String path : List.of(DESK_BOARD_PATH, STAFF_TODAY_PATH)) {
            ScenarioWorld.ApiResponse rejected = apiClient.get(path, BddApiClient.Auth.staff(token));
            if (rejected.status() != 403) {
                throw new AssertionError(
                        "「" + staffName + "」完成改密前用這組憑證呼叫 " + path + " 預期 403，實際 status="
                                + rejected.status() + "，body=" + rejected.body());
            }
        }
    }

    @那麼("登入回應帶員工編號 {string} 與飯店名稱 {string}")
    public void loginResponseCarriesLoginIdAndHotelName(String loginId, String hotelName) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode body = requireStatusWithBody(resp, 200, "登入成功");
        check("LoginResponse.loginId", loginId, textOrNull(body, "loginId"));
        check("LoginResponse.hotelName", hotelName, textOrNull(body, "hotelName"));

        String staffIdText = textOrNull(body, "staffId");
        if (staffIdText == null) {
            throw new AssertionError("登入回應沒有 staffId，無法核對帳號資料，body=" + resp.body());
        }
        Map<String, Object> row = dbProbe
                .queryOne(
                        "SELECT s.login_id, t.name AS hotel_name FROM staff_accounts s"
                                + " JOIN tenants t ON t.id = s.tenant_id WHERE s.id = ?",
                        UUID.fromString(staffIdText))
                .orElseThrow(() -> new AssertionError("staff_accounts 查無登入回應的 staffId=" + staffIdText));
        check("staff_accounts.login_id", row.get("login_id"), textOrNull(body, "loginId"));
        check("tenants.name", row.get("hotel_name"), textOrNull(body, "hotelName"));
    }

    @那麼("登入失敗，回應與密碼錯誤時完全相同，沒有發出憑證")
    public void loginRejectedExactlyLikeWrongPassword() {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode json = requireError(resp, 401, "credentials_invalid", "被擋下的登入");
        requireNoCredential(json, "被擋下的登入");
        for (String leak : List.of("role", "staffId", "name", "loginId", "hotelName", "mustChangePassword")) {
            if (json.has(leak)) {
                throw new AssertionError("被擋下的登入回應不該帶 " + leak + "，實際 body=" + resp.body());
            }
        }

        // 拿同一組員工編號、同一個登入來源，故意送錯密碼再登入一次，兩份回應必須整份相同，才看不出密碼其實是對的。
        Map<String, Object> wrongPassword = new LinkedHashMap<>();
        wrongPassword.put("loginId", lastLoginField("loginId"));
        wrongPassword.put("password", PROBE_WRONG_PASSWORD);
        Object client = lastLoginBody().get("client");
        if (client != null) {
            wrongPassword.put("client", client);
        }
        ScenarioWorld.ApiResponse reference = apiClient.post(LOGIN_PATH, BddApiClient.Auth.none(), wrongPassword);
        if (reference.status() != resp.status() || !Objects.equals(reference.json(), json)) {
            throw new AssertionError(
                    "被擋下的登入回應預期與密碼錯誤時完全相同。被擋下：status=" + resp.status() + "，body=" + resp.body()
                            + "；密碼錯誤：status=" + reference.status() + "，body=" + reference.body());
        }
    }

    @那麼("{string} 的連續錯誤次數仍是 {int} 次，帳號沒有被鎖定")
    public void failedCountUnchangedAndNotLocked(String staffName, int times) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        Map<String, Object> row = staffRow(staffId, staffName);
        check("「" + staffName + "」staff_accounts.failed_login_count", times,
                ((Number) row.get("failed_login_count")).intValue());
        if (row.get("locked_until") != null) {
            throw new AssertionError(
                    "預期「" + staffName + "」沒有被鎖定，實際 staff_accounts.locked_until=" + row.get("locked_until"));
        }

        // 被擋下的那次送的就是正確密碼；不帶登入來源再用它登入一次，要能照常登入。
        Map<String, Object> plainLogin = Map.of(
                "loginId", String.valueOf(row.get("login_id")),
                "password", lastLoginField("password"));
        ScenarioWorld.ApiResponse again = apiClient.post(LOGIN_PATH, BddApiClient.Auth.none(), plainLogin);
        if (again.status() != 200) {
            throw new AssertionError(
                    "「" + staffName + "」不帶登入來源用正確密碼登入預期 200，實際 status=" + again.status()
                            + "，body=" + again.body());
        }
    }

    // 只讀資料庫、不再登入，所以可以接在其他 Then 後面，不會改到上一次登入的回應。
    @那麼("{string} 的連續錯誤次數是 {int} 次")
    public void failedCountIs(String staffName, int times) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        check("「" + staffName + "」staff_accounts.failed_login_count", times,
                ((Number) staffRow(staffId, staffName).get("failed_login_count")).intValue());
    }

    @那麼("登入成功，標示必須先設定新密碼，角色為 {string}")
    public void loginSucceedsMustChangePasswordWithRole(String roleTerm) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        if (resp.status() == 401) {
            throw new AssertionError("臨時密碼登入不該被當成密碼錯誤擋下，實際 body=" + resp.body());
        }
        JsonNode body = requireStatusWithBody(resp, 200, "臨時密碼登入");
        check("LoginResponse.mustChangePassword", true, body.path("mustChangePassword").asBoolean(false));
        check("LoginResponse.role", roleCode(roleTerm), textOrNull(body, "role"));
        requireToken(body, "臨時密碼登入");
    }

    @那麼("新密碼設定成功，{string} 不必再登入就拿到可用的憑證")
    public void newPasswordSetWithFreshCredential(String staffName) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode body = requireStatusWithBody(resp, 200, "「" + staffName + "」設定新密碼");
        UUID staffId = UUID.fromString(world.staffId(staffName));
        check("LoginResponse.staffId", staffId.toString(), textOrNull(body, "staffId"));
        check("LoginResponse.mustChangePassword", false, body.path("mustChangePassword").asBoolean(true));
        String token = requireCredential(body, "設定新密碼");

        check("「" + staffName + "」staff_accounts.must_change_password", false,
                staffRow(staffId, staffName).get("must_change_password"));

        ScenarioWorld.ApiResponse board = apiClient.get(DESK_BOARD_PATH, BddApiClient.Auth.staff(token));
        if (board.status() != 200) {
            throw new AssertionError(
                    "用設定新密碼換發的憑證讀櫃檯看板預期 200，實際 status=" + board.status() + "，body=" + board.body());
        }
    }

    @那麼("{string} 的密碼已是 {string}，原本的臨時密碼登不進去，之後登入不再要求設定新密碼")
    public void passwordReplacedAndTemporaryPasswordRetired(String staffName, String password) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        String temporaryPassword = world.temporaryPassword(staffName);
        Map<String, Object> row = staffRow(staffId, staffName);
        String hash = String.valueOf(row.get("password_hash"));
        if (!passwordEncoder.matches(password, hash)) {
            throw new AssertionError("「" + staffName + "」staff_accounts.password_hash 預期對得上「" + password + "」，實際對不上");
        }
        if (passwordEncoder.matches(temporaryPassword, hash)) {
            throw new AssertionError("「" + staffName + "」staff_accounts.password_hash 仍對得上原本的臨時密碼");
        }
        check("「" + staffName + "」staff_accounts.must_change_password", false, row.get("must_change_password"));

        String loginId = String.valueOf(row.get("login_id"));
        ScenarioWorld.ApiResponse withTemporary = apiClient.post(
                LOGIN_PATH, BddApiClient.Auth.none(), Map.of("loginId", loginId, "password", temporaryPassword));
        requireError(withTemporary, 401, "credentials_invalid", "「" + staffName + "」用原本的臨時密碼登入");

        ScenarioWorld.ApiResponse withNew = apiClient.post(
                LOGIN_PATH, BddApiClient.Auth.none(), Map.of("loginId", loginId, "password", password));
        JsonNode body = requireStatusWithBody(withNew, 200, "「" + staffName + "」用新密碼登入");
        check("用新密碼登入的 mustChangePassword", false, body.path("mustChangePassword").asBoolean(true));
    }

    @那麼("這次設定新密碼被拒絕，沒過的規則是 {string}")
    public void newPasswordRejectedByRule(String ruleTerm) {
        String rule = PASSWORD_RULE.get(ruleTerm);
        if (rule == null) {
            throw new IllegalArgumentException("找不到密碼規則「" + ruleTerm + "」的對照，只認得 " + PASSWORD_RULE.keySet());
        }
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode json = requireError(resp, 422, "new_password_too_weak", "設定新密碼");
        JsonNode failedRules = json.path("context").path("failedRules");
        List<String> actual = new ArrayList<>();
        if (failedRules.isArray()) {
            for (JsonNode item : failedRules) {
                actual.add(item.asText());
            }
        }
        check("context.failedRules", List.of(rule), actual);
    }

    @那麼("這次設定新密碼被拒絕，因為新密碼和目前的密碼相同")
    public void newPasswordRejectedAsSameAsCurrent() {
        requireError(world.lastResponse(), 422, "new_password_invalid", "設定新密碼");
    }

    @那麼("{string} 的密碼仍是原本的臨時密碼，仍然必須先設定新密碼")
    public void passwordStillTemporary(String staffName) {
        UUID staffId = UUID.fromString(world.staffId(staffName));
        Map<String, Object> row = staffRow(staffId, staffName);
        // 雜湊每次重算都不同，所以用「仍對得上臨時密碼」來確認這次請求沒有改到密碼。
        if (!passwordEncoder.matches(world.temporaryPassword(staffName), String.valueOf(row.get("password_hash")))) {
            throw new AssertionError("「" + staffName + "」staff_accounts.password_hash 預期仍是原本的臨時密碼，實際已被改掉");
        }
        check("「" + staffName + "」staff_accounts.must_change_password", true, row.get("must_change_password"));
    }

    @那麼("設定新密碼的回應帶的角色是 {string}")
    public void changePasswordResponseCarriesRole(String roleTerm) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode body = requireStatusWithBody(resp, 200, "設定新密碼");
        check("LoginResponse.role", roleCode(roleTerm), textOrNull(body, "role"));
        check("LoginResponse.mustChangePassword", false, body.path("mustChangePassword").asBoolean(true));
    }

    @那麼("查到的飯店名稱是 {string}，回應沒有其他資料")
    public void publicHotelNameOnly(String name) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode body = requireStatusWithBody(resp, 200, "查飯店名稱");
        if (body.size() != 1 || !body.has("name")) {
            throw new AssertionError("查飯店名稱的回應預期只有 name 一個欄位，實際 body=" + resp.body());
        }
        check("PublicHotel.name", name, textOrNull(body, "name"));

        String code = lastPublicHotelCode();
        Object tenantName = dbProbe
                .queryOne("SELECT name FROM tenants WHERE code = ?", code)
                .orElseThrow(() -> new AssertionError("tenants 查無識別代碼「" + code + "」"))
                .get("name");
        check("tenants.name", tenantName, textOrNull(body, "name"));
    }

    @那麼("查不到這家飯店")
    public void publicHotelNotFound() {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        JsonNode json = requireError(resp, 404, "not_found", "查飯店名稱");
        if (json.has("name")) {
            throw new AssertionError("查不到飯店時回應不該帶飯店資料，實際 body=" + resp.body());
        }

        // 不同原因要回一模一樣的內容，才看不出是哪一種：拿一個格式合法但沒有飯店用的代碼再查一次，兩份回應必須整份相同。
        ScenarioWorld.ApiResponse reference =
                apiClient.get(PUBLIC_HOTEL_PATH_PREFIX + PROBE_UNKNOWN_HOTEL_CODE, BddApiClient.Auth.none());
        if (reference.status() != resp.status() || !Objects.equals(reference.json(), json)) {
            throw new AssertionError(
                    "查不到飯店的回應預期與其他原因完全相同。這次：status=" + resp.status() + "，body=" + resp.body()
                            + "；代碼不存在：status=" + reference.status() + "，body=" + reference.body());
        }
    }

    private Map<String, Object> staffRow(UUID staffId, String staffName) {
        return dbProbe
                .queryOne(
                        "SELECT tenant_id, name, login_id, password_hash, role, active, must_change_password,"
                                + " failed_login_count, locked_until FROM staff_accounts WHERE id = ?",
                        staffId)
                .orElseThrow(() -> new AssertionError("staff_accounts 查無員工「" + staffName + "」id=" + staffId));
    }

    /** 上一個請求必須是登入，回傳它送出的內容。Then 用它取得「這次登入的是哪個員工編號」。 */
    private Map<?, ?> lastLoginBody() {
        ScenarioWorld.ApiRequest request = world.lastRequest();
        if (!LOGIN_PATH.equals(request.path()) || !(request.body() instanceof Map<?, ?> body)) {
            throw new IllegalStateException("這一句要接在登入之後，上一個請求卻是 " + request.method() + " " + request.path());
        }
        return body;
    }

    /** 上一次登入要登進哪一家：帶了飯店代碼就是那一家（對不到飯店時是空的），沒帶就是「測試飯店」。 */
    private Optional<Long> loginTargetTenantId() {
        Object hotelCode = lastLoginBody().get("hotelCode");
        if (hotelCode == null) {
            return Optional.of(tenantFixtures.testTenantId());
        }
        return dbProbe
                .queryOne("SELECT id FROM tenants WHERE code = ?", hotelCode.toString())
                .map(row -> ((Number) row.get("id")).longValue());
    }

    private String lastLoginField(String field) {
        Object value = lastLoginBody().get(field);
        if (value == null) {
            throw new IllegalStateException("上一次登入的請求沒有 " + field);
        }
        return value.toString();
    }

    private String lastPublicHotelCode() {
        ScenarioWorld.ApiRequest request = world.lastRequest();
        if (!request.path().startsWith(PUBLIC_HOTEL_PATH_PREFIX)) {
            throw new IllegalStateException("這一句要接在查飯店名稱之後，上一個請求卻是 " + request.method() + " " + request.path());
        }
        return request.path().substring(PUBLIC_HOTEL_PATH_PREFIX.length());
    }

    /** 角色用語：主管、值班主管、老闆查角色對照；櫃檯、房務、工務的角色代碼和部門代碼相同。 */
    private static String roleCode(String term) {
        try {
            return Terms.role(term);
        } catch (IllegalArgumentException notManagerLike) {
            return Terms.dept(term);
        }
    }

    private static JsonNode requireStatusWithBody(ScenarioWorld.ApiResponse resp, int status, String what) {
        if (resp.status() != status) {
            throw new AssertionError(what + "預期回應 " + status + "，實際 status=" + resp.status() + "，body=" + resp.body());
        }
        if (resp.json() == null) {
            throw new AssertionError(what + "的回應沒有 body");
        }
        return resp.json();
    }

    private static JsonNode requireError(ScenarioWorld.ApiResponse resp, int status, String code, String what) {
        JsonNode json = requireStatusWithBody(resp, status, what);
        check(what + "的 Error.code", code, textOrNull(json, "code"));
        return json;
    }

    /** 登入回應的憑證是 token 加到期時間，兩樣都要有，後續請求才用得了。 */
    private static String requireCredential(JsonNode body, String what) {
        String token = requireToken(body, what);
        String expiresAt = textOrNull(body, "expiresAt");
        if (expiresAt == null || expiresAt.isBlank()) {
            throw new AssertionError(what + "的回應預期帶 expiresAt，實際=" + expiresAt);
        }
        return token;
    }

    private static String requireToken(JsonNode body, String what) {
        String token = textOrNull(body, "token");
        if (token == null || token.isBlank()) {
            throw new AssertionError(what + "的回應預期帶 token，實際=" + token);
        }
        return token;
    }

    private static void requireNoCredential(JsonNode json, String what) {
        String token = textOrNull(json, "token");
        if (token != null) {
            throw new AssertionError(what + "不該發出任何憑證，實際回應 token=" + token);
        }
    }

    private static void check(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static Instant toInstant(Object v) {
        if (v instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (v instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (v instanceof Instant i) {
            return i;
        }
        throw new IllegalStateException("預期時間型別，實際 " + (v == null ? "null" : v.getClass()));
    }
}
