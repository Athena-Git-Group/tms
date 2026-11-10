package com.athena.app.bdd.steps.account;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.java.zh_tw.假設;

import java.time.Duration;
import java.util.List;

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

    @假設("{string} 已在 App 登入，目前手機離線")
    public void loggedInAndPhoneOffline(String staffName) {
        world.ensureLoggedInAs(world.staff(staffName));
        openMeTab();
        world.goOffline();
    }

    @假設("{string} 已在 App 登入，名下有進行中的任務 {string}")
    public void loggedInWithTaskInProgress(String staffName, String roomNo) {
        world.seedTasks(staffName, List.of(
                new AppiumWorld.TaskFixture(roomNo, "退房清潔", null, null, "進行中", 0, null, null, false)));
        world.ensureLoggedInAs(world.staff(staffName));
        // 「進行中」是被改種成 accepted ＋ 欠一次「在 App 按開始」的起始狀態，
        // 不補這一步，任務停在 accepted，登出守衛看不到 doing 的單就不會攔，
        // 而這個場景要驗的正是它會攔。
        world.settleDeferredStarts();
        openMeTab();
    }

    @假設("{string} 已用臨時密碼登入，正在強制改密畫面")
    public void loggedInWithTemporaryPasswordAtForceChangeScreen(String staffName) {
        world.backend().markMustChangePassword(world.staff(staffName).id());
        submitSharedDeviceLogin(staffName);
        world.waitForTestId("login.force-password-change.new-password-field", SCREEN_TIMEOUT);
    }

    @假設("{string} 已連續輸入錯誤密碼 {int} 次")
    public void staffHasConsecutiveFailedLogins(String staffName, int count) {
        world.backend().setFailedLoginCount(world.staff(staffName).id(), count);
    }

    @假設("{string} 的帳號是臨時密碼")
    public void staffAccountHasTemporaryPassword(String staffName) {
        world.backend().markMustChangePassword(world.staff(staffName).id());
    }

    /**
     * 以共用手機模式送出一次登入，但不等首頁。
     *
     * <p>先把裝置清成共用手機模式（不依賴前一個情境留下的記住帳號狀態），再在登入
     * 畫面輸入員工編號與密碼送出。元素 id 依模組 dsl.md：
     * {@code login.shared-device.staff-id-field}／{@code login.password-field}／
     * {@code login.submit-button}。
     *
     * <p>只有臨時密碼那一句用這裡，其餘「已在 App 登入」的句子一律走
     * {@link AppiumWorld#ensureLoggedInAs}。臨時密碼登入成功後落地的是強制改密畫面
     * 而不是首頁，{@code ensureLoggedInAs} 等的是首頁，用它會逾時。
     */
    private void submitSharedDeviceLogin(String staffName) {
        BackendChannel.CreatedStaff staff = world.staff(staffName);
        world.postBackdoorJson("/remembered-account", "{\"employee\":null}");
        world.typeInto("login.shared-device.staff-id-field", SCREEN_TIMEOUT, staff.loginId());
        world.typeInto("login.password-field", SCREEN_TIMEOUT, staff.password());
        world.tapTestId("login.submit-button", SCREEN_TIMEOUT);
    }

    /**
     * 切到「我的」分頁，不論前一步把 App 留在哪一頁。
     *
     * <p>先退回首頁再點分頁，因為 tab bar 只畫在六支分頁的根畫面上。前一步鋪前提時
     * 可能把 App 推到疊在分頁之上的頁面（例如為了按「開始」而進任務頁），那裡找不到
     * 「我的」這個字，直接點會逾時。
     *
     * <p>Tab bar 項目沒有專屬 accessibility id（ui-plan 的 id 表只定義到畫面內元素），
     * 所以用分頁的可見文字「我的」點擊，再以 {@code me.screen} 確認真的到頁。
     */
    private void openMeTab() {
        world.ensureOnHome(SCREEN_TIMEOUT);
        world.waitForLabelContaining("我的", SCREEN_TIMEOUT).click();
        world.waitForTestId("me.screen", SCREEN_TIMEOUT);
    }

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
