package com.athena.app.bdd;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;

/**
 * 情境上下文：driver lifecycle 與雙平台 accessibility id 定位。
 *
 * <p>研究決策 5／6（{@code specs/plans/001-staff-app-increment/research.md}）：
 * App 是 Flutter，Appium driver 走 vanilla <b>XCUITest（iOS）＋ UiAutomator2（Android）</b>，
 * 讀取 Flutter {@code Semantics(identifier:)} 曝露到原生 accessibility API 的節點；
 * 明確排除 {@code appium-flutter-driver}（建在已棄用的 {@code flutter_driver} SDK 上，
 * 測不了 release build）。
 *
 * <p>兩平台不共用同一個 Appium locator（決策 6）：同一個 id 字串，iOS 落在
 * {@code accessibilityIdentifier}（{@link AppiumBy#accessibilityId}），Android 落在
 * {@code resource-id}（要用 {@code resource-id} 比對，不能用 {@code accessibilityId()}）。
 * 所有 step 定位一律經由 {@link #byTestId(String)}，不手刻 locator。
 *
 * <p><b>TODO（決策 7，測試後門）</b>：裝置時間注入、強制離線、強制第 N 張照片上傳失敗
 * 三項能力，由 App 端以 {@code --dart-define} 隔離出的 localhost debug HTTP endpoint
 * 提供；本輪只留介面位置，{@link #start()} 尚未真正連線 Appium server，各 step 的
 * body 先丟 {@link io.cucumber.java.PendingException}，不會呼叫到這裡。
 *
 * <p>本機 iOS simulator runtime 還在下載，Appium 實際連線留待下一輪補上
 * （本輪驗收只到 cucumber dry-run 全綠）。
 */
public class AppiumWorld {

    /** 系統屬性 {@code appium.platform} 的合法值：ios｜android。預設 ios（本機先行補的平台）。 */
    public static final String PLATFORM_IOS = "ios";
    public static final String PLATFORM_ANDROID = "android";

    private final String platform;
    private final String androidAppPackage;
    private AppiumDriver driver;

    public AppiumWorld() {
        this.platform = System.getProperty("appium.platform", PLATFORM_IOS);
        this.androidAppPackage = System.getProperty("appium.android.app-package", "com.athena.tms.app");
    }

    /**
     * TODO：依 {@link #platform} 建立 {@code XCUITestOptions}（iOS）或
     * {@code UiAutomator2Options}（Android），連線到 {@code appium.server.url}
     * 系統屬性指定的 Appium server（預設 {@code http://127.0.0.1:4723}），啟動已編譯的
     * Flutter App。Appium 黑箱驅動已編譯的 App，runner 決策與 App 實作技術解耦
     * （決策 5 的 rationale）。
     *
     * <p>本輪尚未連線實機／模擬器，呼叫此方法會直接丟例外；所有 step definitions 目前
     * 都在呼叫到這裡之前就先丟 {@code PendingException}。
     */
    public void start() {
        throw new UnsupportedOperationException(
                "AppiumWorld.start() 尚未實作：等 iOS simulator runtime 下載完成、"
                        + "完成決策 5 的一日 Appium spike 之後才接上真正的 driver。");
    }

    /** 目前情境使用中的 driver；尚未 {@link #start()} 過時丟例外，不回傳 null。 */
    public AppiumDriver driver() {
        if (driver == null) {
            throw new IllegalStateException("AppiumWorld 尚未 start()，沒有可用的 driver");
        }
        return driver;
    }

    public void stop() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }

    /**
     * 依平台分流定位 accessibility id（決策 6，呼應 {@code ui-plan.md} 的「accessibility id 約定」）。
     * id 字串兩平台相同（dot 分段＋kebab-case，例如 {@code task.handover.unread-dot}），
     * 但 Flutter 的 {@code Semantics(identifier:)} 在 iOS 映射 {@code accessibilityIdentifier}、
     * 在 Android 映射 {@code resource-id}，所以不能兩平台共用同一個 locator 寫法。
     *
     * <p>重複元素（列表項）的 id 帶執行期變數（例如 {@code pool.card.305.status-tag}），
     * 呼叫端自己把房號／單號等識別值拼進 id 字串再傳進來。
     *
     * @param id ui-plan.md 的 accessibility id 約定字串
     */
    public By byTestId(String id) {
        if (PLATFORM_ANDROID.equals(platform)) {
            return AppiumBy.id(androidAppPackage + ":id/" + id);
        }
        return AppiumBy.accessibilityId(id);
    }

    public String platform() {
        return platform;
    }
}
