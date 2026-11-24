package com.athena.web.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.athena.web.bdd.support.WebApp;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import org.junit.jupiter.api.Test;

class SmokeTest {

    private static final String BASE_URL = WebApp.baseUrl();

    private static final String LOGIN_WITH_UNKNOWN_ACCOUNT = """
            async () => {
              const response = await fetch('/api/v1/staff-accounts/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ tenantId: 1, loginId: 'smoke-no-such-account', password: 'smoke' }),
              });
              return response.status;
            }
            """;

    @Test
    void 瀏覽器載得到首頁並經代理打得到後端() {
        WebProfile profile = WebProfile.current();
        try (Playwright playwright = Playwright.create();
                Browser browser = profile.launch(playwright);
                BrowserContext context = profile.newContext(browser)) {
            Page page = context.newPage();

            Response home = page.navigate(BASE_URL + "/");
            assertNotNull(home, "首頁沒有回應");
            assertEquals(200, home.status(), "首頁的狀態碼");
            assertEquals(1, page.locator("#root").count(), "首頁要有 React 掛載的 #root");

            int width = (Integer) page.evaluate("window.innerWidth");
            int height = (Integer) page.evaluate("window.innerHeight");
            boolean touch = (Boolean) page.evaluate("'ontouchstart' in window");
            int loginStatus = (Integer) page.evaluate(LOGIN_WITH_UNKNOWN_ACCOUNT);

            System.out.printf(
                    "[smoke] web.profile=%s 引擎=%s %s 視窗=%dx%d 觸控=%s 首頁=%d 經代理登入=%d 位址=%s%n",
                    profile.id(), browser.browserType().name(), browser.version(),
                    width, height, touch, home.status(), loginStatus, BASE_URL);

            assertEquals(profile.width(), width, "視窗寬度");
            assertEquals(profile.height(), height, "視窗高度");
            assertEquals(profile.touch(), touch, "是否觸控");
            assertEquals(401, loginStatus, "經代理打後端登入端點的狀態碼");
        }
    }
}
