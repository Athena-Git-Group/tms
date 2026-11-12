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

public class AppiumWorld {

    public static final String PLATFORM_IOS = "ios";
    public static final String PLATFORM_ANDROID = "android";

    private static final int BACKDOOR_PORT = 9099;
    private static final Duration BACKDOOR_READY_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration NOTIFICATION_FIND_TIMEOUT = Duration.ofSeconds(4);
    private static final int MAX_LIST_SCROLLS = 6;

    private static final int MAX_BACK_TAPS = 3;

    private static final int MAX_LOGIN_ATTEMPTS = 2;
    private static final Duration LOGIN_TIMEOUT = Duration.ofSeconds(25);

    private final String platform;
    private final String androidAppPackage;

    private final String appiumServerUrl;
    private final String udid;
    private final String bundleId;
    private final String appPath;

    private final BackendChannel backend = new BackendChannel();

    private final Map<String, BackendChannel.CreatedStaff> staffByName = new LinkedHashMap<>();

    private final java.util.Map<String, DeferredStart> roomsToStartInApp = new java.util.LinkedHashMap<>();

    private Path lastPushPayloadFile;
    private String lastPushTitle;

    private BackendChannel.CreatedStaff notificationViewer;

    private BackendChannel.CreatedStaff loggedInStaff;

    private String currentTaskRoomNo;

    private OptionalInt offlineWindowPendingCount;

    private Map<String, String> offlineWindowTaskStates;

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

    @Before(order = 0)
    public void before() {
        start();
        ensureSessionAlive();
        resetAppState();
    }

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
                }
            }
            start();
        }
    }

    @After(order = 0)
    public void after() {
    }

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
        // headless 環境下 simctl privacy grant notifications 會因 TCC 建檔失敗而授不了權，
        // 所以交給 WDA 自動按掉系統彈窗。
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
                }
            }
        }));
    }

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
        backend.resetBusinessData();
        backend.resetBackendClock();

        runSimctl("terminate", udid, bundleId);
        runSimctlRequired("launch", udid, bundleId);
        waitForBackdoorReady();
    }

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

    public BackendChannel backend() {
        return backend;
    }

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

    private void dismissForgotPasswordAlert() {
        if (driver().findElements(byTestId("login.forgot-password-alert")).isEmpty()) {
            return;
        }
        for (WebElement dismiss
                : driver().findElements(byTestId("login.forgot-password-alert.dismiss-button"))) {
            dismiss.click();
        }
    }

    private static String landingScreenId(BackendChannel.CreatedStaff staff) {
        if ("manager".equals(staff.role())) {
            return "manager.home";
        }
        return "engineering".equals(staff.dept()) ? "engHome.screen" : "home.screen";
    }

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

    public void ensureOnEngineeringHome(BackendChannel.CreatedStaff staff) {
        ensureLoggedInAs(staff);
        Duration timeout = Duration.ofSeconds(10);
        if (!driver().findElements(byTestId("engHome.screen")).isEmpty()) {
            return;
        }
        waitForLabelContaining("首頁", timeout).click();
        waitForTestId("engHome.screen", timeout);
    }

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

    public void openAskPanel(BackendChannel.CreatedStaff staff, String roomNo) {
        Duration timeout = Duration.ofSeconds(10);
        if (!driver().findElements(byTestId("ask.sheet")).isEmpty()) {
            return;
        }
        ensureOnTaskDetail(staff, roomNo);
        tapTestId("task.ask-button", timeout);
        waitForTestId("ask.suggestion.0", timeout);
    }

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

    public void settleDeferredStarts() {
        for (Map.Entry<String, DeferredStart> entry :
                new java.util.LinkedHashMap<>(roomsToStartInApp).entrySet()) {
            ensureOnTaskDetail(entry.getValue().staff(), entry.getKey());
        }
    }

    private record DeferredStart(BackendChannel.CreatedStaff staff) {
    }

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

    public Map<String, String> offlineWindowUnitStates() {
        if (offlineWindowUnitStates == null) {
            throw new IllegalStateException(
                    "目前這個離線視窗沒有量到開場的後端狀態，無從比對操作前後的狀態。"
                            + "手機要嘛還沒切到離線，要嘛切過之後又恢復連線把基準清掉了");
        }
        return offlineWindowUnitStates;
    }

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

    public void goOnline() {
        postBackdoorJson("/offline", "{\"offline\":false}");
        offlineWindowPendingCount = null;
        offlineWindowTaskStates = null;
        offlineWindowUnitStates = null;
    }

    public OptionalInt offlineWindowPendingCount() {
        if (offlineWindowPendingCount == null) {
            throw new IllegalStateException(
                    "目前這個離線視窗沒有量到開場的待送出筆數，無從比對操作前後的筆數。"
                            + "手機要嘛還沒切到離線，要嘛切過之後又恢復連線把基準清掉了");
        }
        return offlineWindowPendingCount;
    }

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

    public BackendChannel.CreatedStaff notificationViewer() {
        if (notificationViewer == null) {
            throw new IllegalStateException(
                    "通知中心的當事員工尚未設定——場景是否漏了「X 的通知中心已有以下通知」或「X 已在 App 打開通知中心」？");
        }
        return notificationViewer;
    }

    public BackendChannel.CreatedStaff loggedInStaff() {
        if (loggedInStaff == null) {
            throw new IllegalStateException("App 尚未登入——場景是否漏了打開畫面的 When／Given？");
        }
        return loggedInStaff;
    }

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

    private List<String> visibleTestIdsWithPrefix(String prefix) {
        // XCUITest 的 name 屬性就是 accessibilityIdentifier。
        By locator = AppiumBy.iOSNsPredicateString("name BEGINSWITH " + nsPredicateString(prefix));
        List<WebElement> found = driver().findElements(locator);
        List<Map.Entry<Integer, String>> rows = new ArrayList<>();
        for (WebElement e : found) {
            try {
                org.openqa.selenium.Rectangle rect = e.getRect();
                String name = e.getAttribute("name");
                // XCUITest 把捲出可視範圍的 cell 回報成原點上的 0×0 矩形。
                if (rect.getHeight() > 0 && name != null) {
                    rows.add(Map.entry(rect.getY(), name));
                }
            } catch (RuntimeException ignored) {
            }
        }
        rows.sort(java.util.Comparator.comparingInt(Map.Entry::getKey));
        return rows.stream().map(Map.Entry::getValue).toList();
    }

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

    public String labelOf(String id) {
        List<WebElement> found = driver().findElements(byTestId(id));
        return found.isEmpty() ? null : found.get(0).getAttribute("label");
    }

    public BackendChannel.CreatedStaff staff(String name) {
        BackendChannel.CreatedStaff staff = staffByName.get(name);
        if (staff == null) {
            throw new IllegalStateException(
                    "員工「" + name + "」尚未建立——場景是否漏了背景句「飯店有以下員工」？");
        }
        return staff;
    }

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

    private BackendChannel.CreatedStaff onlyOperator() {
        if (staffByName.size() == 1) {
            return staffByName.values().iterator().next();
        }
        return onlyFieldStaff();
    }

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

    public void setStaffCredentials(String name, String loginId, String password) {
        BackendChannel.CreatedStaff old = staff(name);
        backend.setCredentials(old.id(), loginId, password);
        staffByName.put(name, new BackendChannel.CreatedStaff(
                old.id(), old.name(), loginId, password, old.role(), old.dept()));
    }

    public void seedStaff(String name, String deptChinese) {
        seedStaff(name, deptChinese, null);
    }

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

    private static boolean startableInApp(String stateCode, TaskFixture task) {
        if (!"doing".equals(stateCode) || task.pausedReason() != null) {
            return false;
        }
        if (task.inspectionWaived()) {
            return false;
        }
        return task.elapsedMinutes() == 0;
    }

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
        // JDK HttpClient 預設對 http:// 帶 h2c 升級標頭，Dart HttpServer 收到會回 200 卻丟掉
        // request body，所以鎖 HTTP/1.1。
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

    public OffsetDateTime deviceNow() {
        OffsetDateTime injected = injectedDeviceClock();
        return injected != null ? injected : OffsetDateTime.now();
    }

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

    public void sendPush(String title, String body, String dest, String room, String taskId, String highlight) {
        sendPush(title, body, dest, room, taskId, highlight, null, null);
    }

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

    public void backgroundApp() {
        driver().executeScript("mobile: pressButton", Map.of("name", "home"));
    }

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
        // iOS 的通知橫幅幾秒就自動收起，同一則通知仍留在通知中心。
        if (tryTapNotificationCenter()) {
            return;
        }
        throw new IllegalStateException(
                "點開推播失敗：橫幅（NotificationShortLookView，標題含 \"" + lastPushTitle + "\"）"
                        + "兩次都沒抓到，下拉通知中心備援也找不到這則通知。"
                        + "可能原因：通知授權沒生效、或 payload 沒送達。");
    }

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

    /** Flutter 不會替還沒捲進畫面的清單項目建立 semantics 節點，要捲到它出現才定位得到。 */
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

    private WebElement failWithPageSource(String id, Duration timeout, RuntimeException cause) {
        String dump = dumpPageSourceTagged("missing-" + id);
        // XCUITest 的元素樹不含鍵盤、系統對話框這些系統層元素，被它們蓋住時只有截圖看得出來。
        String shot = dumpScreenshotTagged("missing-" + id);
        throw new AssertionError(
                "等不到畫面元素 " + id + "（逾時 " + timeout.getSeconds() + "s，含往下捲動尋找）；元素樹 "
                        + dump + "；截圖 " + shot,
                cause);
    }

    private String dumpScreenshotTagged(String tag) {
        String path = "/tmp/tms-app-bdd-" + tag + "-" + System.currentTimeMillis() + ".png";
        try {
            Files.write(Path.of(path), driver().getScreenshotAs(OutputType.BYTES));
            return path;
        } catch (Exception e) {
            return "（存檔失敗：" + e.getMessage() + "）";
        }
    }

    public void typeInto(String testId, Duration timeout, String text) {
        withStaleRetry(testId, timeout, field -> {
            field.click();
            if (text.chars().allMatch(c -> c < 128)) {
                field.sendKeys(text);
                return;
            }
            // WDA 的 typeText 逐字敲軟體鍵盤，英文鍵盤敲不出非 ASCII 字元而且不報錯，所以改用剪貼簿貼上。
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

    public String dumpPageSourceTagged(String tag) {
        String path = "/tmp/tms-app-bdd-" + tag + "-" + System.currentTimeMillis() + ".xml";
        dumpPageSource(path);
        return path;
    }

    public List<String> describeTestIdsWithPrefix(String prefix) {
        By locator = AppiumBy.iOSNsPredicateString("name BEGINSWITH " + nsPredicateString(prefix));
        List<String> described = new ArrayList<>();
        for (WebElement e : driver().findElements(locator)) {
            try {
                described.add(e.getAttribute("name") + "@" + e.getRect());
            } catch (RuntimeException ignored) {
            }
        }
        return described;
    }

    private void dumpPageSource(String path) {
        try {
            Files.writeString(Path.of(path), driver().getPageSource(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

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

    public void tapTestId(String testId, Duration timeout) {
        withStaleRetry(testId, timeout, element -> {
            waitForStableRect(element);
            element.click();
        });
    }

    public void tapWhenEnabled(String testId, Duration timeout) {
        withStaleRetry(testId, timeout, element -> {
            long deadline = System.nanoTime() + timeout.toNanos();
            // XCUITest 的 sendKeys 會在 Flutter 處理完最後一個字之前返回，這時送出鈕可能還是禁用。
            while (!element.isEnabled() && System.nanoTime() < deadline) {
                sleep(150);
            }
            waitForSettledLayoutAfterTyping(element);
            element.click();
        });
    }

    /** iOS 的密碼自動填入列會在鍵盤出現之後才長出來，把貼著鍵盤的置底列再往上推一次。 */
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

    /** XCUITest 以點擊當下的座標落點，轉場動畫還在跑時會點到元素移動前的位置。 */
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

    /** Flutter 在轉場或 rebuild 時會重發 semantics 節點，先前拿到的 element handle 會失效。 */
    private void withStaleRetry(String testId, Duration timeout, java.util.function.Consumer<WebElement> action) {
        try {
            action.accept(waitForTestId(testId, timeout));
        } catch (org.openqa.selenium.StaleElementReferenceException e) {
            action.accept(waitForTestId(testId, timeout));
        }
    }

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

    public By byTestId(String id) {
        if (PLATFORM_ANDROID.equals(platform)) {
            // Flutter 的 Semantics(identifier:) 在 Android 映射成 resource-id，不能用 accessibilityId() 比對。
            return AppiumBy.id(androidAppPackage + ":id/" + id);
        }
        return AppiumBy.accessibilityId(id);
    }

    public String platform() {
        return platform;
    }

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

    public void writeLocalPreference(String key, String value) {
        // shared_preferences 在 iOS 存進 NSUserDefaults，鍵名固定加上 flutter. 前綴。
        runSimctlRequired("spawn", udid, "defaults", "write", preferencesDomain(), "flutter." + key, "-string", value);
    }

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
            // defaults 只給 bundle id 時讀寫的是模擬器使用者層的 plist，不是 App 容器裡那一份，
            // 所以要給容器內的完整路徑（不含 .plist）。
            return container + "/Library/Preferences/" + bundleId;
        } catch (IOException e) {
            throw new IllegalStateException("查 App 資料容器失敗：" + bundleId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("查 App 資料容器時被中斷：" + bundleId, e);
        }
    }

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

    public void deleteLocalPreference(String key) {
        runSimctl("spawn", udid, "defaults", "delete", preferencesDomain(), "flutter." + key);
    }

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
        // 不把 stdout 讀掉，pipe 塞滿時子程序會卡住。
        process.getInputStream().readAllBytes();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("命令逾時：" + String.join(" ", command));
        }
        return process.exitValue();
    }

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
