package com.athena.web.bdd.steps.guest.realtime;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.RequestPage;
import com.athena.web.bdd.pages.guest.RequestPage.Step;
import com.athena.web.bdd.steps.common.CommonThenSteps;
import com.athena.web.bdd.steps.guest.common.GuestCommonThenSteps;
import com.athena.web.bdd.steps.guest.home.HomeThenSteps;
import com.athena.web.bdd.support.GuestTerms;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.athena.web.bdd.support.WhenStart;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.cucumber.java.zh_tw.那麼;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 「即時更新」模組的 Then 句型。
 *
 * <p>「{秒數} 秒內 …」的句子和沒有秒數的同一句看的是同一件事，所以直接用介面根那一句的檢查，
 * 另外要求它成立的那一刻離 When 送出不超過該秒數：介面根的檢查從呼叫時起等，不知道 When 是什麼時候送的。
 */
public class RealtimeThenSteps {

    private static final long POLL_INTERVAL_MS = 100;

    /** 「沒有任何提示列或錯誤訊息」在 When 之後至少等這麼久才看，提示若會出現，這時已經出現了。 */
    private static final Duration QUIET_AFTER_WHEN = Duration.ofSeconds(2);

    /** 「沒有每 4 秒定時重讀」把瀏覽器時鐘往後推這麼久，定期重讀若還開著會觸發三次。 */
    private static final Duration NO_POLL_WINDOW = Duration.ofSeconds(12);

    private static final Duration POLL_PERIOD = Duration.ofSeconds(4);

    /** 推完時鐘之後等畫面把觸發的請求送出去最多這麼久。 */
    private static final long REQUESTS_SETTLE_MS = 1_000;

    private static final List<String> TROUBLE_WORDS = List.of("連線", "異常", "失敗", "錯誤", "自動更新");

    private final WebWorld world;

    private final GuestLive live;

    private final WhenStart whenStart;

    private final GuestCommonThenSteps common;

    private final CommonThenSteps screenText;

    private final HomeThenSteps homeThen;

    private final GuestFrame frame;

    private final RequestPage request;

    public RealtimeThenSteps(WebWorld world, GuestLive live, WhenStart whenStart, GuestCommonThenSteps common,
            CommonThenSteps screenText, HomeThenSteps homeThen) {
        this.world = world;
        this.live = live;
        this.whenStart = whenStart;
        this.common = common;
        this.screenText = screenText;
        this.homeThen = homeThen;
        this.frame = new GuestFrame(world);
        this.request = new RequestPage(world);
    }

    @那麼("{int} 秒內 {string} 的狀態標籤變成 {string}")
    public void tagBecomes(int seconds, String title, String tag) {
        within(seconds, () -> common.tagIs(title, tag));
    }

    @那麼("{int} 秒內 {string} 那一步的副文變成 {string}")
    public void stepSubBecomes(int seconds, String step, String sub) {
        within(seconds, () -> {
            String language = frame.language();
            Optional<Locator> fourSteps = request.fourSteps(language);
            if (fourSteps.isEmpty()) {
                return "進度頁上沒有四步進度";
            }
            String name = GuestTerms.in(language, step.strip());
            Optional<Step> found = request.steps(fourSteps.get()).stream().filter(s -> s.name().equals(name)).findFirst();
            if (found.isEmpty()) {
                return "四步進度裡沒有「" + step + "」";
            }
            return found.get().sub().equals(sub.strip()) ? null
                    : "「" + step + "」那一步的副文是「" + found.get().sub() + "」，不是「" + sub + "」";
        });
    }

    @那麼("{int} 秒內進度頁底部變成 {string}")
    public void bottomBecomes(int seconds, String bottom) {
        within(seconds, () -> common.bottomIs(bottom));
    }

    @那麼("{int} 秒內狀態訊息框變成 {string}，內文 {string}")
    public void messageBoxBecomes(int seconds, String title, String body) {
        within(seconds, () -> common.messageBoxIs(title, body));
    }

    @那麼("{int} 秒內進度頁出現評分卡")
    public void ratingCardAppears(int seconds) {
        within(seconds, () -> new ShellFrame(world).showsText(RequestPage.RATING_QUESTION) ? null
                : "進度頁上沒有評分卡「" + RequestPage.RATING_QUESTION + "」");
    }

    @那麼("{int} 秒內四步進度走到 {string}")
    public void stepsReach(int seconds, String step) {
        within(seconds, () -> common.stepsReach(step));
    }

    @那麼("{int} 秒內清單 {string} 由上到下是 {string}")
    public void sectionBecomes(int seconds, String section, String titleList) {
        within(seconds, () -> common.sectionIs(section, titleList));
    }

    @那麼("{int} 秒內首頁的進度卡是 {string}")
    public void homeProgressCardBecomes(int seconds, String title) {
        within(seconds, () -> common.homeProgressCardIs(title));
    }

    @那麼("{int} 秒內首頁的進度卡的進度條亮到 {string}，說明是 {string}")
    public void homeProgressBarBecomes(int seconds, String step, String description) {
        within(seconds, () -> homeThen.progressCardLitUpTo(step, description));
    }

    @那麼("{int} 秒內畫面上看得到 {string}")
    public void textAppears(int seconds, String text) {
        within(seconds, () -> screenText.screenShowsText(text));
    }

    @那麼("{int} 秒內畫面重新讀取了首頁資料與需求清單")
    public void refetchedHomeAndRequests(int seconds) {
        within(seconds, () -> {
            long homes = live.requestsSinceWhen(Network.GUEST_HOME);
            long requests = live.requestsSinceWhen(Network.GUEST_REQUESTS);
            return homes > 0 && requests > 0 ? null
                    : "When 之後這個分頁讀了 " + homes + " 次首頁資料、" + requests + " 次需求清單，兩者都至少要一次";
        });
    }

    @那麼("這段期間住客沒有按任何東西，也沒有重新整理網頁")
    public void guestDidNothing() {
        int presses = whenStart.pressesSince();
        assertThat(presses == 0, "前置走完之後畫面被按了 " + presses + " 下");
        assertThat(!whenStart.pageReloadedSince(), "前置走完之後這個分頁整頁重新載入過");
    }

    /**
     * 連線正常時畫面只在收到即時訊號時重讀。後端真的心跳照常送進來，測試不再送任何訊號，
     * 把瀏覽器時鐘每次推 4 秒、推三次：定期重讀若還開著，每一次都會到期送出需求清單的請求。
     */
    @那麼("畫面沒有每 4 秒定時重新讀取需求清單")
    public void noPeriodicReread() {
        Page page = world.page();
        Network network = world.network();
        int before = network.sentRequestCount();
        live.fastForwardBrowser(NO_POLL_WINDOW, POLL_PERIOD);
        page.waitForTimeout(REQUESTS_SETTLE_MS);
        long rereads = network.sentRequestsSince(before).stream()
                .filter(sent -> sent.page() == page && sent.is(Network.GUEST_REQUESTS))
                .count();
        assertThat(rereads == 0, "瀏覽器時鐘往後推 " + NO_POLL_WINDOW.toSeconds() + " 秒、期間沒有即時訊號，畫面卻讀了 "
                + rereads + " 次需求清單");
    }

    /** 畫面上的標題要和 When 那一刻一樣都還在：畫面沒有被換成錯誤頁或重試狀態。 */
    @那麼("畫面上沒有任何提示列或錯誤訊息")
    public void noNoticeOrError() {
        Page page = world.page();
        long waited = Duration.between(live.whenAt(), Instant.now()).toMillis();
        if (waited < QUIET_AFTER_WHEN.toMillis()) {
            page.waitForTimeout(QUIET_AFTER_WHEN.toMillis() - waited);
        }
        int layers = GuestLive.visibleNoticeLayers(page, new GuestFrame(world));
        assertThat(layers == 0, "畫面上有 " + layers + " 個提醒列、提示浮層、提示框或對話框");
        List<String> lines = ShellFrame.linesOf(page.locator("body").innerText());
        String text = String.join(" ", lines);
        List<String> found = TROUBLE_WORDS.stream().filter(text::contains).toList();
        assertThat(found.isEmpty(), "畫面上有 " + found + "；畫面上的文字是 " + lines);
        List<String> missing = live.headingsAtWhen().stream()
                .filter(heading -> !GuestLive.headings(page).contains(heading))
                .toList();
        assertThat(missing.isEmpty(), "When 之前畫面上的標題 " + missing + " 不見了；畫面上的文字是 " + lines);
    }

    /**
     * 從 When 送出起算該秒數內檢查成立；檢查自己會等，不成立時丟 {@link AssertionError}。
     * 成立得太晚（超過 When 之後該秒數）也算失敗。
     */
    private void within(int seconds, Runnable check) {
        Instant deadline = live.whenAt().plusSeconds(seconds);
        while (true) {
            try {
                check.run();
            } catch (AssertionError notYet) {
                if (Instant.now().isAfter(deadline)) {
                    throw new AssertionError("When 之後 " + seconds + " 秒內沒有成立：" + notYet.getMessage(), notYet);
                }
                world.page().waitForTimeout(POLL_INTERVAL_MS);
                continue;
            }
            long late = Duration.between(deadline, Instant.now()).toMillis();
            assertThat(late <= 0, "When 之後超過 " + seconds + " 秒才成立（晚了 " + late + " 毫秒）");
            return;
        }
    }

    private void within(int seconds, Supplier<String> problem) {
        within(seconds, () -> {
            String found = problem.get();
            if (found != null) {
                throw new AssertionError(found);
            }
        });
    }

    private static void assertThat(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
