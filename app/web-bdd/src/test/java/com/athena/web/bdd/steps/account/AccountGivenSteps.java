package com.athena.web.bdd.steps.account;

import com.athena.bddsupport.BackendChannel;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.bddsupport.TaskSituation;
import com.athena.bddsupport.TaskSituationSpec;
import com.athena.web.bdd.pages.AccountPanel;
import com.athena.web.bdd.pages.AnswerPanel;
import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.ChangePasswordPage;
import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.LoginPage;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.UiWords.Language;
import com.athena.web.bdd.support.AccountScenario;
import com.athena.web.bdd.support.Clocks;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.ScenarioTasks;
import com.athena.web.bdd.support.SessionInjector;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** 「帳號與登入」模組的 Given 句型。 */
public class AccountGivenSteps {

    /** 「"{面板}" 開著」那一句替面板備的待派單：一位住客打字問的事，當場回答與派單都能用。 */
    private static final String PANEL_ROOM = "301";
    private static final String PANEL_GUEST_TEXT = "請問早餐幾點開始？";
    private static final String PANEL_GUEST = "黃志明";

    /** 鎖定時間的檢查容許這麼多誤差：後端算鎖定到期的時刻與測試讀時間之間隔了幾次請求。 */
    private static final Duration LOCK_TOLERANCE = Duration.ofMinutes(1);

    /** 等看板資料最久這麼久；和外框載入看板的上限相同。 */
    private static final double BOARD_LOAD_TIMEOUT_MS = 10_000;

    private final WebWorld world;

    private final AccountScenario account;

    private final LoginPage login;

    private final ChangePasswordPage changePassword;

    private final ShellFrame shell;

    private final AccountPanel accountPanel;

    private final BoardPage board;

    private final DetailPanel detail;

    private final AnswerPanel answer;

    public AccountGivenSteps(WebWorld world, AccountScenario account) {
        this.world = world;
        this.account = account;
        this.login = new LoginPage(world);
        this.changePassword = new ChangePasswordPage(world);
        this.shell = new ShellFrame(world);
        this.accountPanel = new AccountPanel(world);
        this.board = new BoardPage(world);
        this.detail = new DetailPanel(world);
        this.answer = new AnswerPanel(world);
    }

    /**
     * 句子要求這個分頁沒有登入。分頁若還留著登入狀態，櫃檯 Web 會直接把人送去總覽看板，
     * 所以開登入頁之前要先拿掉。
     */
    @假設("登入頁已經打開")
    public void loginPageOpen() {
        if (shell.loggedIn()) {
            world.session().clear(world.page());
        }
        login.openFor(world.defaults().tenantCode());
    }

    /** 登入頁的語言是在畫面啟動時讀的，所以要先寫進這台裝置、再開登入頁。 */
    @假設("登入頁已經打開，介面語言是 {string}")
    public void loginPageOpenIn(String language) {
        world.session().putLocalItem(world.page(), SessionInjector.LOGIN_PAGE_LANGUAGE_KEY,
                Language.of(language).code());
        loginPageOpen();
    }

    @假設("登入頁已經打開，員工編號欄已輸入 {string}")
    public void loginPageWithLoginId(String loginId) {
        loginPageOpen();
        login.field("員工編號").fill(loginId);
    }

    @假設("登入頁已經打開，密碼欄已輸入 {string}")
    public void loginPageWithPassword(String password) {
        loginPageOpen();
        login.field("密碼").fill(password);
    }

    @假設("登入頁已經打開，密碼欄已輸入 {string} 並切成明文顯示")
    public void loginPageWithPasswordShown(String password) {
        loginPageWithPassword(password);
        shell.press(button("顯示密碼"));
        button("隱藏密碼").waitFor();
    }

    @假設("登入頁已經打開，忘記密碼的提示開著")
    public void loginPageWithForgotPasswordHint() {
        loginPageOpen();
        shell.press(button("忘記密碼？"));
        anyDialog().waitFor();
    }

    /**
     * 用錯誤的密碼真的向後端登入這麼多次，連續錯誤次數與鎖定到期都由後端自己算。
     * 送完再讀帳號確認真的鎖住、鎖的分鐘數與句子相同，前提不成立時在這一步就失敗，不會拖到 Then 才看出來。
     */
    @假設("{string} 的帳號已連續輸錯密碼 {int} 次，被鎖定 {int} 分鐘")
    public void lockedAfterWrongPasswords(String staffName, int attempts, int minutes) {
        CreatedStaff staff = world.staff(staffName);
        BackendChannel backend = world.backend();
        String wrongPassword = staff.password() + "-wrong";
        Instant wallBefore = Instant.now();
        Instant backendBefore = backend.backendNow().toInstant();
        for (int i = 0; i < attempts; i++) {
            int status = backend.attemptLogin(staff.loginId(), wrongPassword);
            if (status == 200) {
                throw new IllegalStateException("用錯誤密碼登入「" + staffName + "」竟然成功了");
            }
        }
        Map<String, Object> row = backend.staffAccountRow(staff.id());
        int failed = ((Number) row.get("failed_login_count")).intValue();
        if (failed != attempts) {
            throw new IllegalStateException("「" + staffName + "」連續錯誤次數應為 " + attempts + "，後端記的是 " + failed);
        }
        Instant lockedUntil = toInstant(row.get("locked_until"));
        if (lockedUntil == null) {
            throw new IllegalStateException("「" + staffName + "」輸錯 " + attempts + " 次後沒有被鎖定");
        }
        // 後端以哪一個時鐘算鎖定到期（真實時間或測試時鐘）不在這句的範圍，兩者任一對得上就算。
        Duration lock = Duration.ofMinutes(minutes);
        if (!near(lockedUntil, wallBefore.plus(lock)) && !near(lockedUntil, backendBefore.plus(lock))) {
            throw new IllegalStateException("「" + staffName + "」應鎖定 " + minutes + " 分鐘，後端記的鎖定到期是 "
                    + lockedUntil + "（送出前真實時間 " + wallBefore + "、後端時鐘 " + backendBefore + "）");
        }
    }

    /** 只改員工編號，密碼不變；之後的句子以新的員工編號找帳號卡與登入憑證。 */
    @假設("{string} 的員工編號是 {string}")
    public void loginIdIs(String staffName, String loginId) {
        CreatedStaff staff = world.staff(staffName);
        world.backend().setCredentials(staff.id(), loginId, staff.password());
        account.rememberLoginId(staffName, loginId);
    }

    @假設("{string} 的帳號已被停用")
    public void deactivated(String staffName) {
        world.backend().deactivateStaff(world.staff(staffName).id());
    }

    /** 記下臨時密碼，之後「用臨時密碼登入」與檢查後端密碼的句子都用這一個。 */
    @假設("{string} 的密碼是主管給的臨時密碼 {string}")
    public void temporaryPassword(String staffName, String password) {
        CreatedStaff staff = world.staff(staffName);
        world.backend().setCredentials(staff.id(), staff.loginId(), password);
        world.backend().markMustChangePassword(staff.id());
        account.rememberTemporaryPassword(staffName, password);
    }

    @假設("{string} 已用臨時密碼登入，停在設定新密碼畫面")
    public void onChangePasswordScreen(String staffName) {
        CreatedStaff staff = world.staff(staffName);
        loginPageOpen();
        login.submit(staff.loginId(), account.temporaryPassword(staffName));
        world.page().waitForCondition(changePassword::onChangePasswordPath);
        changePassword.newPasswordField().waitFor();
    }

    /** 在後端改，不經過畫面；這個分頁維持沒有登入，後面的句子從登入頁開始。 */
    @假設("{string} 已經把臨時密碼改成 {string}")
    public void changedTemporaryPassword(String staffName, String newPassword) {
        CreatedStaff staff = world.staff(staffName);
        world.backend().changeTemporaryPassword(staff.loginId(), account.temporaryPassword(staffName), newPassword);
        if (shell.loggedIn()) {
            world.session().clear(world.page());
        }
    }

    @假設("{string} 已登入櫃檯畫面，帳號面板開著")
    public void accountPanelOpen(String staffName) {
        openLoggedIn(world.staff(staffName), "總覽看板");
        accountPanel.openFromSidebar(staffName);
    }

    @假設("{string} 已登入櫃檯畫面，登出確認提示開著")
    public void logoutConfirmOpen(String staffName) {
        accountPanelOpen(staffName);
        shell.press(accountPanel.logoutButton());
        accountPanel.logoutConfirmDialog().waitFor();
    }

    /** 他的介面語言是登入時從這台裝置讀的，所以要先寫進去、再放登入狀態。 */
    @假設("{string} 已登入櫃檯畫面，介面語言是 {string}，登出確認提示開著")
    public void logoutConfirmOpenIn(String staffName, String language) {
        rememberStaffLanguage(world.staff(staffName), language);
        logoutConfirmOpen(staffName);
    }

    @假設("{string} 已登入櫃檯畫面，介面語言是 {string}，停在 {string}")
    public void loggedInWithLanguageOn(String staffName, String language, String pageName) {
        CreatedStaff staff = world.staff(staffName);
        rememberStaffLanguage(staff, language);
        openLoggedIn(staff, pageName);
    }

    @假設("{string} 已登入櫃檯畫面，語言頁開著")
    public void languagePageOpen(String staffName) {
        accountPanelOpen(staffName);
        accountPanel.openLanguagePage();
    }

    @假設("{string} 已登入櫃檯畫面，語言頁開著，已經選了 {string}")
    public void languagePageOpenAndChosen(String staffName, String option) {
        languagePageOpen(staffName);
        accountPanel.chooseLanguage(option);
    }

    /** 不管開哪一個面板都先備一張待派單，三種面板的前提才一樣；單要在開看板之前建好，看板第一次載入就有它。 */
    @假設("{string} 已登入櫃檯畫面，{string} 開著")
    public void panelOpen(String staffName, String panelName) {
        UUID taskId = createTriageTaskForPanels();
        openLoggedIn(world.staff(staffName), "總覽看板");
        switch (panelName.strip()) {
            case "詳情面板" -> openDetail(taskId);
            case "當場回答面板" -> {
                openDetail(taskId);
                shell.press(detail.button("當場回答…"));
                answer.panel().waitFor();
            }
            case "帳號面板" -> accountPanel.openFromSidebar(staffName);
            default -> throw new IllegalArgumentException(
                    "面板只接受詳情面板、當場回答面板或帳號面板，收到「" + panelName + "」");
        }
    }

    /** 新分頁要開在同一個瀏覽器裡，才驗得到「裝置上記的語言共用、登入狀態不共用」。 */
    @假設("{string} 已登入櫃檯畫面，另開的新分頁停在登入頁")
    public void newTabOnLoginPage(String staffName) {
        openLoggedIn(world.staff(staffName), "總覽看板");
        Page original = world.page();
        Page added = world.openTab();
        world.open(ShellFrame.pathOf("總覽看板"));
        login.waitUntilShown();
        account.rememberTabs(original, added);
    }

    /** 兩個分頁都真的走登入頁，因為句子說的是「各自走登入頁登入」。 */
    @假設("原本的分頁是 {string} 登入、新分頁是 {string} 登入")
    public void twoTabsLoggedIn(String staffName, String colleagueName) {
        Page original = world.page();
        loginThroughPage(world.staff(staffName));
        Page added = world.openTab();
        loginThroughPage(world.staff(colleagueName));
        account.rememberTabs(original, added);
    }

    @假設("{string} 已在總覽看板點了 {string} 數量卡、搜尋房號 {string}、把泳道切到 {string}")
    public void boardViewChosen(String staffName, String countCard, String roomNo, String lane) {
        Locator card = board.countCard(countCard);
        shell.press(card);
        world.page().waitForCondition(() -> "true".equals(card.getAttribute("aria-pressed")));
        board.roomSearch().fill(roomNo);
        shell.press(board.laneOption(lane));
        world.page().waitForCondition(() -> board.laneSelected(lane)
                && roomNo.equals(board.roomSearch().inputValue())
                && "true".equals(card.getAttribute("aria-pressed")));
    }

    /** 換一台沒有任何紀錄的裝置：關掉場景到目前開的瀏覽器，以該語系開一個儲存空間全空的新瀏覽器。 */
    @假設("這台裝置還沒有任何人選過介面語言，瀏覽器的語言是 {string}")
    public void freshDeviceWithBrowserLanguage(String browserLanguage) {
        world.closeBrowser();
        world.openContext(options -> options.setLocale(browserLanguage.strip()));
    }

    @假設("這台裝置上各人選過的介面語言如下：")
    public void staffLanguagesOnDevice(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            rememberStaffLanguage(world.staff(required(row, "姓名")), required(row, "選過的語言"));
        }
    }

    /** 只是交代背景：那台裝置不是這個場景的瀏覽器，所以不寫任何紀錄；只確認人名與選項是規格認得的。 */
    @假設("{string} 在櫃檯原本那台裝置上選過 {string}")
    public void choseOnAnotherDevice(String staffName, String option) {
        world.staff(staffName);
        Language.of(option);
    }

    /**
     * 關掉瀏覽器時分頁的登入狀態跟著不見，裝置上記的語言還在。
     * 先取出這個瀏覽器的 {@code localStorage} 再關掉、帶著它開新的瀏覽器；
     * 取出來的內容不含 {@code sessionStorage}，登入狀態自然不會留下。
     */
    @假設("上一次的瀏覽器已經關掉，登入頁重新打開")
    public void browserReopened() {
        if (world.hasPage()) {
            String storage = world.page().context().storageState();
            world.closeBrowser();
            world.openContext(options -> options.setStorageState(storage));
        } else {
            world.closeBrowser();
        }
        login.openFor(world.defaults().tenantCode());
    }

    private void rememberStaffLanguage(CreatedStaff staff, String language) {
        world.session().putLocalItem(world.page(), SessionInjector.staffLanguageKey(staff.loginId()),
                Language.of(language).code());
    }

    /**
     * 跳過登入頁放進登入狀態，開該頁並等看板資料回來、頁標題出現。
     * 不用外框的「等頁標題是某某」：介面語言不是繁體中文時頁標題也跟著換，這裡只等有一個頁標題出現。
     */
    private void openLoggedIn(CreatedStaff staff, String pageName) {
        world.session().loginAs(staff);
        world.page().waitForResponse(AccountGivenSteps::isBoardResponse,
                new Page.WaitForResponseOptions().setTimeout(BOARD_LOAD_TIMEOUT_MS),
                () -> world.open(ShellFrame.pathOf(pageName)));
        world.page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)).first().waitFor();
    }

    private void loginThroughPage(CreatedStaff staff) {
        login.openFor(world.defaults().tenantCode());
        login.submit(staff.loginId(), staff.password());
        shell.waitForPage("總覽看板");
    }

    private void openDetail(UUID taskId) {
        detail.openAndWait(taskId, PANEL_ROOM, () -> shell.press(board.card(PANEL_ROOM)));
    }

    /** 住客打字的待派單，掛在一筆今天在住的訂房上，和「全館有以下的單：」建的住客單同一個樣子。 */
    private UUID createTriageTaskForPanels() {
        LocalDate today = world.clocks().today();
        UUID reservationId = world.backend().createReservation(PANEL_ROOM, PANEL_GUEST, false,
                Clocks.at(today.minusDays(1), LocalTime.of(15, 0)),
                Clocks.at(today.plusDays(1), LocalTime.of(11, 0)));
        world.tasks().rememberReservation(PANEL_ROOM, reservationId);
        TaskSituationSpec spec = TaskSituationSpec.of(PANEL_ROOM, BackendChannel.taskType("其他"), "guest")
                .dept("frontdesk")
                .guestText(PANEL_GUEST_TEXT, "zh-TW", null)
                .reservationId(reservationId);
        UUID taskId = world.backend().createTaskInSituation(TaskSituation.ofTerm("待派單"), spec);
        world.tasks().add(new ScenarioTasks.Entry(PANEL_ROOM, PANEL_GUEST_TEXT, taskId, "待派單"));
        return taskId;
    }

    private Locator button(String name) {
        return world.page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private Locator anyDialog() {
        return world.page().getByRole(AriaRole.DIALOG)
                .or(world.page().getByRole(AriaRole.ALERTDIALOG))
                .first();
    }

    private static boolean isBoardResponse(Response response) {
        Network.Api boardApi = Network.DESK_BOARD;
        return boardApi.method().equals(response.request().method())
                && response.url().split("[?#]")[0].endsWith(boardApi.path());
    }

    private static boolean near(Instant actual, Instant expected) {
        return Duration.between(expected, actual).abs().compareTo(LOCK_TOLERANCE) <= 0;
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toInstant();
        }
        throw new IllegalStateException("讀不懂鎖定到期的值：" + value + "（" + value.getClass() + "）");
    }

    private static String required(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("「這台裝置上各人選過的介面語言如下：」每一列都要填「" + column + "」");
        }
        return value.strip();
    }
}
