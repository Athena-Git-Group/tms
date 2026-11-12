package com.athena.app.bdd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.support.ui.FluentWait;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 情境上下文：driver lifecycle、雙平台 accessibility id 定位，以及 F3（點開推播的去處）
 * 用到的測試後門／推播操作。
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
 * <p>啟動一個 XCUITest session 要花數秒到十幾秒（WDA 要先跑起來），12 個情境各開一次
 * session 會把整個 suite 拖慢很多，所以這裡把 {@code driver} 設成 static，讓所有情境
 * 共用同一個 session；每個情境要的「乾淨狀態」改成用 {@link #resetAppState()} 做
 * {@code simctl terminate}＋{@code launch} 換一個全新的 App process，不必整個重開
 * session。JVM 關閉時用 shutdown hook 補 {@code driver.quit()}，避免忘了收尾留下孤兒
 * session；Appium server 本身與 simulator 的生命週期不歸這裡管，是
 * {@code app/bdd/run-f3.sh} 的責任。
 *
 * <p>測試後門（{@code TEST_BACKDOOR=true} 建置才會起的 localhost:9099 HTTP server，見
 * {@code app/mobile/lib/core/test_backdoor.dart}）：iOS simulator 的 localhost 與本機
 * host 共享同一個 loopback 介面，所以這裡的 Java 程式可以直接打
 * {@code 127.0.0.1:9099}，不用額外轉接。後門只負責裝置側注入（時間、離線、上傳失敗）
 * 與 {@code GET /state} 探活；測試資料（員工、任務、工務單）一律走
 * {@link BackendChannel} 打真後端，App 畫面再透過自己的 API client 讀回同一份真相。
 */
public class AppiumWorld {

    /** 系統屬性 {@code appium.platform} 的合法值：ios｜android。預設 ios（本機先行補的平台）。 */
    public static final String PLATFORM_IOS = "ios";
    public static final String PLATFORM_ANDROID = "android";

    private static final int BACKDOOR_PORT = 9099;
    private static final Duration BACKDOOR_READY_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration NOTIFICATION_FIND_TIMEOUT = Duration.ofSeconds(4);
    /**
     * 掃清單或找元素時最多往下捲幾屏。劇本裡最長的清單（待認領池）也只有個位數筆，
     * 捲這麼多屏之後還收不到新東西，就是畫面上真的沒有，再捲下去只是把失敗拖慢。
     */
    private static final int MAX_LIST_SCROLLS = 6;

    /** 從推進去的畫面退回首頁時，最多按幾次導覽列的「返回」。 */
    private static final int MAX_BACK_TAPS = 3;

    /** 登入送出最多試幾次（第一次落空時原樣重送）。 */
    private static final int MAX_LOGIN_ATTEMPTS = 2;
    /**
     * 等登入後的首頁出現可以等多久。送出帳密到首頁之間隔著一次後端來回，整套 suite
     * 一起跑時模擬器負載比單跑一個模組高，這段來回偶發會拖到十幾秒；等太短只會把
     * 機器忙不過來誤報成登入壞掉。
     */
    private static final Duration LOGIN_TIMEOUT = Duration.ofSeconds(25);

    private final String platform;
    private final String androidAppPackage;

    private final String appiumServerUrl;
    private final String udid;
    private final String bundleId;
    private final String appPath;

    /** 對真後端的資料通道（fixture 落地與權威讀回）；連線懶建，dry-run 不需要後端在跑。 */
    private final BackendChannel backend = new BackendChannel();

    /**
     * 本情境已建立的員工，姓名 → 帳號。後續的 Given／Then 都用姓名指涉員工
     * （「"阿美" 名下有以下任務」），要能回查出 fixture 給她的 staff id 與登入帳密。
     */
    private final Map<String, BackendChannel.CreatedStaff> staffByName = new LinkedHashMap<>();

    /**
     * 還沒在 App 裡按過「開始」的房號。情境要求起始狀態是進行中、而任務被改種成
     * 待開始時記在這裡，進任務頁時補按一次開始（見 {@link #startInAppIfPending}）。
     */
    private final java.util.Map<String, DeferredStart> roomsToStartInApp = new java.util.LinkedHashMap<>();

    /**
     * 上一次送出的推播 payload 檔案與標題。banner 自動收起的時間很短，{@link #tapPushNotification()}
     * 第一次找不到時要能照原樣重送同一則推播再試一次，所以要留著。
     */
    private Path lastPushPayloadFile;
    private String lastPushTitle;

    /**
     * 通知中心情境的當事員工（通知的收件人＝打開通知中心的人）。通知的 Then 句
     * （「X 的通知顯示未讀藍點」等）不帶員工參數，後端讀回要靠這裡記住的人把查詢
     * 鎖在她自己的通知上，不然同租戶別人的同標題通知會被誤撈進來。
     */
    private BackendChannel.CreatedStaff notificationViewer;

    /**
     * 目前已在 App 裡登入的員工；App 還停在登入畫面時為 null。登入殼落地後
     * （main.dart 起始畫面一律是登入，測試建置不再跳過），所有「以該員工身分
     * 操作畫面」的 step 都要先經 {@link #ensureLoggedInAs} 換到她的身分。
     */
    private BackendChannel.CreatedStaff loggedInStaff;

    /**
     * App 目前打開的是哪一間房的任務頁；還沒打開過任何一張任務頁時為 null。
     * 讀法與用途見 {@link #currentTaskRoomNo()}。
     */
    private String currentTaskRoomNo;

    /**
     * 裝置切到離線的那一刻，App 本機待送出佇列的筆數。
     *
     * <p>給「這次操作沒有留在手機上等補送」用：那一句要比的是「操作前後佇列筆數相同」，
     * 操作之後才讀畫面是比不出來的，所以基準要在動作發生之前就先量好。離線視窗一開始
     * 就量，是因為這一句服務的動作（恢復處理、開始、認領、延後）全都發生在離線之後，
     * 中間沒有別的動作會動到佇列。
     *
     * <p>這個欄位分三種狀態，三者意思不同不可混用：null 代表這個情境還沒切過離線，
     * 根本沒有離線視窗；空的 {@code OptionalInt} 代表切離線的那一刻畫面上找不到離線列，
     * 筆數沒量到；有值才是真的量到的筆數。沒量到絕不記成 0——記成 0 的話，手機上明明
     * 押著幾筆的情境會拿 0 去比，比出來的差值跟這次操作無關，錯誤訊息還會指向錯的原因。
     */
    private OptionalInt offlineWindowPendingCount;

    /**
     * 裝置切到離線的那一刻，後端每一間房那一筆任務的狀態（房號 -> {@code tasks.state}）；
     * 這個情境還沒切過離線時為 null。
     *
     * <p>同樣給「這次操作沒有留在手機上等補送」用的另一面：後端該任務狀態要與操作前
     * 一樣。切離線時任務頁還沒打開（{@link #currentTaskRoomNo} 還是 null），所以這裡
     * 把當下全部房務任務的狀態都記下來，由斷言時再挑出當事那一間。
     */
    private Map<String, String> offlineWindowTaskStates;

    /**
     * 裝置切到離線的那一刻，後端每一張單的狀態快照（鍵見 {@link #backendUnitStatesNow}）。
     * 主管在離線時被擋下的動作對象不是任務頁上的房（查房從清單開、驗收對的是工務單），
     * {@link #offlineWindowTaskStates} 以房號指認不到它們，所以另外把任務與工務單
     * 連驗收人一起記下來，斷言時整份比對：被擋在送出之前的動作，後端一張單都不該變。
     */
    private Map<String, String> offlineWindowUnitStates;

    private static volatile AppiumDriver driver;
    private static volatile boolean shutdownHookRegistered;

    public AppiumWorld() {
        this.platform = System.getProperty("appium.platform", PLATFORM_IOS);
        this.androidAppPackage = System.getProperty("appium.android.app-package", "com.athena.tms.app");
        this.appiumServerUrl = System.getProperty("appium.server.url", "http://127.0.0.1:4723");
        this.udid = System.getProperty("appium.ios.udid", "79F32D5C-7A71-47D6-AA8D-8142E68CFF48");
        this.bundleId = System.getProperty("appium.ios.bundle-id", "com.athena.tms.tmsStaffApp");
        this.appPath = System.getProperty("appium.ios.app-path", "");
    }

    /**
     * 每個情境的 {@code @Before}：建立（第一次）或沿用（之後每個情境）共用 session，
     * 再把 App 換成乾淨狀態，讓每個情境看到的都是全新的一輪 fixture。
     */
    @Before(order = 0)
    public void before() {
        start();
        ensureSessionAlive();
        resetAppState();
    }

    /**
     * 共用 session 可能被前一個情境的失敗弄死（WDA 中斷、逾時）。壞一條情境不該連鎖
     * 拖垮後面所有情境，所以每個情境開場先打一個輕量指令探活，死了就丟掉重建。
     */
    private void ensureSessionAlive() {
        try {
            driver().executeScript("mobile: activeAppInfo");
        } catch (RuntimeException e) {
            AppiumDriver dead = driver;
            driver = null;
            if (dead != null) {
                try {
                    dead.quit();
                } catch (Exception ignored) {
                    // session 早斷了，quit 失敗是預期內。
                }
            }
            start();
        }
    }

    @After(order = 0)
    public void after() {
        // 刻意不在這裡 quit() driver：session 給下一個情境繼續用，整個 suite
        // 跑完才收尾交給 run-f3.sh（它會關 appium server、shutdown simulator）。
    }

    /**
     * 跟 Appium server 建一個 session。刻意不帶 {@code app} capability，靠
     * {@code noReset=true} 假設 App 已經裝好——裝 App 這件事交給呼叫端（例如
     * {@code run-f3.sh} 的 {@code flutter build} + {@code simctl install}），
     * 這裡只負責「連上去操作」。要讓 session 自己裝 App 的話，用
     * {@code appium.ios.app-path} 系統屬性指定 .app 路徑。
     */
    public synchronized void start() {
        if (driver != null) {
            return;
        }
        if (PLATFORM_ANDROID.equals(platform)) {
            throw new UnsupportedOperationException(
                    "AppiumWorld.start()：Android（UiAutomator2）driver 本輪未實作，F3 只做 iOS。");
        }

        XCUITestOptions options = new XCUITestOptions();
        options.setPlatformName("iOS");
        options.setUdid(udid);
        options.setBundleId(bundleId);
        options.setNoReset(true);
        options.setNewCommandTimeout(Duration.ofSeconds(180));
        // 這台機器是 headless 環境：simctl privacy grant notifications 會因 TCC 建檔失敗而
        // 授不了權，App 首次啟動的通知授權彈窗沒人按就永遠沒有通知橫幅可點。
        // 交給 WDA 自動按掉所有系統彈窗（含通知授權的「允許」）。
        options.setAutoAcceptAlerts(true);
        if (!appPath.isBlank()) {
            options.setApp(appPath);
        }

        try {
            driver = new IOSDriver(new URL(appiumServerUrl), options);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "AppiumWorld.start()：連不上 Appium server（" + appiumServerUrl + "）或建立 session 失敗。"
                            + "先確認 appium 有在跑、simulator 已 boot、App 已用 TEST_BACKDOOR=true 建置並安裝。",
                    e);
        }
        registerShutdownHook();
    }

    private static synchronized void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            AppiumDriver d = driver;
            if (d != null) {
                try {
                    d.quit();
                } catch (Exception ignored) {
                    // JVM 正在關閉，session 可能早就斷了，吞掉即可，不影響關閉流程。
                }
            }
        }));
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
     * 情境間重啟 App 拿乾淨狀態。選 {@code terminate}＋{@code launch}（整個 process
     * 重來）而不是單純把 App 切到背景再切回來，是因為 Dart 側的 {@code TestHooks}
     * singleton（seed 進去的員工／任務 fixture）只存在記憶體裡，process 不重啟就
     * 會把前一個情境的資料帶到下一個情境。
     */
    private void resetAppState() {
        staffByName.clear();
        lastPushPayloadFile = null;
        lastPushTitle = null;
        notificationViewer = null;
        loggedInStaff = null;
        currentTaskRoomNo = null;
        offlineWindowPendingCount = null;
        offlineWindowTaskStates = null;
        offlineWindowUnitStates = null;
        // 真後端的資料在情境之間會留下來（不像以前的記憶體 fixture 隨 App process 消失），
        // 不清掉會讓上一個情境的任務繼續出現在待認領池；清法與 tms 的 ResetHooks 相同。
        backend.resetBusinessData();
        // 上一個情境可能用「現在時間是」把後端時鐘撥走了，不放回去的話這個情境的「今天」「下班了沒」
        // 會沿用那個時刻。
        backend.resetBackendClock();

        runSimctl("terminate", udid, bundleId); // 第一個情境時 App 還沒啟動過，失敗沒關係
        runSimctlRequired("launch", udid, bundleId);
        waitForBackdoorReady();
    }

    /**
     * App process 重開之後，Dart 側要先跑到 {@code main()} 把 {@code TestBackdoor} server
     * 重新綁好 port，這中間有個短暫的空檔；用重試代替固定 sleep，是因為這段空檔長短跟
     * 模擬器當下的負載有關，固定等一個數字容易不是等太久、就是等不夠。
     * 探活打 {@code GET /state}（後門唯一的讀取端點，不帶任何副作用）。
     */
    private void waitForBackdoorReady() {
        long deadline = System.nanoTime() + BACKDOOR_READY_TIMEOUT.toNanos();
        IOException lastError = null;
        while (System.nanoTime() < deadline) {
            try {
                httpGet("/state");
                return;
            } catch (IOException e) {
                lastError = e;
                sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待測試後門就緒時被中斷", e);
            }
        }
        throw new IllegalStateException(
                "等測試後門（127.0.0.1:" + BACKDOOR_PORT + "）就緒逾時——App 是否真的用 "
                        + "--dart-define=TEST_BACKDOOR=true 建置？", lastError);
    }

    /** 後端通道，給 stepdef 直接做權威讀回斷言或建立這裡沒有包裝的 fixture。 */
    public BackendChannel backend() {
        return backend;
    }

    /**
     * 確保 App 目前是以這位員工的身分登入；已經是她就不動。App 每個情境都從
     * 登入畫面開始（{@code main.dart} 的起始畫面一律是登入，測試建置不再跳過
     * 登入殼），所以需要身分的 step 先走一次共用手機模式的 UI 登入：清掉記住
     * 帳號 → 輸入員工編號與密碼 → 送出 → 等首頁（{@code home.screen}）。
     * fixture 的帳密都是 ASCII（fx-…／demo1234），{@link #typeInto} 直接 sendKeys。
     *
     * <p>同一情境要求換成另一位員工時直接噴錯：推播／通知中心的場景都只有一位
     * 當事員工，換人代表劇本或 stepdef 寫錯，默默重登會把錯誤藏起來。
     *
     * <p>送出後等不到首頁、而畫面還停在登入頁的那一次，重點一次送出鍵：這是連跑整個套件
     * 時的偶發（送出的那一下落在重繪之間，欄位有值但請求沒發出去），不是帳密錯。只重點
     * 送出鍵，帳密維持原樣不重打：{@link #typeInto} 的 sendKeys 不清空欄位，重打會把員工
     * 編號接成兩份，後端回「員工編號或密碼不正確」，真正的原因被一個看起來像帳密錯的
     * 錯誤橫幅蓋掉。重送之後仍停在登入頁才讓它紅，訊息裡附現場元素樹。
     */
    public void ensureLoggedInAs(BackendChannel.CreatedStaff staff) {
        if (loggedInStaff != null) {
            if (!loggedInStaff.id().equals(staff.id())) {
                throw new IllegalStateException("App 已以「" + loggedInStaff.name()
                        + "」登入，情境中途不支援換成「" + staff.name() + "」");
            }
            return;
        }
        Duration timeout = LOGIN_TIMEOUT;
        String landing = landingScreenId(staff);
        // 要登入的不是那些「欠一次在 App 按開始」的任務的負責人時（主管看別人清的房），
        // 那幾筆進行中永遠沒有機會在 App 補上，所以改由後端把它們開工——介面根 dsl
        // 「名下有以下任務」：操作者是別人時只建資料、不換登入者。
        settleDeferredStartsViaBackendExcept(staff);
        postBackdoorJson("/remembered-account", "{\"employee\":null}");
        typeInto("login.shared-device.staff-id-field", timeout, staff.loginId());
        typeInto("login.password-field", timeout, staff.password());
        for (int attempt = 1; attempt <= MAX_LOGIN_ATTEMPTS; attempt++) {
            dismissForgotPasswordAlert();
            tapWhenEnabled("login.submit-button", timeout);
            if (waitForTestIdQuietly(landing, timeout) != null) {
                loggedInStaff = staff;
                return;
            }
            if (driver().findElements(byTestId("login.submit-button")).isEmpty()) {
                break;
            }
        }
        waitForTestId(landing, timeout);
        loggedInStaff = staff;
    }

    /**
     * 關掉蓋在登入頁上的「忘記密碼」提示。
     *
     * <p>「忘記密碼？」就排在送出鍵下面一點，送出那一下偶爾會落在它上面（機器負載高時
     * 比較容易中）。它一彈出來就是 modal，Flutter 會把底下整頁的 semantics 擋掉，於是送出
     * 鍵連找都找不到，重送那一輪必然無效，最後空等一個登入逾時，紅成「等不到首頁」。
     * 先關掉它，重送才真的有機會成功。
     */
    private void dismissForgotPasswordAlert() {
        if (driver().findElements(byTestId("login.forgot-password-alert")).isEmpty()) {
            return;
        }
        for (WebElement dismiss
                : driver().findElements(byTestId("login.forgot-password-alert.dismiss-button"))) {
            dismiss.click();
        }
    }

    /**
     * 登入成功後會停在哪個首頁。App 先看角色再看部門分流（{@code landing.dart}：主管走
     * 主管首頁，不是主管的工務走工務首頁，其他部門走房務首頁），所以等待條件要跟著
     * 員工的角色與部門走——一律等房務首頁會讓工務與主管的登入永遠逾時。
     */
    private static String landingScreenId(BackendChannel.CreatedStaff staff) {
        if ("manager".equals(staff.role())) {
            return "manager.home";
        }
        return "engineering".equals(staff.dept()) ? "engHome.screen" : "home.screen";
    }

    /**
     * 確保 App 已以這位工務的身分登入，並停在工務待認領（{@code engPool}）。
     * 待認領不是登入後的落地畫面，要從工務首頁再點一次入口；已經在待認領上
     * 時不重複點。
     */
    public void ensureOnEngineeringPool(BackendChannel.CreatedStaff staff) {
        ensureLoggedInAs(staff);
        Duration timeout = Duration.ofSeconds(10);
        if (!driver().findElements(byTestId("engPool.screen")).isEmpty()) {
            return;
        }
        waitForTestId("engHome.screen", timeout);
        waitForLabelContaining("待認領", timeout).click();
        waitForTestId("engPool.screen", timeout);
    }

    /**
     * 確保 App 已以這位工務的身分登入，並停在工務首頁（{@code engHome}）。登入後就落在
     * 這一頁，所以只有前面的句子把畫面切走時才要切回來；已經在首頁上時不重複點。
     */
    public void ensureOnEngineeringHome(BackendChannel.CreatedStaff staff) {
        ensureLoggedInAs(staff);
        Duration timeout = Duration.ofSeconds(10);
        if (!driver().findElements(byTestId("engHome.screen")).isEmpty()) {
            return;
        }
        waitForLabelContaining("首頁", timeout).click();
        waitForTestId("engHome.screen", timeout);
    }

    /**
     * 確保 App 已以這位員工的身分登入，並停在房務待認領（{@code pool}）。待認領不是
     * 登入後的落地畫面，要從首頁再點一次入口；已經在待認領上時不重複點（與
     * {@link #ensureOnEngineeringPool} 同一套作法）。
     */
    public void ensureOnPool(BackendChannel.CreatedStaff staff) {
        ensureLoggedInAs(staff);
        Duration timeout = Duration.ofSeconds(10);
        if (!driver().findElements(byTestId("pool.screen")).isEmpty()) {
            return;
        }
        waitForTestId("home.screen", timeout);
        waitForLabelContaining("待認領", timeout).click();
        waitForTestId("pool.screen", timeout);
    }

    /**
     * 點一個會換頁／開面板的元素，並確認目標畫面真的出現；沒出現就再點一次。
     *
     * <p>有些入口（待認領的「開工單」FAB）在列表還在重排時點下去會整個落空：Appium 回報
     * 點擊成功，畫面卻留在原地，接著等表單元素就逾時。這種落空重點一次就好，所以這裡只在
     * 目標畫面沒出現時補一次，仍然沒有才讓 {@link #waitForTestId} 以現場元素樹失敗。
     */
    public void tapUntilVisible(String tapId, String expectedId, Duration timeout) {
        tapTestId(tapId, timeout);
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            if (!driver().findElements(byTestId(expectedId)).isEmpty()) {
                return;
            }
            sleep(200);
        }
        if (!driver().findElements(byTestId(tapId)).isEmpty()) {
            tapTestId(tapId, timeout);
        }
        waitForTestId(expectedId, timeout);
    }

    /**
     * 確保 App 停在房務首頁。已經在首頁就不動；停在推進去的畫面（任務頁、工務單詳情……）
     * 時，照員工自己的動線按導覽列的「返回」一路退回來。
     *
     * <p>詳情頁的返回鈕是自家的圓鈕（{@code nav.back-button}），還沒換過去的畫面仍是
     * 系統預設的返回鈕（accessibility id {@code Back}），所以兩個都找。
     *
     * <p>只等 {@code home.screen} 是不夠的：等待不會把畫面帶回去。離線完成任務之後 App
     * 留在任務頁，接著那句「在 App 打開首頁」就會在任務頁上空等到逾時，紅在
     * 「等不到 home.screen」而不是劇本真的要驗的事。
     *
     * <p>完成慶祝那張卡蓋著時先按卡上的「回到首頁」。那張卡是疊在任務頁上的覆蓋層，
     * 它的遮罩會收下落在卡外面的每一下點擊，所以底下那顆返回鈕按不到；員工自己的動線
     * 也是按卡上的鍵離開，不是去按被蓋住的返回鈕。
     */
    public void ensureOnHome(Duration timeout) {
        for (int i = 0; i < MAX_BACK_TAPS; i++) {
            if (!driver().findElements(byTestId("home.screen")).isEmpty()) {
                return;
            }
            if (!driver().findElements(byTestId("celebrate.screen")).isEmpty()) {
                tapTestId("celebrate.back-home-button", timeout);
                sleep(300);
                continue;
            }
            List<WebElement> back = driver().findElements(byTestId("nav.back-button"));
            if (back.isEmpty()) {
                back = driver().findElements(AppiumBy.accessibilityId("Back"));
            }
            if (back.isEmpty()) {
                break;
            }
            back.get(0).click();
            sleep(300);
        }
        waitForTestId("home.screen", timeout);
    }

    public void ensureOnTaskDetail(BackendChannel.CreatedStaff staff, String roomNo) {
        ensureLoggedInAs(staff);
        currentTaskRoomNo = roomNo;
        Duration timeout = Duration.ofSeconds(10);
        if (driver().findElements(byTestId("task.screen")).isEmpty()
                || driver().findElements(byTestId("task.nav." + roomNo)).isEmpty()) {
            ensureOnHome(timeout);
            tapTestId("home.task." + roomNo + ".card", timeout);
            waitForTestId("task.screen", timeout);
        }
        startInAppIfPending(roomNo, timeout);
    }

    /**
     * 進該房的任務頁、點「查一下」，等推薦問題載入完成（查一下 dsl.md 的
     * 「打開查一下」，Given 與 When 兩句同一條路）。
     *
     * <p>推薦問題是打開面板後才向後端要的，載入前畫面上一則也沒有；不等第一則出現
     * 就往下走，接著的順序斷言會掃到空清單，紅在「還沒載完」而不是真的內容不符。
     * 已經打開著就不重複點——Given 鋪好面板、When 再點一次推薦問題是常見的接法。
     */
    public void openAskPanel(BackendChannel.CreatedStaff staff, String roomNo) {
        Duration timeout = Duration.ofSeconds(10);
        if (!driver().findElements(byTestId("ask.sheet")).isEmpty()) {
            return;
        }
        ensureOnTaskDetail(staff, roomNo);
        tapTestId("task.ask-button", timeout);
        waitForTestId("ask.suggestion.0", timeout);
    }

    /**
     * 把「進行中」這個起始狀態補齊：在任務頁按一次「開始」，讓後端自己把任務轉成
     * {@code doing}。
     *
     * <p>{@link #seedTasks} 把這種任務先種成待開始，真正的開工交給這裡用 App 的主按鈕
     * 完成。按鈕打的是正式的開工端點，後端落地的狀態與事件和員工自己操作完全一樣。
     *
     * <p>按完要等後端真的翻成 {@code doing} 才往下走。開工是一次 HTTP 來回，畫面先
     * 回應、後端稍後才落地的話，接著的中斷操作會打在還是待開始的任務上，被後端以
     * 狀態不符擋掉。
     */
    private void startInAppIfPending(String roomNo, Duration timeout) {
        if (roomsToStartInApp.remove(roomNo) == null) {
            return;
        }
        tapTestId("task.primary-button", timeout);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if ("doing".equals(String.valueOf(taskRowByRoomNo(roomNo).get("state")))) {
                return;
            }
            sleep(200);
        }
        throw new AssertionError("在 App 按下「開始」後，後端沒有把「" + roomNo
                + "」轉成進行中（逾時 " + timeout.getSeconds() + "s）");
    }

    /**
     * 把還欠的那幾次「在 App 按開始」現在就補完。
     *
     * <p>情境把裝置切到離線之前要先呼叫這裡。開工是離線時被擋下來的動作之一，裝置一旦
     * 離線，那一筆進行中就再也補不起來，情境會停在待開始上驗一個它沒有要驗的狀態。
     *
     * <p>這裡只補開始。一筆都不欠時它什麼也不做，不會順帶把登入的身分建立起來——
     * 離線前要有人登入由 {@link #goOffline()} 負責。
     */
    public void settleDeferredStarts() {
        for (Map.Entry<String, DeferredStart> entry :
                new java.util.LinkedHashMap<>(roomsToStartInApp).entrySet()) {
            ensureOnTaskDetail(entry.getValue().staff(), entry.getKey());
        }
    }

    /** 欠一次「在 App 按開始」的那筆任務掛在誰名下。 */
    private record DeferredStart(BackendChannel.CreatedStaff staff) {
    }

    /**
     * 把不屬於這位員工的「欠一次在 App 按開始」任務改由後端開工（{@code POST /tasks/{id}/start}），
     * 屬於她的留著，等她進任務頁時照原路在 App 按。
     *
     * <p>同一情境只允許一位登入者，別人名下的那幾筆一旦由她登入就再也補不起來；劇本要的只是
     * 「那張單在後端是進行中」這個真相（主管首頁看小美的進行中），不是她按下按鈕的動線。
     */
    private void settleDeferredStartsViaBackendExcept(BackendChannel.CreatedStaff loggingIn) {
        for (Map.Entry<String, DeferredStart> entry :
                new java.util.LinkedHashMap<>(roomsToStartInApp).entrySet()) {
            BackendChannel.CreatedStaff owner = entry.getValue().staff();
            if (owner.id().equals(loggingIn.id())) {
                continue;
            }
            Map<String, Object> row = taskRowByRoomNo(entry.getKey());
            backend.postTaskAction((UUID) row.get("id"), "start", owner);
            roomsToStartInApp.remove(entry.getKey());
        }
    }

    /**
     * 把裝置切到無網路，並記下這個離線視窗的開場基準。
     *
     * <p>基準包含兩項：App 本機待送出佇列的筆數，以及後端每一筆房務任務的狀態。
     * 這兩項在動作做完之後就再也讀不回來，所以必須在動作之前量。為什麼非比這兩項
     * 不可，見「這次操作沒有留在手機上等補送」那一句的說明。
     *
     * <p>筆數讀的是測試後門的 {@code pendingSyncCount}，不是畫面上那一列。離線列只掛在
     * 少數幾個頁面、而且會被覆蓋層與對話框蓋掉，拿它當基準會把「這一頁沒有那一列」
     * 混進「佇列是空的」。後門回的是 App 自己的佇列長度，任何一頁都讀得到。
     *
     * <p>基準只在「由連線轉離線」的那一次量。已經在離線視窗裡時再呼叫一次只是把裝置
     * 維持在離線，不重量：重量會把視窗開場之後押進佇列的那幾筆算進基準，等於拿操作後
     * 的筆數當操作前的筆數。
     *
     * <p>還沒有人登入時，先以劇本裡那位現場員工登入，再斷網。App 依設計拒絕離線登入
     * （登入頁一偵測到離線就只彈提示，連請求都不發），所以身分只能在斷網之前建立；
     * 劇本寫「名下有以下任務、手機目前離線、做一個需要身分的動作」而沒有一句登入時，
     * 少了這一步，之後每一句都會停在登入頁的離線提示上。
     */
    public void goOffline() {
        boolean alreadyInOfflineWindow = offlineWindowPendingCount != null;
        if (loggedInStaff == null) {
            ensureLoggedInAs(onlyOperator());
        }
        postBackdoorJson("/offline", "{\"offline\":true}");
        if (alreadyInOfflineWindow) {
            return;
        }
        offlineWindowPendingCount = OptionalInt.of(pendingSyncCountInApp());
        offlineWindowTaskStates = taskStatesByRoomNo();
        offlineWindowUnitStates = backendUnitStatesNow();
    }

    /**
     * 這個離線視窗開場時後端每一張單的狀態快照；沒有離線視窗時噴錯（理由同
     * {@link #offlineWindowPendingCount()}）。
     */
    public Map<String, String> offlineWindowUnitStates() {
        if (offlineWindowUnitStates == null) {
            throw new IllegalStateException(
                    "目前這個離線視窗沒有量到開場的後端狀態，無從比對操作前後的狀態。"
                            + "手機要嘛還沒切到離線，要嘛切過之後又恢復連線把基準清掉了");
        }
        return offlineWindowUnitStates;
    }

    /**
     * 權威讀回：本情境每一張單目前的狀態，任務以 {@code task:{房號或任務 id}}、工務單以
     * {@code ticket:{單號}} 為鍵；工務單的值連驗收人與驗收時間一起（驗收不改 state，只寫
     * {@code repair_tickets.verified_*}），主管驗收被擋下有沒有偷偷送出才看得出來。
     */
    public Map<String, String> backendUnitStatesNow() {
        long tenantId = backend.ensureTenant();
        Map<String, String> states = new LinkedHashMap<>();
        for (Map<String, Object> row : backend.queryRows("""
                SELECT t.id, t.state, t.type, r.room_no FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? ORDER BY t.created_at, t.id
                """, tenantId)) {
            String key = "repair".equals(String.valueOf(row.get("type")))
                    ? "task:" + row.get("id") : "task:" + row.get("room_no");
            states.put(key, String.valueOf(row.get("state")));
        }
        for (Map<String, Object> row : backend.queryRows("""
                SELECT rt.ticket_no, t.state, rt.verified_by, rt.verified_at FROM repair_tickets rt
                  JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                 WHERE rt.tenant_id = ? ORDER BY rt.ticket_no
                """, tenantId)) {
            states.put("ticket:" + row.get("ticket_no"), row.get("state") + "|verified_by="
                    + row.get("verified_by") + "|verified_at=" + row.get("verified_at"));
        }
        return states;
    }

    /**
     * 把裝置切回有網路，並丟掉這個離線視窗的開場基準。
     *
     * <p>基準只對它被量下來的那一段離線視窗有效。恢復連線後不清掉的話，
     * 「離線、恢復連線、再離線、下斷言」的情境會拿第二段離線視窗的現況去比第一段
     * 留下的陳舊筆數，比出來的差值跟這次操作無關。
     */
    public void goOnline() {
        postBackdoorJson("/offline", "{\"offline\":false}");
        offlineWindowPendingCount = null;
        offlineWindowTaskStates = null;
        offlineWindowUnitStates = null;
    }

    /**
     * 這個離線視窗開場時，App 本機待送出佇列的筆數。
     *
     * <p>根本沒有離線視窗時直接噴錯，那是劇本漏了把手機切到離線，或是切過之後又恢復
     * 連線把基準清掉了。不許自行補預設值：補小了會讓比對筆數的斷言永遠通過。
     */
    public OptionalInt offlineWindowPendingCount() {
        if (offlineWindowPendingCount == null) {
            throw new IllegalStateException(
                    "目前這個離線視窗沒有量到開場的待送出筆數，無從比對操作前後的筆數。"
                            + "手機要嘛還沒切到離線，要嘛切過之後又恢復連線把基準清掉了");
        }
        return offlineWindowPendingCount;
    }

    /**
     * 這個離線視窗開場時，後端某房號那一筆任務的狀態。
     *
     * <p>沒有基準，或切到離線時後端根本沒有這一筆房務任務，都直接噴錯。兩者都不是
     * 斷言可以自行補上預設值的情況。基準會缺，可能是手機還沒切到離線，也可能是切過
     * 之後又恢復連線，把上一段離線視窗的基準清掉了。
     */
    public String offlineWindowTaskState(String roomNo) {
        if (offlineWindowTaskStates == null) {
            throw new IllegalStateException(
                    "目前這個離線視窗沒有量到開場的後端狀態，無從比對操作前後的狀態。"
                            + "手機要嘛還沒切到離線，要嘛切過之後又恢復連線把基準清掉了");
        }
        String state = offlineWindowTaskStates.get(roomNo);
        if (state == null) {
            throw new IllegalStateException(
                    "切到離線時後端沒有「" + roomNo + "」這一筆任務，無從比對操作前後的狀態");
        }
        return state;
    }

    /**
     * 權威讀回：本情境每一間房那一筆房務任務目前的狀態，以房號為鍵。
     *
     * <p>工務單不算在內，範圍與 {@link #taskRowByRoomNo} 相同：句型裡用房號指稱的
     * 一律是房務那一筆。
     */
    private Map<String, String> taskStatesByRoomNo() {
        List<Map<String, Object>> rows = backend.queryRows(
                """
                SELECT r.room_no AS room_no, t.state AS state FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? AND t.type <> 'repair'
                """, backend.ensureTenant());
        Map<String, String> states = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            states.put(String.valueOf(row.get("room_no")), String.valueOf(row.get("state")));
        }
        return states;
    }

    /**
     * 權威讀回：某房號在本情境中唯一那筆任務的 {@code tasks} 整列。句型用房號代表任務
     * （backend dsl.md「任務識別」：同一案例不得出現同房兩筆無法區分的任務），所以查到
     * 不是剛好一筆就是劇本或 fixture 有問題，直接噴錯而不是挑一筆用。
     *
     * <p>工務單（{@code type = 'repair'}）不算在內：它在後端也是一筆 tasks，但句型裡
     * 用房號指稱的一律是房務那一筆，工務單一律用單號指稱（「已有以下工務單：」）。
     * 同一間房同時有房務任務與它的工務單是正常劇本（報修就會造出這種狀態），不是
     * 「同房兩筆無法區分」。
     */
    public Map<String, Object> taskRowByRoomNo(String roomNo) {
        List<Map<String, Object>> rows = backend.queryRows(
                """
                SELECT t.* FROM tasks t
                  JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? AND r.room_no = ? AND t.type <> 'repair'
                """, backend.ensureTenant(), roomNo);
        if (rows.size() != 1) {
            throw new IllegalStateException(
                    "房號「" + roomNo + "」在後端有 " + rows.size() + " 筆任務，無法以房號唯一指認");
        }
        return rows.get(0);
    }

    /**
     * App 目前打開的是哪一間房的任務頁。
     *
     * <p>有些 Then 句只說「任務頁顯示…」，句子裡沒有房號，但斷言仍要跟後端那一筆
     * 對照。這個值讓那些句子知道現在對照的是哪一間房。還沒有打開過任何任務頁時
     * 直接噴錯：那代表劇本少了打開任務頁的那一句。
     */
    /** 目前有沒有打開過任務頁（有才有「當事那一間房」可比）。 */
    public boolean hasCurrentTaskRoomNo() {
        return currentTaskRoomNo != null;
    }

    public String currentTaskRoomNo() {
        if (currentTaskRoomNo == null) {
            throw new IllegalStateException("這個情境還沒有打開過任何任務頁，無從判斷是哪一間房");
        }
        return currentTaskRoomNo;
    }

    public void setNotificationViewer(BackendChannel.CreatedStaff staff) {
        this.notificationViewer = staff;
    }

    /** 通知中心情境的當事員工；沒設定過直接噴錯——通知 Then 沒有 Given 鋪當事人是劇本錯誤。 */
    public BackendChannel.CreatedStaff notificationViewer() {
        if (notificationViewer == null) {
            throw new IllegalStateException(
                    "通知中心的當事員工尚未設定——場景是否漏了「X 的通知中心已有以下通知」或「X 已在 App 打開通知中心」？");
        }
        return notificationViewer;
    }

    /**
     * 目前登入 App 的員工；還沒登入時噴錯。給「句型不帶員工參數、但權威讀回要以
     * 當事人身分查」的 Then 用（例如待認領的列表斷言：後端只回登入者自己部門的池）。
     */
    public BackendChannel.CreatedStaff loggedInStaff() {
        if (loggedInStaff == null) {
            throw new IllegalStateException("App 尚未登入——場景是否漏了打開畫面的 When／Given？");
        }
        return loggedInStaff;
    }

    /**
     * 找出畫面上所有 accessibility id 以某前綴開頭的元素，依畫面由上而下排序後回傳
     * 它們的 id。給「列表順序」這類必須知道同類元素有哪些、各自排第幾的斷言用——
     * id 帶執行期變數（房號）時沒辦法逐一指名定位，只能先掃再比。
     *
     * <p>iOS 的 {@code name} 屬性就是 {@code accessibilityIdentifier}，所以用
     * NSPredicate 的 {@code BEGINSWITH} 掃。Android（UiAutomator2）要改掃
     * {@code resource-id}，本輪 iOS 先行，踩到時再補。
     */
    public List<String> findTestIdsWithPrefix(String prefix) {
        if (PLATFORM_ANDROID.equals(platform)) {
            throw new UnsupportedOperationException(
                    "findTestIdsWithPrefix()：Android 的 resource-id 前綴掃描本輪未實作。");
        }
        java.util.LinkedHashSet<String> collected = new java.util.LinkedHashSet<>(visibleTestIdsWithPrefix(prefix));
        int scrolled = 0;
        while (scrolled < MAX_LIST_SCROLLS) {
            if (!scrollList("down")) {
                break;
            }
            scrolled++;
            int before = collected.size();
            collected.addAll(visibleTestIdsWithPrefix(prefix));
            if (collected.size() == before) {
                break;
            }
        }
        for (int i = 0; i < scrolled; i++) {
            scrollList("up");
        }
        return List.copyOf(collected);
    }

    /**
     * 目前這一屏看得到的同前綴元素，由上而下。iOS 把捲出可視範圍的 cell 的矩形收成
     * 0×0 全部堆在原點，照 y 排會讓它們插到最前面，所以這裡直接略過沒有高度的節點，
     * 由 {@link #findTestIdsWithPrefix} 捲到它們真的露出來時再收。
     */
    private List<String> visibleTestIdsWithPrefix(String prefix) {
        By locator = AppiumBy.iOSNsPredicateString("name BEGINSWITH " + nsPredicateString(prefix));
        List<WebElement> found = driver().findElements(locator);
        List<Map.Entry<Integer, String>> rows = new ArrayList<>();
        for (WebElement e : found) {
            try {
                org.openqa.selenium.Rectangle rect = e.getRect();
                String name = e.getAttribute("name");
                if (rect.getHeight() > 0 && name != null) {
                    rows.add(Map.entry(rect.getY(), name));
                }
            } catch (RuntimeException ignored) {
                // 元素在讀矩形之前就重畫掉了；捲動後的下一輪會再收一次。
            }
        }
        rows.sort(java.util.Comparator.comparingInt(Map.Entry::getKey));
        return rows.stream().map(Map.Entry::getValue).toList();
    }

    /**
     * 把畫面上第一個捲動容器往指定方向捲一屏，回報有沒有真的捲動到。畫面沒有捲動容器、
     * 或已經捲到底時回 false，呼叫端據此停止，不會空轉到次數上限。
     */
    private boolean scrollList(String direction) {
        List<WebElement> scrollViews = driver().findElements(By.className("XCUIElementTypeScrollView"));
        if (scrollViews.isEmpty()) {
            return false;
        }
        try {
            driver().executeScript("mobile: scroll", Map.of(
                    "element", ((RemoteWebElement) scrollViews.get(0)).getId(),
                    "direction", direction));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 讀某個 accessibility id 節點目前的 label；元素不在畫面上時回 null。
     *
     * <p>給「要先掃出畫面上有哪些同類元素、再逐個讀值」的列表斷言用；期待值在掃之前
     * 還不知道是哪一個，所以不能用 {@link #waitForLabelOf}（那支要先給期待值才等得了）。
     * 呼叫端自己負責先等列表出現，再逐個讀。
     */
    public String labelOf(String id) {
        List<WebElement> found = driver().findElements(byTestId(id));
        return found.isEmpty() ? null : found.get(0).getAttribute("label");
    }

    /** 查本情境已建立的員工；查不到直接噴錯——場景引用沒建過的員工是劇本錯誤，不是可忽略情況。 */
    public BackendChannel.CreatedStaff staff(String name) {
        BackendChannel.CreatedStaff staff = staffByName.get(name);
        if (staff == null) {
            throw new IllegalStateException(
                    "員工「" + name + "」尚未建立——場景是否漏了背景句「飯店有以下員工」？");
        }
        return staff;
    }

    /**
     * 本情境唯一那位主管。查房判定只有主管身分送得出去（後端的角色×動作矩陣），但
     * 「後端的 X 是 … 不合格 … 的單」這句的參數裡沒有查房者——劇本在背景已經建好一位
     * 主管，就以那一位為準。沒有或不只一位時直接噴錯，不默默挑一個頂上。
     */
    public BackendChannel.CreatedStaff onlyManager() {
        List<BackendChannel.CreatedStaff> managers = staffByName.values().stream()
                .filter(s -> "manager".equals(s.role()))
                .toList();
        if (managers.size() != 1) {
            throw new IllegalStateException("本情境有 " + managers.size()
                    + " 位主管，無法推定查房者；背景「飯店有以下員工」需要剛好一位部門為「主管」的員工");
        }
        return managers.get(0);
    }

    /**
     * 本情境唯一那位工務。給「句型要指派一位工務、但參數裡沒有他」的 stepdef 用，
     * 例如「後端已有 "X" 報的 … 工務單，狀態為 "維修中"」——維修中的單在後端必須有
     * 負責人，劇本只在背景建了一位工務，就以那一位為準。沒有或不只一位時直接噴錯，
     * 讓劇本自己補清楚是哪一位，不默默挑一個頂上（理由同 {@link #onlyManager()}）。
     */
    public BackendChannel.CreatedStaff onlyEngineer() {
        List<BackendChannel.CreatedStaff> engineers = staffByName.values().stream()
                .filter(s -> "engineering".equals(s.dept()))
                .toList();
        if (engineers.size() != 1) {
            throw new IllegalStateException("本情境有 " + engineers.size()
                    + " 位工務，無法推定處理人；背景「飯店有以下員工」需要剛好一位部門為「工務」的員工");
        }
        return engineers.get(0);
    }

    /**
     * 本情境唯一那位現場員工，也就是有部門的那一位（主管與值班經理的部門是 NULL）。
     * 給「需要一個登入身分、但句型裡沒有員工參數」的地方用，例如把手機切到離線那一句：
     * 離線之前得先有人登入，而劇本只在背景建了一位現場員工，就以那一位為準。沒有或
     * 不只一位時直接噴錯，讓劇本自己補清楚是哪一位，不默默挑一個頂上（理由同
     * {@link #onlyManager()}）。
     */
    private BackendChannel.CreatedStaff onlyFieldStaff() {
        List<BackendChannel.CreatedStaff> fieldStaff = staffByName.values().stream()
                .filter(s -> s.dept() != null)
                .toList();
        if (fieldStaff.size() != 1) {
            throw new IllegalStateException("本情境有 " + fieldStaff.size()
                    + " 位現場員工，無法推定操作人；背景「飯店有以下員工」需要剛好一位有部門的員工");
        }
        return fieldStaff.get(0);
    }

    /**
     * 劇本的操作者：句型沒有員工參數、又還沒有人登入時，該由誰登入。背景只建了一位員工就是
     * 他（主管一個人的離線劇本）；建了不只一位時沿用「唯一那位現場員工」的推定——主管在
     * 背景裡多半只是被通知的人。兩種推定都不成立就噴錯，讓劇本自己寫清楚。
     */
    private BackendChannel.CreatedStaff onlyOperator() {
        if (staffByName.size() == 1) {
            return staffByName.values().iterator().next();
        }
        return onlyFieldStaff();
    }

    /**
     * 「飯店有以下員工」一列的完整版：角色、負責樓層、班次結束與狀態都開放。
     *
     * <p>角色決定登入後的外殼（主管即使寫了部門也進主管首頁），所以「部門房務、角色主管」
     * 要落成 role=manager、dept=housekeeping，不能照部門推角色。班次結束只在有負責樓層時
     * 落地：它存在排房計畫列上，而那一列要掛在一間房底下；沒有樓層的員工（主管）沒有這
     * 個概念。狀態不是在職的員工照樣建，待開通與停用的人不出現在挑人名單是後端的事。
     */
    public void seedStaff(String name, String deptChinese, String roleChinese, Integer floor,
                          String shiftEndHhmm, String stateChinese) {
        staffByName.computeIfAbsent(name, n -> {
            String stateCode = stateChinese == null ? "active" : BackendChannel.staffState(stateChinese);
            boolean managerDept = "主管".equals(deptChinese) || "值班經理".equals(deptChinese);
            if (managerDept) {
                return backend.createManagerLikeStaff(n, BackendChannel.role(deptChinese), stateCode);
            }
            String deptCode = BackendChannel.dept(deptChinese);
            String roleCode = "主管".equals(roleChinese) ? BackendChannel.role("主管") : deptCode;
            BackendChannel.CreatedStaff created = backend.createStaff(n, roleCode, deptCode, stateCode);
            if (floor != null) {
                if (shiftEndHhmm != null) {
                    backend.assignPlanRoom(created.id(), floor, shiftEndHhmm);
                } else {
                    backend.assignPlanRoom(created.id(), floor);
                }
            }
            return created;
        });
    }

    /**
     * 改某位已建員工的登入帳密（「"X" 的員工編號密碼為 …」）。除了落地到後端
     * （{@link BackendChannel#setCredentials}），還要把 {@link #staffByName} 裡那筆換成
     * 帶新帳密的 record——後續的 Given／When 都用姓名查員工，查到舊的 fixture 帳密
     * 會讓 UI 登入打錯密碼，錯在測試資料而不是產品。
     */
    public void setStaffCredentials(String name, String loginId, String password) {
        BackendChannel.CreatedStaff old = staff(name);
        backend.setCredentials(old.id(), loginId, password);
        staffByName.put(name, new BackendChannel.CreatedStaff(
                old.id(), old.name(), loginId, password, old.role(), old.dept()));
    }

    /**
     * 背景句「飯店有以下員工」：在真後端建立員工與登入帳號。部門照表上的中文對照權威
     * enum（{@link BackendChannel#dept}：房務→housekeeping、工務→engineering、
     * 櫃檯→frontdesk）；「主管／值班經理」不是部門是角色，走
     * {@link BackendChannel#createManagerLikeStaff}（dept 落 NULL），分流對照 tms
     * {@code CommonGivenSteps.hotelHasStaff}。狀態先固定在職；負責樓層／狀態要開放
     * 參數時，由對應模組的 stepdef 直接呼叫 {@link #backend()}。
     */
    public void seedStaff(String name, String deptChinese) {
        seedStaff(name, deptChinese, null);
    }

    /**
     * 同上，另外吃「負責樓層」欄：現場部門員工有這一欄時補一筆 {@code plan_rooms}
     * （{@link BackendChannel#assignPlanRoom}），待認領的同樓層標示才有真相可依。
     * 主管／值班經理沒有負責樓層的概念，給了也不落地。
     */
    public void seedStaff(String name, String deptChinese, Integer floor) {
        staffByName.computeIfAbsent(name, n -> {
            if ("主管".equals(deptChinese) || "值班經理".equals(deptChinese)) {
                return backend.createManagerLikeStaff(n, BackendChannel.role(deptChinese), "active");
            }
            BackendChannel.CreatedStaff created =
                    backend.createFieldStaff(n, BackendChannel.dept(deptChinese), "active");
            if (floor != null) {
                backend.assignPlanRoom(created.id(), floor);
            }
            return created;
        });
    }

    /**
     * 「{@code "X" 名下有以下任務}」：在真後端建立掛在這位員工名下、帶到指定狀態的任務。
     *
     * <p>停在進行中的那筆留到最後才建。後端有「一次只做一間」的規則（開工時已有
     * {@code doing} 的單會回 409 {@code doing_task_exists}），而中斷／完成／查房通過這幾種
     * 狀態都得先經過開工；表上的順序只是在描述一組狀態、不是操作先後，照表順序建會讓
     * 排在進行中後面的那幾筆開不了工。離開 {@code doing} 的狀態之間彼此不衝突，所以只要
     * 把「終點就是進行中」的挪到最後，同一組 fixture 的相對順序其餘不變。
     */
    public void seedTasks(String staffName, List<TaskFixture> tasks) {
        BackendChannel.CreatedStaff assignee = staff(staffName);
        List<TaskFixture> ordered = new ArrayList<>(tasks.size());
        List<TaskFixture> endingInDoing = new ArrayList<>();
        for (TaskFixture task : tasks) {
            if ("doing".equals(BackendChannel.taskState(task.status()))
                    && !startableInApp("doing", task)) {
                endingInDoing.add(task);
            } else {
                ordered.add(task);
            }
        }
        ordered.addAll(endingInDoing);

        for (TaskFixture task : ordered) {
            String stateCode = BackendChannel.taskState(task.status());
            Integer workedMinutes = task.elapsedMinutes() > 0 ? task.elapsedMinutes() : null;
            if (startableInApp(stateCode, task)) {
                stateCode = "accepted";
                roomsToStartInApp.put(task.roomNo(), new DeferredStart(assignee));
            }
            UUID taskId = backend.createTask(
                    task.roomNo(),
                    task.content(),
                    // 表上沒有「類別」欄時一律當打掃（多數模組的表只有房號內容狀態，
                    // 內容都是打掃類）；有這一欄就照權威對照表換成 tasks.type。
                    task.categoryChinese() == null
                            ? "cleaning"
                            : BackendChannel.taskType(task.categoryChinese()),
                    "housekeeping",
                    task.estMinutes(),
                    stateCode,
                    assignee,
                    workedMinutes,
                    task.pausedReason(),
                    task.inspectionWaived() || "完成 · 免查房".equals(task.status()));
            if (task.redoCount() != null) {
                backend.setRedoCount(taskId, task.redoCount());
            }
        }
    }

    /**
     * 判斷這筆任務的「進行中」能不能改用 App 的開始按鈕達成。
     *
     * <p>開工交給 App 的主按鈕，後端落地的狀態與事件就跟員工自己操作一模一樣，不必
     * 在資料庫裡自己湊出一筆進行中。
     *
     * <p>停住原因擋這條路：它是任務停住時才有的欄位，而這條路走完任務是進行中的，兩者
     * 湊在一起不是後端生得出來的狀態，硬湊會讓情境驗在一個不存在的真相上。
     *
     * <p>已做分鐘也擋這條路。已做分鐘只能直接落地（沒有人能等 35 分鐘），而落地的時機
     * 是在 App 按下開始之後，任務頁拿到的卻是開始那一刻的回應，上面的已做時間還是 0。
     * 要讓畫面上的已做時間是對的，這種任務就得一開始就種成進行中。
     */
    private static boolean startableInApp(String stateCode, TaskFixture task) {
        if (!"doing".equals(stateCode) || task.pausedReason() != null) {
            return false;
        }
        // 免查房擋這條路：那個鎖定值是開工那一刻依租戶開關寫進任務的，而這條路的
        // 開工晚到員工按下主按鈕那一刻，開關那時已經收回去了。
        if (task.inspectionWaived()) {
            return false;
        }
        return task.elapsedMinutes() == 0;
    }

    /**
     * 推播去處指向待認領池且要標亮某房號時（dsl.md「建立去處所指的任務」），畫面上要先
     * 有一張狀態是待認領的卡才有東西可以標亮，但 outline 場景並沒有另外一個「名下有以下
     * 任務」步驟替它建這張卡，所以由這裡補。同房號已有待認領任務就不再建，不疊兩張重複的卡。
     */
    public void ensurePoolTask(String roomNo, String content) {
        long tenantId = backend.ensureTenant();
        List<Map<String, Object>> existing = backend.queryRows(
                """
                SELECT t.id FROM tasks t JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                WHERE t.tenant_id = ? AND r.room_no = ? AND t.state = 'pool'
                """, tenantId, roomNo);
        if (!existing.isEmpty()) {
            return;
        }
        backend.createTask(roomNo, content, "other", "housekeeping", null, "pool", null, null, null);
    }

    /**
     * 測試後門是 Dart {@code HttpServer} 最簡單的用法，沒有特別調過連線池行為；實測中
     * 觀察到用同一個 {@code HttpClient} 重用 keep-alive 連線時，偶爾會連到一條後門那邊
     * 已經關閉的連線，收到 {@code Connection reset}。Java 的 {@code HttpClient} 不讓呼叫端
     * 自己設 {@code Connection} 這個 restricted header 去關掉 keep-alive，所以改成每次都
     * 開一個全新的 {@code HttpClient}（天生沒有舊連線可重用）＋失敗重送一次，比追查
     * Dart 那邊的連線池細節划算。
     *
     * <p>給後門僅存的職責用：裝置側注入（時間、離線、上傳失敗……）。測試資料不走
     * 這裡，見類別 Javadoc。
     */
    public void postBackdoorJson(String path, String json) {
        try {
            IOException lastError = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    sendOnce(path, json);
                    return;
                } catch (IOException e) {
                    lastError = e;
                }
            }
            throw lastError;
        } catch (IOException e) {
            throw new IllegalStateException("呼叫測試後門 " + path + " 失敗", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("呼叫測試後門 " + path + " 時被中斷", e);
        }
    }

    private void sendOnce(String path, String json) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + BACKDOOR_PORT + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(5))
                .build();
        // 必須鎖 HTTP/1.1：JDK HttpClient 預設 HTTP/2，對 http:// 會帶 h2c 升級標頭，
        // Dart HttpServer 收到這種請求會回 200 但把 request body 整個吞掉（實測重現），
        // 注入內容就會靜默變成空包。
        HttpResponse<Void> response = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build()
                .send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != 200) {
            throw new IOException("POST " + path + " -> HTTP " + response.statusCode());
        }
    }

    private String httpGet(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + BACKDOOR_PORT + path))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("GET " + path + " -> HTTP " + response.statusCode());
        }
        return response.body();
    }

    /**
     * 讀回裝置目前被注入的時鐘（測試後門 {@code GET /state} 的 {@code injectedClockIso}）；
     * 這個場景還沒撥過裝置時鐘時回 {@code null}。
     *
     * <p>給「把時間往前撥 N 分鐘」這種<b>相對</b>推撥句用：相對推撥的基準必須是
     * 「目前生效的裝置時間」——場景前面若已有句子撥過時鐘（「現在時間是」「已中斷
     * N 分鐘」），再拿真實當下當基準會把時鐘倒轉回去，撥快的那一段就被吃掉了。
     */
    public OffsetDateTime injectedDeviceClock() {
        String body;
        try {
            body = httpGet("/state");
        } catch (IOException e) {
            throw new IllegalStateException("讀測試後門 /state 失敗", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("讀測試後門 /state 時被中斷", e);
        }
        try {
            JsonNode state = new ObjectMapper().readTree(body);
            JsonNode injected = state.get("injectedClockIso");
            return injected == null || injected.isNull()
                    ? null
                    : OffsetDateTime.parse(injected.asText());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("測試後門 /state 回應不是合法 JSON：" + body, e);
        }
    }

    /**
     * 裝置目前生效的時間：撥過裝置時鐘就是注入值，沒撥過就是真實當下。
     *
     * <p>場景裡凡是以「現在」「今天」為基準算出來、又要和畫面對照的值（相對推撥的
     * 起點、通知的今天／更早分組、計時的累計秒數），都從這裡取。拿 harness 的
     * 真實當下算，場景一撥過時鐘，畫面照注入值分組、斷言照真實日期分組，兩邊就對不上。
     */
    public OffsetDateTime deviceNow() {
        OffsetDateTime injected = injectedDeviceClock();
        return injected != null ? injected : OffsetDateTime.now();
    }

    /**
     * App 本機待送出佇列目前還有幾筆（測試後門 {@code GET /state} 的
     * {@code pendingSyncCount}）。
     *
     * <p>要等「佇列送完」的地方讀這裡，不要改讀畫面上的離線列：那一列整段只綁連線狀態
     * （{@code ConnectionStatus.offline}），一恢復連線就連同裡面的筆數節點一起不見，
     * 跟佇列還剩幾筆無關。拿它當佇列空了的判準，會在補送還在路上時就放行，接著的權威
     * 讀回數到的是沒送完的筆數。
     */
    public int pendingSyncCountInApp() {
        String body;
        try {
            body = httpGet("/state");
        } catch (IOException e) {
            throw new IllegalStateException("讀測試後門 /state 失敗", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("讀測試後門 /state 時被中斷", e);
        }
        try {
            JsonNode state = new ObjectMapper().readTree(body);
            JsonNode pending = state.get("pendingSyncCount");
            if (pending == null || pending.isNull()) {
                throw new IllegalStateException("測試後門 /state 沒有回 pendingSyncCount：" + body);
            }
            return pending.asInt();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("測試後門 /state 回應不是合法 JSON：" + body, e);
        }
    }

    /**
     * 等 App 本機待送出佇列清空。
     *
     * <p>等的是一個會變的權威條件（佇列長度），不是等一段固定時間：每一筆補送都是一次
     * HTTP 來回，快慢看模擬器當下的負載。逾時還沒空就直接噴錯並附上還剩幾筆。
     */
    public void waitForPendingSyncDrained(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        int remaining = pendingSyncCountInApp();
        while (System.nanoTime() < deadline) {
            if (remaining == 0) {
                return;
            }
            sleep(200);
            remaining = pendingSyncCountInApp();
        }
        throw new AssertionError("App 本機待送出佇列沒有在 " + timeout.getSeconds()
                + "s 內清空，還剩 " + remaining + " 筆");
    }

    /**
     * 送一則推播給 simulator。先把 App 切到背景再推：這支 feature 要驗證的是
     * 「背景中被推播喚回前景後導到哪裡」，所以推播必須在 App 不在前景時送出，App
     * 才會是被「喚回」而不是單純收到提示。payload 的 {@code dest}/{@code room}/
     * {@code taskId}/{@code highlight} 欄位跟 {@code lib/core/push_gateway.dart}
     * 的約定一致（{@code taskId} 可為 null，對應「去處房號沒有既存任務」的推播）。
     */
    public void sendPush(String title, String body, String dest, String room, String taskId, String highlight) {
        sendPush(title, body, dest, room, taskId, highlight, null, null);
    }

    /**
     * 同上，另外帶待認領入口的部門與要標亮的單 id（契約 {@code PushTarget}：kind=pool 時
     * {@code dept} 必填、{@code highlightTaskId} 選填）。主管收的推播要指明開哪一個部門的池，
     * 而工務單在池裡以單號當卡片鍵，房號反查不到它，所以單 id 也一起帶。
     */
    public void sendPush(String title, String body, String dest, String room, String taskId,
                         String highlight, String dept, String highlightTaskId) {
        backgroundApp();

        StringBuilder json = new StringBuilder();
        json.append("{\"aps\":{\"alert\":{\"title\":").append(jsonString(title))
                .append(",\"body\":").append(jsonString(body == null ? "" : body))
                .append("},\"sound\":\"default\"}");
        json.append(",\"dest\":").append(jsonString(dest));
        if (room != null) {
            json.append(",\"room\":").append(jsonString(room));
        }
        if (taskId != null) {
            json.append(",\"taskId\":").append(jsonString(taskId));
        }
        if (highlight != null) {
            json.append(",\"highlight\":").append(jsonString(highlight));
        }
        if (dept != null) {
            json.append(",\"dept\":").append(jsonString(dept));
        }
        if (highlightTaskId != null) {
            json.append(",\"highlightTaskId\":").append(jsonString(highlightTaskId));
        }
        json.append('}');

        try {
            Path file = Files.createTempFile("tms-push-", ".json");
            Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
            lastPushPayloadFile = file;
            lastPushTitle = title;
            runSimctlRequired("push", udid, bundleId, file.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new IllegalStateException("寫推播 payload 暫存檔失敗", e);
        }
    }

    /** 把 App 送到背景（回到模擬器主畫面），讓推播以系統通知橫幅的形式出現。 */
    public void backgroundApp() {
        driver().executeScript("mobile: pressButton", Map.of("name", "home"));
    }

    /**
     * 在通知橫幅上點一下，等 App 回前景完成導頁。橫幅顯示時間很短，第一次找不到就重送
     * 一次同一則推播再試一次——這是對「橫幅自動收起」這個已知的環境限制做一次性緩解，
     * 不是無限重試；兩次都找不到就不再猜，直接丟例外讓呼叫端知道卡在哪一步。
     */
    public void tapPushNotification() {
        if (lastPushPayloadFile == null) {
            throw new IllegalStateException("tapPushNotification() 之前沒有呼叫過 sendPush()");
        }
        if (tryTapNotificationBanner()) {
            return;
        }
        runSimctlRequired("push", udid, bundleId, lastPushPayloadFile.toAbsolutePath().toString());
        if (tryTapNotificationBanner()) {
            return;
        }
        if (tryTapNotificationCenter()) {
            return;
        }
        throw new IllegalStateException(
                "點開推播失敗：橫幅（NotificationShortLookView，標題含 \"" + lastPushTitle + "\"）"
                        + "兩次都沒抓到，下拉通知中心備援也找不到這則通知。"
                        + "可能原因：通知授權沒生效、或 payload 沒送達。");
    }

    /**
     * 備援路徑：橫幅是暫態、幾秒就自動收起，但同一則通知會留在通知中心不會消失。
     * 從螢幕頂緣下拉打開通知中心，點 label 含推播標題的那一則——比搶橫幅穩定，
     * 代價是多一次下拉手勢，所以只當橫幅錯過後的第二選擇。
     */
    private boolean tryTapNotificationCenter() {
        try {
            AppiumDriver d = driver();
            d.executeScript("mobile: dragFromToForDuration", Map.of(
                    "duration", 0.4, "fromX", 200, "fromY", 2, "toX", 200, "toY", 550));
            By locator = AppiumBy.iOSNsPredicateString(
                    "label CONTAINS " + nsPredicateString(lastPushTitle));
            FluentWait<AppiumDriver> wait = new FluentWait<>(d)
                    .withTimeout(Duration.ofSeconds(6))
                    .pollingEvery(Duration.ofMillis(250))
                    .ignoring(NoSuchElementException.class);
            WebElement cell = wait.until(x -> x.findElement(locator));
            cell.click();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean tryTapNotificationBanner() {
        By locator = AppiumBy.iOSNsPredicateString(
                "name == \"NotificationShortLookView\" AND label CONTAINS " + nsPredicateString(lastPushTitle));
        try {
            FluentWait<AppiumDriver> wait = new FluentWait<>(driver())
                    .withTimeout(NOTIFICATION_FIND_TIMEOUT)
                    .pollingEvery(Duration.ofMillis(200))
                    .ignoring(NoSuchElementException.class);
            WebElement banner = wait.until(d -> d.findElement(locator));
            banner.click();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 等某個 accessibility id 的元素出現。導頁有轉場動畫，點開推播到畫面真正定住之間
     * 有一段延遲，用輪詢等待取代固定 sleep，逾時才當失敗。
     */
    public WebElement waitForTestId(String id, Duration timeout) {
        try {
            FluentWait<AppiumDriver> wait = new FluentWait<>(driver())
                    .withTimeout(timeout)
                    .pollingEvery(Duration.ofMillis(200))
                    .ignoring(NoSuchElementException.class);
            return wait.until(d -> d.findElement(byTestId(id)));
        } catch (RuntimeException notOnThisScreenful) {
            WebElement afterScrolling = scrollUntilVisible(id);
            if (afterScrolling != null) {
                return afterScrolling;
            }
            return failWithPageSource(id, timeout, notOnThisScreenful);
        }
    }

    /**
     * 跟 {@link #waitForTestId} 一樣等元素出現，但等不到時回 {@code null} 而不是丟錯，
     * 也不傾印元素樹——給「等不到就換個做法再試一次」的呼叫端用。
     */
    private WebElement waitForTestIdQuietly(String id, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            List<WebElement> found = driver().findElements(byTestId(id));
            if (!found.isEmpty()) {
                return found.get(0);
            }
            sleep(200);
        }
        return null;
    }

    /**
     * 往下捲找元素，找到就停在那一屏讓呼叫端接著點它。清單比畫面長時，Flutter 根本
     * 不會建出看不到的那幾筆，捲到它露出來是唯一能定位的方式——真人也是這樣用的。
     * 捲到底還是沒有就回 null，由呼叫端當成「畫面上沒有這個元素」。
     */
    private WebElement scrollUntilVisible(String id) {
        for (int i = 0; i < MAX_LIST_SCROLLS; i++) {
            if (!scrollList("down")) {
                return null;
            }
            List<WebElement> found = driver().findElements(byTestId(id));
            if (!found.isEmpty()) {
                return found.get(0);
            }
        }
        return null;
    }

    /**
     * 定位不到元素時丟出帶現場的斷言錯誤。訊息裡附上元素樹與截圖的檔案路徑，看 log 的人
     * 不必重跑一次就能知道畫面當時停在哪一頁、有哪些元素、實際長什麼樣子。
     *
     * <p>兩份現場都要留。元素樹只看得到這個 App 自己的元素，系統層的東西（鍵盤、
     * 自動填入提示、系統對話框）不在裡面；畫面被那類東西蓋住時，元素樹會顯示該有的元素
     * 都在、只是全部 {@code visible="false"}，看起來像 App 沒畫出來，截圖才看得出真相。
     */
    private WebElement failWithPageSource(String id, Duration timeout, RuntimeException cause) {
        String dump = dumpPageSourceTagged("missing-" + id);
        String shot = dumpScreenshotTagged("missing-" + id);
        throw new AssertionError(
                "等不到畫面元素 " + id + "（逾時 " + timeout.getSeconds() + "s，含往下捲動尋找）；元素樹 "
                        + dump + "；截圖 " + shot,
                cause);
    }

    /**
     * 把目前畫面存成 PNG，回傳檔案路徑。檔名帶標籤與時間戳，和元素樹同一套命名。
     *
     * <p>存檔失敗就把失敗原因當成路徑回傳，不往外丟例外：這只是輔助資訊，為了它再丟一個
     * 例外會蓋掉真正的失敗原因。
     */
    private String dumpScreenshotTagged(String tag) {
        String path = "/tmp/tms-app-bdd-" + tag + "-" + System.currentTimeMillis() + ".png";
        try {
            Files.write(Path.of(path), driver().getScreenshotAs(OutputType.BYTES));
            return path;
        } catch (Exception e) {
            return "（存檔失敗：" + e.getMessage() + "）";
        }
    }

    /**
     * 對文字欄位輸入內容。先點一下取得焦點（XCUITest 對未聚焦欄位 sendKeys 偶發失敗），
     * 再依內容選輸入路徑：
     *
     * <ul>
     *   <li>純 ASCII 直接 {@code sendKeys}——WDA 的 typeText 逐字元敲軟體鍵盤，英文鍵盤
     *       敲得出來；</li>
     *   <li>含非 ASCII（中文等）改走剪貼簿＋長按「貼上」——英文軟體鍵盤敲不出 CJK，
     *       WDA 會回 200 但整段文字被靜默丟掉（實測重現），欄位收到的是空字串。</li>
     * </ul>
     */
    public void typeInto(String testId, Duration timeout, String text) {
        withStaleRetry(testId, timeout, field -> {
            field.click();
            if (text.chars().allMatch(c -> c < 128)) {
                field.sendKeys(text);
                return;
            }
            ((IOSDriver) driver()).setClipboardText(text);
            String elementId = ((RemoteWebElement) field).getId();
            driver().executeScript("mobile: touchAndHold",
                    Map.of("element", elementId, "elementId", elementId, "duration", 1.0));
            By pasteItem = AppiumBy.iOSNsPredicateString("label == 'Paste' OR label == '貼上'");
            FluentWait<AppiumDriver> wait = new FluentWait<>(driver())
                    .withTimeout(Duration.ofSeconds(4))
                    .pollingEvery(Duration.ofMillis(200))
                    .ignoring(NoSuchElementException.class);
            try {
                wait.until(d -> d.findElement(pasteItem)).click();
            } catch (RuntimeException e) {
                dumpPageSource("/tmp/tms-app-bdd-paste-menu-missing.xml");
                throw e;
            }
        });
    }

    /**
     * 給 stepdef 在組斷言訊息時留下當下的元素樹，檔名帶標籤與時間戳，整輪跑下來多個失敗
     * 各留各的一份，不會互相覆蓋；回傳的路徑放進斷言訊息，看 log 的人才知道去哪裡翻現場。
     */
    public String dumpPageSourceTagged(String tag) {
        String path = "/tmp/tms-app-bdd-" + tag + "-" + System.currentTimeMillis() + ".xml";
        dumpPageSource(path);
        return path;
    }

    /**
     * 掃出某個前綴的元素，連同它們在畫面上的矩形一起回傳。列表類斷言只比對 id 的順序，
     * 順序不符時分不出是 App 真的排錯、還是有元素被擠出可視範圍而讀不到正確位置；帶著
     * 矩形才看得出是哪一種。
     */
    public List<String> describeTestIdsWithPrefix(String prefix) {
        By locator = AppiumBy.iOSNsPredicateString("name BEGINSWITH " + nsPredicateString(prefix));
        List<String> described = new ArrayList<>();
        for (WebElement e : driver().findElements(locator)) {
            try {
                described.add(e.getAttribute("name") + "@" + e.getRect());
            } catch (RuntimeException ignored) {
                // 元素在讀矩形之前就消失了；診斷訊息少一列不影響真正的失敗原因。
            }
        }
        return described;
    }

    /**
     * 把目前畫面的元素樹寫到檔案，給定位失敗時回頭看畫面上到底有什麼用。
     *
     * <p>寫檔本身失敗就放著不管：這只是輔助資訊，為了它再丟一個例外會蓋掉真正的失敗原因。
     */
    private void dumpPageSource(String path) {
        try {
            Files.writeString(Path.of(path), driver().getPageSource(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // 見方法說明。
        }
    }

    /**
     * 讀某個元素的文字，讀到期待值就回傳，否則重讀到逾時為止，最後回傳最近一次讀到的值。
     *
     * <p>App 在一次操作之後更新畫面，中間常隔著一次網路來回。只讀一次會讀到還沒更新的
     * 舊文字，讓斷言誤報成功能壞掉。逾時仍不符時不自己丟錯，把實際讀到的值交回給呼叫端
     * 組斷言訊息，才看得出畫面究竟停在哪個狀態。
     */
    public String waitForLabelOf(String id, String expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        String actual = null;
        while (System.nanoTime() < deadline) {
            try {
                actual = driver().findElement(byTestId(id)).getAttribute("label");
            } catch (RuntimeException e) {
                actual = null;
            }
            if (expected.equals(actual)) {
                return actual;
            }
            sleep(200);
        }
        dumpPageSource("/tmp/tms-app-bdd-label-mismatch.xml");
        return actual;
    }

    /**
     * 等某個 accessibility id 的元素從畫面上消失。
     *
     * <p>用在「送出後面板關閉」這種需要等 App 把一次網路來回做完的地方。Appium 的點擊
     * 只保證手勢送到了，不保證 App 已經收到後端回應並更新畫面；緊接著斷言畫面狀態會
     * 讀到還沒更新的舊值，看起來像功能壞掉，其實是測試跑在 App 前面。
     */
    public void waitForTestIdGone(String id, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (driver().findElements(byTestId(id)).isEmpty()) {
                return;
            }
            sleep(200);
        }
        throw new AssertionError("畫面元素 " + id + " 沒有如期消失（逾時 " + timeout.getSeconds() + "s）");
    }

    /** 等某個 accessibility id 的元素出現後點擊（stale 時以 id 重找重試，見 {@link #withStaleRetry}）。 */
    public void tapTestId(String testId, Duration timeout) {
        withStaleRetry(testId, timeout, element -> {
            waitForStableRect(element);
            element.click();
        });
    }

    /**
     * 等送出鈕變成可按再點。文字欄位剛打完字時，XCUITest 的 sendKeys 會在 Flutter 處理完
     * 最後一個字之前就返回；這時送出鈕還是禁用的，點下去只會收起鍵盤、什麼都不送（實測：
     * 登入頁兩欄都填好、鍵盤收起、送出鈕可按，但後端沒收到登入）。只等位置定格分不出這種
     * 情況，所以先等 enabled 成立。逾時仍禁用就照樣點，交給後面的斷言報錯。
     */
    public void tapWhenEnabled(String testId, Duration timeout) {
        withStaleRetry(testId, timeout, element -> {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (!element.isEnabled() && System.nanoTime() < deadline) {
                sleep(150);
            }
            waitForSettledLayoutAfterTyping(element);
            element.click();
        });
    }

    /**
     * 打完字之後等版面真的停下來。送出鈕在置底列，置底列貼著鍵盤頂端；密碼欄位拿到焦點
     * 之後，鍵盤上方的密碼自動填入列會晚半拍才長出來，把整個置底列往上推。只等 150ms
     * 定格會在它長出來之前就放行，點擊落在送出鈕原本的位置，也就是現在「忘記密碼？」
     * 的位置（實測：登入頁停在原地或跳出忘記密碼提示，後端沒收到登入）。所以這裡把鍵盤
     * 的外框也算進來，送出鈕與鍵盤連續 600ms 都沒動才點；一直在動最多等 3 秒就放行，
     * 交給 click 自己成敗。
     */
    private void waitForSettledLayoutAfterTyping(WebElement element) {
        By keyboard = AppiumBy.className("XCUIElementTypeKeyboard");
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        String last = layoutSignature(element, keyboard);
        int stableReads = 0;
        while (stableReads < 3 && System.nanoTime() < deadline) {
            sleep(200);
            String now = layoutSignature(element, keyboard);
            stableReads = now.equals(last) ? stableReads + 1 : 0;
            last = now;
        }
    }

    private String layoutSignature(WebElement element, By keyboard) {
        StringBuilder signature = new StringBuilder(element.getRect().toString());
        for (WebElement shown : driver().findElements(keyboard)) {
            signature.append('|').append(shown.getRect());
        }
        return signature.toString();
    }

    /**
     * 等元素的畫面位置定下來再回傳。頁面轉場動畫進行中就點，XCUITest 以當下
     * 座標落點，會點到還在移動的畫面之外（實測重現：通知中心剛推入時點
     * 「全部標為已讀」沒有任何效果）；連續兩次讀到相同的 frame 才視為定格。
     * 位置一直在動（例如跑不停的動畫）時最多等 2 秒就放行，交給 click 自己
     * 成敗，不把這裡變成無上限的等待。
     */
    private void waitForStableRect(WebElement element) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        org.openqa.selenium.Rectangle last = element.getRect();
        while (System.nanoTime() < deadline) {
            sleep(150);
            org.openqa.selenium.Rectangle now = element.getRect();
            if (now.equals(last)) {
                return;
            }
            last = now;
        }
    }

    /**
     * 以 id 找到元素後執行動作；吃到 {@code StaleElementReferenceException} 就以同一個
     * id 重找一次再做。畫面轉場或 rebuild 會讓 Flutter 重發 semantics 節點，剛找到的
     * element handle 在下一個指令前就可能失效——這在整個模組連跑（情境間 App 重啟、
     * 轉場密集）時會間歇出現，單跑同一情境反而碰不到。重試一次就夠：第二次找到的
     * handle 來自已定格的畫面。
     */
    private void withStaleRetry(String testId, Duration timeout, java.util.function.Consumer<WebElement> action) {
        try {
            action.accept(waitForTestId(testId, timeout));
        } catch (org.openqa.selenium.StaleElementReferenceException e) {
            action.accept(waitForTestId(testId, timeout));
        }
    }

    /**
     * 等畫面上出現某段文字（label 內含即可）。給那些沒有專屬 accessibility id、但內容
     * 本身就是斷言目標的文字節點用，例如任務頁標題裡的房號。
     */
    public WebElement waitForLabelContaining(String text, Duration timeout) {
        By locator = AppiumBy.iOSNsPredicateString("label CONTAINS " + nsPredicateString(text));
        try {
            FluentWait<AppiumDriver> wait = new FluentWait<>(driver())
                    .withTimeout(timeout)
                    .pollingEvery(Duration.ofMillis(200))
                    .ignoring(NoSuchElementException.class);
            return wait.until(d -> d.findElement(locator));
        } catch (RuntimeException e) {
            throw new AssertionError("等不到含文字「" + text + "」的畫面元素（逾時 " + timeout.getSeconds() + "s）", e);
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

    /** 把 Java 字串包成 JSON 字串字面值（雙引號＋basic escaping）。 */
    static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** 把字串包成 NSPredicate 字串字面值（單引號＋把字串裡的單引號、反斜線跳脫掉）。 */
    private static String nsPredicateString(String s) {
        String escaped = s.replace("\\", "\\\\").replace("'", "\\'");
        return "'" + escaped + "'";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 這台手機上 App 本機偏好設定的一把鍵（Flutter {@code shared_preferences}）。iOS 上它落在
     * App 容器的 NSUserDefaults，鍵前面固定加 {@code flutter.}；用 {@code simctl spawn … defaults}
     * 直接寫，不經畫面也不經測試後門——「上次停在哪個部門」這種只存在手機上的狀態，
     * 正是 dsl 要求「不經畫面」鋪的。寫完要 {@link #restartApp()} App 才會讀到。
     */
    public void writeLocalPreference(String key, String value) {
        runSimctlRequired("spawn", udid, "defaults", "write", preferencesDomain(), "flutter." + key, "-string", value);
    }

    /**
     * App 的 NSUserDefaults 在模擬器上的 domain：App 資料容器裡的
     * {@code Library/Preferences/{bundleId}}（給 {@code defaults} 的是不帶 {@code .plist} 的完整路徑）。
     * 只寫 bundle id 的話，{@code defaults} 讀寫的是模擬器使用者層的
     * {@code Library/Preferences/{bundleId}.plist}，跟 App 自己存的不是同一份，兩邊互相看不到。
     * 容器路徑在重新安裝後會變，所以每次現查。
     */
    private String preferencesDomain() {
        try {
            Process process = new ProcessBuilder(buildSimctlCommand("get_app_container", udid, bundleId, "data"))
                    .start();
            String container = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("查 App 資料容器逾時：" + bundleId);
            }
            if (process.exitValue() != 0 || container.isEmpty()) {
                throw new IllegalStateException("查不到 App 資料容器：" + bundleId + "，exit=" + process.exitValue());
            }
            return container + "/Library/Preferences/" + bundleId;
        } catch (IOException e) {
            throw new IllegalStateException("查 App 資料容器失敗：" + bundleId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("查 App 資料容器時被中斷：" + bundleId, e);
        }
    }

    /** 讀 {@link #writeLocalPreference} 那一把鍵目前的值；沒有紀錄回 null。 */
    public String readLocalPreference(String key) {
        try {
            Process process = new ProcessBuilder(buildSimctlCommand(
                    "spawn", udid, "defaults", "read", preferencesDomain(), "flutter." + key))
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("讀本機偏好設定逾時：" + key);
            }
            return process.exitValue() == 0 ? output : null;
        } catch (IOException e) {
            throw new IllegalStateException("讀本機偏好設定失敗：" + key, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("讀本機偏好設定時被中斷：" + key, e);
        }
    }

    /**
     * 刪掉 {@link #writeLocalPreference} 那一把鍵。給「這台手機還沒有任何紀錄」的起始狀態用：
     * 同一台模擬器連跑很多情境，前一個情境寫下的偏好設定會留給下一個情境。
     */
    public void deleteLocalPreference(String key) {
        runSimctl("spawn", udid, "defaults", "delete", preferencesDomain(), "flutter." + key);
    }

    /**
     * 情境中途把 App process 整個重開（terminate＋launch），再等測試後門就緒。給「改了只有
     * 啟動時才讀的本機狀態」的 Given 用。重開之後 App 回到登入畫面，所以登入者記錄一併清掉，
     * 下一個需要身分的句子會重新登入；後端資料與員工表不動。
     */
    public void restartApp() {
        runSimctl("terminate", udid, bundleId);
        runSimctlRequired("launch", udid, bundleId);
        waitForBackdoorReady();
        loggedInStaff = null;
        currentTaskRoomNo = null;
        offlineWindowPendingCount = null;
        offlineWindowTaskStates = null;
        offlineWindowUnitStates = null;
    }

    /**
     * 把畫面帶回某個外殼的根畫面（主管首頁 {@code manager.home}、工務首頁 {@code engHome.screen}）：
     * 已在上面就不動，否則逐層按返回，最多 {@link #MAX_BACK_TAPS} 次。與 {@link #ensureOnHome}
     * 同一套走法，只是根畫面的 id 由呼叫端給——首頁依角色分流之後，根畫面不再只有一種。
     */
    public void ensureOnScreenRoot(String screenId, Duration timeout) {
        for (int i = 0; i < MAX_BACK_TAPS; i++) {
            if (!driver().findElements(byTestId(screenId)).isEmpty()) {
                return;
            }
            if (!driver().findElements(byTestId("celebrate.screen")).isEmpty()) {
                tapTestId("celebrate.back-home-button", timeout);
                sleep(300);
                continue;
            }
            List<WebElement> back = driver().findElements(byTestId("nav.back-button"));
            if (back.isEmpty()) {
                back = driver().findElements(AppiumBy.accessibilityId("Back"));
            }
            if (back.isEmpty()) {
                break;
            }
            back.get(0).click();
            sleep(300);
        }
        waitForTestId(screenId, timeout);
    }

    private void runSimctl(String... args) {
        try {
            runProcess(buildSimctlCommand(args));
        } catch (IOException | InterruptedException e) {
            // 非必要指令（例如 terminate 一個還沒啟動過的 App）失敗就算了，不影響後續流程。
        }
    }

    private void runSimctlRequired(String... args) {
        try {
            int exit = runProcess(buildSimctlCommand(args));
            if (exit != 0) {
                throw new IllegalStateException("xcrun simctl " + String.join(" ", args) + " 失敗，exit=" + exit);
            }
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("執行 xcrun simctl " + String.join(" ", args) + " 失敗", e);
        }
    }

    private List<String> buildSimctlCommand(String... args) {
        List<String> command = new ArrayList<>();
        command.add("xcrun");
        command.add("simctl");
        for (String a : args) {
            command.add(a);
        }
        return command;
    }

    private int runProcess(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        // 吞掉 stdout，避免 pipe 塞滿卡住子程序；呼叫端只關心 exit code，不需要輸出內容。
        process.getInputStream().readAllBytes();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("命令逾時：" + String.join(" ", command));
        }
        return process.exitValue();
    }

    /**
     * 「名下有以下任務」一列的 fixture。類別／預估分鐘／重做次數是選填欄：沒給時
     * 類別預設打掃（多數情境的表只有房號內容狀態），預估分鐘與重做次數不落地。
     */
    public record TaskFixture(
            String roomNo,
            String content,
            String categoryChinese,
            Integer estMinutes,
            String status,
            int elapsedMinutes,
            Integer redoCount,
            String pausedReason,
            boolean inspectionWaived) {
    }
}
