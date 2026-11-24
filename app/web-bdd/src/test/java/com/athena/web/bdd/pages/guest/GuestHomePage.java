package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.BrowserRegex;
import com.athena.web.bdd.support.JsResult;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;
import java.util.regex.Pattern;

public class GuestHomePage extends PageObject {

    public static final String SUPPLY_CARD = "備品補充";

    public static final String REPAIR_CARD = "設備報修";

    public static final String MY_REQUESTS = "我的需求";

    public static final String PROGRESS_CARD_EYEBROW = "進行中的需求";

    public static final String VIEW = "查看";

    public GuestHomePage(WebWorld world) {
        super(world);
    }

    public Locator main() {
        return page().getByRole(AriaRole.MAIN);
    }

    public int heroCards(String word) {
        Pattern name = Pattern.compile(BrowserRegex.literal(word));
        return main().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(name))
                .or(main().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name)))
                .filter(new Locator.FilterOptions().setVisible(true))
                .count();
    }

    public Locator section(String heading) {
        Locator title = main().getByRole(AriaRole.HEADING,
                new Locator.GetByRoleOptions().setLevel(2).setName(heading).setExact(true));
        return title.locator("xpath=ancestor::*[self::section or @role='region'][1]");
    }

    public List<List<String>> progressCards() {
        Locator cards = section(MY_REQUESTS)
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions()
                        .setName(Pattern.compile("^" + BrowserRegex.literal(PROGRESS_CARD_EYEBROW))))
                .or(section(MY_REQUESTS).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                        .setName(Pattern.compile("^" + BrowserRegex.literal(PROGRESS_CARD_EYEBROW)))))
                .filter(new Locator.FilterOptions().setVisible(true));
        return cards.all().stream().map(card -> ShellFrame.linesOf(card.innerText())).toList();
    }

    public Locator progressCardView() {
        Pattern eyebrow = Pattern.compile("^" + BrowserRegex.literal(PROGRESS_CARD_EYEBROW));
        Locator card = section(MY_REQUESTS).getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(eyebrow))
                .or(section(MY_REQUESTS).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(eyebrow)))
                .filter(new Locator.FilterOptions().setVisible(true));
        return card.first().getByText(VIEW, new Locator.GetByTextOptions().setExact(true));
    }

    public static final String ASK_ROW = "問問看：製冰機在哪裡？";

    public static final String COMING_SOON = "即將開放";

    public static final String EMERGENCY_NOTE = "緊急狀況請用房內電話撥 9 找櫃檯";

    public static final List<String> BLOCKS =
            List.of("頂列", "{房號} 號房", "問候", "提問列", MY_REQUESTS, "主視覺卡", "服務", "住宿指南", EMERGENCY_NOTE);

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

    public Locator roomCapsule(String roomNo) {
        return page().getByText(roomNo, new com.microsoft.playwright.Page.GetByTextOptions().setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true)).first();
    }

    public Locator greeting() {
        return main().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(1));
    }

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

    public Locator progressCard() {
        Pattern eyebrow = Pattern.compile("^" + BrowserRegex.literal(PROGRESS_CARD_EYEBROW));
        return section(MY_REQUESTS).getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(eyebrow))
                .or(section(MY_REQUESTS).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(eyebrow)))
                .filter(new Locator.FilterOptions().setVisible(true));
    }

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

    public enum BarLook {
        LIT("亮"),
        UNLIT("不亮"),
        UNRECOGNISED("認不出");

        private final String label;

        BarLook(String label) {
            this.label = label;
        }

        static BarLook of(String backgroundColor) {
            return switch (backgroundColor.replace(" ", "")) {
                case "rgb(12,118,128)" -> LIT;
                case "rgba(118,118,128,0.12)" -> UNLIT;
                default -> UNRECOGNISED;
            };
        }

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

    public List<BarLook> progressBars(Locator card) {
        Object colours = card.evaluate(PROGRESS_BAR_COLOURS);
        return ((List<?>) colours).stream().map(c -> BarLook.of(String.valueOf(c))).toList();
    }

    public String progressCardDescription(Locator card, String lastStepName) {
        List<String> lines = ShellFrame.linesOf(card.innerText());
        int last = lines.lastIndexOf(lastStepName);
        String bottom = String.join(" ", lines.subList(last + 1, lines.size())).strip();
        return bottom.endsWith(VIEW) ? bottom.substring(0, bottom.length() - VIEW.length()).strip() : bottom;
    }

    public Locator comingSoonTags() {
        return main().getByText(COMING_SOON, new Locator.GetByTextOptions().setExact(true))
                .filter(new Locator.FilterOptions().setVisible(true));
    }

    public static boolean insidePressable(Locator element) {
        return Boolean.TRUE.equals(element.evaluate(
                "el => !!el.closest('a[href], button, [role=button], [role=link], [role=menuitem], [onclick]')"));
    }

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
        return JsResult.strings(found);
    }
}
