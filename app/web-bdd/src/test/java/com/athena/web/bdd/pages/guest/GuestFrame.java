package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.BrowserRegex;
import com.athena.web.bdd.support.GuestTerms;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.options.AriaRole;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 住客每一頁共用的外框：子頁標題列、載入中、最上面那一層可以按的東西、提示框，以及這支手機的介面語言。
 *
 * <p>找元素只用角色與看得到的字，產品在 Phase 4 照這裡寫的可及性結構做：
 * <ul>
 *   <li>子頁標題列是頁面最外層的 {@code <header>}（角色 {@code banner}），裡面依序是返回鈕、
 *       一個 {@code h1} 頁標題、第二行「房號 {房號}」。返回鈕是按鈕或連結都可以，名稱跟著介面語言
 *       （繁體中文「返回」、英文「Back」，{@link GuestTerms}）。</li>
 *   <li>載入中的骨架或轉圈標 {@code aria-busy="true"}；送出中鎖住的按鈕也是。</li>
 *   <li>提示框（清單或表單下方、不會自己消失）是角色 {@code alert}；提示浮層是角色 {@code status}，兩者不混用。</li>
 * </ul>
 */
public class GuestFrame extends PageObject {

    /** 等畫面或後端跟上的上限（共通約定：輪詢，上限 5 秒）。 */
    public static final long SETTLE_LIMIT_MS = 5_000;

    /** 產品記住住客介面語言的 {@code localStorage} 鍵，和櫃檯的語言鍵不同。 */
    public static final String LANGUAGE_KEY = "tms.guest.language";

    private static final String DEFAULT_LANGUAGE = "zh-TW";

    private static final long POLL_INTERVAL_MS = 100;

    /** 「住客按」找得到的東西：按鈕、連結、可點的列、選單裡的選項、單選的膠囊與星星。 */
    private static final List<AriaRole> PRESSABLE_ROLES = List.of(
            AriaRole.BUTTON, AriaRole.LINK, AriaRole.MENUITEM, AriaRole.MENUITEMRADIO, AriaRole.MENUITEMCHECKBOX,
            AriaRole.OPTION, AriaRole.RADIO, AriaRole.TAB, AriaRole.ROW);

    /** 由上往下的層：對話框、選單、提示框，最後才是整頁。 */
    private static final List<AriaRole> LAYERS = List.of(
            AriaRole.ALERTDIALOG, AriaRole.DIALOG, AriaRole.MENU, AriaRole.LISTBOX, AriaRole.ALERT);

    public GuestFrame(WebWorld world) {
        super(world);
    }

    /** 目前分頁網址的路徑，不含查詢參數。 */
    public String path() {
        String path = URI.create(page().url()).getPath();
        return path == null ? "" : path;
    }

    /** 畫面上有沒有標成載入中的東西。 */
    public boolean loading() {
        return page().locator("[aria-busy='true']").count() > 0;
    }

    public Locator titleBar() {
        return page().getByRole(AriaRole.BANNER);
    }

    /** 標題列裡的頁標題；沒有標題列時是空字串。 */
    public String pageTitle() {
        Locator heading = titleBar().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(1));
        return heading.count() == 0 ? "" : ShellFrame.normalizeSpaces(heading.first().innerText());
    }

    /** 標題列裡看得到的每一行字，由上到下。 */
    public List<String> titleBarLines() {
        Locator bar = titleBar();
        return bar.count() == 0 ? List.of() : ShellFrame.linesOf(bar.first().innerText());
    }

    /** 這一塊裡看得到的返回鈕：按鈕或連結，名稱是目前介面語言的「返回」。 */
    public Locator backControl(Locator scope) {
        Pattern name = exactName(GuestTerms.in(language(), "返回"));
        return scope.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name))
                .or(scope.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(name)))
                .filter(new Locator.FilterOptions().setVisible(true));
    }

    /**
     * 住客頁畫完了沒有：網址在住客頁底下、沒有標成載入中、看得到一個有字的標題。畫完回 {@code null}，否則回差在哪裡。
     * 「畫面上沒有…」之類的否定句先等它成立，免得在還沒畫出來的空白頁上通過。
     */
    public String drawnProblem() {
        if (!path().startsWith("/g/")) {
            return "網址不在住客頁底下：" + path();
        }
        if (loading()) {
            return "畫面還標著載入中";
        }
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("\\S")))
                .filter(new Locator.FilterOptions().setVisible(true)).count() > 0 ? null : "畫面上還沒有任何標題";
    }

    /**
     * 畫面上有沒有遮罩：位置固定、蓋住整個視窗、接得到點按的一層。透明的也算：產品靠它在選單或確認框外點一下收起，
     * 收起之後它要拿掉或不再接點按。
     */
    public boolean scrimShown() {
        Object found = page().evaluate("""
                () => [...document.body.querySelectorAll('*')].some(el => {
                  const s = getComputedStyle(el);
                  if (s.position !== 'fixed' || s.visibility === 'hidden' || s.display === 'none') return false;
                  if (s.pointerEvents === 'none') return false;
                  const r = el.getBoundingClientRect();
                  return r.width >= window.innerWidth - 1 && r.height >= window.innerHeight - 1;
                })
                """);
        return Boolean.TRUE.equals(found);
    }

    /** 這一列右端有沒有箭頭：列裡最後一個看得到的 {@code svg} 落在列的右邊三分之一。 */
    public static boolean hasTrailingArrow(Locator row) {
        return Boolean.TRUE.equals(row.evaluate("""
                row => {
                  const icons = [...row.querySelectorAll('svg')].filter(svg => {
                    const b = svg.getBoundingClientRect();
                    return b.width > 0 && b.height > 0 && getComputedStyle(svg).visibility !== 'hidden';
                  });
                  if (icons.length === 0) return false;
                  const r = row.getBoundingClientRect();
                  return icons[icons.length - 1].getBoundingClientRect().left >= r.left + r.width * 2 / 3;
                }
                """));
    }

    /** 標題列最左邊那顆可以按的東西。 */
    public Locator titleBarFirstPressable() {
        return pressables(titleBar().first(), Pattern.compile(".*")).first();
    }

    /** 標題列是不是黏在頁面頂端：{@code position} 是 sticky 或 fixed，而且頂緣貼著視窗頂端。 */
    public boolean titleBarStuckToTop() {
        Locator bar = titleBar().first();
        Object position = bar.evaluate("el => getComputedStyle(el).position");
        Object top = bar.evaluate("el => el.getBoundingClientRect().top");
        return ("sticky".equals(position) || "fixed".equals(position)) && Math.abs(((Number) top).doubleValue()) < 1;
    }

    /**
     * 最上面那一層裡名稱相符、看得到的那一個可以按的東西：先找對話框、選單、提示框，最後找整頁。
     * 名稱先比整串；比不到時比「第一行字就是這個名稱」的列，例如清單上名稱是「毛巾 14:05 送出 · 房務 已受理」的那一列，
     * 這樣「毛巾」不會按到「毛巾 ×2」。都找不到時回整頁第一個名稱完全相符的，讓呼叫端等它出現。
     */
    public Locator topmostPressable(String name) {
        for (AriaRole layer : LAYERS) {
            Locator shown = page().getByRole(layer).filter(new Locator.FilterOptions().setVisible(true));
            for (int i = shown.count() - 1; i >= 0; i--) {
                Locator found = pressableIn(shown.nth(i), name);
                if (found != null) {
                    return found;
                }
            }
        }
        Locator found = pressableIn(page().locator("body"), name);
        return found != null ? found : pressables(page().locator("body"), exactName(name)).first();
    }

    /**
     * 按一下最上面那一層的「名稱」，觸控設定下用點觸；等這一下引發的請求都回來、畫面不再標成載入中。
     * 找不到時最多等 5 秒讓它出現。
     */
    public void press(String name) {
        Locator target = waitForPressable(name);
        settleAfter(() -> {
            if (world.profile().touch()) {
                target.tap();
            } else {
                target.click();
            }
        });
    }

    /**
     * 做一個動作，等它引發的請求都結束（成功、失敗或被攔截回應都算），再等畫面畫完、不再標成載入中。
     *
     * <p>事件串流是不會結束的請求，不等它。被測試刻意扣住的請求也不會結束，所以兩段等待各以 5 秒為限，
     * 到了就往下走，不在這裡失敗：畫面該是什麼樣子由之後的結果句判斷。
     */
    public void settleAfter(Runnable action) {
        Page page = page();
        Set<Request> pending = new HashSet<>();
        Consumer<Request> started = request -> {
            if (!isLiveStream(request.url())) {
                pending.add(request);
            }
        };
        Consumer<Request> ended = pending::remove;
        page.onRequest(started);
        page.onRequestFinished(ended);
        page.onRequestFailed(ended);
        try {
            action.run();
            waitAtMost(page, () -> pending.isEmpty());
        } finally {
            page.offRequest(started);
            page.offRequestFinished(ended);
            page.offRequestFailed(ended);
        }
        waitForFrames(page);
        waitAtMost(page, () -> !loading());
    }

    /** 看得到的提示框（角色 {@code alert}），依在頁面上的先後。 */
    public Locator notices() {
        return page().getByRole(AriaRole.ALERT).filter(new Locator.FilterOptions().setVisible(true));
    }

    /** 看得到、有字的提示浮層（角色 {@code status}）；載入中的轉圈沒有字，不算。 */
    public Locator toasts() {
        return page().getByRole(AriaRole.STATUS).filter(new Locator.FilterOptions().setVisible(true))
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("\\S")));
    }

    /** 提示框裡看得到的每一行字。 */
    public List<String> noticeLines(Locator notice) {
        return ShellFrame.linesOf(notice.innerText());
    }

    /**
     * 這個提示框蓋住了哪些不在它裡面的按鈕（以名稱列出）。比的是兩者在畫面上的方框有沒有重疊。
     */
    public List<String> buttonsCoveredBy(Locator notice) {
        Object covered = notice.evaluate("""
                box => {
                  const a = box.getBoundingClientRect();
                  const hit = [];
                  for (const el of document.querySelectorAll('button, [role="button"], a[href]')) {
                    if (box.contains(el)) continue;
                    const b = el.getBoundingClientRect();
                    if (b.width === 0 || b.height === 0) continue;
                    const overlap = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;
                    if (overlap) hit.push((el.getAttribute('aria-label') || el.innerText || '').trim());
                  }
                  return hit;
                }
                """);
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) covered;
        return names;
    }

    /**
     * 這一塊裡看得到、字剛好是某個狀態標籤（這個語言的說法）的元素，回傳它們的繁體中文說法。
     * 狀態標籤沒有自己的角色，所以靠字認：一列或一張卡裡只該有一個。
     */
    public static List<String> tagsIn(Locator container, String language) {
        return GuestTerms.TAGS.stream()
                .filter(tag -> container.getByText(GuestTerms.in(language, tag), new Locator.GetByTextOptions().setExact(true))
                        .filter(new Locator.FilterOptions().setVisible(true)).count() > 0)
                .toList();
    }

    /** 這支手機目前的住客介面語言代碼；還沒選過時產品依手機語言決定，手機設定是繁體中文。 */
    public String language() {
        if (!world.hasPage()) {
            return DEFAULT_LANGUAGE;
        }
        try {
            Object stored = page().evaluate("key => localStorage.getItem(key)", LANGUAGE_KEY);
            return stored instanceof String code && !code.isBlank() ? code : DEFAULT_LANGUAGE;
        } catch (PlaywrightException notOnApp) {
            return DEFAULT_LANGUAGE;
        }
    }

    /** 最多等 5 秒讓這個名稱的東西出現在最上面那一層，回傳它。 */
    public Locator waitForPressable(String name) {
        Page page = page();
        try {
            page.waitForCondition(() -> topmostPressable(name).isVisible(),
                    new Page.WaitForConditionOptions().setTimeout(SETTLE_LIMIT_MS));
        } catch (PlaywrightException notShown) {
            throw new AssertionError("畫面上找不到可以按的「" + name + "」；畫面上的文字是 "
                    + ShellFrame.linesOf(page.locator("body").innerText()), notShown);
        }
        return topmostPressable(name);
    }

    /**
     * 重讀到這個檢查成立為止，最多 5 秒；檢查回 {@code null} 表示成立，否則回差在哪裡，到時間時以最後一次的說明失敗，
     * 附上畫面上的文字。讀畫面時出的錯（換頁中、元素剛被換掉）當成還沒好；規格寫錯的參數
     * （{@link IllegalArgumentException}、{@link IllegalStateException}）直接丟出去，不等 5 秒。
     */
    public void eventually(Supplier<String> problem) {
        long deadline = System.nanoTime() + SETTLE_LIMIT_MS * 1_000_000;
        while (true) {
            world.doWaitingChores();
            String last;
            try {
                last = problem.get();
            } catch (IllegalArgumentException | IllegalStateException misuse) {
                throw misuse;
            } catch (RuntimeException notYet) {
                last = "讀取時出錯：" + notYet;
            }
            if (last == null) {
                return;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError(last + "；畫面上的文字是 " + screenLines());
            }
            page().waitForTimeout(POLL_INTERVAL_MS);
        }
    }

    /** 畫面上看得到的每一行字；分頁已經關掉或正在換頁時是空的。 */
    public List<String> screenLines() {
        try {
            return ShellFrame.linesOf(page().locator("body").innerText());
        } catch (PlaywrightException unreadable) {
            return List.of();
        }
    }

    /** 等某個條件成立，最多 5 秒；到了沒成立也不丟錯，由呼叫端自己再判斷。 */
    public void waitAtMost(Page page, Supplier<Boolean> condition) {
        try {
            page.waitForCondition(() -> {
                world.doWaitingChores();
                try {
                    return condition.get();
                } catch (PlaywrightException navigating) {
                    return false;
                }
            }, new Page.WaitForConditionOptions().setTimeout(SETTLE_LIMIT_MS));
        } catch (PlaywrightException timedOut) {
            // 由之後的結果句說明畫面實際的樣子。
        }
    }

    private static boolean isLiveStream(String url) {
        String path = URI.create(url).getPath();
        return "/guest/stream".equals(path) || "/staff/stream".equals(path);
    }

    private static Locator pressableIn(Locator layer, String name) {
        Locator exact = pressables(layer, exactName(name)).filter(new Locator.FilterOptions().setVisible(true));
        if (exact.count() > 0) {
            return exact.first();
        }
        Locator startsWith = pressables(layer, namePrefix(name)).filter(new Locator.FilterOptions().setVisible(true));
        String wanted = ShellFrame.normalizeSpaces(name);
        for (int i = 0; i < startsWith.count(); i++) {
            Locator candidate = startsWith.nth(i);
            List<String> lines = ShellFrame.linesOf(candidate.innerText());
            if (!lines.isEmpty() && lines.get(0).equals(wanted)) {
                return candidate;
            }
        }
        return labelInsidePressable(layer, name);
    }

    /**
     * 整張卡是一個連結、名稱從卡上第一行字來，畫面上要按的字（首頁進度卡的「查看」、「查看紀錄」）只是卡裡的一行：
     * 名稱裡含這幾個字的可按元素中，找字剛好是它的那一行，點在那一行上。
     */
    private static Locator labelInsidePressable(Locator layer, String name) {
        Locator holders = pressables(layer, Pattern.compile(BrowserRegex.literal(name.strip())))
                .filter(new Locator.FilterOptions().setVisible(true));
        for (int i = 0; i < holders.count(); i++) {
            Locator label = holders.nth(i).getByText(name.strip(), new Locator.GetByTextOptions().setExact(true))
                    .filter(new Locator.FilterOptions().setVisible(true));
            if (label.count() > 0) {
                return label.first();
            }
        }
        return null;
    }

    private static Locator pressables(Locator layer, Pattern name) {
        Locator any = null;
        for (AriaRole role : PRESSABLE_ROLES) {
            Locator byRole = layer.getByRole(role, new Locator.GetByRoleOptions().setName(name));
            any = any == null ? byRole : any.or(byRole);
        }
        return any;
    }

    private static Pattern exactName(String name) {
        return Pattern.compile("^" + BrowserRegex.literal(name.strip()) + "$");
    }

    private static Pattern namePrefix(String name) {
        return Pattern.compile("^" + BrowserRegex.literal(name.strip()) + "\\s");
    }

    private static void waitForFrames(Page page) {
        try {
            page.evaluate("() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))");
        } catch (PlaywrightException navigating) {
            page.waitForLoadState();
        }
    }
}
