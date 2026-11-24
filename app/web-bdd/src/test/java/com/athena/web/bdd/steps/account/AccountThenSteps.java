package com.athena.web.bdd.steps.account;

import com.athena.web.bdd.support.BrowserRegex;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.web.bdd.pages.AccountPanel;
import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.ChangePasswordPage;
import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.LoginPage;
import com.athena.web.bdd.pages.LoginPage.Submission;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.UiWords;
import com.athena.web.bdd.pages.UiWords.Language;
import com.athena.web.bdd.steps.guest.common.GuestCommonThenSteps;
import com.athena.web.bdd.support.AccountScenario;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.ScenarioTasks.Entry;
import com.athena.web.bdd.support.SessionInjector;
import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.AfterStep;
import io.cucumber.java.zh_tw.那麼;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 「帳號與登入」模組的 Then 句型。 */
public class AccountThenSteps {

    /** 等畫面變成預期的樣子最久等這麼久（共通約定：輪詢，上限 5 秒）。 */
    private static final double SCREEN_TIMEOUT_MS = 5_000;

    /** 測試直接打後端時用的位址，和 {@code BackendChannel} 讀同一個系統屬性。 */
    private static final String BACKEND_API =
            System.getProperty("backend.base.url", "http://127.0.0.1:8080").replaceAll("/+$", "") + "/api/v1";

    /** 拿來重送「同一帳號輸入錯誤密碼」的密碼，不會是任何人真的密碼。 */
    private static final String WRONG_PASSWORD = "WebBddWrong-0000";

    /** 錯誤回應裡每次都不一樣的欄位，比對「內容相同」時不看。 */
    private static final List<String> VOLATILE_ERROR_FIELDS =
            List.of("timestamp", "traceId", "requestId", "path", "instance");

    /** 顯示密碼鈕的兩種名稱，按一下就換成另一個。 */
    private static final Map<String, String> TOGGLE_OPPOSITE = Map.of("顯示密碼", "隱藏密碼", "隱藏密碼", "顯示密碼");

    private static final String LOGIN_PAGE_TITLE = "登入 TMS 櫃檯";

    private static final String CHANGE_PASSWORD_TITLE = "設定新密碼";

    /** 共用裝置的說明，登入頁上飯店名稱那一行在大標題與它之間。 */
    private static final String SHARED_DEVICE_NOTE = "這台裝置是櫃檯共用的，請用自己的帳號登入";

    /** 共通約定的預設飯店名稱。 */
    private static final String DEFAULT_HOTEL_NAME = "雅典娜酒店";

    /** 詳情面板上繁體中文的固定字句：關閉、兩個區塊標題與「內容」的六個欄名。 */
    private static final List<String> DETAIL_PANEL_CHINESE_LABELS =
            List.of("關閉", "內容", "歷程", "房號", "住客", "部門", "負責人", "來源", "建立");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebWorld world;

    private final LoginAttempts attempts;

    private final AccountScenario scenario;

    private final LoginPage login;

    private final ChangePasswordPage changePassword;

    private final AccountPanel account;

    private final ShellFrame shell;

    private final BoardPage board;

    private final DetailPanel detail;

    /** 每一步結束時這個場景送出過幾個請求、目前分頁的網址；給「什麼都沒發生」這類檢查找 When 之前的樣子。 */
    private final List<Integer> requestCountAfterStep = new ArrayList<>();

    private final List<String> urlAfterStep = new ArrayList<>();

    /** 住客掃碼頁也有「畫面是 "{頁}"」這一句，頁的種類不同；住客場景由住客那邊判斷。 */
    private final GuestScenario guestScenario;

    private final GuestCommonThenSteps guestThen;

    public AccountThenSteps(WebWorld world, LoginAttempts attempts, AccountScenario scenario,
            GuestScenario guestScenario, GuestCommonThenSteps guestThen) {
        this.world = world;
        this.attempts = attempts;
        this.scenario = scenario;
        this.guestScenario = guestScenario;
        this.guestThen = guestThen;
        this.login = new LoginPage(world);
        this.changePassword = new ChangePasswordPage(world);
        this.account = new AccountPanel(world);
        this.shell = new ShellFrame(world);
        this.board = new BoardPage(world);
        this.detail = new DetailPanel(world);
    }

    /** 只記數字與網址，不碰畫面，每一步多花的時間可以忽略。 */
    @AfterStep
    public void rememberAfterStep() {
        requestCountAfterStep.add(world.network().sentRequestCount());
        urlAfterStep.add(world.hasPage() ? world.page().url() : null);
    }

    // ---------------------------------------------------------------- 登入頁

    @那麼("登入頁的員工編號欄與密碼欄都是空的")
    public void loginFieldsEmpty() {
        assertLoginFieldsEmpty();
    }

    @那麼("登入頁的 {string} 欄顯示 {string}")
    public void loginFieldShows(String fieldName, String content) {
        assertThat(login.field(fieldName)).hasValue(content);
    }

    @那麼("登入頁的 {string} 欄裡只有 {int} 個字")
    public void loginFieldHoldsOnly(String fieldName, int limit) {
        Locator field = login.field(fieldName);
        assertThat(field).hasValue(Pattern.compile("^.{" + limit + "}$", Pattern.DOTALL));
        String value = field.inputValue();
        if (value.codePointCount(0, value.length()) != limit) {
            throw new AssertionError("「" + fieldName + "」欄應該剛好 " + limit + " 個字，實際是「" + value + "」");
        }
    }

    @那麼("密碼欄以明文顯示 {string}，切換鈕變成 {string}")
    public void passwordShownInPlain(String password, String toggleName) {
        Locator field = login.field("密碼");
        assertThat(field).hasAttribute("type", "text");
        assertThat(field).hasValue(password);
        assertToggleNamed(toggleName);
    }

    @那麼("密碼欄的內容被遮住，切換鈕變成 {string}")
    public void passwordMasked(String toggleName) {
        assertThat(login.field("密碼")).hasAttribute("type", "password");
        assertToggleNamed(toggleName);
    }

    /**
     * 畫面、帳號卡、分頁裡的登入憑證三者都要對；憑證另外拿去讀看板資料，確認它真的能用。
     * 送出的員工編號和輸入的照字面相同，看的是最近一次在登入頁按「登入」送出的請求；
     * 登入是前置句自己走的、沒有輸入紀錄時，和這位員工的員工編號比。
     */
    @那麼("{string} 登入成功，畫面是 {string}")
    public void loggedInOn(String staffName, String pageName) {
        CreatedStaff staff = scenario.current(world.staff(staffName));
        assertOnPage(pageName);
        assertAccountCardIs(staff);

        JsonNode stored = storedLoginResponse(world.page());
        if (stored == null) {
            throw new AssertionError("這個分頁的儲存空間沒有登入憑證");
        }
        if (!staff.loginId().equals(stored.path("loginId").asText(null))
                && !staff.id().toString().equals(stored.path("staffId").asText(null))) {
            throw new AssertionError("這個分頁存的登入憑證不是 " + staffName + " 的：" + stored);
        }
        HttpResult boardRead = backendGet(Network.DESK_BOARD.path().replaceFirst("^/api/v1", ""),
                stored.path("token").asText());
        if (boardRead.status() != 200) {
            throw new AssertionError("拿這個分頁存的登入憑證讀看板資料，預期 200，實際 "
                    + boardRead.status() + "：" + boardRead.body());
        }

        String sentLoginId = lastSentLoginId(world.page())
                .orElseThrow(() -> new AssertionError("這個場景沒有在登入頁送出過登入請求，看不到送出的員工編號"));
        String typed = attempts.last().map(Submission::typedLoginId).orElse(staff.loginId());
        if (!sentLoginId.equals(typed)) {
            throw new AssertionError("送出的員工編號應該和輸入的「" + typed + "」照字面相同，實際送出「" + sentLoginId + "」");
        }
    }

    /** 畫面停在登入頁、分頁沒有登入憑證，而且直接開總覽看板的網址會被帶回登入頁。 */
    @那麼("這個分頁沒有登入，畫面是 {string}")
    public void notLoggedInOn(String pageName) {
        if (!"登入頁".equals(pageName.strip())) {
            throw new IllegalArgumentException("沒有登入時畫面只會是「登入頁」，收到「" + pageName + "」");
        }
        assertOnPage("登入頁");
        assertNoCredentials(world.page());

        world.open(WebApp.BOARD);
        waitUntil("直接開總覽看板的網址應該被帶回登入頁", login::onLoginPath);
        login.waitUntilShown();
        assertNoCredentials(world.page());
    }

    @那麼("畫面是 {string}")
    public void screenIs(String pageName) {
        if (guestScenario.active()) {
            guestThen.screenIs(pageName);
            return;
        }
        assertOnPage(pageName);
    }

    /** 畫面上沒有看板卡片與待派單列，這個分頁也從來沒有讀過看板資料。 */
    @那麼("畫面上看不到任何一張單")
    public void noTaskOnScreen() {
        Page page = world.page();
        Locator roomButtons = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^\\d{3,4}(\\s|$)")));
        assertThat(roomButtons).hasCount(0);
        for (Entry entry : world.tasks().all()) {
            assertThat(page.getByText(entry.title(), new Page.GetByTextOptions().setExact(true))).hasCount(0);
        }
        List<Network.SentRequest> boardReads = world.network().sentRequests().stream()
                .filter(request -> request.page() == page && request.is(Network.DESK_BOARD))
                .toList();
        if (!boardReads.isEmpty()) {
            throw new AssertionError("這個分頁送出過 " + boardReads.size() + " 次看板資料的讀取");
        }
    }

    /** 一行紅字（不是提示卡）、密碼欄清空、員工編號欄照字面留著剛才輸入的員工編號，送出的也是它。 */
    @那麼("登入頁顯示紅字 {string}，密碼欄被清空，員工編號欄保留")
    public void loginErrorLine(String text) {
        assertSingleNoticeLine(text);
        assertThat(login.field("密碼")).hasValue("");

        String sentLoginId = lastSentLoginId(world.page())
                .orElseThrow(() -> new AssertionError("這個場景沒有在登入頁送出過登入請求"));
        Optional<String> typed = attempts.last().map(Submission::typedLoginId);
        if (typed.isPresent() && !sentLoginId.equals(typed.get())) {
            throw new AssertionError("送出的員工編號應該和輸入的「" + typed.get() + "」照字面相同，實際送出「" + sentLoginId + "」");
        }
        assertThat(login.field("員工編號")).hasValue(sentLoginId);
    }

    /**
     * 提示位置只有這一句，沒有提示卡、對話框，也沒有進設定新密碼畫面；
     * 這次的登入回應是 401，內容和同一帳號以錯誤密碼登入時一樣（另外以錯誤密碼重送一次來比）。
     */
    @那麼("登入頁除了 {string} 沒有其他提示")
    public void onlyThisNotice(String text) {
        assertSingleNoticeLine(text);
        Page page = world.page();
        assertThat(page.getByRole(AriaRole.DIALOG)).hasCount(0);
        assertThat(page.getByRole(AriaRole.ALERTDIALOG)).hasCount(0);
        if (!login.onLoginPath() || changePassword.onChangePasswordPath()) {
            throw new AssertionError("應該停在登入頁，實際網址是 " + page.url());
        }

        Submission last = attempts.last().orElse(null);
        String loginId = last != null ? last.loginId() : lastSentLoginId(page).orElse(null);
        String client = last != null ? last.client() : null;
        int status = last != null ? last.status() : lastLoginStatus(page);
        String body = last != null ? last.responseBody() : "";
        if (loginId == null) {
            throw new AssertionError("這個場景沒有在登入頁送出過登入請求");
        }
        if (status != 401) {
            throw new AssertionError("這次登入的回應應該是 401，實際 " + status + "：" + body);
        }

        Map<String, Object> replay = new LinkedHashMap<>();
        replay.put("loginId", loginId);
        replay.put("password", WRONG_PASSWORD);
        if (client != null) {
            replay.put("client", client);
        }
        HttpResult wrongPassword = backendPost("/staff-accounts/login", toJson(replay));
        if (wrongPassword.status() != 401) {
            throw new AssertionError("同一帳號以錯誤密碼登入應該是 401，實際 " + wrongPassword.status());
        }
        JsonNode actual = comparableError(body);
        JsonNode expected = comparableError(wrongPassword.body());
        if (!actual.equals(expected)) {
            throw new AssertionError("這次登入的回應和同一帳號輸入錯誤密碼時不同：這次 " + actual + "，錯誤密碼 " + expected);
        }
    }

    /** 白卡下方是一張有標題與內文的提示卡，不是一行紅字；密碼欄是空的。 */
    @那麼("登入頁顯示提示卡，標題 {string}、內文 {string}")
    public void loginNoticeCard(String title, String body) {
        Locator notices = login.visibleNotices();
        assertThat(notices).hasCount(1);
        Locator card = notices.first();
        assertThat(card.getByText(title, new Locator.GetByTextOptions().setExact(true))).isVisible();
        assertThat(card.getByText(body, new Locator.GetByTextOptions().setExact(true))).isVisible();
        assertThat(login.field("密碼")).hasValue("");
    }

    /** 大標題與共用裝置說明之間沒有任何字；兩欄、「登入」「忘記密碼？」都在。 */
    @那麼("登入頁沒有飯店名稱那一行，其餘文字與欄位照常")
    public void noHotelNameLine() {
        login.waitUntilShown();
        Page page = world.page();
        assertThat(page.getByText(LOGIN_PAGE_TITLE, new Page.GetByTextOptions().setExact(true))).isVisible();
        assertThat(page.getByText(SHARED_DEVICE_NOTE, new Page.GetByTextOptions().setExact(true))).isVisible();
        String text = page.locator("body").innerText();
        int titleEnd = text.indexOf(LOGIN_PAGE_TITLE) + LOGIN_PAGE_TITLE.length();
        int note = text.indexOf(SHARED_DEVICE_NOTE);
        if (note < titleEnd) {
            throw new AssertionError("大標題應該在共用裝置說明之前：" + text);
        }
        String between = text.substring(titleEnd, note).strip();
        if (!between.isEmpty()) {
            throw new AssertionError("大標題與共用裝置說明之間不應該有字，實際是「" + between + "」");
        }
        assertThat(page.getByText(DEFAULT_HOTEL_NAME, new Page.GetByTextOptions().setExact(true))).hasCount(0);
        assertThat(login.field("員工編號")).isVisible();
        assertThat(login.field("密碼")).isVisible();
        assertThat(login.loginButton()).isVisible();
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("忘記密碼？").setExact(true)))
                .isVisible();
    }

    // ---------------------------------------------------------------- 設定新密碼畫面

    @那麼("設定新密碼畫面只有 {string} 與 {string} 兩個輸入欄")
    public void onlyTwoPasswordFields(String first, String second) {
        assertOnPage("設定新密碼畫面");
        assertThat(changePassword.typingFields()).hasCount(2);
        assertThat(changePassword.field(first)).isVisible();
        assertThat(changePassword.field(second)).isVisible();
        Page page = world.page();
        for (String forbidden : List.of("目前密碼", "臨時密碼", "舊密碼")) {
            assertThat(page.getByLabel(forbidden)).hasCount(0);
        }
    }

    @那麼("設定新密碼畫面的兩個輸入欄都是空的")
    public void passwordFieldsEmpty() {
        assertThat(changePassword.newPasswordField()).hasValue("");
        assertThat(changePassword.confirmPasswordField()).hasValue("");
    }

    @那麼("規則 {string} 沒有打勾")
    public void ruleUnchecked(String rule) {
        assertRules(rule);
    }

    @那麼("四條規則都打勾")
    public void allRulesChecked() {
        assertRules(null);
    }

    /** 以後端確認：用臨時密碼登入仍成功，而且仍要求先設定新密碼。 */
    @那麼("後端 {string} 的密碼仍是臨時密碼")
    public void backendStillTemporary(String staffName) {
        CreatedStaff staff = world.staff(staffName);
        String temporary = scenario.temporaryPassword(staffName);
        JsonNode response = world.backend().loginResponse(withPassword(staff, temporary));
        if (!response.path("mustChangePassword").asBoolean(false)) {
            throw new AssertionError(staffName + " 用臨時密碼登入應該仍要求先設定新密碼：" + response);
        }
    }

    /** 以後端（登入不帶 client）確認：臨時密碼登入回 401；新密碼登入成功，不再要求設定新密碼。 */
    @那麼("後端 {string} 的密碼已是 {string}，臨時密碼不能再用，也不再需要設定新密碼")
    public void backendPasswordChanged(String staffName, String newPassword) {
        CreatedStaff staff = world.staff(staffName);
        int oldLogin = world.backend().attemptLogin(staff.loginId(), scenario.temporaryPassword(staffName));
        if (oldLogin != 401) {
            throw new AssertionError(staffName + " 用臨時密碼登入應該回 401，實際 " + oldLogin);
        }
        JsonNode response = world.backend().loginResponse(withPassword(staff, newPassword));
        if (!response.has("mustChangePassword") || response.path("mustChangePassword").asBoolean(true)) {
            throw new AssertionError(staffName + " 用新密碼登入後不應該再要求設定新密碼：" + response);
        }
    }

    // ---------------------------------------------------------------- 帳號面板與登出

    /** 面板標題相符；裡面依序是身分卡、第一列、第二列，沒有別的可以按的東西（標題列的「完成」除外）。 */
    @那麼("標題 {string} 的面板打開，裡面只有身分卡、{string} 與 {string} 三項")
    public void accountPanelOpened(String title, String first, String second) {
        Locator panel = account.panelTitled(title);
        assertThat(panel).isVisible();
        assertThat(panel.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName(title).setExact(true)))
                .isVisible();

        List<String> items = new ArrayList<>();
        for (Locator button : account.pressables().all()) {
            String name = button.innerText().strip();
            if (!isPanelHeaderButton(name)) {
                items.add(name);
            }
        }
        if (items.size() != 2 || !items.get(0).startsWith(first) || !items.get(1).equals(second)) {
            throw new AssertionError("面板身分卡以外應該剛好是「" + first + "」「" + second + "」兩列，實際是 " + items);
        }

        Locator identity = account.identityCard(loggedInName(world.page()));
        assertThat(identity).isVisible();
        Locator firstRow = account.row(rowNamePattern(first)).first();
        if (identity.boundingBox().y >= firstRow.boundingBox().y) {
            throw new AssertionError("身分卡應該在「" + first + "」上面");
        }
    }

    /** 身分卡四處文字照字面相符，飯店名稱等於登入回應的 {@code hotelName}。 */
    @那麼("身分卡顯示頭像 {string}、姓名 {string}、{string} 與 {string}")
    public void identityCardShows(String avatar, String name, String secondLine, String hotel) {
        Locator panel = account.panel();
        assertThat(panel).isVisible();
        for (String text : List.of(avatar, name, secondLine, hotel)) {
            assertThat(panel.getByText(text, new Locator.GetByTextOptions().setExact(true)).first()).isVisible();
        }
        JsonNode stored = storedLoginResponse(world.page());
        String hotelName = stored == null ? null : stored.path("hotelName").asText(null);
        if (!hotel.equals(hotelName)) {
            throw new AssertionError("身分卡的飯店應該等於登入回應的 hotelName（" + hotelName + "），規格寫「" + hotel + "」");
        }
    }

    /** 帳號面板還開著、還是三項；沒有新的對話框，網址沒變，When 之後這個分頁沒有送出任何請求（事件串流的重連除外）。 */
    @那麼("帳號面板維持原樣，沒有開任何頁面或對話框")
    public void accountPanelUnchanged() {
        if (requestCountAfterStep.size() < 2) {
            throw new IllegalStateException("這一句要接在一個 When 之後，前面至少要有兩步");
        }
        int beforeWhen = requestCountAfterStep.get(requestCountAfterStep.size() - 2);
        String urlBeforeWhen = urlAfterStep.get(urlAfterStep.size() - 2);
        Page page = world.page();
        // 點了沒有反應的東西不等待；留一點時間讓可能引發的請求或對話框出現，才看得出來。
        page.waitForTimeout(500);

        assertThat(account.panel()).isVisible();
        assertThat(account.pressables().filter(new Locator.FilterOptions()
                .setHasText(UiWords.anyLanguage("登出")))).hasCount(1);
        assertThat(account.languageOptions()).hasCount(0);
        assertThat(page.getByRole(AriaRole.ALERTDIALOG)).hasCount(0);
        assertThat(page.getByRole(AriaRole.DIALOG)).hasCount(1);
        if (!page.url().equals(urlBeforeWhen)) {
            throw new AssertionError("網址不應該變，原本 " + urlBeforeWhen + "，現在 " + page.url());
        }
        List<Network.SentRequest> sent = world.network().sentRequestsSince(beforeWhen).stream()
                .filter(request -> request.page() == page && !request.is(Network.STAFF_STREAM))
                .toList();
        if (!sent.isEmpty()) {
            throw new AssertionError("點了之後不應該送出任何請求，實際送出 "
                    + sent.stream().map(r -> r.method() + " " + r.path()).toList());
        }
    }

    @那麼("帳號面板是關的")
    public void accountPanelClosed() {
        assertThat(account.panel()).hasCount(0);
        assertThat(world.page().getByRole(AriaRole.DIALOG)).hasCount(0);
    }

    /** 最上層是一個對話框，標題與內文照字面相符，剛好兩顆按鈕、順序相符。 */
    @那麼("畫面出現確認對話框，標題 {string}、內文 {string}、按鈕是 {string} 與 {string}")
    public void confirmDialogShown(String title, String body, String first, String second) {
        Page page = world.page();
        Locator dialog = page.getByRole(AriaRole.ALERTDIALOG, new Page.GetByRoleOptions().setName(title).setExact(true))
                .or(page.getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName(title).setExact(true)));
        assertThat(dialog).hasCount(1);
        assertThat(dialog.getByText(title, new Locator.GetByTextOptions().setExact(true)).first()).isVisible();
        assertThat(dialog.getByText(body, new Locator.GetByTextOptions().setExact(true))).isVisible();
        Locator buttons = dialog.getByRole(AriaRole.BUTTON);
        assertThat(buttons).hasCount(2);
        List<String> names = buttons.allInnerTexts().stream().map(String::strip).toList();
        if (!names.equals(List.of(first, second))) {
            throw new AssertionError("確認對話框的按鈕應該依序是「" + first + "」「" + second + "」，實際是 " + names);
        }
        String topName = shell.currentScreen().getAttribute("aria-label");
        if (topName != null && !topName.equals(title)) {
            throw new AssertionError("最上層應該是「" + title + "」對話框，實際是「" + topName + "」");
        }
    }

    /** 登入頁的文字與欄位值都不含他的姓名與員工編號；沒有帳號清單或切換帳號；分頁沒有他的登入回應。 */
    @那麼("登入頁上沒有 {string} 的姓名或員工編號，也沒有可以點選或切換的帳號")
    public void loginPageForgetsStaff(String staffName) {
        CreatedStaff staff = world.staff(staffName);
        login.waitUntilShown();
        Page page = world.page();
        String text = page.locator("body").innerText();
        if (text.contains(staff.name()) || text.toUpperCase().contains(staff.loginId().toUpperCase())) {
            throw new AssertionError("登入頁上還看得到 " + staffName + " 的姓名或員工編號：" + text);
        }
        for (String value : List.of(login.field("員工編號").inputValue(), login.field("密碼").inputValue())) {
            if (value.contains(staff.name()) || value.equalsIgnoreCase(staff.loginId())) {
                throw new AssertionError("登入頁的欄位裡還有 " + staffName + " 的資料：「" + value + "」");
            }
        }
        assertThat(page.getByText(Pattern.compile("切換帳號|選擇帳號|最近登入"))).hasCount(0);
        assertThat(page.getByRole(AriaRole.LISTBOX)).hasCount(0);
        for (String value : sessionValues(page)) {
            if (value.contains(staff.loginId()) || value.contains(staff.name())) {
                throw new AssertionError("這個分頁的儲存空間還留著 " + staffName + " 的登入資料：" + value);
            }
        }
    }

    // ---------------------------------------------------------------- 登入狀態跟著分頁

    /** 還是登入後的那一頁、帳號卡是他；詳情面板、當場回答面板、帳號面板與對話框都不在。 */
    @那麼("{string} 仍是登入狀態，沒有任何面板或對話框開著")
    public void stillLoggedInNothingOpen(String staffName) {
        String pageName = loggedInPageName(world.page());
        shell.waitForPage(pageName);
        assertAccountCardIs(world.staff(staffName));
        Page page = world.page();
        assertThat(detail.panel()).hasCount(0);
        assertThat(page.getByRole(AriaRole.DIALOG)).hasCount(0);
        assertThat(page.getByRole(AriaRole.ALERTDIALOG)).hasCount(0);
    }

    /** 數量卡是選中的樣子、搜尋房號與泳道相符；看板只剩那張數量卡的欄，卡片都是搜尋的房號。 */
    @那麼("總覽看板的數量卡篩選是 {string}、搜尋房號是 {string}、泳道是 {string}")
    public void boardSelectionKept(String countCard, String roomNo, String lane) {
        shell.waitForPage("總覽看板");
        assertThat(board.countCard(countCard)).hasAttribute("aria-pressed", "true");
        assertThat(board.roomSearch()).hasValue(roomNo);
        waitUntil("泳道分段鈕應該選在「" + lane + "」", () -> board.laneSelected(lane));
        List<String> columns = BoardPage.columnsFilteredBy(countCard);
        waitUntil("看板應該只剩 " + columns, () -> board.columnNames().equals(columns));
        Locator otherRooms = world.page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^(?!" + BrowserRegex.literal(roomNo) + ")\\d{3,4}(\\s|$)")));
        assertThat(otherRooms).hasCount(0);
    }

    @那麼("新分頁是登入頁，員工編號欄與密碼欄都是空的")
    public void newTabIsEmptyLogin() {
        onTab(scenario.newTab(), () -> {
            waitUntil("新分頁應該停在登入頁", login::onLoginPath);
            assertLoginFieldsEmpty();
            assertNoCredentials(world.page());
        });
    }

    /** 重新整理之後還是他的登入狀態，才代表登入狀態真的存在這個分頁裡，不只是畫面還沒換。 */
    @那麼("原本的分頁仍是 {string} 的登入狀態")
    public void originalTabStillLoggedIn(String staffName) {
        CreatedStaff staff = world.staff(staffName);
        onTab(scenario.originalTab(), () -> {
            String pageName = loggedInPageName(world.page());
            shell.waitForPage(pageName);
            assertAccountCardIs(staff);
            world.page().reload();
            shell.waitForPage(pageName);
            assertAccountCardIs(staff);
        });
    }

    @那麼("新分頁是 {string} 的登入狀態")
    public void newTabLoggedIn(String staffName) {
        CreatedStaff staff = world.staff(staffName);
        onTab(scenario.newTab(), () -> {
            waitUntil("新分頁應該停在總覽看板", () -> shell.onPage("總覽看板"));
            shell.waitForPage("總覽看板");
            assertAccountCardIs(staff);
        });
    }

    @那麼("原本的分頁回到登入頁")
    public void originalTabBackToLogin() {
        onTab(scenario.originalTab(), () -> {
            waitUntil("原本的分頁應該回到登入頁", login::onLoginPath);
            assertLoginFieldsEmpty();
        });
    }

    // ---------------------------------------------------------------- 介面語言

    /** 「登入」鈕與欄名是該語言的寫法；右上語言選單上的名稱相符。 */
    @那麼("登入頁的介面語言是 {string}，語言選單顯示 {string}")
    public void loginPageLanguage(String languageName, String option) {
        Language language = Language.of(languageName);
        login.waitUntilShown();
        Page page = world.page();
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(UiWords.in(language, "登入")).setExact(true))).isVisible();
        assertThat(page.getByLabel(UiWords.in(language, "員工編號"), new Page.GetByLabelOptions().setExact(true)))
                .isVisible();
        assertThat(login.languageMenuButton()).hasText(Pattern.compile("^\\s*" + BrowserRegex.literal(option) + "\\s*$"));
        assertDocumentLanguage(page, language);
    }

    @那麼("語言選單依序列出 {string}、{string}、{string}、{string}，打勾的是 {string}")
    public void languageMenuLists(String one, String two, String three, String four, String selected) {
        assertThat(login.languageMenu()).isVisible();
        Locator options = login.languageMenuOptions();
        assertThat(options).hasCount(4);
        List<String> expected = List.of(one, two, three, four);
        List<String> names = options.allInnerTexts().stream().map(String::strip).toList();
        if (!names.equals(expected)) {
            throw new AssertionError("語言選單應該依序是 " + expected + "，實際是 " + names);
        }
        for (int i = 0; i < expected.size(); i++) {
            boolean checked = options.nth(i).isChecked();
            if (checked != expected.get(i).equals(selected)) {
                throw new AssertionError("語言選單只有「" + selected + "」應該打勾，「" + expected.get(i) + "」是 "
                        + (checked ? "打勾" : "沒打勾"));
            }
        }
    }

    @那麼("登入後的介面語言是 {string}")
    public void loggedInLanguage(String languageName) {
        assertShellLanguage(Language.of(languageName));
    }

    @那麼("新分頁登入後的介面語言是 {string}")
    public void newTabLoggedInLanguage(String languageName) {
        Language language = Language.of(languageName);
        onTab(scenario.newTab(), () -> assertShellLanguage(language));
    }

    /**
     * 規格要求「登出再登入後介面仍是該語言」。登出會改掉目前分頁的樣子，影響後面的步驟，
     * 所以改在同一台裝置另開一個分頁重新登入：分頁的登入狀態各自獨立，讀到的語言紀錄和登出再登入時相同。
     */
    @那麼("這台裝置記下 {string} 的介面語言是 {string}")
    public void deviceRemembersLanguage(String staffName, String languageName) {
        CreatedStaff staff = world.staff(staffName);
        Language language = Language.of(languageName);
        Page current = world.page();
        Object stored = current.evaluate("key => localStorage.getItem(key)",
                SessionInjector.staffLanguageKey(staff.loginId()));
        if (!language.code().equals(stored)) {
            throw new AssertionError("這台裝置記的 " + staffName + " 的介面語言應該是 " + language.code() + "，實際是 " + stored);
        }

        Page again = current.context().newPage();
        try {
            world.switchTo(again);
            world.open(WebApp.LOGIN);
            login.waitUntilShown();
            login.submit(staff.loginId(), staff.password());
            assertShellLanguage(language);
        } finally {
            world.switchTo(current);
            again.close();
        }
    }

    /** 帳號面板那一列（列名在任何一種語言都認得）右側的文字相符。 */
    @那麼("帳號面板 {string} 那一列的右邊寫著 {string}")
    public void accountRowValue(String rowName, String value) {
        assertThat(account.panel()).isVisible();
        Locator row = account.row(rowNamePattern(rowName));
        assertThat(row).hasCount(1);
        waitUntil("帳號面板「" + rowName + "」那一列的右邊應該寫著「" + value + "」",
                () -> valueOfRow(row.innerText(), rowName).equals(value));
    }

    /** 段落標題相符；選項剛好四列，兩行文字與順序都與表格相同；只有那一個選項打勾。 */
    @那麼("語言頁的段落標題是 {string}，四個選項依序如下，打勾的是 {string}：")
    public void languagePageLists(String title, String selected, DataTable table) {
        assertThat(account.panel()).isVisible();
        Locator group = account.languageGroup(title);
        assertThat(group).isVisible();
        assertThat(account.panel().getByText(title, new Locator.GetByTextOptions().setExact(true)).first()).isVisible();
        List<Map<String, String>> rows = table.asMaps();
        Locator options = group.getByRole(AriaRole.RADIO);
        assertThat(options).hasCount(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            String first = rows.get(i).get("第一行").strip();
            String second = rows.get(i).get("第二行").strip();
            String text = options.nth(i).innerText().strip();
            if (!text.startsWith(first) || !text.substring(first.length()).strip().equals(second)) {
                throw new AssertionError("語言頁第 " + (i + 1) + " 個選項應該是「" + first + "／" + second
                        + "」，實際是「" + text + "」");
            }
            boolean checked = options.nth(i).isChecked();
            if (checked != first.equals(selected)) {
                throw new AssertionError("語言頁只有「" + selected + "」應該打勾，「" + first + "」是 "
                        + (checked ? "打勾" : "沒打勾"));
            }
        }
    }

    @那麼("語言頁打勾的是 {string}")
    public void languagePageChecked(String selected) {
        Locator options = account.languageOptions();
        waitUntil("語言頁應該只有「" + selected + "」打勾", () -> {
            List<String> checked = new ArrayList<>();
            for (Locator option : options.all()) {
                if (option.isChecked()) {
                    checked.add(option.innerText().strip());
                }
            }
            return checked.size() == 1 && checked.get(0).startsWith(selected);
        });
    }

    /**
     * 詳情面板的固定字句（「內容」的欄名、區塊標題、狀態標籤、「關閉」）不是繁體中文。
     * 規格沒有給英文、日文、越南文的譯文，所以這裡確認的是：文件語言是該語言、繁體中文的固定字句都不在面板上，
     * 而且面板上仍有按鈕可以關閉、只是換了名稱。
     */
    @那麼("詳情面板的欄名與按鈕是 {string}")
    public void detailPanelLanguage(String languageName) {
        Language language = Language.of(languageName);
        Page page = world.page();
        Locator panel = detail.panel().filter(new Locator.FilterOptions().setVisible(true));
        assertThat(panel).hasCount(1);
        assertDocumentLanguage(page, language);
        if (language == Language.TRADITIONAL_CHINESE) {
            for (String label : DETAIL_PANEL_CHINESE_LABELS) {
                assertThat(panel.getByText(label, new Locator.GetByTextOptions().setExact(true)).first()).isVisible();
            }
            return;
        }
        List<String> chinese = new ArrayList<>(DETAIL_PANEL_CHINESE_LABELS);
        for (Entry entry : world.tasks().all()) {
            chinese.add(entry.situation().replaceAll("（.*）$", ""));
        }
        for (String label : chinese) {
            assertThat(panel.getByText(label, new Locator.GetByTextOptions().setExact(true))).hasCount(0);
        }
        assertThat(panel.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("關閉").setExact(true)))
                .hasCount(0);
        if (panel.getByRole(AriaRole.BUTTON).count() == 0) {
            throw new AssertionError("詳情面板上應該還有關閉鈕");
        }
    }

    // ---------------------------------------------------------------- 共用的讀取與比對

    private void assertLoginFieldsEmpty() {
        login.waitUntilShown();
        assertThat(login.field("員工編號")).hasValue("");
        assertThat(login.field("密碼")).hasValue("");
        // 瀏覽器的自動填入會在畫完之後才補上，隔一下再看一次。
        world.page().waitForTimeout(300);
        String loginId = login.field("員工編號").inputValue();
        String password = login.field("密碼").inputValue();
        if (!loginId.isEmpty() || !password.isEmpty()) {
            throw new AssertionError("登入頁的兩欄應該是空的，員工編號欄「" + loginId + "」、密碼欄長度 " + password.length());
        }
    }

    private void assertToggleNamed(String toggleName) {
        Page page = world.page();
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(toggleName).setExact(true)))
                .isVisible();
        String opposite = TOGGLE_OPPOSITE.get(toggleName.strip());
        if (opposite != null) {
            assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(opposite).setExact(true)))
                    .hasCount(0);
        }
    }

    /** 提示位置只有一個提示，而且整個就是這一句：一行紅字，不是有標題與內文的提示卡。 */
    private void assertSingleNoticeLine(String text) {
        Locator notices = login.visibleNotices();
        assertThat(notices).hasCount(1);
        assertThat(notices.first()).hasText(Pattern.compile("^\\s*" + BrowserRegex.literal(text) + "\\s*$"));
    }

    /** 以網址與大標題判斷畫面是哪一頁：登入頁、設定新密碼畫面、總覽看板、待派單頁。 */
    private void assertOnPage(String pageName) {
        Page page = world.page();
        switch (pageName.strip()) {
            case "登入頁" -> {
                waitUntil("畫面應該是登入頁，網址是 " + page.url(), login::onLoginPath);
                login.waitUntilShown();
                assertThat(login.pageTitle()).hasText(LOGIN_PAGE_TITLE);
            }
            case "設定新密碼畫面" -> {
                waitUntil("畫面應該是設定新密碼畫面，網址是 " + page.url(), changePassword::onChangePasswordPath);
                assertThat(changePassword.pageTitle()).hasText(CHANGE_PASSWORD_TITLE);
            }
            case "總覽看板", "待派單頁" -> {
                waitUntil("畫面應該是" + pageName + "，網址是 " + page.url(), () -> shell.onPage(pageName));
                shell.waitForPage(pageName);
            }
            default -> throw new IllegalArgumentException(
                    "頁只接受登入頁、設定新密碼畫面、總覽看板、待派單頁，收到「" + pageName + "」");
        }
    }

    /**
     * 帳號卡是這位員工。側欄收起時卡片若只是藏起來，直接看藏起來的那張；
     * 收起時整個不在畫面裡的話，打開側欄看完再收回去。
     */
    private void assertAccountCardIs(CreatedStaff staff) {
        Locator card = account.accountCardOf(staff.name(), staff.loginId());
        if (card.count() > 0 || shell.sidebarOpen()) {
            assertThat(card).hasCount(1);
            return;
        }
        shell.openSidebar();
        try {
            assertThat(card).hasCount(1);
        } finally {
            shell.collapseSidebar();
        }
    }

    /** 分頁沒有任何登入憑證：沒有登入狀態那一筆，其他筆也不含通行憑證。 */
    private void assertNoCredentials(Page page) {
        Object session = page.evaluate("key => sessionStorage.getItem(key)", SessionInjector.SESSION_KEY);
        if (session != null) {
            throw new AssertionError("這個分頁還存著登入狀態：" + session);
        }
        for (String value : sessionValues(page)) {
            if (value.contains("\"token\"")) {
                throw new AssertionError("這個分頁的儲存空間還有登入憑證：" + value);
            }
        }
    }

    /**
     * 登入後畫面的固定字句是該語言：文件語言相符；繁體中文時頁標題是「總覽看板」或「待派單」、
     * 有「打開側欄」或「收合側欄」；其他語言時這幾句繁體中文都不在畫面上，頁標題也不是空的。
     * 規格沒有給這幾句的其他語言譯文，所以其他語言只能確認「換掉了」。
     */
    private void assertShellLanguage(Language language) {
        Page page = world.page();
        waitUntil("畫面應該是登入後的頁面，網址是 " + page.url(),
                () -> shell.onPage("總覽看板") || shell.onPage("待派單頁"));
        String pageName = loggedInPageName(page);
        Locator title = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1));
        assertThat(title).isVisible();
        assertDocumentLanguage(page, language);
        Locator chineseSidebarButtons = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^(打開側欄|收合側欄)$")));
        if (language == Language.TRADITIONAL_CHINESE) {
            assertThat(title).hasText(ShellFrame.titleOf(pageName));
            assertThat(chineseSidebarButtons).hasCount(1);
            return;
        }
        assertThat(title).not().hasText(Pattern.compile("^\\s*(總覽看板|待派單)?\\s*$"));
        assertThat(chineseSidebarButtons).hasCount(0);
    }

    /** {@code <html lang>} 的主語言相符，例如 {@code zh-TW} 只看 {@code zh}。 */
    private void assertDocumentLanguage(Page page, Language language) {
        String primary = language.code().split("-")[0];
        waitUntil("文件語言應該是 " + language.code(), () -> {
            Object lang = page.evaluate("() => document.documentElement.lang");
            return lang instanceof String text && text.toLowerCase().startsWith(primary);
        });
    }

    /** 規則清單上指定的那一條沒有打勾、其餘打勾；{@code unmet} 是 null 時四條都要打勾。 */
    private void assertRules(String unmet) {
        List<String> rules = List.of("至少 8 碼", "包含英文字母", "包含數字", "兩次輸入相同");
        String target = unmet == null ? null : unmet.strip();
        if (target != null && !rules.contains(target)) {
            throw new IllegalArgumentException("規則只有 " + rules + "，收到「" + unmet + "」");
        }
        for (String rule : rules) {
            Locator item = changePassword.rule(rule);
            if (rule.equals(target)) {
                assertThat(item).not().isChecked();
            } else {
                assertThat(item).isChecked();
            }
        }
    }

    /** 帳號面板標題列上的按鈕（「完成」、語言頁的返回）不算在面板內容裡。 */
    private static boolean isPanelHeaderButton(String name) {
        return UiWords.anyLanguage("帳號").matcher(name).matches()
                || Pattern.compile("^(完成|Done|完了|Hoàn thành)$").matcher(name).matches();
    }

    /** 面板列名在四種語言的寫法，比對時只要列的名稱以其中一種開頭；譯文表沒有這個列名時只認繁體中文。 */
    private static Pattern rowNamePattern(String rowName) {
        List<String> names = new ArrayList<>();
        for (Language language : Language.values()) {
            try {
                names.add(UiWords.in(language, rowName.strip()));
            } catch (IllegalArgumentException notInTable) {
                names.add(rowName.strip());
            }
        }
        return Pattern.compile("^(?:" + names.stream().distinct().map(BrowserRegex::literal)
                .collect(Collectors.joining("|")) + ")");
    }

    /** 帳號面板一列的文字去掉開頭的列名，剩下的就是右邊寫的字。 */
    private static String valueOfRow(String rowText, String rowName) {
        String text = rowText.strip();
        Matcher label = rowNamePattern(rowName).matcher(text);
        return label.find() ? text.substring(label.end()).strip() : text;
    }

    /** 目前分頁停在哪一頁（總覽看板或待派單頁）；兩頁都不是時直接失敗。 */
    private String loggedInPageName(Page page) {
        if (shell.onPage("總覽看板")) {
            return "總覽看板";
        }
        if (shell.onPage("待派單頁")) {
            return "待派單頁";
        }
        throw new AssertionError("畫面應該是登入後的頁面，實際網址是 " + page.url());
    }

    /** 這個分頁存的登入回應；沒有登入時是 null。 */
    private static JsonNode storedLoginResponse(Page page) {
        Object raw = page.evaluate("key => sessionStorage.getItem(key)", SessionInjector.SESSION_KEY);
        if (!(raw instanceof String text)) {
            return null;
        }
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new AssertionError("這個分頁存的登入狀態不是 JSON：" + text, e);
        }
    }

    private static String loggedInName(Page page) {
        JsonNode stored = storedLoginResponse(page);
        if (stored == null || !stored.hasNonNull("name")) {
            throw new AssertionError("這個分頁沒有登入狀態，不知道身分卡上該是誰");
        }
        return stored.get("name").asText();
    }

    @SuppressWarnings("unchecked")
    private static List<String> sessionValues(Page page) {
        Object values = page.evaluate("() => Object.keys(sessionStorage).map(k => sessionStorage.getItem(k))");
        return values instanceof List<?> list ? (List<String>) list : List.of();
    }

    /**
     * 最近一次送出的員工編號：優先用 When 句記下的送出紀錄。
     * 前置句自己走登入頁登入時不會記進那份紀錄，這時改看這個分頁的請求紀錄。
     */
    private Optional<String> lastSentLoginId(Page page) {
        Optional<String> recorded = attempts.last().map(Submission::loginId);
        if (recorded.isPresent()) {
            return recorded;
        }
        List<SeenLogin> seen = loginRequestsOn(page);
        return seen.isEmpty() ? Optional.empty() : Optional.ofNullable(seen.get(seen.size() - 1).loginId());
    }

    private int lastLoginStatus(Page page) {
        List<SeenLogin> seen = loginRequestsOn(page);
        return seen.isEmpty() ? 0 : seen.get(seen.size() - 1).status();
    }

    /** 分頁送出過的一次登入請求。 */
    private record SeenLogin(String loginId, int status) {
    }

    /** 這個分頁送出過的登入請求，依送出的先後。 */
    private static List<SeenLogin> loginRequestsOn(Page page) {
        List<SeenLogin> seen = new ArrayList<>();
        for (Request request : page.requests()) {
            if (!"POST".equals(request.method())
                    || !request.url().split("[?#]")[0].endsWith(LoginPage.LOGIN_API_PATH)) {
                continue;
            }
            JsonNode body;
            try {
                body = JSON.readTree(request.postData() == null ? "{}" : request.postData());
            } catch (Exception notJson) {
                continue;
            }
            Response response = request.existingResponse();
            seen.add(new SeenLogin(body.path("loginId").asText(null), response == null ? 0 : response.status()));
        }
        return seen;
    }

    private static CreatedStaff withPassword(CreatedStaff staff, String password) {
        return new CreatedStaff(staff.id(), staff.name(), staff.loginId(), password, staff.role(), staff.dept());
    }

    /** 讓檢查只影響指定的分頁：檢查完把步驟操作的對象換回原來的分頁，後面的步驟不受影響。 */
    private void onTab(Page tab, Runnable check) {
        Page before = world.page();
        world.switchTo(tab);
        try {
            check.run();
        } finally {
            world.switchTo(before);
        }
    }

    /** 輪詢到條件成立，最多 5 秒；逾時以斷言失敗回報。 */
    private void waitUntil(String what, BooleanSupplier condition) {
        try {
            world.page().waitForCondition(condition,
                    new Page.WaitForConditionOptions().setTimeout(SCREEN_TIMEOUT_MS));
        } catch (TimeoutError timeout) {
            throw new AssertionError(what);
        }
    }

    /** 錯誤回應拿掉每次都不同的欄位，剩下的拿來比內容；不是 JSON 時整段當文字比。 */
    private static JsonNode comparableError(String body) {
        try {
            JsonNode node = JSON.readTree(body == null || body.isBlank() ? "null" : body);
            if (node instanceof ObjectNode object) {
                VOLATILE_ERROR_FIELDS.forEach(object::remove);
            }
            return node;
        } catch (Exception notJson) {
            return JSON.getNodeFactory().textNode(body);
        }
    }

    private record HttpResult(int status, String body) {
    }

    /** 拿畫面存的登入憑證直接讀後端；{@code BackendChannel} 只能用測試自己登入拿到的憑證。 */
    private static HttpResult backendGet(String path, String token) {
        return send(HttpRequest.newBuilder(URI.create(BACKEND_API + path)).GET()
                .header("Authorization", "Bearer " + token));
    }

    /** 照畫面送出的內容（含 client）重送登入；{@code BackendChannel} 的登入不帶 client，也不回錯誤內容。 */
    private static HttpResult backendPost(String path, String json) {
        return send(HttpRequest.newBuilder(URI.create(BACKEND_API + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)));
    }

    private static HttpResult send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()
                    .send(builder.timeout(Duration.ofSeconds(10)).build(),
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpResult(response.statusCode(), response.body());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("連不上後端（" + BACKEND_API + "）", e);
        }
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("轉不成 JSON：" + value, e);
        }
    }
}
