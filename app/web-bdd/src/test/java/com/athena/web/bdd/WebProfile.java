package com.athena.web.bdd;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

/**
 * 櫃檯 Web 驗收的兩組瀏覽器設定，用系統屬性 {@code web.profile} 選（{@code desktop}、{@code ipad}），
 * 沒給時是 {@code desktop}。
 */
public enum WebProfile {

    /** 電腦 1280 × 720 滑鼠：Chromium。 */
    DESKTOP("desktop", 1280, 720, false),

    /** iPad 橫向 1180 × 820 觸控：WebKit（近似 iPad 上的 Safari，不等於真機）。 */
    IPAD("ipad", 1180, 820, true);

    public static final String SYSTEM_PROPERTY = "web.profile";

    private final String id;
    private final int width;
    private final int height;
    private final boolean touch;

    WebProfile(String id, int width, int height, boolean touch) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.touch = touch;
    }

    /** 依系統屬性 {@code web.profile} 取設定；值不認得時直接失敗，不默默退回 desktop。 */
    public static WebProfile current() {
        return of(System.getProperty(SYSTEM_PROPERTY, DESKTOP.id));
    }

    public static WebProfile of(String id) {
        for (WebProfile profile : values()) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        throw new IllegalArgumentException(
                SYSTEM_PROPERTY + " 只接受 desktop 或 ipad，收到的是：" + id);
    }

    public String id() {
        return id;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean touch() {
        return touch;
    }

    public BrowserType browserType(Playwright playwright) {
        return this == IPAD ? playwright.webkit() : playwright.chromium();
    }

    public Browser launch(Playwright playwright) {
        return browserType(playwright).launch();
    }

    /**
     * 瀏覽器的語言固定是繁體中文，和台灣櫃檯的裝置一樣。
     * 櫃檯 Web 在這台裝置還沒有人選過語言時跟瀏覽器的語言，不固定的話會跟著跑測試那台電腦的系統語言，
     * 沒交代語言的場景就可能變成英文介面。要測別的瀏覽器語言的場景自己另開一個改了語言的瀏覽器。
     */
    public Browser.NewContextOptions contextOptions() {
        return new Browser.NewContextOptions()
                .setViewportSize(width, height)
                .setHasTouch(touch)
                .setLocale("zh-TW");
    }

    public BrowserContext newContext(Browser browser) {
        return browser.newContext(contextOptions());
    }
}
