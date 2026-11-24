package com.athena.web.bdd.support;

import com.athena.web.bdd.WebProfile;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import io.cucumber.java.AfterAll;
import java.util.EnumMap;
import java.util.Map;

public final class BrowserProfiles {

    public static final String DESKTOP_DEVICE = "電腦 1280 × 720 滑鼠";

    public static final String IPAD_DEVICE = "iPad 橫向 1180 × 820 觸控";

    public static final String PHONE_DEVICE = "手機直向 393 × 852 觸控";

    private static final Map<WebProfile, Browser> BROWSERS = new EnumMap<>(WebProfile.class);

    private static Playwright playwright;

    private BrowserProfiles() {
    }

    public static WebProfile defaultProfile() {
        return WebProfile.current();
    }

    public static WebProfile ofDevice(String device) {
        String normalized = device.strip();
        if (DESKTOP_DEVICE.equals(normalized)) {
            return WebProfile.DESKTOP;
        }
        if (IPAD_DEVICE.equals(normalized)) {
            return WebProfile.IPAD;
        }
        if (PHONE_DEVICE.equals(normalized)) {
            return WebProfile.PHONE;
        }
        throw new IllegalArgumentException("裝置只接受「" + DESKTOP_DEVICE + "」「" + IPAD_DEVICE + "」或「"
                + PHONE_DEVICE + "」，收到的是：" + device);
    }

    public static String deviceOf(WebProfile profile) {
        return switch (profile) {
            case DESKTOP -> DESKTOP_DEVICE;
            case IPAD -> IPAD_DEVICE;
            case PHONE -> PHONE_DEVICE;
        };
    }

    public static synchronized BrowserContext newContext(WebProfile profile) {
        return profile.newContext(browser(profile));
    }

    public static synchronized BrowserContext newContext(
            WebProfile profile, java.util.function.Consumer<Browser.NewContextOptions> customize) {
        Browser.NewContextOptions options = profile.contextOptions();
        customize.accept(options);
        return browser(profile).newContext(options);
    }

    private static Browser browser(WebProfile profile) {
        Browser browser = BROWSERS.get(profile);
        if (browser == null || !browser.isConnected()) {
            if (playwright == null) {
                playwright = Playwright.create();
            }
            browser = profile.launch(playwright);
            BROWSERS.put(profile, browser);
        }
        return browser;
    }

    @AfterAll
    public static synchronized void closeAll() {
        BROWSERS.values().forEach(Browser::close);
        BROWSERS.clear();
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }
}
