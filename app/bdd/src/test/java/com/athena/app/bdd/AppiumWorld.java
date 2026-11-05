package com.athena.app.bdd;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * {@code 127.0.0.1:9099}，不用額外轉接。
 */
public class AppiumWorld {

    /** 系統屬性 {@code appium.platform} 的合法值：ios｜android。預設 ios（本機先行補的平台）。 */
    public static final String PLATFORM_IOS = "ios";
    public static final String PLATFORM_ANDROID = "android";

    private static final int BACKDOOR_PORT = 9099;
    private static final Duration BACKDOOR_READY_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration NOTIFICATION_FIND_TIMEOUT = Duration.ofSeconds(4);

    private final String platform;
    private final String androidAppPackage;

    private final String appiumServerUrl;
    private final String udid;
    private final String bundleId;
    private final String appPath;

    /**
     * 累積中的 {@code /seed} 狀態。後門的 {@code /seed} 是整包覆蓋（沒帶到的欄位視為清空），
     * 不是合併；同一情境內可能有多個 Given 各自貢獻一部分資料（例如背景句先建員工，
     * 後面又建任務），所以要在這裡先把目前情境累積到的全部狀態留著，每次改動後整包重送，
     * 才不會後面的 Given 把前面 Given 建好的資料洗掉。
     */
    private final List<String> pendingStaffNames = new ArrayList<>();
    private final List<TaskFixture> pendingTasks = new ArrayList<>();

    /**
     * 上一次送出的推播 payload 檔案與標題。banner 自動收起的時間很短，{@link #tapPushNotification()}
     * 第一次找不到時要能照原樣重送同一則推播再試一次，所以要留著。
     */
    private Path lastPushPayloadFile;
    private String lastPushTitle;

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
        pendingStaffNames.clear();
        pendingTasks.clear();
        lastPushPayloadFile = null;
        lastPushTitle = null;

        runSimctl("terminate", udid, bundleId); // 第一個情境時 App 還沒啟動過，失敗沒關係
        runSimctlRequired("launch", udid, bundleId);
        waitForBackdoorReady();
    }

    /**
     * App process 重開之後，Dart 側要先跑到 {@code main()} 把 {@code TestBackdoor} server
     * 重新綁好 port，這中間有個短暫的空窗；用重試代替固定 sleep，是因為這段空窗長短跟
     * 模擬器當下的負載有關，固定等一個數字容易不是等太久、就是等不夠。
     */
    private void waitForBackdoorReady() {
        long deadline = System.nanoTime() + BACKDOOR_READY_TIMEOUT.toNanos();
        IOException lastError = null;
        while (System.nanoTime() < deadline) {
            try {
                httpPostJson("/seed", "{\"staff\":[],\"tasks\":[]}");
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

    /** 背景句「飯店有以下員工」：記下姓名清單，整包重送 /seed。 */
    public void seedStaff(List<String> names) {
        pendingStaffNames.clear();
        pendingStaffNames.addAll(names);
        postSeed();
    }

    /** 「{@code "X" 名下有以下任務}」：把這批任務疊加進既有清單，整包重送 /seed。 */
    public void seedTasks(List<TaskFixture> tasks) {
        pendingTasks.addAll(tasks);
        postSeed();
    }

    /**
     * 推播去處指向待認領池且要標亮某房號時（dsl.md「建立去處所指的任務」），畫面上要先
     * 有一張狀態是待認領的卡才有東西可以標亮，但 outline 場景並沒有另外一個「名下有以下
     * 任務」步驟替它建這張卡，所以由這裡補。同房號已存在就覆蓋掉，不疊兩張重複的卡。
     */
    public void ensurePoolTask(String roomNo, String content) {
        pendingTasks.removeIf(t -> t.roomNo.equals(roomNo));
        pendingTasks.add(new TaskFixture(roomNo, content, "待認領", 0, null));
        postSeed();
    }

    private void postSeed() {
        StringBuilder json = new StringBuilder();
        json.append("{\"staff\":[");
        for (int i = 0; i < pendingStaffNames.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(jsonString(pendingStaffNames.get(i)));
        }
        json.append("],\"tasks\":[");
        for (int i = 0; i < pendingTasks.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(pendingTasks.get(i).toJson());
        }
        json.append("]}");
        try {
            httpPostJson("/seed", json.toString());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("呼叫測試後門 /seed 失敗", e);
        }
    }

    /**
     * 測試後門是 Dart {@code HttpServer} 最簡單的用法，沒有特別調過連線池行為；實測中
     * 觀察到用同一個 {@code HttpClient} 重用 keep-alive 連線時，偶爾會連到一條後門那邊
     * 已經關閉的連線，收到 {@code Connection reset}。Java 的 {@code HttpClient} 不讓呼叫端
     * 自己設 {@code Connection} 這個 restricted header 去關掉 keep-alive，所以改成每次都
     * 開一個全新的 {@code HttpClient}（天生沒有舊連線可重用）＋失敗重送一次，比追查
     * Dart 那邊的連線池細節划算。
     */
    private void httpPostJson(String path, String json) throws IOException, InterruptedException {
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
        // seed 就會靜默變成空包。
        HttpResponse<Void> response = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build()
                .send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != 200) {
            throw new IOException("POST " + path + " -> HTTP " + response.statusCode());
        }
    }

    /**
     * 送一則推播給 simulator。先把 App 切到背景再推：這支 feature 要驗證的是
     * 「背景中被推播喚回前景後導到哪裡」，所以推播必須在 App 不在前景時送出，App
     * 才會是被「喚回」而不是單純收到提示。payload 的 {@code dest}/{@code room}/
     * {@code highlight} 欄位跟 {@code lib/core/push_gateway.dart} 的約定一致。
     */
    public void sendPush(String title, String body, String dest, String room, String highlight) {
        backgroundApp();

        StringBuilder json = new StringBuilder();
        json.append("{\"aps\":{\"alert\":{\"title\":").append(jsonString(title))
                .append(",\"body\":").append(jsonString(body == null ? "" : body))
                .append("},\"sound\":\"default\"}");
        json.append(",\"dest\":").append(jsonString(dest));
        if (room != null) {
            json.append(",\"room\":").append(jsonString(room));
        }
        if (highlight != null) {
            json.append(",\"highlight\":").append(jsonString(highlight));
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
        } catch (RuntimeException e) {
            try {
                Files.writeString(Path.of("/tmp/tms-f3-debug-source.xml"), driver().getPageSource(), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
            throw new AssertionError("等不到畫面元素 " + id + "（逾時 " + timeout.getSeconds() + "s）", e);
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

    /** {@code /seed} 的單筆任務 fixture，欄位對應 feature 檔「名下有以下任務」data table。 */
    public record TaskFixture(
            String roomNo,
            String content,
            String status,
            int elapsedMinutes,
            String pausedReason) {

        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"roomNo\":").append(jsonString(roomNo));
            sb.append(",\"content\":").append(jsonString(content));
            sb.append(",\"status\":").append(jsonString(status));
            sb.append(",\"elapsedMinutes\":").append(elapsedMinutes);
            sb.append(",\"pausedReason\":").append(pausedReason == null ? "null" : jsonString(pausedReason));
            sb.append('}');
            return sb.toString();
        }
    }
}
