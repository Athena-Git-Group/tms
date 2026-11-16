package com.athena.web.bdd.support;

import com.athena.bddsupport.BackendChannel;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 不經過登入頁，直接讓某個分頁變成「已登入」：把後端的登入回應寫進這個分頁的儲存空間。
 *
 * <p>櫃檯 Web 啟動時從 {@code sessionStorage} 讀登入狀態，所以要在畫面啟動之前寫好。做法是先讓分頁停在
 * 同一個網址來源底下的一張空白頁（由測試自己回應，不會載入櫃檯 Web），寫入之後呼叫端再開要去的頁。
 * 這樣只寫一次：之後畫面登出或重新整理時，儲存空間裡是什麼就是什麼，測試不會偷偷再塞一次。
 *
 * <p>分頁已經在櫃檯 Web 的網址底下時直接寫入，不換頁；畫面要重新載入才會讀到寫進去的內容。
 *
 * <p>鍵的名稱與 {@code app/web/src/stores/} 及語言設定用的相同，那邊改了這裡要跟著改。
 */
public final class SessionInjector {

    /** 登入狀態：{@code sessionStorage}，值是登入回應的 JSON 原樣。 */
    public static final String SESSION_KEY = "tms.frontdesk.session";

    /** 看板的選擇（泳道、篩選、房號搜尋、側欄）：{@code sessionStorage}。 */
    public static final String BOARD_VIEW_KEY = "tms.frontdesk.board-view";

    /** 登入頁選的語言：{@code localStorage}，值是語言代碼。 */
    public static final String LOGIN_PAGE_LANGUAGE_KEY = "tms.frontdesk.language.login-page";

    /** 測試用來寫儲存空間的空白頁。櫃檯 Web 沒有這條路徑。 */
    static final String STORAGE_PAGE_PATH = "/__web-bdd-storage";

    private final BackendChannel backend;

    private final Supplier<Page> currentPage;

    SessionInjector(BackendChannel backend, Supplier<Page> currentPage) {
        this.backend = backend;
        this.currentPage = currentPage;
    }

    /** 某位員工自己選的語言：{@code localStorage} 的鍵，值是語言代碼。 */
    public static String staffLanguageKey(String loginId) {
        return "tms.frontdesk.language.staff." + loginId;
    }

    /** 以這位員工向後端登入，把登入回應放進分頁並回傳。之後開櫃檯 Web 的任何一頁都是已登入。 */
    public JsonNode loginAs(Page page, CreatedStaff staff) {
        JsonNode loginResponse = backend.loginResponse(staff);
        inject(page, loginResponse);
        return loginResponse;
    }

    public JsonNode loginAs(CreatedStaff staff) {
        return loginAs(currentPage.get(), staff);
    }

    /** 把一份登入回應原樣放進分頁。要放改過內容的登入回應時用這個。 */
    public void inject(Page page, JsonNode loginResponse) {
        putSessionItem(page, SESSION_KEY, loginResponse.toString());
    }

    /** 拿掉分頁的登入狀態，其他儲存的東西不動。 */
    public void clear(Page page) {
        onAppOrigin(page);
        page.evaluate("key => sessionStorage.removeItem(key)", SESSION_KEY);
    }

    /** 寫一筆 {@code sessionStorage}（只屬於這個分頁）。 */
    public void putSessionItem(Page page, String key, String value) {
        onAppOrigin(page);
        page.evaluate("([key, value]) => sessionStorage.setItem(key, value)", List.of(key, value));
    }

    /** 寫一筆 {@code localStorage}（同一個 context 的分頁共用）。 */
    public void putLocalItem(Page page, String key, String value) {
        onAppOrigin(page);
        page.evaluate("([key, value]) => localStorage.setItem(key, value)", List.of(key, value));
    }

    /**
     * 瀏覽器只讓頁面碰自己網址來源的儲存空間，停在空白分頁時寫進去的東西櫃檯 Web 讀不到。
     * 所以分頁不在櫃檯 Web 的網址底下時，先讓它停到類別註解說的那張空白頁。
     */
    private void onAppOrigin(Page page) {
        if (page.url().startsWith(WebApp.baseUrl() + "/")) {
            return;
        }
        String storagePage = WebApp.url(STORAGE_PAGE_PATH);
        Predicate<String> url = storagePage::equals;
        Consumer<Route> blank = route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(200)
                .setContentType("text/html; charset=utf-8")
                .setBody("<!doctype html><title>web-bdd</title>"));
        page.route(url, blank);
        try {
            page.navigate(storagePage);
        } finally {
            page.unroute(url, blank);
        }
    }
}
