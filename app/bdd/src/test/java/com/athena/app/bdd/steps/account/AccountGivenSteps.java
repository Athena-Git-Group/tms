package com.athena.app.bdd.steps.account;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.java.zh_tw.假設;

import java.time.Duration;
import java.util.List;

/** 帳號與登入模組的 Given。 */
public class AccountGivenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(6);

    private final AppiumWorld world;

    public AccountGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：透過 App 本機設定（測試後門 {@code POST /remembered-account}）把裝置設為
     * 「記住帳號」模式並記住該員工。payload 帶員工編號（登入請求用）與姓名（身分卡
     * 顯示用）：登入前 App 沒有任何可以用員工編號反查姓名的 API（也不該有，會洩漏
     * 員工編號是否存在），姓名只能在這裡一起給。回寫：登入畫面顯示該員工的身分卡
     * （{@code login.remembered-identity.card}，由 Then 斷言）。
     */
    @假設("這支手機已記住 {string} 的帳號")
    public void phoneRemembersAccount(String staffName) {
        BackendChannel.CreatedStaff staff = world.staff(staffName);
        world.postBackdoorJson("/remembered-account",
                "{\"employee\":" + json(staff.loginId()) + ",\"name\":" + json(staff.name()) + "}");
    }

    /** 怎麼做：清除 App 本機的記住帳號設定（{@code employee} 給 null 代表共用手機）。 */
    @假設("這支手機是共用手機，沒有記住任何帳號")
    public void phoneIsSharedWithoutRememberedAccount() {
        world.postBackdoorJson("/remembered-account", "{\"employee\":null}");
    }

    /**
     * 怎麼做：透過後端測試資料設定該員工的登入憑證（員工編號＝{@code login_id}、密碼
     * 重新雜湊），並讓本情境之後用姓名查到的員工帶新帳密。
     *
     * <p>句型裡的 {@code /} 要跳脫成 {@code \/}：Cucumber expression 把裸的 {@code /}
     * 當替代字元分隔符（alternation），不跳脫會把句子切成兩個永遠對不上的分支。
     */
    @假設("{string} 的員工編號密碼為 {string} \\/ {string}")
    public void staffHasCredentials(String staffName, String employeeNo, String password) {
        world.setStaffCredentials(staffName, employeeNo, password);
    }

    /**
     * 怎麼做：以該員工登入後把裝置切到無網路（測試後門 {@code POST /offline}），
     * 停在「我的」分頁。權威狀態落地：後端已核發該員工的憑證；離線只是裝置側注入。
     */
    @假設("{string} 已在 App 登入，目前手機離線")
    public void loggedInAndPhoneOffline(String staffName) {
        loginViaSharedDevice(staffName);
        openMeTab();
        world.goOffline();
    }

    /**
     * 怎麼做：透過後端測試資料建立該員工進行中（{@code doing}）的任務，再以該員工登入，
     * 停在「我的」分頁。權威狀態落地：後端任務真相已存在且掛在該員工名下。
     */
    @假設("{string} 已在 App 登入，名下有進行中的任務 {string}")
    public void loggedInWithTaskInProgress(String staffName, String roomNo) {
        world.seedTasks(staffName, List.of(
                new AppiumWorld.TaskFixture(roomNo, "退房清潔", null, null, "進行中", 0, null, null)));
        loginViaSharedDevice(staffName);
        openMeTab();
    }

    /**
     * 怎麼做：以共用手機模式用該員工的臨時密碼完成登入，停在強制改密畫面，還沒設定新密碼。
     * 權威狀態落地：後端 {@code must_change_password=true}，LoginResponse 帶
     * {@code mustChangePassword=true}，App 據此進強制改密畫面。
     */
    @假設("{string} 已用臨時密碼登入，正在強制改密畫面")
    public void loggedInWithTemporaryPasswordAtForceChangeScreen(String staffName) {
        world.backend().markMustChangePassword(world.staff(staffName).id());
        loginViaSharedDevice(staffName);
        world.waitForTestId("login.force-password-change.new-password-field", SCREEN_TIMEOUT);
    }

    /**
     * 怎麼做：透過後端測試資料把該員工帳號累計到指定的登入失敗次數
     * （{@code staff_accounts.failed_login_count}，T240 建的欄位）。
     */
    @假設("{string} 已連續輸入錯誤密碼 {int} 次")
    public void staffHasConsecutiveFailedLogins(String staffName, int count) {
        world.backend().setFailedLoginCount(world.staff(staffName).id(), count);
    }

    /**
     * 怎麼做：透過後端測試資料把該員工帳號標記為需要強制改密
     * （{@code staff_accounts.must_change_password}，T240 建的欄位）。
     */
    @假設("{string} 的帳號是臨時密碼")
    public void staffAccountHasTemporaryPassword(String staffName) {
        world.backend().markMustChangePassword(world.staff(staffName).id());
    }

    /**
     * 「已在 App 登入」的共用動作：先把裝置清成共用手機模式（不依賴前一個情境留下的
     * 記住帳號狀態），再在登入畫面輸入員工編號與密碼送出。元素 id 依模組 dsl.md：
     * {@code login.shared-device.staff-id-field}／{@code login.password-field}／
     * {@code login.submit-button}。
     */
    private void loginViaSharedDevice(String staffName) {
        BackendChannel.CreatedStaff staff = world.staff(staffName);
        world.postBackdoorJson("/remembered-account", "{\"employee\":null}");
        world.typeInto("login.shared-device.staff-id-field", SCREEN_TIMEOUT, staff.loginId());
        world.typeInto("login.password-field", SCREEN_TIMEOUT, staff.password());
        world.tapTestId("login.submit-button", SCREEN_TIMEOUT);
    }

    /**
     * 切到「我的」分頁。Tab bar 項目沒有專屬 accessibility id（ui-plan 的 id 表只定義到
     * 畫面內元素），用分頁的可見文字「我的」點擊，再以 {@code me.screen} 確認真的到頁。
     */
    private void openMeTab() {
        world.waitForLabelContaining("我的", SCREEN_TIMEOUT).click();
        world.waitForTestId("me.screen", SCREEN_TIMEOUT);
    }

    /** 把字串包成 JSON 字面值（後門 payload 用；值來自 feature 檔，僅做基本跳脫）。 */
    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
