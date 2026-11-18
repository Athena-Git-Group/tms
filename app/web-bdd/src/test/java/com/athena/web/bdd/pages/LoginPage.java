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

/** 登入頁（路徑 {@code /login}）。 */
public class LoginPage extends PageObject {

    public LoginPage(WebWorld world) {
        super(world);
    }

    /** 登入頁查飯店名稱的端點（契約的 {@code readPublicHotel}），路徑最後一段是飯店識別代碼。 */
    public static final String PUBLIC_HOTEL_PATH_PREFIX = "/api/v1/public/hotels/";

    /** 登入的端點（{@code staffAccountsLogin}）。 */
    public static final String LOGIN_API_PATH = "/api/v1/staff-accounts/login";

    /** 等登入頁的請求與畫面最久這麼久；後端與開發伺服器都在本機，正常一秒內就好。 */
    private static final double LOAD_TIMEOUT_MS = 10_000;

    /**
     * 一次在登入頁按「登入」送出的請求與它的回應。
     *
     * @param page 送出的那個分頁
     * @param loginId 請求裡的 {@code loginId}，照畫面送出的原樣
     * @param client 請求裡的 {@code client}；沒帶時是 {@code null}
     * @param status 回應的狀態碼；請求沒有拿到回應（例如斷線）時是 0
     * @param responseBody 回應的內容原樣；沒有回應時是空字串
     * @param typedLoginId 這次在員工編號欄填進去的字，照字面
     */
    public record Submission(Page page, String loginId, String client, int status, String responseBody,
            String typedLoginId) {
    }

    /** 登入頁的網址：飯店識別代碼放在路徑最前面，例如 {@code /athena/login}。 */
    public static String pathFor(String hotelCode) {
        return WebApp.underHotel(hotelCode, WebApp.LOGIN);
    }

    /**
     * 以帶這個飯店識別代碼的網址開登入頁，等到飯店名稱的查詢有了回應（查到或查不到都算）、登入頁畫完。
     * 查詢要在開網址之前就開始等，才不會錯過。
     */
    public void openFor(String hotelCode) {
        page().waitForResponse(
                response -> isPublicHotelResponse(response, hotelCode),
                new Page.WaitForResponseOptions().setTimeout(LOAD_TIMEOUT_MS),
                () -> world.openPath(pathFor(hotelCode)));
        waitUntilShown();
    }

    /** 等到登入頁畫完：「登入」鈕出現。 */
    public void waitUntilShown() {
        loginButton().waitFor();
    }

    /** 目前的分頁是不是停在登入頁的網址。 */
    public boolean onLoginPath() {
        return pathOf(page().url()).endsWith(WebApp.LOGIN);
    }

    /** 登入頁的輸入欄，以欄名找：「員工編號」或「密碼」，不管畫面現在是哪一種語言。 */
    public Locator field(String fieldName) {
        String name = fieldName.strip();
        if (!"員工編號".equals(name) && !"密碼".equals(name)) {
            throw new IllegalArgumentException("登入頁的欄位只接受「員工編號」或「密碼」，收到「" + fieldName + "」");
        }
        return page().getByLabel(UiWords.anyLanguage(name));
    }

    /** 「登入」鈕。 */
    public Locator loginButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiWords.anyLanguage("登入")));
    }

    /** 在這一欄一個字一個字打進去，和人用鍵盤輸入一樣，每個字都會觸發畫面的輸入檢查。 */
    public void typeInto(String fieldName, String text) {
        field(fieldName).pressSequentially(text);
    }

    /**
     * 照字面填兩欄（不轉大小寫、不去空白）並按「登入」，等登入請求結束、畫面換頁或出現提示，回傳送出的內容與回應。
     * 回應的內容在這裡就讀出來：畫面換頁之後瀏覽器可能已經丟掉它。
     */
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

    /** 登入頁右上的語言選單按鈕。按鈕的名稱也會跟著介面語言換。 */
    public Locator languageMenuButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiWords.anyLanguage("語言")));
    }

    /** 打開之後的語言選單。 */
    public Locator languageMenu() {
        return page().getByRole(AriaRole.MENU);
    }

    /** 語言選單上的某個選項，以該語言自己的寫法找，例如「日本語」。 */
    public Locator languageMenuOption(String option) {
        return languageMenu().getByRole(AriaRole.MENUITEMRADIO,
                new Locator.GetByRoleOptions().setName(option.strip()).setExact(true));
    }

    /** 點右上的語言選單，等選項出現。 */
    public void openLanguageMenu() {
        new ShellFrame(world).press(languageMenuButton());
        languageMenu().waitFor();
    }

    /** 打開語言選單並點這個選項，等登入頁的文字換成那一種語言（以「登入」鈕的文字判斷）。 */
    public void chooseLanguage(String option) {
        if (!languageMenu().isVisible()) {
            openLanguageMenu();
        }
        new ShellFrame(world).press(languageMenuOption(option));
        String signIn = UiWords.in(Language.of(option), "登入");
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(signIn).setExact(true)).waitFor();
    }

    /** 登入頁正在顯示提示：一行紅字、提示卡或對話框。 */
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

    /** 請求內容不是 JSON 時當成沒有欄位，讓 Then 句回報「沒有送出 loginId」，不在這裡就失敗。 */
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

    /** 登入頁的大標題（「登入 TMS 櫃檯」那一行），不管畫面現在是哪一種語言。 */
    public Locator pageTitle() {
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1));
    }

    /**
     * 登入頁現在看得到的提示：白卡下方的一行紅字與提示卡都是 {@code alert}。
     * 一行紅字只有一句話；提示卡有標題與內文兩段，用內容分辨。
     */
    public Locator visibleNotices() {
        return page().getByRole(AriaRole.ALERT).filter(new Locator.FilterOptions().setVisible(true));
    }

    /** 語言選單裡的全部選項，由上到下。 */
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
