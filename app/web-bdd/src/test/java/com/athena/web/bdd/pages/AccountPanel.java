package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.BrowserRegex;
import com.athena.web.bdd.pages.UiWords.Language;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;

/** 帳號面板：從側欄的帳號卡打開，裡面有身分卡、介面語言與登出。 */
public class AccountPanel extends PageObject {

    public AccountPanel(WebWorld world) {
        super(world);
    }

    /** 帳號面板本身：名稱是「帳號」（跟著介面語言換）的對話框。換到語言頁時還是同一個面板。 */
    public Locator panel() {
        return page().getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName(UiWords.anyLanguage("帳號")));
    }

    /** 側欄底部的帳號卡：名稱以這位員工的姓名開頭的按鈕。 */
    public Locator accountCard(String staffName) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(staffName.strip()))));
    }

    /** 側欄收起時先打開，再點底部的帳號卡，等帳號面板出現。 */
    public void openFromSidebar(String staffName) {
        ShellFrame shell = new ShellFrame(world);
        if (!shell.sidebarOpen()) {
            shell.openSidebar();
        }
        shell.press(accountCard(staffName));
        panel().waitFor();
    }

    /** 面板最上面的身分卡，以上面的姓名找。 */
    public Locator identityCard(String staffName) {
        return panel().getByText(staffName.strip(), new Locator.GetByTextOptions().setExact(true)).first();
    }

    /** 面板上的「登出」。 */
    public Locator logoutButton() {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(UiWords.anyLanguage("登出")));
    }

    /** 按「登出」之後跳出的確認對話框。 */
    public Locator logoutConfirmDialog() {
        return page().getByRole(AriaRole.ALERTDIALOG);
    }

    /** 按確認對話框裡的「登出」，等登入頁畫完。 */
    public void confirmLogout() {
        new ShellFrame(world).press(logoutConfirmDialog().getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(UiWords.anyLanguage("登出"))));
        new LoginPage(world).waitUntilShown();
    }

    /** 從側欄打開帳號面板（已經開著就不再開）、按「登出」並在確認對話框按「登出」，等登入頁畫完。 */
    public void logOut(String staffName) {
        if (!panel().isVisible()) {
            openFromSidebar(staffName);
        }
        new ShellFrame(world).press(logoutButton());
        logoutConfirmDialog().waitFor();
        confirmLogout();
    }

    /** 語言頁上的某個選項，以該語言自己的寫法開頭（第二行是用目前介面語言寫的名稱）。 */
    public Locator languageOption(String option) {
        return panel().getByRole(AriaRole.RADIO, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(option.strip()))));
    }

    /** 點語言頁的這個選項，等畫面文字換成那一種語言（以語言頁的標題「語言」判斷）。 */
    public void chooseLanguage(String option) {
        new ShellFrame(world).press(languageOption(option));
        String title = UiWords.in(Language.of(option), "語言");
        panel().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName(title).setExact(true)).waitFor();
    }

    /**
     * 側欄底部這位員工的帳號卡，側欄收起時也找得到（收起的側欄可能只是藏起來）。
     * 卡上有姓名與「{角色} · {員工編號}」，角色會跟著介面語言換，所以用姓名加員工編號認。
     */
    public Locator accountCardOf(String staffName, String loginId) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setIncludeHidden(true)
                .setName(Pattern.compile(BrowserRegex.literal(staffName.strip()) + ".*" + BrowserRegex.literal(loginId.strip()))));
    }

    /** 標題是這句的帳號面板。 */
    public Locator panelTitled(String title) {
        return page().getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName(title.strip()).setExact(true));
    }

    /** 面板上可以按的東西（身分卡不能按，所以不在裡面），由上到下。 */
    public Locator pressables() {
        return panel().getByRole(AriaRole.BUTTON).filter(new Locator.FilterOptions().setVisible(true));
    }

    /** 面板上這一列：名稱以列名開頭的按鈕，列名可以是四種語言的任何一種寫法。 */
    public Locator row(Pattern rowNamePrefix) {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(rowNamePrefix));
    }

    /** 語言頁上那一組單選，名稱是段落標題（「App 語言」）。 */
    public Locator languageGroup(String title) {
        return panel().getByRole(AriaRole.RADIOGROUP, new Locator.GetByRoleOptions().setName(title.strip()).setExact(true));
    }

    /** 語言頁上的全部選項，由上到下。 */
    public Locator languageOptions() {
        return panel().getByRole(AriaRole.RADIO);
    }

    /** 語言頁左上的返回鈕，寫著要回去的「帳號」。 */
    public Locator backButton() {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(UiWords.anyLanguage("帳號")));
    }

    /**
     * 帳號面板上的「語言」那一列。這一列的名稱是「語言」再接右邊寫的目前語言，例如「語言 繁體中文」，
     * 所以只比對開頭；「語言」本身也跟著介面語言換。
     */
    public Locator languageRow() {
        String anyWord = UiWords.anyLanguage("語言").pattern().replaceFirst("\\$$", "");
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile(anyWord + "(\\s|$)")));
    }

    /** 點「語言」那一列，等語言頁的四個選項出現；以越南文那一個的寫法判斷，它不會跟著介面語言換。 */
    public void openLanguagePage() {
        new ShellFrame(world).press(languageRow());
        languageOption("Tiếng Việt").waitFor();
    }
}
