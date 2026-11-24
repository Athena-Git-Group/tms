package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.BrowserRegex;
import com.athena.web.bdd.pages.UiWords.Language;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;

public class AccountPanel extends PageObject {

    public AccountPanel(WebWorld world) {
        super(world);
    }

    public Locator panel() {
        return page().getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName(UiWords.anyLanguage("帳號")));
    }

    public Locator accountCard(String staffName) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(staffName.strip()))));
    }

    public void openFromSidebar(String staffName) {
        ShellFrame shell = new ShellFrame(world);
        if (!shell.sidebarOpen()) {
            shell.openSidebar();
        }
        shell.press(accountCard(staffName));
        panel().waitFor();
    }

    public Locator identityCard(String staffName) {
        return panel().getByText(staffName.strip(), new Locator.GetByTextOptions().setExact(true)).first();
    }

    public Locator logoutButton() {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(UiWords.anyLanguage("登出")));
    }

    public Locator logoutConfirmDialog() {
        return page().getByRole(AriaRole.ALERTDIALOG);
    }

    public void confirmLogout() {
        new ShellFrame(world).press(logoutConfirmDialog().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(UiWords.anyLanguage("登出"))));
        new LoginPage(world).waitUntilShown();
    }

    public void logOut(String staffName) {
        if (!panel().isVisible()) {
            openFromSidebar(staffName);
        }
        new ShellFrame(world).press(logoutButton());
        logoutConfirmDialog().waitFor();
        confirmLogout();
    }

    public Locator languageOption(String option) {
        return panel().getByRole(AriaRole.RADIO, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(option.strip()))));
    }

    public void chooseLanguage(String option) {
        new ShellFrame(world).press(languageOption(option));
        String title = UiWords.in(Language.of(option), "語言");
        panel().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName(title).setExact(true)).waitFor();
    }

    public Locator accountCardOf(String staffName, String loginId) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setIncludeHidden(true)
                .setName(Pattern.compile(BrowserRegex.literal(staffName.strip()) + ".*" + BrowserRegex.literal(loginId.strip()))));
    }

    public Locator panelTitled(String title) {
        return page().getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName(title.strip()).setExact(true));
    }

    public Locator pressables() {
        return panel().getByRole(AriaRole.BUTTON).filter(new Locator.FilterOptions().setVisible(true));
    }

    public Locator row(Pattern rowNamePrefix) {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(rowNamePrefix));
    }

    public Locator languageGroup(String title) {
        return panel().getByRole(AriaRole.RADIOGROUP, new Locator.GetByRoleOptions().setName(title.strip()).setExact(true));
    }

    public Locator languageOptions() {
        return panel().getByRole(AriaRole.RADIO);
    }

    public Locator backButton() {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(UiWords.anyLanguage("帳號")));
    }

    public Locator languageRow() {
        String anyWord = UiWords.anyLanguage("語言").pattern().replaceFirst("\\$$", "");
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile(anyWord + "(\\s|$)")));
    }

    public void openLanguagePage() {
        new ShellFrame(world).press(languageRow());
        languageOption("Tiếng Việt").waitFor();
    }
}
