package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.options.AriaRole;

public class ChangePasswordPage extends PageObject {

    public ChangePasswordPage(WebWorld world) {
        super(world);
    }

    public static final String CHANGE_PASSWORD_API_PATH = "/api/v1/staff-accounts/change-password";

    private static final double SUBMIT_TIMEOUT_MS = 10_000;

    public Locator newPasswordField() {
        return page().getByLabel("新密碼", new Page.GetByLabelOptions().setExact(true));
    }

    public Locator confirmPasswordField() {
        return page().getByLabel("確認密碼", new Page.GetByLabelOptions().setExact(true));
    }

    public Locator updateButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("更新密碼").setExact(true));
    }

    public void enter(String newPassword, String confirmPassword) {
        newPasswordField().fill(newPassword);
        confirmPasswordField().fill(confirmPassword);
    }

    public void submit(String newPassword) {
        enter(newPassword, newPassword);
        Page current = page();
        Request request = current.waitForRequest(
                ChangePasswordPage::isChangePasswordRequest,
                new Page.WaitForRequestOptions().setTimeout(SUBMIT_TIMEOUT_MS),
                () -> new ShellFrame(world).press(updateButton()));
        request.response();
        current.waitForCondition(() -> !onChangePasswordPath() || noticeShown(),
                new Page.WaitForConditionOptions().setTimeout(SUBMIT_TIMEOUT_MS));
    }

    public boolean onChangePasswordPath() {
        return page().url().split("[?#]")[0].endsWith(WebApp.CHANGE_PASSWORD);
    }

    public Locator pageTitle() {
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1));
    }

    public Locator typingFields() {
        return page().locator("input:not([type=checkbox]):not([type=radio]):not([type=hidden])"
                + ":not([type=button]):not([type=submit]), textarea")
                .filter(new Locator.FilterOptions().setVisible(true));
    }

    public Locator field(String fieldName) {
        return page().getByLabel(fieldName.strip(), new Page.GetByLabelOptions().setExact(true));
    }

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
