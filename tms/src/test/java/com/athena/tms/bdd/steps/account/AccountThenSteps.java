package com.athena.tms.bdd.steps.account;

import com.athena.tms.bdd.ScenarioWorld;
import com.athena.tms.bdd.support.BddApiClient;
import com.athena.tms.bdd.support.DbProbe;
import io.cucumber.java.zh_tw.那麼;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * 帳號與登入句型的 Then（dsl.md Then 句型區）：每句依 dsl.md 的「必查」清單斷言。
 */
public class AccountThenSteps {

    private final ScenarioWorld world;
    private final BddApiClient apiClient;
    private final DbProbe dbProbe;

    public AccountThenSteps(ScenarioWorld world, BddApiClient apiClient, DbProbe dbProbe) {
        this.world = world;
        this.apiClient = apiClient;
        this.dbProbe = dbProbe;
    }

    /**
     * dsl.md Then「登入成功，取得 "{員工}" 的身分與可用憑證」：回應 200，body
     * （{@code LoginResponse}）的 name／dept 與該員工在 staff_accounts 的權威狀態相符，
     * 並附帶非空的 token 供後續請求使用；登入成功不建立或改動任何 staff_accounts 資料，
     * 所以只讀 DB 現有列比對，不另外落地任何東西。
     */
    @那麼("登入成功，取得 {string} 的身分與可用憑證")
    public void loginSucceedsWithIdentityAndCredential(String staffName) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        if (resp.status() != 200) {
            throw new AssertionError(
                    "預期登入成功回應 200，實際 status=" + resp.status() + "，body=" + resp.body());
        }

        JsonNode body = resp.json();
        if (body == null) {
            throw new AssertionError("登入成功回應沒有 body");
        }

        UUID staffId = UUID.fromString(world.staffId(staffName));
        Map<String, Object> staffRow = dbProbe
                .queryOne("SELECT name, dept FROM staff_accounts WHERE id = ?", staffId)
                .orElseThrow(() -> new AssertionError("staff_accounts 查無員工「" + staffName + "」id=" + staffId));

        check("LoginResponse.staffId", staffId.toString(), body.path("staffId").asText(null));
        check("LoginResponse.name", staffRow.get("name"), body.path("name").asText(null));

        Object expectedDept = staffRow.get("dept");
        String actualDept = body.path("dept").isMissingNode() || body.path("dept").isNull()
                ? null
                : body.path("dept").asText(null);
        check("LoginResponse.dept", expectedDept, actualDept);

        String token = body.path("token").asText(null);
        if (token == null || token.isBlank()) {
            throw new AssertionError("LoginResponse.token 預期附帶可用憑證，實際 " + token);
        }
    }

    private static void check(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + " 預期 " + expected + "，實際 " + actual);
        }
    }

    /**
     * dsl.md「登入失敗，提示 "{提示}"」必查：回應 4xx，body 的提示文字等於固定文案；
     * 且沒有建立任何憑證（回應沒有非空的 token）。
     */
    @那麼("登入失敗，提示 {string}")
    public void loginFailedWithHint(String hint) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        if (resp.status() < 400 || resp.status() > 499) {
            throw new AssertionError("登入失敗預期 4xx 回應，實際狀態碼=" + resp.status() + "，body=" + resp.body());
        }
        JsonNode json = resp.json();
        String message = textOrNull(json, "message");
        if (!hint.equals(message)) {
            throw new AssertionError("登入失敗提示預期「" + hint + "」，實際 message=" + message + "，body=" + resp.body());
        }
        String token = textOrNull(json, "token");
        if (token != null) {
            throw new AssertionError("登入失敗不該建立任何憑證，實際回應 token=" + token);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    /**
     * dsl.md「"{員工}" 的帳號已鎖定 {分鐘} 分鐘」必查：鎖定到期時間欄位已由
     * V15__staff_account_login_lockout.sql 建好（{@code staff_accounts.locked_until}，
     * timestamptz nullable）——`權威狀態`：讀該欄位非 NULL，且到期時間≈now+{分鐘}分鐘
     * （允許 2 分鐘誤差，涵蓋測試執行耗時，不要求精確到秒）。同時維持「鎖定期間內即使
     * 密碼正確，登入仍回 4xx」：前一個 When 已用正確密碼重新登入，這裡讀 {@link
     * ScenarioWorld#lastResponse()} 確認仍失敗、且沒有建立任何憑證。
     */
    @那麼("{string} 的帳號已鎖定 {int} 分鐘")
    public void accountLocked(String staffName, int minutes) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        if (resp.status() < 400 || resp.status() > 499) {
            throw new AssertionError(
                    "「" + staffName + "」帳號鎖定期間登入預期 4xx 回應，實際 status=" + resp.status()
                            + "，body=" + resp.body());
        }
        JsonNode json = resp.json();
        String token = textOrNull(json, "token");
        if (token != null) {
            throw new AssertionError("帳號鎖定期間不該建立任何憑證，實際回應 token=" + token);
        }

        UUID staffId = UUID.fromString(world.staffId(staffName));
        Map<String, Object> staffRow = dbProbe
                .queryOne("SELECT locked_until FROM staff_accounts WHERE id = ?", staffId)
                .orElseThrow(() -> new AssertionError("staff_accounts 查無員工「" + staffName + "」id=" + staffId));

        Object lockedUntilValue = staffRow.get("locked_until");
        if (lockedUntilValue == null) {
            throw new AssertionError("預期「" + staffName + "」staff_accounts.locked_until 非 NULL，實際=null");
        }

        Instant lockedUntil = toInstant(lockedUntilValue);
        Instant expected = Instant.now().plus(Duration.ofMinutes(minutes));
        Duration diff = Duration.between(expected, lockedUntil).abs();
        if (diff.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new AssertionError(
                    "預期「" + staffName + "」staff_accounts.locked_until≈" + expected + "（±2 分鐘），實際="
                            + lockedUntil);
        }
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

    /**
     * dsl.md「"{員工}" 的帳號已停用，登入被拒」必查：回應 4xx，且該員工的
     * {@code staff_accounts.active} 為 false——停用的帳號登入一律被拒，不因密碼
     * 正確而放行（對齊介面根共用 DSL「停用的員工不出現在挑人名單、不收推播」
     * 的既有約定，本句型擴充為「也不能登入」）。
     */
    @那麼("{string} 的帳號已停用，登入被拒")
    public void accountDeactivatedLoginRejected(String staffName) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        if (resp.status() < 400 || resp.status() > 499) {
            throw new AssertionError("帳號已停用預期登入回應 4xx，實際狀態碼=" + resp.status() + "，body=" + resp.body());
        }

        UUID staffId = UUID.fromString(world.staffId(staffName));
        Map<String, Object> staffRow = dbProbe
                .queryOne("SELECT active FROM staff_accounts WHERE id = ?", staffId)
                .orElseThrow(() -> new AssertionError("staff_accounts 查無員工「" + staffName + "」id=" + staffId));

        Object activeValue = staffRow.get("active");
        boolean active = Boolean.TRUE.equals(activeValue);
        if (active) {
            throw new AssertionError("預期「" + staffName + "」staff_accounts.active=false，實際=" + activeValue);
        }
    }

    /**
     * dsl.md「"{員工}" 必須先設定新密碼才能繼續」必查：登入回應（{@code LoginResponse}）
     * 標示 {@code mustChangePassword}=true；且「不該發生」——在完成改密前，用這組憑證
     * （登入回應附帶的 token）呼叫其他需要身分的 API 一律被拒。用 {@code GET /me/today}
     * 當代表性的「需要身分的 API」驗證第二段，不另外發明一個不存在的端點。
     */
    @那麼("{string} 必須先設定新密碼才能繼續")
    public void mustChangePasswordBeforeProceeding(String staffName) {
        ScenarioWorld.ApiResponse resp = world.lastResponse();
        if (resp.status() != 200) {
            throw new AssertionError(
                    "「" + staffName + "」臨時密碼登入預期仍回應 200，實際 status=" + resp.status()
                            + "，body=" + resp.body());
        }

        JsonNode body = resp.json();
        if (body == null || !body.path("mustChangePassword").asBoolean(false)) {
            throw new AssertionError(
                    "「" + staffName + "」登入回應預期 mustChangePassword=true，實際 body=" + resp.body());
        }

        String token = textOrNull(body, "token");
        if (token == null || token.isBlank()) {
            throw new AssertionError("「" + staffName + "」強制改密前的登入回應預期仍附帶 token，實際=" + token);
        }

        ScenarioWorld.ApiResponse rejected = apiClient.get("/me/today", BddApiClient.Auth.staff(token));
        if (rejected.status() < 400 || rejected.status() > 499) {
            throw new AssertionError(
                    "「" + staffName + "」完成改密前用這組憑證呼叫其他需要身分的 API 預期被拒（4xx），實際 status="
                            + rejected.status() + "，body=" + rejected.body());
        }
    }
}
