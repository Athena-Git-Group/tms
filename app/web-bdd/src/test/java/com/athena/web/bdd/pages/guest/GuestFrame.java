package com.athena.web.bdd.pages.guest;

import com.athena.web.bdd.pages.PageObject;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.BrowserRegex;
import com.athena.web.bdd.support.GuestTerms;
import com.athena.web.bdd.support.JsResult;
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

public class GuestFrame extends PageObject {

    public static final long SETTLE_LIMIT_MS = 5_000;

    public static final String LANGUAGE_KEY = "tms.guest.language";

    private static final String DEFAULT_LANGUAGE = "zh-TW";

    private static final long POLL_INTERVAL_MS = 100;

    private static final List<AriaRole> PRESSABLE_ROLES = List.of(
            AriaRole.BUTTON, AriaRole.LINK, AriaRole.MENUITEM, AriaRole.MENUITEMRADIO, AriaRole.MENUITEMCHECKBOX,
            AriaRole.OPTION, AriaRole.RADIO, AriaRole.TAB, AriaRole.ROW);

    private static final List<AriaRole> LAYERS = List.of(
            AriaRole.ALERTDIALOG, AriaRole.DIALOG, AriaRole.MENU, AriaRole.LISTBOX, AriaRole.ALERT);

    public GuestFrame(WebWorld world) {
        super(world);
    }

    public String path() {
        String path = URI.create(page().url()).getPath();
        return path == null ? "" : path;
    }

    public boolean loading() {
        return page().locator("[aria-busy='true']").count() > 0;
    }

    public Locator titleBar() {
        return page().getByRole(AriaRole.BANNER);
    }

    public String pageTitle() {
        Locator heading = titleBar().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(1));
        return heading.count() == 0 ? "" : ShellFrame.normalizeSpaces(heading.first().innerText());
    }

    public List<String> titleBarLines() {
        Locator bar = titleBar();
        return bar.count() == 0 ? List.of() : ShellFrame.linesOf(bar.first().innerText());
    }

    public Locator backControl(Locator scope) {
        Pattern name = exactName(GuestTerms.in(language(), "返回"));
        return scope.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name))
                .or(scope.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(name)))
                .filter(new Locator.FilterOptions().setVisible(true));
    }

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

    public Locator titleBarFirstPressable() {
        return pressables(titleBar().first(), Pattern.compile(".*")).first();
    }

    public boolean titleBarStuckToTop() {
        Locator bar = titleBar().first();
        Object position = bar.evaluate("el => getComputedStyle(el).position");
        Object top = bar.evaluate("el => el.getBoundingClientRect().top");
        return ("sticky".equals(position) || "fixed".equals(position)) && Math.abs(((Number) top).doubleValue()) < 1;
    }

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

    public Locator notices() {
        return page().getByRole(AriaRole.ALERT).filter(new Locator.FilterOptions().setVisible(true));
    }

    public Locator toasts() {
        return page().getByRole(AriaRole.STATUS).filter(new Locator.FilterOptions().setVisible(true))
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("\\S")));
    }

    public List<String> noticeLines(Locator notice) {
        return ShellFrame.linesOf(notice.innerText());
    }

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
        return JsResult.strings(covered);
    }

    public static List<String> tagsIn(Locator container, String language) {
        return GuestTerms.TAGS.stream()
                .filter(tag -> container.getByText(GuestTerms.in(language, tag), new Locator.GetByTextOptions().setExact(true))
                        .filter(new Locator.FilterOptions().setVisible(true)).count() > 0)
                .toList();
    }

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

    public List<String> screenLines() {
        try {
            return ShellFrame.linesOf(page().locator("body").innerText());
        } catch (PlaywrightException unreadable) {
            return List.of();
        }
    }

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
