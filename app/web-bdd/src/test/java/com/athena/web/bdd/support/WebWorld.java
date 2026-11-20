package com.athena.web.bdd.support;

import com.athena.bddsupport.BackendChannel;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.bddsupport.BackendChannel.FrontdeskDefaults;
import com.athena.web.bdd.WebProfile;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 一個場景的執行環境：這個場景開的瀏覽器分頁、後端資料準備的入口、時鐘、網路控制與登入狀態。
 * 步驟類別與 page object 都用建構子拿同一個 {@code WebWorld}，場景結束就丟掉，下一個場景是全新的一個。
 *
 * <h2>每個場景保證什麼</h2>
 * <ul>
 *   <li><b>從同一份資料開始。</b>場景開始前清掉後端所有業務資料，再建櫃檯 Web 的預設飯店、帳號、客房與
 *       住客資訊頁，並把後端時鐘固定在預設時刻。測試連的是本機的開發資料庫，裡面可能有別人留下的單，
 *       不清的話「待派單只有這三張」這類檢查會看運氣。</li>
 *   <li><b>瀏覽器裡什麼都不留。</b>分頁第一次用到才開，開在這個場景自己的 context 裡；場景結束時不管成功
 *       失敗，所有 context 一律關掉，登入狀態與語言設定不會帶到下一個場景。</li>
 *   <li><b>失敗看得到畫面。</b>場景失敗時，關掉分頁之前先替每個還開著的分頁截圖，存在
 *       {@code target/failure-screenshots/}，同時附進 Cucumber 報表。</li>
 * </ul>
 *
 * <h2>分頁怎麼分</h2>
 * 「目前的分頁」是步驟與 page object 操作的對象，用 {@link #page()} 取。
 * 同一個人開第二個分頁用 {@link #openTab()}：兩個分頁在同一個 context，{@code localStorage} 共用，
 * {@code sessionStorage} 各自一份。另一台裝置用 {@link #openContext()}：儲存空間完全分開。
 * 這兩個方法一呼叫，目前的分頁就是它開出來的那一個；要操作別的分頁時用 {@link #switchTo(Page)} 指定。
 */
public class WebWorld {

    private static final Path FAILURE_SCREENSHOT_DIR = Path.of("target", "failure-screenshots");

    /**
     * 整次測試共用一個後端入口。它自己握著一條資料庫連線而且沒有關閉的方法，
     * 每個場景各建一個的話，跑完四百多個場景會把資料庫的連線數用完。
     */
    private static final BackendChannel BACKEND = new BackendChannel();

    private final Clocks clocks = new Clocks(BACKEND);

    private final Network network = new Network(this::page);

    private final SessionInjector session = new SessionInjector(BACKEND, this::page, () -> defaults().tenantCode());

    private final ScenarioTasks tasks = new ScenarioTasks();

    private final List<BrowserContext> contexts = new ArrayList<>();

    private final List<java.util.function.Consumer<BrowserContext>> contextSetups = new ArrayList<>();

    private final List<Runnable> waitingChores = new ArrayList<>();

    private WebProfile profile = BrowserProfiles.defaultProfile();

    private FrontdeskDefaults defaults;

    private Page page;

    /** 瀏覽器時鐘被步驟停住、等著引發的那一步放開的分頁；沒有時是 {@code null}。 */
    private Page pausedClockPage;

    @Before(order = 0)
    public void prepareScenario() {
        BACKEND.resetBusinessData();
        defaults = BACKEND.ensureFrontdeskDefaults();
        clocks.startAtDefault();
    }

    @After(order = 0)
    public void finishScenario(Scenario scenario) {
        try {
            if (scenario.isFailed()) {
                captureOpenPages(scenario);
            }
        } finally {
            try {
                resumePausedClock();
            } catch (RuntimeException alreadyGone) {
                // 分頁已經壞掉放不開也沒關係，下一行就把它整個關掉。
            }
            try {
                closeContexts();
            } finally {
                clocks.reset();
            }
        }
    }

    /**
     * 記下這個分頁的瀏覽器時鐘剛被停住，要由之後引發畫面動作的那一步（按「重試」、等時間過去）放開。
     *
     * <p>時鐘停著時，畫面框架排在 {@code setTimeout(0)} 的更新也不會跑：資料讀回來了，畫面卻不換。
     * 所以停住只能用來擋住「兩步之間自己先發生」的計時，動作觸發之後一定要放開。
     */
    public void clockPausedUntilTriggered(Page paused) {
        pausedClockPage = paused;
    }

    /** 目前有沒有被停住、還沒放開的瀏覽器時鐘。 */
    public boolean clockPaused() {
        return pausedClockPage != null && !pausedClockPage.isClosed();
    }

    /** 放開被停住的瀏覽器時鐘，讓它從停住的時刻照真實速度往前走；沒有停住時不做事。 */
    public void resumePausedClock() {
        Page paused = pausedClockPage;
        pausedClockPage = null;
        if (paused != null && !paused.isClosed()) {
            paused.clock().resume();
        }
    }

    /** 後端資料準備與讀取權威狀態的入口，資料準備都對預設飯店做。 */
    public BackendChannel backend() {
        return BACKEND;
    }

    /** 預設飯店與 12 個預設帳號。 */
    public FrontdeskDefaults defaults() {
        if (defaults == null) {
            throw new IllegalStateException("預設資料還沒建好：這個方法只能在場景開始之後呼叫");
        }
        return defaults;
    }

    /** 以姓名取預設帳號，例如「林佩君」。 */
    public CreatedStaff staff(String name) {
        return defaults().staff(name);
    }

    public Clocks clocks() {
        return clocks;
    }

    public Network network() {
        return network;
    }

    public SessionInjector session() {
        return session;
    }

    /** 這個場景準備了哪些單：房號（同房兩張時加標題）對到哪一個 task id。 */
    public ScenarioTasks tasks() {
        return tasks;
    }

    /** 這個場景現在用的瀏覽器設定。 */
    public WebProfile profile() {
        return profile;
    }

    /** 目前的分頁；這個場景還沒開過分頁時，以現在的瀏覽器設定開第一個。 */
    public Page page() {
        if (page == null || page.isClosed()) {
            page = openContext();
        }
        return page;
    }

    /** 這個場景是否已經開了分頁。用在不想為了檢查而多開一個分頁的地方。 */
    public boolean hasPage() {
        return page != null && !page.isClosed();
    }

    /** 在目前的分頁開預設飯店底下的某一頁，例如 {@link WebApp#BOARD} 開的是 {@code /athena/board}。 */
    public void open(String path) {
        openPath(WebApp.underHotel(defaults().tenantCode(), path));
    }

    /** 在目前的分頁開這條完整的路徑，不再加上飯店識別代碼；給要指定別家飯店代碼的地方用。 */
    public void openPath(String fullPath) {
        page().navigate(WebApp.url(fullPath));
    }

    /** 在目前分頁所在的 context 再開一個分頁，並把它當成目前的分頁。 */
    public Page openTab() {
        page = page().context().newPage();
        return page;
    }

    /** 以現在的瀏覽器設定另開一個 context 與它的第一個分頁，並把它當成目前的分頁。 */
    public Page openContext() {
        return openContext(options -> { });
    }

    /**
     * 同上，但這個 context 另外改幾項設定，例如瀏覽器的語言（{@code setLocale}），
     * 或帶進上一個瀏覽器留下的 {@code localStorage}（{@code setStorageState}）。
     */
    public Page openContext(java.util.function.Consumer<com.microsoft.playwright.Browser.NewContextOptions> customize) {
        BrowserContext context = BrowserProfiles.newContext(profile, customize);
        contexts.add(context);
        clocks.install(context);
        network.watch(context);
        contextSetups.forEach(setup -> setup.accept(context));
        page = context.newPage();
        return page;
    }

    /**
     * 這個場景之後開的每一個 context 在開出第一個分頁之前都先做這件事，例如裝上整個場景都要的請求攔截。
     * 要在場景還沒開任何分頁時登記，已經開著的 context 不會補做。
     */
    public void onEveryNewContext(java.util.function.Consumer<BrowserContext> setup) {
        contextSetups.add(setup);
    }

    /**
     * 步驟輪詢等畫面或後端跟上時，每一輪都先做這件事，例如放行扣滿時間的回應。
     * 攔截的處理程式跑在測試這條執行緒上、不能自己睡著等，到時間的事只能在輪詢時做。
     */
    public void whileWaiting(Runnable chore) {
        waitingChores.add(chore);
    }

    /** 做一輪 {@link #whileWaiting(Runnable)} 登記的事。 */
    public void doWaitingChores() {
        waitingChores.forEach(Runnable::run);
    }

    /** 把步驟與 page object 操作的對象換成這個場景開過的另一個分頁。 */
    public void switchTo(Page target) {
        if (target.isClosed()) {
            throw new IllegalArgumentException("這個分頁已經關掉了");
        }
        page = target;
    }

    /**
     * 換一組瀏覽器設定。已經開著的分頁全部關掉，之後第一次用到分頁時以換過的設定開。
     *
     * <p>關掉分頁會連儲存空間一起丟掉，換組之前已經登入的話，呼叫端要自己在之後的分頁再放一次登入狀態。
     */
    public void useProfile(WebProfile next) {
        if (next == profile) {
            return;
        }
        closeContexts();
        profile = next;
    }

    /**
     * 給「關掉瀏覽器再打開」這類案例用：關掉這個場景開過的所有分頁，儲存空間跟著不見。
     * 之後第一次用到分頁時以現在的瀏覽器設定重開。
     */
    public void closeBrowser() {
        closeContexts();
    }

    private void closeContexts() {
        RuntimeException firstFailure = null;
        for (BrowserContext context : List.copyOf(contexts)) {
            clocks.forget(context);
            try {
                context.close();
            } catch (RuntimeException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        contexts.clear();
        page = null;
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private void captureOpenPages(Scenario scenario) {
        int index = 0;
        for (BrowserContext context : contexts) {
            for (Page open : context.pages()) {
                if (open.isClosed()) {
                    continue;
                }
                index++;
                String name = screenshotName(scenario, index);
                try {
                    byte[] png = open.screenshot(new Page.ScreenshotOptions().setFullPage(true));
                    Files.createDirectories(FAILURE_SCREENSHOT_DIR);
                    Files.write(FAILURE_SCREENSHOT_DIR.resolve(name), png);
                    scenario.attach(png, "image/png", name);
                } catch (RuntimeException | IOException e) {
                    // 截不到圖時只在報表記一筆：讓例外往外丟的話，報表上看到的會是截圖的錯，不是場景真正失敗的原因。
                    scenario.log("截圖失敗（" + name + "）：" + e);
                }
            }
        }
    }

    /** 檔名帶場景名稱、所在行號與瀏覽器設定：同名的 Scenario Outline 各列、兩組設定各跑一次都不會互相蓋掉。 */
    private String screenshotName(Scenario scenario, int index) {
        String title = scenario.getName().replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (title.length() > 80) {
            title = title.substring(0, 80);
        }
        return title + "-L" + scenario.getLine() + "-" + profile.id() + "-" + index + ".png";
    }
}
