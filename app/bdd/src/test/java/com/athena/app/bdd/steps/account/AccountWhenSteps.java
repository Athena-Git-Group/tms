package com.athena.app.bdd.steps.account;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;

public class AccountWhenSteps {

    /** 登入畫面元素出現的等待上限：畫面切換有轉場動畫，用輪詢等待取代固定 sleep。 */
    private static final Duration ELEMENT_TIMEOUT = Duration.ofSeconds(8);

    private final AppiumWorld world;

    public AccountWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    @當("{string} 在記住帳號畫面輸入密碼 {string} 並登入")
    public void loginOnRememberedScreen(String staffName, String password) {
        world.typeInto("login.password-field", ELEMENT_TIMEOUT, password);
        world.tapTestId("login.submit-button", ELEMENT_TIMEOUT);
    }

    @當("{string} 在共用手機畫面輸入員工編號 {string} 密碼 {string} 並登入")
    public void loginOnSharedDeviceScreen(String staffName, String staffId, String password) {
        world.typeInto("login.shared-device.staff-id-field", ELEMENT_TIMEOUT, staffId);
        world.typeInto("login.password-field", ELEMENT_TIMEOUT, password);
        world.tapTestId("login.submit-button", ELEMENT_TIMEOUT);
    }

    /**
     * 怎麼做：在強制改密畫面以 {@code login.force-password-change.new-password-field}、
     * {@code login.force-password-change.confirm-password-field} 輸入兩次新密碼，
     * 點 {@code login.force-password-change.submit-button}，再在跳出的確認對話框點
     * {@code login.force-password-change.confirm-alert.confirm-button}。
     *
     * <p>送出鈕要等兩欄都打完才會啟用：畫面上的四條密碼規則與「兩次相符」全部成立
     * 之前它是灰的。所以這個步驟傳進來的新密碼必須真的能通過那四條，否則點不下去。
     *
     * <p>對話框那一步是真的會送出的那一步。它存在是因為換密碼會把這個帳號在其他
     * 裝置上的登入全部踢掉，員工按下送出鈕時還有機會反悔。
     */
    @當("{string} 設定新密碼 {string} 並確認")
    public void setNewPassword(String staffName, String newPassword) {
        world.typeInto("login.force-password-change.new-password-field", ELEMENT_TIMEOUT, newPassword);
        world.typeInto("login.force-password-change.confirm-password-field", ELEMENT_TIMEOUT, newPassword);
        world.tapTestId("login.force-password-change.submit-button", ELEMENT_TIMEOUT);
        world.tapTestId("login.force-password-change.confirm-alert.confirm-button", ELEMENT_TIMEOUT);
    }

    @當("{string} 在「我的」點登出")
    public void tapLogoutOnMeTab(String staffName) {
        world.tapTestId("me.logout-button", ELEMENT_TIMEOUT);
    }
}
