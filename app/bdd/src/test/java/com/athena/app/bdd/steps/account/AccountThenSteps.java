package com.athena.app.bdd.steps.account;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import com.athena.app.bdd.steps.manager.ManagerHomeWait;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.WebElement;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public class AccountThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(6);

    private final AppiumWorld world;

    public AccountThenSteps(AppiumWorld world) {
        this.world = world;
    }

    @那麼("登入畫面顯示 {string} 的身分卡，只有密碼欄位")
    public void loginShowsRememberedIdentityCard(String staffName) {
        WebElement card = world.waitForTestId("login.remembered-identity.card", SCREEN_TIMEOUT);
        assertLabelContains(card, "login.remembered-identity.card", staffName);
        assertLabelContains(card, "login.remembered-identity.card",
                world.staff(staffName).loginId());
        world.waitForTestId("login.password-field", SCREEN_TIMEOUT);
        assertAbsent("login.shared-device.staff-id-field");
    }

    @那麼("登入畫面顯示員工編號與密碼兩個欄位")
    public void loginShowsSharedDeviceFields() {
        WebElement staffIdField = world.waitForTestId("login.shared-device.staff-id-field", SCREEN_TIMEOUT);
        WebElement passwordField = world.waitForTestId("login.password-field", SCREEN_TIMEOUT);
        assertEnabled(staffIdField, "login.shared-device.staff-id-field");
        assertEnabled(passwordField, "login.password-field");
    }

    @那麼("登入畫面顯示錯誤 {string}")
    public void loginShowsError(String message) {
        WebElement banner = world.waitForTestId("login.error-banner", SCREEN_TIMEOUT);
        assertLabelContains(banner, "login.error-banner", message);

        List<Map<String, Object>> attempted = world.backend().queryRows(
                "SELECT name, failed_login_count FROM staff_accounts WHERE tenant_id = ? AND failed_login_count > 0",
                world.backend().ensureTenant());
        if (attempted.isEmpty()) {
            throw new AssertionError(
                    "後端權威狀態不符：登入失敗後應有員工的 failed_login_count > 0（代表沒有核發憑證），實際整個租戶都是 0");
        }
    }

    @那麼("登入畫面顯示帳號已鎖定，剩餘 {int} 分鐘")
    public void loginShowsLockout(int minutes) {
        WebElement banner = world.waitForTestId("login.lockout-banner", SCREEN_TIMEOUT);
        assertLabelContains(banner, "login.lockout-banner", "請 " + minutes + " 分鐘後再試");
        WebElement submit = world.waitForTestId("login.submit-button", SCREEN_TIMEOUT);
        if (submit.isEnabled()) {
            throw new AssertionError("鎖定中 login.submit-button 應為禁用，實際仍可按");
        }

        List<Map<String, Object>> locked = world.backend().queryRows(
                "SELECT name, locked_until FROM staff_accounts WHERE tenant_id = ? AND locked_until IS NOT NULL",
                world.backend().ensureTenant());
        if (locked.isEmpty()) {
            throw new AssertionError("後端權威狀態不符：應有員工的 locked_until 已設定，實際整個租戶都是 null");
        }
        Instant lockedUntil = toInstant(locked.get(0).get("locked_until"));
        Instant now = Instant.now();
        if (!lockedUntil.isAfter(now)) {
            throw new AssertionError("後端 locked_until（" + lockedUntil + "）應在未來，實際已過期");
        }
        Instant upperBound = now.plus(Duration.ofMinutes(minutes)).plusSeconds(30);
        if (lockedUntil.isAfter(upperBound)) {
            throw new AssertionError(
                    "後端 locked_until（" + lockedUntil + "）距今超過 " + minutes + " 分鐘，與畫面顯示的剩餘時間不一致");
        }
    }

    @那麼("App 進入強制改密畫面，完成前無法使用其他畫面")
    public void appStuckOnForcedPasswordChange() {
        world.waitForTestId("login.force-password-change.new-password-field", SCREEN_TIMEOUT);
        world.waitForTestId("login.force-password-change.confirm-password-field", SCREEN_TIMEOUT);

        world.driver().executeScript("mobile: dragFromToForDuration", Map.of(
                "duration", 0.3, "fromX", 1, "fromY", 400, "toX", 320, "toY", 400));
        world.waitForTestId("login.force-password-change.new-password-field", SCREEN_TIMEOUT);
        assertAbsent("home.screen");

        List<Map<String, Object>> pending = world.backend().queryRows(
                "SELECT name FROM staff_accounts WHERE tenant_id = ? AND must_change_password = TRUE",
                world.backend().ensureTenant());
        if (pending.isEmpty()) {
            throw new AssertionError(
                    "後端權威狀態不符：改密完成前 must_change_password 應仍為 true，實際整個租戶都已清掉");
        }
    }

    @那麼("{string} 登入成功，進入 {string}")
    public void loginSucceeds(String staffName, String homeChinese) {
        BackendChannel.CreatedStaff staff = world.staff(staffName);
        String expectedHome = "manager".equals(staff.role()) ? "主管首頁"
                : "engineering".equals(staff.dept()) ? "工務首頁" : "房務首頁";
        if (!expectedHome.equals(homeChinese)) {
            throw new AssertionError("「" + staffName + "」的角色是 " + staff.role() + "、部門是 "
                    + staff.dept() + "，依分流應進「" + expectedHome + "」，劇本卻寫「" + homeChinese + "」");
        }
        switch (homeChinese) {
            case "房務首頁" -> {
                world.waitForTestId("home.screen", SCREEN_TIMEOUT);
                world.waitForTestId("home.progress.cleaned-ratio", SCREEN_TIMEOUT);
                assertAbsent("engHome.screen");
                assertAbsent("manager.home");
            }
            case "工務首頁" -> {
                world.waitForTestId("engHome.screen", SCREEN_TIMEOUT);
                world.waitForTestId("engHome.progress.in-hand-count", SCREEN_TIMEOUT);
                assertAbsent("home.screen");
                assertAbsent("manager.home");
            }
            case "主管首頁" -> {
                world.waitForTestId("manager.home", SCREEN_TIMEOUT);
                ManagerHomeWait.awaitExactly(world, staffName, SCREEN_TIMEOUT);
                assertAbsent("home.screen");
                assertAbsent("engHome.screen");
            }
            default -> throw new IllegalArgumentException("首頁只收房務首頁／工務首頁／主管首頁，實際「"
                    + homeChinese + "」");
        }

        Map<String, Object> row = world.backend().staffAccountRow(staff.id());
        Object failedCount = row.get("failed_login_count");
        if (!(failedCount instanceof Number number) || number.intValue() != 0) {
            throw new AssertionError(
                    "後端權威狀態不符：登入成功後 failed_login_count 應清零，實際為 " + failedCount);
        }
        if (row.get("locked_until") != null) {
            throw new AssertionError(
                    "後端權威狀態不符：登入成功後 locked_until 應為 null，實際為 " + row.get("locked_until"));
        }
    }

    @那麼("App 顯示登出被攔住，原因為 {string}")
    public void logoutBlocked(String reason) {
        WebElement alert = world.waitForTestId("me.logout-blocked-alert", SCREEN_TIMEOUT);
        assertLabelContains(alert, "me.logout-blocked-alert", reason);
        // Flutter 的 modal 會把被蓋住的畫面移出 accessibility tree，要先關掉 Alert 才找得到底下的元素。
        world.waitForTestId("me.logout-blocked-alert.confirm-button", SCREEN_TIMEOUT).click();
        world.waitForTestId("me.logout-button", SCREEN_TIMEOUT);
        assertAbsent("login.password-field");
    }

    private static void assertLabelContains(WebElement element, String id, String expected) {
        String label = element.getText();
        if (label == null || !label.contains(expected)) {
            throw new AssertionError(id + " 應顯示「" + expected + "」，實際為「" + label + "」");
        }
    }

    private static void assertEnabled(WebElement element, String id) {
        if (!element.isEnabled()) {
            throw new AssertionError(id + " 應可輸入，實際為禁用");
        }
    }

    private void assertAbsent(String id) {
        if (!world.driver().findElements(world.byTestId(id)).isEmpty()) {
            throw new AssertionError("畫面上不應出現 " + id + "，實際找得到");
        }
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        throw new AssertionError("locked_until 的型別無法解讀：" + value.getClass() + "（" + value + "）");
    }
}
