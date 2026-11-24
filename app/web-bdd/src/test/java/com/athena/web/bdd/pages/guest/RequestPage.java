package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.GuestTerms;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 一張需求的進度頁。
 *
 * <p>可及性結構（產品在 Phase 4 照做，和原型 {@code ui/guest.js} 的進度頁相同）：
 * <ul>
 *   <li>第一張卡的需求標題是 {@code main} 裡第一個 {@code h2}，狀態標籤是和它同一張卡裡、字剛好是標籤的那一個元素；
 *       類型圖示在卡的左邊（{@link RequestIcons}）。</li>
 *   <li>狀態訊息框是角色 {@code note} 的元素，第一行是標題、第二行是內文。</li>
 *   <li>四步進度是一個清單（{@code ol}），四個 {@code li} 的第一行是步名、之後是副文；目前那一步標
 *       {@code aria-current="step"}，四步全亮時沒有任何一步標 {@code aria-current}。每一步在步名左邊有一個 16 至 40 點的
 *       圓點：打勾的圓點裡有勾勾圖示（{@code svg}），實心的圓點有不透明的底色、沒有圖示，空心的圓點沒有底色或底色和
 *       四步那張卡相同、沒有圖示。</li>
 * </ul>
 */
public class RequestPage extends PageObject {

    public static final String CANCEL_BUTTON = "取消這個需求";

    public static final String CANCEL_NOTE = "還沒有同仁接手前，都可以自己取消";

    public static final String LOCKED_NOTE = "同仁已經接手。需要更改或取消，請用房內電話撥 9 找櫃檯。";

    public static final String RATING_QUESTION = "這次服務還滿意嗎？";

    public static final String RATING_SUBMIT = "送出評分";

    /** 四步進度裡一步的樣子：打勾、實心或空心，以及步名之後的副文（沒有副文是空字串）。 */
    public record Step(String name, String look, String sub) {
    }

    /** 一步的圓點畫成什麼樣子：打勾、實心、空心，找不到圓點時是「沒有圓點」。 */
    private static final String STEP_LOOK = """
            step => {
              const opaque = color => {
                const m = /rgba?\\(([^)]*)\\)/.exec(color);
                if (!m) return false;
                const parts = m[1].split(',').map(Number);
                return parts.length < 4 || parts[3] > 0.5;
              };
              const dot = [...step.querySelectorAll('*')].find(el => {
                const r = el.getBoundingClientRect();
                const radius = getComputedStyle(el).borderTopLeftRadius;
                const round = radius.endsWith('%') ? parseFloat(radius) >= 50 : parseFloat(radius) >= r.width / 2 - 1;
                return r.width >= 16 && r.width <= 40 && Math.abs(r.width - r.height) <= 1 && round;
              });
              if (!dot) return '沒有圓點';
              const icon = dot.querySelector('svg');
              if (icon && icon.getBoundingClientRect().width > 0) return '打勾';
              let card = step.parentElement;
              while (card && !opaque(getComputedStyle(card).backgroundColor)) card = card.parentElement;
              const fill = getComputedStyle(dot).backgroundColor;
              const cardFill = card ? getComputedStyle(card).backgroundColor : 'rgb(255, 255, 255)';
              return opaque(fill) && fill !== cardFill ? '實心' : '空心';
            }
            """;

    public RequestPage(WebWorld world) {
        super(world);
    }

    private Locator main() {
        return page().getByRole(AriaRole.MAIN);
    }

    /**
     * 進度頁畫完了沒有：網址是一張需求的進度頁、標題列的頁標題與第一張卡的標題都出現、沒有標成載入中。
     * 畫完回 {@code null}，否則回差在哪裡；「進度頁沒有…」之類的否定句先等它成立。
     */
    public String drawnProblem() {
        GuestFrame frame = new GuestFrame(world);
        if (!frame.path().matches("^/g/[^/]+/requests/[^/]+$")) {
            return "網址不是進度頁：" + frame.path();
        }
        if (frame.pageTitle().isEmpty()) {
            return "進度頁的標題列還沒畫出來";
        }
        if (cardTitle().filter(title -> !title.isEmpty()).isEmpty()) {
            return "進度頁的第一張卡還沒畫出來";
        }
        return frame.loading() ? "進度頁還標著載入中" : null;
    }

    /** 第一張卡的需求標題；還沒畫出來時是空的。 */
    public Optional<String> cardTitle() {
        Locator title = main().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(2));
        return title.count() == 0 ? Optional.empty()
                : Optional.of(ShellFrame.normalizeSpaces(title.first().innerText()));
    }

    /** 第一張卡：從標題往外找，第一個也包著狀態標籤的那一層。 */
    public Optional<Locator> card(String language) {
        Locator title = main().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(2));
        if (title.count() == 0) {
            return Optional.empty();
        }
        for (int level = 1; level <= 3; level++) {
            Locator around = title.first().locator("xpath=ancestor::*[" + level + "]");
            if (!GuestFrame.tagsIn(around, language).isEmpty()) {
                return Optional.of(around);
            }
        }
        return Optional.empty();
    }

    /** 包著第一張卡和它的類型圖示的那一層：從標題往外找，第一個同時有狀態標籤與 {@code svg} 的；找不到時是空的。 */
    public Optional<Locator> cardWithIcon(String language) {
        Locator title = main().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(2));
        if (title.count() == 0) {
            return Optional.empty();
        }
        for (int level = 1; level <= 4; level++) {
            Locator around = title.first().locator("xpath=ancestor::*[" + level + "]");
            if (!GuestFrame.tagsIn(around, language).isEmpty() && around.locator("svg").count() > 0) {
                return Optional.of(around);
            }
        }
        return Optional.empty();
    }

    /** 畫面上的狀態訊息框，每一個是它看得到的每一行字。 */
    public List<List<String>> messageBoxes() {
        return main().getByRole(AriaRole.NOTE).filter(new Locator.FilterOptions().setVisible(true)).all().stream()
                .map(box -> ShellFrame.linesOf(box.innerText()))
                .toList();
    }

    /** 狀態訊息框是不是在第一張卡的下方。 */
    public boolean messageBoxBelowCard(Locator card) {
        Locator box = main().getByRole(AriaRole.NOTE).filter(new Locator.FilterOptions().setVisible(true)).first();
        return box.boundingBox().y >= card.boundingBox().y + card.boundingBox().height - 1;
    }

    /** 四步進度那一塊；畫面上沒有時是空的。以四個步名認出它，所以介面換語言時傳入那個語言。 */
    public Optional<Locator> fourSteps(String language) {
        List<String> names = GuestTerms.STEPS.stream().map(step -> GuestTerms.in(language, step)).toList();
        for (Locator list : main().getByRole(AriaRole.LIST).all()) {
            List<String> firstLines = list.getByRole(AriaRole.LISTITEM).all().stream()
                    .map(item -> firstLine(item)).toList();
            if (firstLines.equals(names)) {
                return Optional.of(list);
            }
        }
        return Optional.empty();
    }

    /**
     * 四步進度每一步的樣子與副文，由左到右。樣子看畫面上的圓點（見類別說明）；畫成實心的那一步和標
     * {@code aria-current="step"} 的那一步要是同一步，不是時樣子後面註明。
     */
    public List<Step> steps(Locator fourSteps) {
        List<Step> steps = new ArrayList<>();
        for (Locator item : fourSteps.getByRole(AriaRole.LISTITEM).all()) {
            String look = String.valueOf(item.evaluate(STEP_LOOK));
            boolean current = "step".equals(item.getAttribute("aria-current"));
            if (look.equals("實心") != current) {
                look += current ? "（標了 aria-current）" : "（沒有標 aria-current）";
            }
            List<String> lines = ShellFrame.linesOf(item.innerText());
            String sub = String.join(" ", lines.subList(Math.min(1, lines.size()), lines.size()));
            steps.add(new Step(lines.isEmpty() ? "" : lines.get(0), look, sub));
        }
        return steps;
    }

    public Locator button(String name) {
        return page().getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions()
                .setName(name).setExact(true)).filter(new Locator.FilterOptions().setVisible(true));
    }

    /** 星星（名稱「{n} 顆星」的單選）看得到的有幾顆。 */
    public int stars() {
        return page().getByRole(AriaRole.RADIO, new com.microsoft.playwright.Page.GetByRoleOptions()
                        .setName(java.util.regex.Pattern.compile("^[1-5] 顆星$")))
                .filter(new Locator.FilterOptions().setVisible(true)).count();
    }

    /** 第一張卡的標題有沒有完整顯示：沒有被截成「…」，也沒有被裁掉。 */
    public boolean cardTitleFullyShown() {
        Locator title = main().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(2)).first();
        Object whole = title.evaluate("""
                el => el.scrollHeight <= el.clientHeight + 1 && el.scrollWidth <= el.clientWidth + 1
                  && getComputedStyle(el).textOverflow !== 'ellipsis'
                """);
        return Boolean.TRUE.equals(whole);
    }

    /** 最上面那一個看得到的對話框（角色 {@code alertdialog} 或 {@code dialog}）；沒有時是空的。 */
    public Optional<Locator> dialog() {
        Locator shown = page().getByRole(AriaRole.ALERTDIALOG).or(page().getByRole(AriaRole.DIALOG))
                .filter(new Locator.FilterOptions().setVisible(true));
        int count = shown.count();
        return count == 0 ? Optional.empty() : Optional.of(shown.nth(count - 1));
    }

    /** 對話框的標題：裡面的標題元素。 */
    public static String dialogTitle(Locator dialog) {
        Locator heading = dialog.getByRole(AriaRole.HEADING);
        return heading.count() == 0 ? "" : ShellFrame.normalizeSpaces(heading.first().innerText());
    }

    /** 對話框裡的按鈕名稱，照畫面上的先後。 */
    public static List<String> dialogButtons(Locator dialog) {
        return dialog.getByRole(AriaRole.BUTTON).filter(new Locator.FilterOptions().setVisible(true)).all().stream()
                .map(button -> ShellFrame.normalizeSpaces(button.innerText()))
                .toList();
    }

    /** 對話框裡標題與按鈕以外的字，接成一段。 */
    public static String dialogBody(Locator dialog) {
        List<String> lines = new ArrayList<>(ShellFrame.linesOf(dialog.innerText()));
        lines.remove(dialogTitle(dialog));
        lines.removeAll(dialogButtons(dialog));
        return String.join("", lines);
    }

    /** 焦點是不是在對話框的第一顆按鈕上。 */
    public static boolean focusOnFirstButton(Locator dialog) {
        Object focused = dialog.evaluate("""
                d => {
                  const first = d.querySelector('button, [role=button]');
                  return !!first && first === document.activeElement;
                }
                """);
        return Boolean.TRUE.equals(focused);
    }

    /**
     * 對話框後面有沒有遮罩：瀏覽器原生的模態對話框自帶遮罩；自己畫的遮罩是蓋滿整個視窗、有底色的固定層，
     * 包著對話框或疊在對話框底下。
     */
    public static boolean scrimBehind(Locator dialog) {
        Object scrim = dialog.evaluate("""
                d => {
                  try { if (d.matches(':modal')) return true; } catch (e) {}
                  const covers = el => {
                    const cs = getComputedStyle(el);
                    const r = el.getBoundingClientRect();
                    return (cs.position === 'fixed' || cs.position === 'absolute')
                      && r.width >= innerWidth - 1 && r.height >= innerHeight - 1
                      && cs.backgroundColor !== 'rgba(0, 0, 0, 0)' && cs.visibility !== 'hidden';
                  };
                  for (let el = d.parentElement; el && el !== document.body; el = el.parentElement) {
                    if (covers(el)) return true;
                  }
                  return [...document.body.querySelectorAll('*')]
                    .some(el => !el.contains(d) && !d.contains(el) && covers(el));
                }
                """);
        return Boolean.TRUE.equals(scrim);
    }

    /** 評分卡：標題「這次服務還滿意嗎？」、包著五顆星的那一張卡；畫面上沒有時是空的。 */
    public Optional<Locator> ratingCard() {
        Locator question = main().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions()
                .setName(RATING_QUESTION).setExact(true)).filter(new Locator.FilterOptions().setVisible(true));
        if (question.count() == 0) {
            return Optional.empty();
        }
        Locator card = question.first().locator("xpath=ancestor::*[.//*[@role='radio']][1]");
        return card.count() == 0 ? Optional.empty() : Optional.of(card);
    }

    /** 評分卡上的第 {@code n} 顆星（名稱「{n} 顆星」）。 */
    public Locator star(int n) {
        return page().getByRole(AriaRole.RADIO, new com.microsoft.playwright.Page.GetByRoleOptions()
                .setName(n + " 顆星").setExact(true));
    }

    /** 點評分卡上的第 {@code n} 顆星，觸控設定下用點觸。 */
    public void tapStar(int n) {
        if (world.profile().touch()) {
            star(n).tap();
        } else {
            star(n).click();
        }
    }

    /** 單選群組裡選中的星（第幾顆）。 */
    public List<Integer> checkedStars() {
        List<Integer> checked = new ArrayList<>();
        for (int n = 1; n <= 5; n++) {
            Locator star = star(n);
            if (star.count() > 0 && star.first().isChecked()) {
                checked.add(n);
            }
        }
        return checked;
    }

    /** 五顆星各自畫成哪一種圖形（{@link StarShape}），由第 1 顆到第 5 顆；找不到或不只一顆同名的算認不出。 */
    public List<StarShape> starShapes() {
        List<StarShape> shapes = new ArrayList<>();
        for (int n = 1; n <= 5; n++) {
            Object paths = star(n).evaluateAll(STAR_PATHS);
            shapes.add(StarShape.of((List<?>) paths));
        }
        return shapes;
    }

    static final String STAR_PATHS =
            "els => els.length === 1 ? [...els[0].querySelectorAll('svg path')].map(p => p.getAttribute('d') || '') : []";

    /** 這一塊裡有幾個可以打字的輸入框。 */
    public static int textInputsIn(Locator area) {
        return area.locator("textarea, input:not([type]), input[type=text], input[type=search], [contenteditable=true]")
                .or(area.getByRole(AriaRole.TEXTBOX))
                .count();
    }

    private static String firstLine(Locator item) {
        List<String> lines = ShellFrame.linesOf(item.innerText());
        return lines.isEmpty() ? "" : lines.get(0);
    }
}
