package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.BrowserRegex;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 住客首頁。
 *
 * <p>可及性結構（產品在 Phase 4 照做）：首頁內容在 {@code main} 裡；頂列有只顯示、不能按的房號膠囊（字就是房號）；
 * 兩張主視覺卡是名稱含「備品補充」「設備報修」的連結或按鈕；每個區段是 {@code h2} 區段標題加它下面的內容，
 * 「我的需求」區段裡的進度卡是一個名稱以眉標「進行中的需求」開頭的連結或按鈕。
 */
public class GuestHomePage extends PageObject {

    public static final String SUPPLY_CARD = "備品補充";

    public static final String REPAIR_CARD = "設備報修";

    public static final String MY_REQUESTS = "我的需求";

    public static final String PROGRESS_CARD_EYEBROW = "進行中的需求";

    /** 進度卡底列右邊那兩個字。 */
    public static final String VIEW = "查看";

    public GuestHomePage(WebWorld world) {
        super(world);
    }

    public Locator main() {
        return page().getByRole(AriaRole.MAIN);
    }

    /** 名稱裡有這幾個字的主視覺卡（連結或按鈕），看得到的有幾張。 */
    public int heroCards(String word) {
        Pattern name = Pattern.compile(BrowserRegex.literal(word));
        return main().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(name))
                .or(main().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name)))
                .filter(new Locator.FilterOptions().setVisible(true))
                .count();
    }

    /** 以 {@code h2} 區段標題找到的區段：標題所在、而且把它下面內容包起來的那一層。 */
    public Locator section(String heading) {
        Locator title = main().getByRole(AriaRole.HEADING,
                new Locator.GetByRoleOptions().setLevel(2).setName(heading).setExact(true));
        return title.locator("xpath=ancestor::*[self::section or @role='region'][1]");
    }

    /** 「我的需求」區段裡的進度卡，每一張是它看得到的每一行字。 */
    public List<List<String>> progressCards() {
        Locator cards = section(MY_REQUESTS)
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions()
                        .setName(Pattern.compile("^" + BrowserRegex.literal(PROGRESS_CARD_EYEBROW))))
                .or(section(MY_REQUESTS).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                        .setName(Pattern.compile("^" + BrowserRegex.literal(PROGRESS_CARD_EYEBROW)))))
                .filter(new Locator.FilterOptions().setVisible(true));
        return cards.all().stream().map(card -> ShellFrame.linesOf(card.innerText())).toList();
    }

    /** 進度卡底列的「查看」：整張卡是一個連結，按它的「查看」就是按這張卡。 */
    public Locator progressCardView() {
        Pattern eyebrow = Pattern.compile("^" + BrowserRegex.literal(PROGRESS_CARD_EYEBROW));
        Locator card = section(MY_REQUESTS).getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(eyebrow))
                .or(section(MY_REQUESTS).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(eyebrow)))
                .filter(new Locator.FilterOptions().setVisible(true));
        return card.first().getByText(VIEW, new Locator.GetByTextOptions().setExact(true));
    }

    /** 提問列的字。 */
    public static final String ASK_ROW = "問問看：製冰機在哪裡？";

    /** 即將開放的入口上那個標籤的字。 */
    public static final String COMING_SOON = "即將開放";

    /** 首頁最下面那一行緊急提示。 */
    public static final String EMERGENCY_NOTE = "緊急狀況請用房內電話撥 9 找櫃檯";

    /** 「首頁由上到下依序是以下區塊」認得的區塊說法；{@code {房號} 號房} 那一列另外認。 */
    public static final List<String> BLOCKS =
            List.of("頂列", "{房號} 號房", "問候", "提問列", MY_REQUESTS, "主視覺卡", "服務", "住宿指南", EMERGENCY_NOTE);

    /** 房號膠囊上有沒有圖示（例如向下箭頭）：字剛好是房號的那一層和包著它、字也只有房號的外層裡有沒有 {@code svg}。 */
    public boolean roomCapsuleHasIcon(String roomNo) {
        return Boolean.TRUE.equals(roomCapsule(roomNo).evaluate("""
                (text, roomNo) => {
                  for (let el = text; el && el !== document.body && el.innerText.trim() === roomNo; el = el.parentElement) {
                    if (el.querySelector('svg')) return true;
                  }
                  return false;
                }
                """, roomNo));
    }

    /** 頂列的房號膠囊：字剛好是房號、看得到的那一個元素。 */
    public Locator roomCapsule(String roomNo) {
        return page().getByText(roomNo, new com.microsoft.playwright.Page.GetByTextOptions().setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true)).first();
    }

    /** 問候：首頁內容裡的 {@code h1}，第一行是早安／午安／晚安、第二行是「今天需要什麼？」。 */
    public Locator greeting() {
        return main().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(1));
    }

    /** 某個區塊在畫面上的元素，認不得的說法丟錯；畫面上沒有時是一個數不到的 locator。 */
    public Locator block(String name, String roomNo) {
        String wanted = name.strip();
        if (wanted.equals(roomNo + " 號房")) {
            return main().getByText(wanted, new Locator.GetByTextOptions().setExact(true));
        }
        return switch (wanted) {
            case "頂列" -> page().getByText(roomNo, new com.microsoft.playwright.Page.GetByTextOptions().setExact(true));
            case "問候" -> greeting();
            case "提問列" -> main().getByText(ASK_ROW, new Locator.GetByTextOptions().setExact(true));
            case "主視覺卡" -> {
                Pattern supply = Pattern.compile(BrowserRegex.literal(SUPPLY_CARD));
                yield main().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(supply))
                        .or(main().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(supply)));
            }
            case MY_REQUESTS, "服務", "住宿指南" -> main().getByRole(AriaRole.HEADING,
                    new Locator.GetByRoleOptions().setLevel(2).setName(wanted).setExact(true));
            case EMERGENCY_NOTE -> main().getByText(EMERGENCY_NOTE, new Locator.GetByTextOptions().setExact(true));
            default -> throw new IllegalArgumentException("首頁的區塊只有 " + BLOCKS + "，收到「" + name + "」");
        };
    }

    /** 首頁的進度卡（看得到的那幾張）：「我的需求」區段裡名稱以眉標「進行中的需求」開頭的連結或按鈕。 */
    public Locator progressCard() {
        Pattern eyebrow = Pattern.compile("^" + BrowserRegex.literal(PROGRESS_CARD_EYEBROW));
        return section(MY_REQUESTS).getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(eyebrow))
                .or(section(MY_REQUESTS).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(eyebrow)))
                .filter(new Locator.FilterOptions().setVisible(true));
    }

    /**
     * 進度卡亮到第幾步（0 起算）。卡裡的四個步名是一個清單，每一步是一個 {@code li}、第一行是步名；
     * 目前那一步標 {@code aria-current="step"}，它和它之前的亮、之後的不亮（和進度頁的四步進度同一種寫法）。
     * 找不到四個步名的清單時是 -2，清單裡沒有任何一步標 {@code aria-current} 時是 -1。
     *
     * @param stepNames 這個介面語言的四個步名，由左到右
     */
    public int progressCardLitUpTo(Locator card, List<String> stepNames) {
        for (Locator list : card.getByRole(AriaRole.LIST).all()) {
            List<Locator> items = list.getByRole(AriaRole.LISTITEM).all();
            List<String> names = items.stream().map(item -> {
                List<String> lines = ShellFrame.linesOf(item.innerText());
                return lines.isEmpty() ? "" : lines.get(0);
            }).toList();
            if (!names.equals(stepNames)) {
                continue;
            }
            for (int i = 0; i < items.size(); i++) {
                if ("step".equals(items.get(i).getAttribute("aria-current"))) {
                    return i;
                }
            }
            return -1;
        }
        return -2;
    }

    /** 進度卡四格進度條的一格畫成什麼樣子。 */
    public enum BarLook {
        LIT("亮"),
        UNLIT("不亮"),
        UNRECOGNISED("認不出");

        private final String label;

        BarLook(String label) {
            this.label = label;
        }

        /** 亮的底色是原型的 {@code brand-primary}，不亮的是 {@code fill}。 */
        static BarLook of(String backgroundColor) {
            return switch (backgroundColor.replace(" ", "")) {
                case "rgb(12,118,128)" -> LIT;
                case "rgba(118,118,128,0.12)" -> UNLIT;
                default -> UNRECOGNISED;
            };
        }

        /** 亮到第 {@code upTo} 格（0 起算、含），其餘不亮，共四格。 */
        public static List<BarLook> upTo(int upTo) {
            return java.util.stream.IntStream.range(0, 4).mapToObj(i -> i <= upTo ? LIT : UNLIT).toList();
        }

        @Override
        public String toString() {
            return label;
        }
    }

    static final String PROGRESS_BAR_COLOURS = """
            card => {
              const bars = [...card.querySelectorAll('*')].filter(e => {
                const r = e.getBoundingClientRect();
                return e.children.length === 0 && r.height >= 2 && r.height <= 6 && r.width >= 16;
              }).sort((a, b) => a.getBoundingClientRect().left - b.getBoundingClientRect().left);
              const top = bars.length ? Math.round(bars[0].getBoundingClientRect().top) : 0;
              return bars.filter(e => Math.round(e.getBoundingClientRect().top) === top)
                  .map(e => getComputedStyle(e).backgroundColor);
            }
            """;

    /**
     * 進度卡的四格進度條，由左到右。每一格是卡裡一個沒有子元素、高 2 至 6 點、寬 16 點以上的長條，四格在同一條高度上。
     * 找到的不是剛好四格時，回傳找到的那幾格。
     */
    public List<BarLook> progressBars(Locator card) {
        Object colours = card.evaluate(PROGRESS_BAR_COLOURS);
        return ((List<?>) colours).stream().map(c -> BarLook.of(String.valueOf(c))).toList();
    }

    /** 進度卡底列的一行說明：最後一個步名之後的字，去掉結尾的「查看」。 */
    public String progressCardDescription(Locator card, String lastStepName) {
        List<String> lines = ShellFrame.linesOf(card.innerText());
        int last = lines.lastIndexOf(lastStepName);
        String bottom = String.join(" ", lines.subList(last + 1, lines.size())).strip();
        return bottom.endsWith(VIEW) ? bottom.substring(0, bottom.length() - VIEW.length()).strip() : bottom;
    }

    /** 首頁上字剛好是「即將開放」、看得到的標籤。 */
    public Locator comingSoonTags() {
        return main().getByText(COMING_SOON, new Locator.GetByTextOptions().setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true));
    }

    /** 這個元素自己或它外層有沒有任何可以按的東西（按鈕、連結）。 */
    public static boolean insidePressable(Locator element) {
        return Boolean.TRUE.equals(element.evaluate(
                "el => !!el.closest('a[href], button, [role=button], [role=link], [role=menuitem], [onclick]')"));
    }

    /** 頂列裡房號膠囊左邊、同一條高度上看得到的字與圖片，每一個以字或 {@code <標籤名>} 表示。 */
    public List<String> leftOfRoomCapsule(String roomNo) {
        Object found = roomCapsule(roomNo).evaluate("""
                capsule => {
                  const c = capsule.getBoundingClientRect();
                  const out = [];
                  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
                  for (let n = walker.nextNode(); n; n = walker.nextNode()) {
                    let r, label;
                    if (n.nodeType === Node.TEXT_NODE) {
                      if (!n.textContent.trim()) continue;
                      const range = document.createRange();
                      range.selectNodeContents(n);
                      r = range.getBoundingClientRect();
                      label = n.textContent.trim();
                    } else if (['IMG', 'SVG', 'CANVAS', 'PICTURE', 'VIDEO'].includes(n.tagName.toUpperCase())) {
                      r = n.getBoundingClientRect();
                      label = '<' + n.tagName.toLowerCase() + '>';
                    } else {
                      continue;
                    }
                    const el = n.nodeType === Node.TEXT_NODE ? n.parentElement : n;
                    const style = getComputedStyle(el);
                    if (r.width === 0 || r.height === 0 || style.visibility === 'hidden' || Number(style.opacity) === 0) continue;
                    if (r.top < c.bottom && r.bottom > c.top && r.right <= c.left + 0.5) out.push(label);
                  }
                  return out;
                }
                """);
        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) found;
        return labels;
    }
}
