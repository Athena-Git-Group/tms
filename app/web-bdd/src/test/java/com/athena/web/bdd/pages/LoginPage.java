package com.athena.web.bdd.pages;

import com.athena.web.bdd.pages.UiWords.Language;
import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

public class LoginPage extends PageObject {

    public LoginPage(WebWorld world) {
        super(world);
    }

    public static final String PUBLIC_HOTEL_PATH_PREFIX = "/api/v1/public/hotels/";

    public static final String LOGIN_API_PATH = "/api/v1/staff-accounts/login";

    private static final double LOAD_TIMEOUT_MS = 10_000;

    public record Submission(Page page, String loginId, String client, int status, String responseBody,
            String typedLoginId) {
    }

    public static String pathFor(String hotelCode) {
        return WebApp.underHotel(hotelCode, WebApp.LOGIN);
    }

    public void openFor(String hotelCode) {
        page().waitForResponse(
                response -> isPublicHotelResponse(response, hotelCode),
                new Page.WaitForResponseOptions().setTimeout(LOAD_TIMEOUT_MS),
                () -> world.openPath(pathFor(hotelCode)));
        waitUntilShown();
    }

    public void waitUntilShown() {
        loginButton().waitFor();
    }

    public boolean onLoginPath() {
        return pathOf(page().url()).endsWith(WebApp.LOGIN);
    }

    public Locator field(String fieldName) {
        String name = fieldName.strip();
        if (!"員工編號".equals(name) && !"密碼".equals(name)) {
            throw new IllegalArgumentException("登入頁的欄位只接受「員工編號」或「密碼」，收到「" + fieldName + "」");
        }
        return page().getByLabel(UiWords.anyLanguage(name));
    }

    public Locator loginButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiWords.anyLanguage("登入")));
    }

    public void typeInto(String fieldName, String text) {
        field(fieldName).pressSequentially(text);
    }

    public Submission submit(String loginId, String password) {
        field("員工編號").fill(loginId);
        field("密碼").fill(password);
        Page current = page();
        Request request = current.waitForRequest(
                LoginPage::isLoginRequest,
                new Page.WaitForRequestOptions().setTimeout(LOAD_TIMEOUT_MS),
                () -> new ShellFrame(world).press(loginButton()));
        Response response = request.response();
        Submission submission = toSubmission(current, request, response, loginId);
        current.waitForCondition(() -> !onLoginPath() || noticeShown(),
                new Page.WaitForConditionOptions().setTimeout(LOAD_TIMEOUT_MS));
        return submission;
    }

    public Locator languageMenuButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiWords.anyLanguage("語言")));
    }

    public Locator languageMenu() {
        return page().getByRole(AriaRole.MENU);
    }

    public Locator languageMenuOption(String option) {
        return languageMenu().getByRole(AriaRole.MENUITEMRADIO,
                new Locator.GetByRoleOptions().setName(option.strip()).setExact(true));
    }

    public void openLanguageMenu() {
        new ShellFrame(world).press(languageMenuButton());
        languageMenu().waitFor();
    }

    public void chooseLanguage(String option) {
        if (!languageMenu().isVisible()) {
            openLanguageMenu();
        }
        new ShellFrame(world).press(languageMenuOption(option));
        String signIn = UiWords.in(Language.of(option), "登入");
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(signIn).setExact(true)).waitFor();
    }

    private boolean noticeShown() {
        return page().getByRole(AriaRole.ALERT).count() > 0
                || page().getByRole(AriaRole.ALERTDIALOG).count() > 0
                || page().getByRole(AriaRole.DIALOG).count() > 0;
    }

    private static Submission toSubmission(Page page, Request request, Response response, String typedLoginId) {
        String loginId = null;
        String client = null;
        JsonNode body = readJson(request.postData());
        if (body.hasNonNull("loginId")) {
            loginId = body.get("loginId").asText();
        }
        if (body.hasNonNull("client")) {
            client = body.get("client").asText();
        }
        if (response == null) {
            return new Submission(page, loginId, client, 0, "", typedLoginId);
        }
        String text;
        try {
            text = response.text();
        } catch (RuntimeException bodyGone) {
            // 少數情況下瀏覽器已經丟掉回應內容（例如回應之後馬上換頁），只留狀態碼。
            text = "";
        }
        return new Submission(page, loginId, client, response.status(), text, typedLoginId);
    }

    private static JsonNode readJson(String text) {
        if (text == null || text.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return JSON.readTree(text);
        } catch (JsonProcessingException notJson) {
            return MissingNode.getInstance();
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private static boolean isLoginRequest(Request request) {
        return "POST".equals(request.method()) && pathOf(request.url()).equals(LOGIN_API_PATH);
    }

    private static boolean isPublicHotelResponse(Response response, String hotelCode) {
        String expected = PUBLIC_HOTEL_PATH_PREFIX + URLEncoder.encode(hotelCode, StandardCharsets.UTF_8);
        return "GET".equals(response.request().method()) && pathOf(response.url()).equals(expected);
    }

    public Locator pageTitle() {
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1));
    }

    public Locator visibleNotices() {
        return page().getByRole(AriaRole.ALERT).filter(new Locator.FilterOptions().setVisible(true));
    }

    public Locator languageMenuOptions() {
        return languageMenu().getByRole(AriaRole.MENUITEMRADIO);
    }

    private static String pathOf(String url) {
        String withoutQuery = url.split("[?#]")[0];
        int scheme = withoutQuery.indexOf("://");
        if (scheme < 0) {
            return withoutQuery;
        }
        int pathStart = withoutQuery.indexOf('/', scheme + 3);
        return pathStart < 0 ? "/" : withoutQuery.substring(pathStart);
    }
}
