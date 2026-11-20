package com.athena.web.bdd.support;

import com.athena.web.bdd.WebProfile;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import io.cucumber.java.AfterAll;
import java.util.EnumMap;
import java.util.Map;

/**
 * 三組瀏覽器設定（{@code desktop}、{@code ipad}、{@code phone}）實際開出來的瀏覽器，整次測試共用。
 *
 * <p>瀏覽器每一組只啟動一次，第一次用到才開，所有場景跑完才關；每個場景拿到的是一個全新的
 * {@link BrowserContext}，儲存空間、cookie 與快取都和別的場景分開。這樣分是因為啟動瀏覽器要一兩秒，
 * 四百多個場景各開一次太慢，而場景之間真正不能共用的只有 context 裡的東西。
 *
 * <p>整次測試預設跑哪一組由系統屬性 {@code web.profile} 決定（見 {@link WebProfile#current()}）。
 * 場景中途要換組時用 {@link WebWorld#useProfile(WebProfile)}，各組的瀏覽器可以同時開著。
 */
public final class BrowserProfiles {

    /** 規格句型裡「電腦」那一組的寫法。 */
    public static final String DESKTOP_DEVICE = "電腦 1280 × 720 滑鼠";

    /** 規格句型裡「iPad」那一組的寫法。 */
    public static final String IPAD_DEVICE = "iPad 橫向 1180 × 820 觸控";

    /** 規格句型裡「手機」那一組的寫法，住客掃碼頁用。 */
    public static final String PHONE_DEVICE = "手機直向 393 × 852 觸控";

    private static final Map<WebProfile, Browser> BROWSERS = new EnumMap<>(WebProfile.class);

    private static Playwright playwright;

    private BrowserProfiles() {
    }

    /** 這次測試沒有另外指定時用的那一組。 */
    public static WebProfile defaultProfile() {
        return WebProfile.current();
    }

    /** 把規格句型裡的裝置說法換成瀏覽器設定；說法不認得時直接失敗。 */
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

    /** 規格句型裡這一組設定的說法，用在失敗訊息與截圖檔名以外需要給人看的地方。 */
    public static String deviceOf(WebProfile profile) {
        return switch (profile) {
            case DESKTOP -> DESKTOP_DEVICE;
            case IPAD -> IPAD_DEVICE;
            case PHONE -> PHONE_DEVICE;
        };
    }

    /** 以這一組設定開一個全新的 context。關閉由開的人負責。 */
    public static synchronized BrowserContext newContext(WebProfile profile) {
        return profile.newContext(browser(profile));
    }

    /**
     * 同上，但在這一組的設定之外再改幾項，例如瀏覽器的語言、或帶進另一個瀏覽器留下的儲存空間。
     * 視窗大小與觸控仍以這一組為準，呼叫端不要改這兩項。
     */
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

    /** 所有場景跑完後關掉瀏覽器與 Playwright 的驅動程式，不留行程。 */
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
