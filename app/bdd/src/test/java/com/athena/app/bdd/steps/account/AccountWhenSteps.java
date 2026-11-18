package com.athena.app.bdd.steps.account;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;

public class AccountWhenSteps {

    private static final Duration ELEMENT_TIMEOUT = Duration.ofSeconds(8);

    private final AppiumWorld world;

    public AccountWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    @當("{string} 在記住帳號畫面輸入密碼 {string} 並登入")
    public void loginOnRememberedScreen(String staffName, String password) {
        world.typeInto("login.password-field", ELEMENT_TIMEOUT, password);
        world.tapWhenEnabled("login.submit-button", ELEMENT_TIMEOUT);
    }

    @當("{string} 在共用手機畫面輸入員工編號 {string} 密碼 {string} 並登入")
    public void loginOnSharedDeviceScreen(String staffName, String staffId, String password) {
        world.typeInto("login.shared-device.staff-id-field", ELEMENT_TIMEOUT, staffId);
        world.typeInto("login.password-field", ELEMENT_TIMEOUT, password);
        world.tapWhenEnabled("login.submit-button", ELEMENT_TIMEOUT);
    }

    @當("{string} 設定新密碼 {string} 並確認")
    public void setNewPassword(String staffName, String newPassword) {
        world.typeInto("login.force-password-change.new-password-field", ELEMENT_TIMEOUT, newPassword);
        world.typeInto("login.force-password-change.confirm-password-field", ELEMENT_TIMEOUT, newPassword);
        world.tapWhenEnabled("login.force-password-change.submit-button", ELEMENT_TIMEOUT);
        world.tapTestId("login.force-password-change.confirm-alert.confirm-button", ELEMENT_TIMEOUT);
    }

    @當("{string} 在「我的」點登出")
    public void tapLogoutOnMeTab(String staffName) {
        world.tapTestId("me.logout-button", ELEMENT_TIMEOUT);
    }
}
