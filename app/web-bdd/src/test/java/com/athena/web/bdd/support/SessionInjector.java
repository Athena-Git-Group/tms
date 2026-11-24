package com.athena.web.bdd.support;

import com.athena.bddsupport.BackendChannel;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class SessionInjector {

    public static final String SESSION_KEY = "tms.frontdesk.session";

    public static final String BOARD_VIEW_KEY = "tms.frontdesk.board-view";

    public static final String LOGIN_PAGE_LANGUAGE_KEY = "tms.frontdesk.language.login-page";

    static final String STORAGE_PAGE_PATH = "/__web-bdd-storage";

    private final BackendChannel backend;

    private final Supplier<Page> currentPage;

    private final Supplier<String> hotelCode;

    SessionInjector(BackendChannel backend, Supplier<Page> currentPage, Supplier<String> hotelCode) {
        this.backend = backend;
        this.currentPage = currentPage;
        this.hotelCode = hotelCode;
    }

    public static String staffLanguageKey(String loginId) {
        return "tms.frontdesk.language.staff." + loginId;
    }

    public JsonNode loginAs(Page page, CreatedStaff staff) {
        JsonNode loginResponse = backend.loginResponse(staff);
        inject(page, loginResponse);
        return loginResponse;
    }

    public JsonNode loginAs(CreatedStaff staff) {
        return loginAs(currentPage.get(), staff);
    }

    public void inject(Page page, JsonNode loginResponse) {
        JsonNode stored = loginResponse;
        if (loginResponse instanceof ObjectNode object && !object.has("hotelCode")) {
            stored = object.deepCopy().put("hotelCode", hotelCode.get());
        }
        putSessionItem(page, SESSION_KEY, stored.toString());
    }

    public void clear(Page page) {
        onAppOrigin(page);
        page.evaluate("key => sessionStorage.removeItem(key)", SESSION_KEY);
    }

    public void putSessionItem(Page page, String key, String value) {
        onAppOrigin(page);
        page.evaluate("([key, value]) => sessionStorage.setItem(key, value)", List.of(key, value));
    }

    public void putLocalItem(Page page, String key, String value) {
        onAppOrigin(page);
        page.evaluate("([key, value]) => localStorage.setItem(key, value)", List.of(key, value));
    }

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
