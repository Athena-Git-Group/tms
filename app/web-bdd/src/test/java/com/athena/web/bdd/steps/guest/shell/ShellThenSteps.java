package com.athena.web.bdd.steps.guest.shell;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.GuestHomePage;
import com.athena.web.bdd.pages.guest.LanguageMenu;
import com.athena.web.bdd.pages.guest.PhoneLayout;
import com.athena.web.bdd.pages.guest.RequestPage;
import com.athena.web.bdd.pages.guest.RequestsPage;
import com.athena.web.bdd.support.GuestPhone;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestScreen;
import com.athena.web.bdd.support.GuestTerms;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.cucumber.java.zh_tw.那麼;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 「共用外框」模組的 Then 句型。畫面跟上要一點時間，檢查一律重讀到成立為止，最多 5 秒。
 *
 * <p>介面語言以 {@link LanguageMenu#FIXED_PHRASES} 的固定字句認：該語言的寫法看得到、其他三種語言的同一句看不到，
 * 網頁的 {@code lang} 屬性是該語言的語系代碼。
 */
public class ShellThenSteps {

    /** 規格要求可點的東西至少這麼寬、這麼高（點）。 */
    private static final double MIN_TARGET = 44;

    private static final String ROOM_LINE_PREFIX = "房號 ";

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestPhone phone;

    private final GuestFrame frame;

    private final GuestHomePage home;

    private final RequestPage request;

    private final RequestsPage requests;

    private final LanguageMenu language;

    private final PhoneLayout layout;

    public ShellThenSteps(WebWorld world, GuestScenario scenario) {
        this.world = world;
        this.scenario = scenario;
        this.phone = new GuestPhone(world, scenario);
        this.frame = new GuestFrame(world);
        this.home = new GuestHomePage(world);
        this.request = new RequestPage(world);
        this.requests = new RequestsPage(world);
        this.language = new LanguageMenu(world);
        this.layout = new PhoneLayout(world);
    }

    @那麼("介面語言是 {string}")
    public void interfaceLanguageIs(String languageName) {
        String code = GuestTerms.languageCode(languageName);
        frame.eventually(() -> languageProblem(code));
    }

    @那麼("語言膠囊顯示 {string}")
    public void capsuleShows(String shortName) {
        frame.eventually(() -> {
            String text = language.capsuleText();
            return text.equals(shortName.strip()) ? null : "語言膠囊的字是「" + text + "」，不是「" + shortName + "」";
        });
    }

    @那麼("語言選單列出 {string}，打勾的是 {string}")
    public void menuLists(String optionList, String checked) {
        List<String> expected = Arrays.stream(optionList.split("、")).map(String::strip).toList();
        List<String> expectedChecked = List.of(checked.strip());
        frame.eventually(() -> {
            if (!language.menuShown()) {
                return "畫面上沒有語言選單";
            }
            List<String> options = language.options();
            if (!options.equals(expected)) {
                return "語言選單由上到下是 " + options + "，應該是 " + expected;
            }
            List<String> ticked = language.checkedOptions();
            if (!ticked.equals(expectedChecked)) {
                return "打勾的是 " + ticked + "，應該只有 " + expectedChecked;
            }
            List<String> bold = language.boldOptions();
            return bold.equals(expectedChecked) ? null : "加粗的是 " + bold + "，應該只有 " + expectedChecked;
        });
    }

    @那麼("畫面上沒有語言選單")
    public void noLanguageMenu() {
        frame.eventually(() -> language.menuShown() ? "畫面上還有語言選單"
                : frame.scrimShown() ? "畫面上還有蓋住整個視窗的遮罩" : null);
    }

    /**
     * 「關掉再開」用同一支手機的新分頁做，不用重新整理：住客的 session 放在分頁自己的 {@code sessionStorage}，
     * 只有新分頁才會像真的關掉再掃一次那樣重新換 session，語言只能從這支手機的 {@code localStorage} 來。
     */
    @那麼("這支手機記住的介面語言是 {string}")
    public void rememberedLanguageIs(String languageName) {
        String code = GuestTerms.languageCode(languageName);
        frame.eventually(() -> {
            Object stored = world.page().evaluate("key => localStorage.getItem(key)", GuestFrame.LANGUAGE_KEY);
            return code.equals(stored) ? null : "這支手機記住的介面語言是 " + stored + "，不是 " + code;
        });
        Page old = world.page();
        world.openTab();
        old.close();
        phone.openHome(scenario.phoneRoom());
        frame.eventually(() -> {
            String problem = languageProblem(code);
            return problem == null ? null : "關掉分頁再打開首頁之後，" + problem;
        });
    }

    @那麼("另一支手機的介面語言仍是 {string}")
    public void secondPhoneLanguageIs(String languageName) {
        String code = GuestTerms.languageCode(languageName);
        Page first = world.page();
        world.switchTo(scenario.secondPhone());
        try {
            frame.eventually(() -> languageProblem(code));
            phone.reload();
            frame.eventually(() -> {
                String problem = languageProblem(code);
                return problem == null ? null : "另一支手機重新整理之後，" + problem;
            });
        } finally {
            world.switchTo(first);
        }
    }

    @那麼("畫面上沒有子頁的標題列")
    public void noSubpageTitleBar() {
        frame.eventually(() -> {
            String drawn = frame.drawnProblem();
            if (drawn != null) {
                return drawn;
            }
            Locator bar = frame.titleBar().filter(new Locator.FilterOptions().setVisible(true));
            if (bar.count() > 0 && frame.titleBarStuckToTop()) {
                return "畫面頂端有黏住的標題列：" + frame.titleBarLines();
            }
            if (frame.backControl(world.page().locator("body")).count() > 0) {
                return "畫面上有返回鈕";
            }
            Optional<String> roomLine = bodyLines().stream().filter(line -> line.startsWith(ROOM_LINE_PREFIX)).findFirst();
            return roomLine.map(line -> "畫面上有「" + line + "」").orElse(null);
        });
    }

    @那麼("頁面沒有橫向捲動，內容是單欄置中")
    public void singleCenteredColumn() {
        frame.eventually(() -> {
            Map<String, Object> widths = layout.scrollWidth();
            if (PhoneLayout.num(widths, "scroll") > PhoneLayout.num(widths, "viewport")) {
                return "頁面可以捲動的寬度是 " + widths.get("scroll") + "，比視窗寬 " + widths.get("viewport");
            }
            List<String> past = layout.elementsPastRightEdge();
            if (!past.isEmpty()) {
                return "這些元素超出視窗右緣：" + past;
            }
            Map<String, Object> columns = layout.contentColumns();
            if (!Boolean.TRUE.equals(columns.get("found"))) {
                return "畫面上沒有內容區（main）";
            }
            if (((Number) columns.get("sideBySide")).intValue() > 0) {
                return "內容區有 " + columns.get("sideBySide") + " 對區塊左右並排，不是單欄";
            }
            double left = PhoneLayout.num(columns, "left");
            double right = PhoneLayout.num(columns, "right");
            return Math.abs(left - right) <= 1 ? null : "內容區左邊距 " + left + "、右邊距 " + right + "，不相等";
        });
    }

    @那麼("每一個可以點的東西至少 44 點見方")
    public void pressablesAtLeast44() {
        frame.eventually(() -> {
            String drawn = frame.drawnProblem();
            if (drawn != null) {
                return drawn;
            }
            if (layout.pressableCount() == 0) {
                return "畫面上找不到任何可以點的東西";
            }
            List<String> small = layout.pressablesSmallerThan(MIN_TARGET);
            return small.isEmpty() ? null : "這些可以點的東西小於 44 × 44：" + small;
        });
    }

    @那麼("{string} 那一列的標題換行或以「…」截斷，沒有蓋到狀態標籤")
    public void longTitleInRow(String title) {
        frame.eventually(() -> {
            Optional<Locator> row = requests.row(title);
            if (row.isEmpty()) {
                return "清單上找不到「" + title + "」那一列";
            }
            Locator titleText = row.get().getByText(title.strip(), new Locator.GetByTextOptions().setExact(true));
            if (titleText.count() == 0) {
                return "「" + title + "」那一列找不到標題的字";
            }
            Map<String, Object> titleBox = layout.textBox(titleText.first());
            if (((Number) titleBox.get("lines")).intValue() > 2) {
                return "標題排成 " + titleBox.get("lines") + " 行，超過兩行";
            }
            if (Boolean.TRUE.equals(titleBox.get("clipped")) && !Boolean.TRUE.equals(titleBox.get("ellipsis"))) {
                return "標題被截掉了，但不是以「…」截斷";
            }
            String lang = frame.language();
            List<String> tags = GuestFrame.tagsIn(row.get(), lang);
            if (tags.size() != 1) {
                return "「" + title + "」那一列的狀態標籤是 " + tags + "，應該剛好一個";
            }
            Locator tag = row.get().getByText(GuestTerms.in(lang, tags.get(0)), new Locator.GetByTextOptions().setExact(true)).first();
            Map<String, Object> tagBox = layout.textBox(tag);
            if (PhoneLayout.overlaps(titleBox, tagBox)) {
                return "標題蓋到了狀態標籤「" + tags.get(0) + "」";
            }
            if (Boolean.TRUE.equals(tagBox.get("clipped"))) {
                return "狀態標籤「" + tags.get(0) + "」被截掉了";
            }
            return PhoneLayout.inside(tagBox, layout.box(row.get())) ? null : "狀態標籤「" + tags.get(0) + "」超出了那一列";
        });
    }

    @那麼("進度頁的標題 {string} 完整換行顯示")
    public void fullTitleOnProgressPage(String title) {
        frame.eventually(() -> {
            Optional<String> cardTitle = request.cardTitle();
            if (cardTitle.isEmpty() || !cardTitle.get().equals(title.strip())) {
                return "進度頁第一張卡的標題是 " + cardTitle.orElse("（沒有）") + "，不是完整的「" + title + "」";
            }
            Locator heading = world.page().getByRole(AriaRole.MAIN)
                    .getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(2)).first();
            Map<String, Object> titleBox = layout.textBox(heading);
            if (Boolean.TRUE.equals(titleBox.get("clipped"))) {
                return "進度頁的標題被截掉了";
            }
            Optional<Locator> card = request.card(frame.language());
            if (card.isEmpty()) {
                return "進度頁上找不到第一張卡";
            }
            return PhoneLayout.inside(titleBox, layout.box(card.get())) ? null : "進度頁的標題超出了卡片";
        });
    }

    @那麼("按鈕 {string} 仍釘在頁底")
    public void buttonPinnedToBottom(String name) {
        frame.eventually(() -> {
            Locator button = world.page().getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(name.strip()).setExact(true))
                    .filter(new Locator.FilterOptions().setVisible(true));
            if (button.count() == 0) {
                return "畫面上看不到按鈕「" + name + "」";
            }
            Map<String, Object> pinned = layout.pinnedButton(button.first());
            if (!Boolean.TRUE.equals(pinned.get("inViewport"))) {
                return "捲到底之後按鈕「" + name + "」不在視窗裡";
            }
            if (!Boolean.TRUE.equals(pinned.get("onTop"))) {
                return "按鈕「" + name + "」被別的東西蓋住了";
            }
            Object gap = pinned.get("barGapToBottom");
            if (gap == null) {
                return "按鈕「" + name + "」不在固定或黏住的操作列裡";
            }
            return Math.abs(((Number) gap).doubleValue()) <= 1 ? null
                    : "按鈕「" + name + "」所在的操作列離視窗底部 " + gap + " 點，沒有貼著底部";
        });
    }

    /** 目前這一頁的介面語言是不是這個語言的代碼；是的話回 {@code null}，不是的話回差在哪裡。 */
    private String languageProblem(String code) {
        Object htmlLang = world.page().evaluate("() => document.documentElement.lang");
        if (!code.equals(htmlLang)) {
            return "網頁的 lang 屬性是「" + htmlLang + "」，不是「" + code + "」";
        }
        String path = frame.path();
        if (path.matches("^/g/[^/]+/?$")) {
            return phraseProblem("服務", code, phrase -> home.main().getByRole(AriaRole.HEADING,
                    new Locator.GetByRoleOptions().setLevel(2).setName(phrase).setExact(true)).count() > 0);
        }
        if (path.matches("^/g/[^/]+/requests/[^/]+$")) {
            // 櫃檯回答過、已取消的需求不顯示四步，這時和其他子頁一樣看標題列的頁標題。
            if (GuestTerms.LANGUAGES.stream().noneMatch(other -> request.fourSteps(other).isPresent())) {
                return phraseProblem("需求進度", code, phrase -> phrase.equals(frame.pageTitle()));
            }
            for (String other : GuestTerms.LANGUAGES) {
                boolean shown = request.fourSteps(other).isPresent();
                if (other.equals(code) && !shown) {
                    return "進度頁看不到「" + code + "」的四步步名；畫面上的文字是 " + bodyLines();
                }
                if (!other.equals(code) && shown) {
                    return "進度頁的四步步名是「" + other + "」的寫法";
                }
            }
            return null;
        }
        for (GuestScreen screen : List.of(GuestScreen.SUPPLY, GuestScreen.REPAIR, GuestScreen.REQUESTS)) {
            if (path.matches("^/g/[^/]+/" + screen.sub().orElseThrow() + "$")) {
                return phraseProblem(screen.title().orElseThrow(), code, phrase -> phrase.equals(frame.pageTitle()));
            }
        }
        throw new IllegalStateException("規格沒有替這一頁定判斷介面語言的固定字句：" + path);
    }

    private String phraseProblem(String zhTw, String code, Predicate<String> shown) {
        for (String other : GuestTerms.LANGUAGES) {
            String phrase = LanguageMenu.phrase(zhTw, other);
            boolean visible = shown.test(phrase);
            if (other.equals(code) && !visible) {
                return "畫面上看不到「" + phrase + "」；畫面上的文字是 " + bodyLines();
            }
            if (!other.equals(code) && visible) {
                return "畫面上看得到另一種語言的「" + phrase + "」";
            }
        }
        return null;
    }

    private List<String> bodyLines() {
        return ShellFrame.linesOf(world.page().locator("body").innerText());
    }

    /** 規格寫錯的參數（{@link IllegalStateException}、{@link IllegalArgumentException}）直接丟出去，不要等到 5 秒才報。 */
}
