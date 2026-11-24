package com.athena.web.bdd;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

public enum WebProfile {

    DESKTOP("desktop", 1280, 720, false),

    IPAD("ipad", 1180, 820, true),

    PHONE("phone", 393, 852, true);

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
                SYSTEM_PROPERTY + " 只接受 desktop、ipad 或 phone，收到的是：" + id);
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
        return this == DESKTOP ? playwright.chromium() : playwright.webkit();
    }

    public Browser launch(Playwright playwright) {
        return browserType(playwright).launch();
    }

    public Browser.NewContextOptions contextOptions() {
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setViewportSize(width, height)
                .setHasTouch(touch)
                .setLocale("zh-TW");
        if (this == PHONE) {
            options.setIsMobile(true).setDeviceScaleFactor(3);
        }
        return options;
    }

    public BrowserContext newContext(Browser browser) {
        return browser.newContext(contextOptions());
    }
}
