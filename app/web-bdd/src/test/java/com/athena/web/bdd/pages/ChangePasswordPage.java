package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.options.AriaRole;

/** 改密碼畫面（路徑 {@code /change-password}）。 */
public class ChangePasswordPage extends PageObject {

    public ChangePasswordPage(WebWorld world) {
        super(world);
    }

    /** 改自己密碼的端點（契約的 {@code changeOwnPassword}）。 */
    public static final String CHANGE_PASSWORD_API_PATH = "/api/v1/staff-accounts/change-password";

    /** 等改密碼的請求與換頁最久這麼久。 */
    private static final double SUBMIT_TIMEOUT_MS = 10_000;

    /** 「新密碼」欄。 */
    public Locator newPasswordField() {
        return page().getByLabel("新密碼", new Page.GetByLabelOptions().setExact(true));
    }

    /** 「確認密碼」欄。 */
    public Locator confirmPasswordField() {
        return page().getByLabel("確認密碼", new Page.GetByLabelOptions().setExact(true));
    }

    /** 「更新密碼」鈕。 */
    public Locator updateButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("更新密碼").setExact(true));
    }

    /** 在兩欄輸入，不按「更新密碼」。 */
    public void enter(String newPassword, String confirmPassword) {
        newPasswordField().fill(newPassword);
        confirmPasswordField().fill(confirmPassword);
    }

    /** 兩欄都輸入同一個新密碼並按「更新密碼」，等改密碼的請求結束、畫面換頁或出現提示。 */
    public void submit(String newPassword) {
        enter(newPassword, newPassword);
        Page current = page();
        Request request = current.waitForRequest(
                ChangePasswordPage::isChangePasswordRequest,
                new Page.WaitForRequestOptions().setTimeout(SUBMIT_TIMEOUT_MS),
                () -> new ShellFrame(world).press(updateButton()));
        // 等這個請求有結果；斷線時沒有回應，同樣算結束。
        request.response();
        current.waitForCondition(() -> !onChangePasswordPath() || noticeShown(),
                new Page.WaitForConditionOptions().setTimeout(SUBMIT_TIMEOUT_MS));
    }

    /** 目前的分頁是不是停在改密碼畫面的網址。 */
    public boolean onChangePasswordPath() {
        return page().url().split("[?#]")[0].endsWith(WebApp.CHANGE_PASSWORD);
    }

    /** 改密碼畫面的大標題（「設定新密碼」）。 */
    public Locator pageTitle() {
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1));
    }

    /**
     * 畫面上看得到、可以打字的輸入欄。規則清單若做成打勾框，不算在內。
     * 密碼欄沒有可及性角色，所以這裡直接數輸入元素，不靠角色。
     */
    public Locator typingFields() {
        return page().locator("input:not([type=checkbox]):not([type=radio]):not([type=hidden])"
                + ":not([type=button]):not([type=submit]), textarea")
                .filter(new Locator.FilterOptions().setVisible(true));
    }

    /** 以欄名找輸入欄，例如「新密碼」。 */
    public Locator field(String fieldName) {
        return page().getByLabel(fieldName.strip(), new Page.GetByLabelOptions().setExact(true));
    }

    /**
     * 規則清單上的一條，例如「至少 8 碼」。規則是唯讀的打勾框，可及性名稱就是規則文字，
     * 符合時是打勾的狀態（{@code aria-checked="true"} 或勾選的 checkbox），沒符合時沒有打勾。
     */
    public Locator rule(String ruleText) {
        return page().getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName(ruleText.strip()).setExact(true));
    }

    private boolean noticeShown() {
        return page().getByRole(AriaRole.ALERT).count() > 0
                || page().getByRole(AriaRole.ALERTDIALOG).count() > 0
                || page().getByRole(AriaRole.DIALOG).count() > 0;
    }

    private static boolean isChangePasswordRequest(Request request) {
        return "POST".equals(request.method())
                && request.url().split("[?#]")[0].endsWith(CHANGE_PASSWORD_API_PATH);
    }
}
