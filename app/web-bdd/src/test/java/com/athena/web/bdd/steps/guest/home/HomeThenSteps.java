package com.athena.web.bdd.steps.guest.home;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestEntryPage;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.GuestHomePage;
import com.athena.web.bdd.pages.guest.LanguageMenu;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestTerms;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 「首頁」模組的 Then 句型（{@code specs/truth/features/guest-web/首頁/dsl.md}）。
 *
 * <p>首頁是換 session 加讀需求清單都回來才一次畫出來的，所以每一句先等畫面成立，最多 5 秒。
 */
public class HomeThenSteps {

    /** 四個即將開放的入口：提問列與「服務」三列。 */
    private static final List<String> COMING_SOON_ENTRIES =
            List.of(GuestHomePage.ASK_ROW, "晚點 退房", "寄放 行李", "幫我 叫車");

    private static final String SERVICES = "服務";

    private final WebWorld world;

    private final GuestScenario scenario;

    private final HomeScenario home;

    private final GuestFrame frame;

    private final GuestHomePage homePage;

    public HomeThenSteps(WebWorld world, GuestScenario scenario, HomeScenario home) {
        this.world = world;
        this.scenario = scenario;
        this.home = home;
        this.frame = new GuestFrame(world);
        this.homePage = new GuestHomePage(world);
    }

    /** 表格的區塊都看得到、在畫面上由上到下剛好是表格的順序；表格沒列的區塊不在畫面上。 */
    @那麼("首頁由上到下依序是以下區塊：")
    public void blocksInOrder(DataTable table) {
        String roomNo = scenario.phoneRoom();
        List<String> wanted = table.asMaps().stream().map(row -> row.get("區塊").strip()).toList();
        List<String> known = GuestHomePage.BLOCKS.stream().map(block -> block.replace("{房號}", roomNo)).toList();
        frame.eventually(() -> {
            double previousTop = Double.NEGATIVE_INFINITY;
            String previous = null;
            for (String block : wanted) {
                Locator shown = homePage.block(block, roomNo).filter(new Locator.FilterOptions().setVisible(true));
                if (shown.count() == 0) {
                    return "首頁看不到區塊「" + block + "」";
                }
                double top = shown.first().boundingBox().y;
                if (top <= previousTop) {
                    return "區塊「" + block + "」應該在「" + previous + "」下面";
                }
                previousTop = top;
                previous = block;
            }
            for (String block : known) {
                if (!wanted.contains(block) && homePage.block(block, roomNo)
                        .filter(new Locator.FilterOptions().setVisible(true)).count() > 0) {
                    return "首頁不該有區塊「" + block + "」";
                }
            }
            return null;
        });
    }

    /** 房號膠囊（只顯示、不能按、沒有選單、沒有向下箭頭）在語言膠囊（按鈕，字是短寫法）左邊，兩個都在頂列右邊。 */
    @那麼("頂列右上是房號膠囊 {string} 與語言膠囊 {string}")
    public void topBarCapsules(String roomNo, String shortName) {
        LanguageMenu languageMenu = new LanguageMenu(world);
        frame.eventually(() -> {
            Locator room = homePage.roomCapsule(roomNo);
            Locator language = languageMenu.capsule();
            if (room.count() == 0) {
                return "頂列看不到房號膠囊「" + roomNo + "」";
            }
            if (language.count() != 1) {
                return "頂列應該剛好有一顆語言膠囊，實際有 " + language.count() + " 顆";
            }
            if (!languageMenu.capsuleText().equals(shortName.strip())) {
                return "語言膠囊的字是「" + languageMenu.capsuleText() + "」，不是「" + shortName + "」";
            }
            if (GuestHomePage.insidePressable(room)
                    || Boolean.TRUE.equals(room.evaluate("el => !!el.closest('[aria-haspopup]')"))) {
                return "房號膠囊是可以按、或會打開選單的東西";
            }
            if (homePage.roomCapsuleHasIcon(roomNo)) {
                return "房號膠囊上有圖示（向下箭頭）";
            }
            BoundingBox r = room.boundingBox();
            BoundingBox l = language.first().boundingBox();
            double half = world.page().viewportSize().width / 2.0;
            if (r.x + r.width > l.x + 0.5) {
                return "房號膠囊應該在語言膠囊左邊";
            }
            if (r.x < half || l.x < half) {
                return "房號膠囊與語言膠囊應該都在頂列右邊";
            }
            return r.y < l.y + l.height && l.y < r.y + r.height ? null : "房號膠囊與語言膠囊不在同一條頂列上";
        });
    }

    @那麼("頂列左上是空白的")
    public void topBarLeftEmpty() {
        String roomNo = scenario.phoneRoom();
        frame.eventually(() -> {
            if (homePage.roomCapsule(roomNo).count() == 0) {
                return "頂列看不到房號膠囊「" + roomNo + "」";
            }
            List<String> left = homePage.leftOfRoomCapsule(roomNo);
            return left.isEmpty() ? null : "頂列房號膠囊左邊有東西：" + left;
        });
    }

    /** 「{房號} 號房」下面的問候兩行照字面相符；第一行沒有逗號、稱呼或姓名。 */
    @那麼("問候是 {string} 與 {string}")
    public void greetingIs(String firstLine, String secondLine) {
        String roomLine = scenario.phoneRoom() + " 號房";
        List<String> expected = List.of(firstLine.strip(), secondLine.strip());
        frame.eventually(() -> {
            Locator greeting = homePage.greeting();
            if (greeting.count() != 1) {
                return "首頁應該剛好有一個問候標題，實際有 " + greeting.count() + " 個";
            }
            List<String> lines = ShellFrame.linesOf(greeting.innerText());
            if (!lines.equals(expected)) {
                return "問候是 " + lines + "，應該是 " + expected;
            }
            if (lines.get(0).contains("，") || lines.get(0).contains(",")) {
                return "問候第一行有逗號：" + lines.get(0);
            }
            Locator room = homePage.block(roomLine, scenario.phoneRoom());
            if (room.count() == 0) {
                return "首頁看不到「" + roomLine + "」";
            }
            return greeting.boundingBox().y > room.first().boundingBox().y ? null : "問候不在「" + roomLine + "」下面";
        });
    }

    /** 點之後的網址與畫面文字和點之前相同；沒有對話框、選單、提示浮層。 */
    @那麼("畫面沒有任何變化")
    public void nothingChanged() {
        HomeScenario.Before before = home.beforeTap();
        Page page = world.page();
        assertThat(page.url().equals(before.url()), "網址從 " + before.url() + " 變成 " + page.url());
        List<String> now = ShellFrame.linesOf(page.locator("body").innerText());
        assertThat(now.equals(before.lines()), "畫面文字從 " + before.lines() + " 變成 " + now);
        for (AriaRole layer : List.of(AriaRole.DIALOG, AriaRole.ALERTDIALOG, AriaRole.MENU, AriaRole.LISTBOX)) {
            int shown = page.getByRole(layer).filter(new Locator.FilterOptions().setVisible(true)).count();
            assertThat(shown == 0, "畫面上出現了 " + layer.name().toLowerCase());
        }
        List<String> toasts = new GuestEntryPage(world).toasts();
        assertThat(toasts.isEmpty(), "畫面上出現了提示浮層：" + toasts);
    }

    /** 進度卡四個步名亮到該步（含）、之後的不亮，四格進度條畫成同樣的亮法；底列那一行說明照字面相符。 */
    @那麼("首頁的進度卡的進度條亮到 {string}，說明是 {string}")
    public void progressCardLitUpTo(String step, String description) {
        int expected = GuestTerms.STEPS.indexOf(step.strip());
        if (expected < 0 || expected == GuestTerms.STEPS.size() - 1) {
            throw new IllegalArgumentException("首頁進度卡的步只有已送出、已受理、處理中，收到「" + step + "」");
        }
        frame.eventually(() -> {
            String language = frame.language();
            List<String> stepNames = GuestTerms.STEPS.stream().map(name -> GuestTerms.in(language, name)).toList();
            Locator cards = homePage.progressCard();
            if (cards.count() != 1) {
                return "首頁應該剛好有一張進度卡，實際有 " + cards.count() + " 張";
            }
            Locator card = cards.first();
            int lit = homePage.progressCardLitUpTo(card, stepNames);
            if (lit == -2) {
                return "進度卡上找不到四個步名 " + stepNames + "：" + ShellFrame.linesOf(card.innerText());
            }
            if (lit != expected) {
                return "進度卡亮到「" + (lit < 0 ? "四步全亮" : GuestTerms.STEPS.get(lit)) + "」，應該是「" + step + "」";
            }
            List<GuestHomePage.BarLook> bars = homePage.progressBars(card);
            if (!bars.equals(GuestHomePage.BarLook.upTo(expected))) {
                return "進度卡的四格進度條是 " + bars + "，應該亮到「" + step + "」";
            }
            String actual = homePage.progressCardDescription(card, stepNames.get(stepNames.size() - 1));
            return actual.equals(description.strip()) ? null : "進度卡的說明是「" + actual + "」，應該是「" + description + "」";
        });
    }

    @那麼("首頁沒有進度卡")
    public void noProgressCard() {
        waitUntilHomeDrawn();
        assertThat(homePage.progressCard().count() == 0, "首頁有進度卡：" + homePage.progressCards());
        int eyebrows = homePage.main().getByText(GuestHomePage.PROGRESS_CARD_EYEBROW, new Locator.GetByTextOptions()
                .setExact(true)).filter(new Locator.FilterOptions().setVisible(true)).count();
        assertThat(eyebrows == 0, "首頁上有眉標「" + GuestHomePage.PROGRESS_CARD_EYEBROW + "」");
    }

    /**
     * 首頁上「即將開放」的標籤恰好這麼多個，每一個屬於不同的入口：提問列，或「服務」底下的三列；
     * 四個入口都不是按鈕或連結，也不在按鈕或連結裡面，右端沒有箭頭。入口那一列是包著標籤與入口文字、
     * 寬到內容區八成的最小一層。
     */
    @那麼("首頁上「即將開放」的標籤有 {int} 個")
    public void comingSoonTags(int count) {
        frame.eventually(() -> {
            List<Locator> tags = homePage.comingSoonTags().all();
            if (tags.size() != count) {
                return "首頁上「即將開放」有 " + tags.size() + " 個，應該是 " + count + " 個";
            }
            Set<String> owners = new LinkedHashSet<>();
            for (Locator tag : tags) {
                Locator row = null;
                Object owner = null;
                for (Locator up = tag.locator("xpath=ancestor::*[1]"); up.count() > 0; up = up.locator("xpath=ancestor::*[1]")) {
                    @SuppressWarnings("unchecked")
                    List<Object> found = (List<Object>) up.evaluate("""
                            (el, entries) => {
                              const text = el.innerText.replace(/\\s+/g, ' ');
                              const hit = entries.filter(e => text.includes(e));
                              const main = el.closest('main') || document.body;
                              const fullWidth = el.getBoundingClientRect().width >= main.getBoundingClientRect().width * 0.8;
                              return [hit.length === 1 ? hit[0] : hit.length > 1 ? false : null, fullWidth];
                            }
                            """, COMING_SOON_ENTRIES);
                    Object hit = found.get(0);
                    if (hit == null) {
                        continue;
                    }
                    if (!(hit instanceof String)) {
                        owner = owner == null ? hit : owner;
                        break;
                    }
                    owner = hit;
                    row = up;
                    if (Boolean.TRUE.equals(found.get(1))) {
                        break;
                    }
                }
                if (!(owner instanceof String entry)) {
                    return "有一個「即將開放」不屬於任何一個即將開放的入口";
                }
                if (GuestFrame.hasTrailingArrow(row)) {
                    return "「" + entry + "」右端有箭頭";
                }
                owners.add(entry);
            }
            if (owners.size() != tags.size()) {
                return "同一個入口有兩個「即將開放」：" + owners;
            }
            List<String> problems = new ArrayList<>();
            for (String entry : owners) {
                Locator text = homePage.main().getByText(entry, new Locator.GetByTextOptions().setExact(true)).first();
                if (GuestHomePage.insidePressable(text)) {
                    problems.add("「" + entry + "」是可以按的東西");
                }
                boolean inServices = homePage.section(SERVICES).getByText(entry, new Locator.GetByTextOptions()
                        .setExact(true)).count() > 0;
                if (!entry.equals(GuestHomePage.ASK_ROW) && !inServices) {
                    problems.add("「" + entry + "」不在「" + SERVICES + "」底下");
                }
            }
            return problems.isEmpty() ? null : String.join("；", problems);
        });
    }

    private void waitUntilHomeDrawn() {
        frame.eventually(() -> homePage.main().count() > 0 && !frame.loading() ? null : "首頁沒有畫完");
    }

    private static void assertThat(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
