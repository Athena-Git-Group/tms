package com.athena.web.bdd.steps.account;

import com.athena.web.bdd.pages.AccountPanel;
import com.athena.web.bdd.pages.ChangePasswordPage;
import com.athena.web.bdd.pages.LoginPage;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.AccountScenario;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import io.cucumber.java.en.When;
import java.util.List;

public class AccountWhenSteps {

    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789";

    private static final double RELOAD_TIMEOUT_MS = 10_000;

    private final WebWorld world;

    private final LoginAttempts loginAttempts;

    private final LoginPage login;

    private final ChangePasswordPage changePassword;

    private final AccountPanel account;

    private final ShellFrame shell;

    private final AccountScenario scenario;

    public AccountWhenSteps(WebWorld world, LoginAttempts loginAttempts, AccountScenario scenario) {
        this.world = world;
        this.loginAttempts = loginAttempts;
        this.scenario = scenario;
        this.login = new LoginPage(world);
        this.changePassword = new ChangePasswordPage(world);
        this.account = new AccountPanel(world);
        this.shell = new ShellFrame(world);
    }

    @When("有人打開登入頁")
    public void someoneOpensLoginPage() {
        login.openFor(world.defaults().tenantCode());
    }

    @When("有人用飯店識別代碼 {string} 的網址打開登入頁")
    public void someoneOpensLoginPageWithHotelCode(String hotelCode) {
        login.openFor(hotelCode);
    }

    @When("{string} 在登入頁的 {string} 輸入 {string}")
    public void typesIntoLoginField(String staffName, String fieldName, String text) {
        login.typeInto(fieldName, text);
    }

    @When("{string} 在登入頁的 {string} 輸入 {int} 個字")
    public void typesCharactersIntoLoginField(String staffName, String fieldName, int count) {
        login.typeInto(fieldName, alphanumeric(count));
    }

    @When("{string} 在登入頁輸入員工編號 {string} 與密碼 {string} 後按「登入」")
    public void logsInOnLoginPage(String staffName, String loginId, String password) {
        if ("about:blank".equals(world.page().url())) {
            login.openFor(world.defaults().tenantCode());
        }
        loginAttempts.add(login.submit(loginId, password));
    }

    @When("{string} 在新分頁輸入員工編號 {string} 與密碼 {string} 後按「登入」")
    public void logsInOnNewTab(String staffName, String loginId, String password) {
        world.switchTo(newTab());
        loginAttempts.add(login.submit(loginId, password));
    }

    @When("{string} 打開登入頁右上的語言選單")
    public void opensLoginLanguageMenu(String staffName) {
        login.openLanguageMenu();
    }

    @When("{string} 在登入頁的語言選單選 {string}")
    public void choosesLoginLanguage(String staffName, String option) {
        login.chooseLanguage(option);
    }

    @When("{string} 直接打開 {string} 的網址")
    public void opensPageUrlDirectly(String staffName, String pageName) {
        world.open(ShellFrame.pathOf(pageName));
    }

    @When("{string} 輸入新密碼 {string}、確認密碼 {string}")
    public void entersNewPassword(String staffName, String newPassword, String confirmPassword) {
        changePassword.enter(newPassword, confirmPassword);
    }

    @When("{string} 把新密碼設成 {string} 並按「更新密碼」")
    public void submitsNewPassword(String staffName, String newPassword) {
        changePassword.submit(newPassword);
    }

    @When("{string} 重新整理網頁")
    public void reloadsPage(String staffName) {
        Page page = world.page();
        String path = page.url().split("[?#]")[0];
        String pageName = path.endsWith(WebApp.BOARD) ? "總覽看板" : path.endsWith(WebApp.TRIAGE) ? "待派單頁" : null;
        if (pageName == null) {
            page.reload();
            return;
        }
        page.waitForResponse(AccountWhenSteps::isBoardResponse,
                new Page.WaitForResponseOptions().setTimeout(RELOAD_TIMEOUT_MS), page::reload);
        shell.waitForPage(pageName);
    }

    @When("{string} 點側欄的帳號卡")
    public void clicksAccountCard(String staffName) {
        account.openFromSidebar(staffName);
    }

    @When("{string} 點身分卡")
    public void clicksIdentityCard(String staffName) {
        shell.press(account.identityCard(staffName));
    }

    @When("{string} 在登出確認提示按「登出」")
    public void confirmsLogout(String staffName) {
        account.confirmLogout();
    }

    @When("{string} 在語言頁選 {string}")
    public void choosesLanguageOnLanguagePage(String staffName, String option) {
        account.chooseLanguage(option);
    }

    @When("{string} 從語言頁回到帳號面板")
    public void returnsToAccountPanel(String staffName) {
        shell.press(account.backButton());
        account.logoutButton().waitFor();
    }

    @When("^\"([^\"]*)\" 沒有登出就(.+)，之後有人再打開櫃檯畫面$")
    public void closesWithoutLogoutThenReopens(String staffName, String action) {
        Page current = world.page();
        BrowserContext context = current.context();
        switch (action.strip()) {
            case "關掉這個分頁" -> current.close();
            case "關掉整個瀏覽器" -> List.copyOf(context.pages()).forEach(Page::close);
            default -> throw new IllegalArgumentException(
                    "動作只接受「關掉這個分頁」或「關掉整個瀏覽器」，收到「" + action + "」");
        }
        world.switchTo(context.newPage());
        world.open(WebApp.BOARD);
    }

    @When("{string} 另開一個新分頁打開櫃檯畫面")
    public void opensDeskInNewTab(String staffName) {
        Page original = world.page();
        Page added = world.openTab();
        world.open(WebApp.BOARD);
        scenario.rememberTabs(original, added);
    }

    @When("{string} 在原本的分頁登出")
    public void logsOutOnOriginalTab(String staffName) {
        world.switchTo(firstTab());
        account.logOut(staffName);
    }

    private Page firstTab() {
        return world.page().context().pages().get(0);
    }

    private Page newTab() {
        List<Page> pages = world.page().context().pages();
        if (pages.size() < 2) {
            throw new IllegalStateException("這個場景只開了一個分頁，沒有「新分頁」");
        }
        return pages.get(pages.size() - 1);
    }

    private static String alphanumeric(int count) {
        StringBuilder text = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            text.append(ALPHANUMERIC.charAt(i % ALPHANUMERIC.length()));
        }
        return text.toString();
    }

    private static boolean isBoardResponse(Response response) {
        Network.Api board = Network.DESK_BOARD;
        String path = response.url().split("[?#]")[0];
        return board.method().equals(response.request().method()) && path.endsWith(board.path());
    }
}
