package com.athena.app.bdd.steps.account;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.WebElement;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 帳號與登入模組的 Then。語意逐列對照
 * {@code specs/truth/features/ios-app/帳號與登入/dsl.md} 的「必查」欄：
 * 畫面半邊用 accessibility id（經 {@link AppiumWorld#byTestId(String)} 分流）斷言；
 * 「與後端權威狀態一致」半邊走 {@link BackendChannel} 讀回 {@code staff_accounts}
 * 的 {@code failed_login_count}／{@code locked_until}／{@code must_change_password}
 * （欄位名對齊 {@code specs/truth/contracts/components/schemas/員工帳號與登入.yaml}）。
 */
public class AccountThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(6);

    private final AppiumWorld world;

    public AccountThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：{@code login.remembered-identity.card} 顯示該員工姓名與員工編號；
     * 畫面上沒有 {@code login.shared-device.staff-id-field}。
     *
     * <p>姓名與員工編號兩個都要查。身分卡是員工在輸入密碼前確認「這支手機記住的是我」
     * 的唯一依據，只查姓名的話，同名同姓的兩個人分不出來。
     */
    @那麼("登入畫面顯示 {string} 的身分卡，只有密碼欄位")
    public void loginShowsRememberedIdentityCard(String staffName) {
        WebElement card = world.waitForTestId("login.remembered-identity.card", SCREEN_TIMEOUT);
        assertLabelContains(card, "login.remembered-identity.card", staffName);
        assertLabelContains(card, "login.remembered-identity.card",
                world.staff(staffName).loginId());
        world.waitForTestId("login.password-field", SCREEN_TIMEOUT);
        assertAbsent("login.shared-device.staff-id-field");
    }

    /** 必查：{@code login.shared-device.staff-id-field}、{@code login.password-field} 都存在且可輸入。 */
    @那麼("登入畫面顯示員工編號與密碼兩個欄位")
    public void loginShowsSharedDeviceFields() {
        WebElement staffIdField = world.waitForTestId("login.shared-device.staff-id-field", SCREEN_TIMEOUT);
        WebElement passwordField = world.waitForTestId("login.password-field", SCREEN_TIMEOUT);
        assertEnabled(staffIdField, "login.shared-device.staff-id-field");
        assertEnabled(passwordField, "login.password-field");
    }

    /**
     * 必查：{@code login.error-banner} 顯示該文字；後端沒有核發憑證。
     *
     * <p>「沒有核發憑證」在後端的可觀察痕跡：JWT 無狀態、成功才會把
     * {@code failed_login_count} 清零（T287 的登入語意），所以失敗嘗試留下的權威狀態
     * 就是計數仍大於 0。句型沒有員工參數，改掃整個測試租戶——本 feature 每個場景只有
     * 一位員工在嘗試登入，掃租戶與指名同義。
     */
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

    /**
     * 必查：{@code login.lockout-banner} 要員工等滿該分鐘數；{@code login.submit-button} 禁用。
     * 後端半邊：{@code locked_until} 已落地為未來時間，且距今不超過該分鐘數（鎖定當下起算，
     * 斷言時剩餘只會更少，不會更多）。
     *
     * <p>畫面那一句是固定文案「連續輸錯 5 次，請 15 分鐘後再試，或請主管在後台重設密碼。」，
     * 不會倒數。所以畫面半邊只確認它講的等待時間和場景寫的一致，真正「還剩多久」的真相
     * 在後端那一半。
     */
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

    /**
     * 必查：顯示 {@code login.force-password-change.new-password-field} 等元件；
     * Tab bar 與返回手勢都無法離開這個畫面。
     *
     * <p>「無法離開」的驗法：做一次 iOS 左緣右滑返回手勢後，強制改密欄位仍在、
     * 首頁（{@code home.screen}）沒有出現。後端半邊：{@code must_change_password}
     * 仍為 true——改密完成前這個旗標不會被清掉。
     */
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

    /**
     * 必查：App 停在首頁，顯示該員工的今日進度。後端半邊：登入成功即把該員工的
     * {@code failed_login_count} 清零、{@code locked_until} 保持未鎖定。
     */
    @那麼("{string} 登入成功，進入首頁")
    public void loginSucceeds(String staffName) {
        world.waitForTestId("home.screen", SCREEN_TIMEOUT);
        // 今日進度（進度環卡）的第一格，id 對照 ui-plan.md 的 F1 R1。
        world.waitForTestId("home.progress.cleaned-ratio", SCREEN_TIMEOUT);

        BackendChannel.CreatedStaff staff = world.staff(staffName);
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

    /**
     * 必查：{@code me.logout-blocked-alert} 顯示該原因文字；App 仍停在已登入狀態，
     * 沒有真的登出（登出項目還在、沒有被踢回登入畫面）。
     *
     * <p>「登出項目還在」要先把 Alert 關掉才驗得到：Flutter 的 modal 會把被蓋住的
     * 畫面整個移出 accessibility tree（原生 iOS Alert 則不會），Alert 開著的同時
     * 去找 {@code me.logout-button} 永遠找不到，驗的其實是 Flutter 的遮罩行為而
     * 不是「有沒有真的登出」。
     */
    @那麼("App 顯示登出被攔住，原因為 {string}")
    public void logoutBlocked(String reason) {
        WebElement alert = world.waitForTestId("me.logout-blocked-alert", SCREEN_TIMEOUT);
        assertLabelContains(alert, "me.logout-blocked-alert", reason);
        world.waitForTestId("me.logout-blocked-alert.confirm-button", SCREEN_TIMEOUT).click();
        world.waitForTestId("me.logout-button", SCREEN_TIMEOUT);
        assertAbsent("login.password-field");
    }

    /** 元素的可見文字要包含預期片段；iOS 的 getText() 讀的是 accessibility label。 */
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

    /** 斷言某個 accessibility id 不在畫面上。用 findElements（空清單）而不是 findElement（要等逾時）。 */
    private void assertAbsent(String id) {
        if (!world.driver().findElements(world.byTestId(id)).isEmpty()) {
            throw new AssertionError("畫面上不應出現 " + id + "，實際找得到");
        }
    }

    /** JDBC getObject() 對 timestamptz 可能回 Timestamp 或 OffsetDateTime，統一轉 Instant。 */
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
