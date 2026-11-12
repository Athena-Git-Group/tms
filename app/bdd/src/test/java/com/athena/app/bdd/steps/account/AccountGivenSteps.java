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

    // Cucumber expression 把裸的 / 當成替代字的分隔符，所以要跳脫成 \/。
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

    private void submitSharedDeviceLogin(String staffName) {
        BackendChannel.CreatedStaff staff = world.staff(staffName);
        world.postBackdoorJson("/remembered-account", "{\"employee\":null}");
        world.typeInto("login.shared-device.staff-id-field", SCREEN_TIMEOUT, staff.loginId());
        world.typeInto("login.password-field", SCREEN_TIMEOUT, staff.password());
        world.tapWhenEnabled("login.submit-button", SCREEN_TIMEOUT);
    }

    private void openMeTab() {
        world.ensureOnHome(SCREEN_TIMEOUT);
        world.waitForLabelContaining("我的", SCREEN_TIMEOUT).click();
        world.waitForTestId("me.screen", SCREEN_TIMEOUT);
    }

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
