package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.BrowserRegex;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.SessionInjector;
import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class ShellFrame extends PageObject {

    public ShellFrame(WebWorld world) {
        super(world);
    }

    private static final double BOARD_LOAD_TIMEOUT_MS = 10_000;

    public static String pathOf(String pageName) {
        return switch (pageName.strip()) {
            case "總覽看板" -> WebApp.BOARD;
            case "待派單頁" -> WebApp.TRIAGE;
            default -> throw new IllegalArgumentException("頁只接受「總覽看板」或「待派單頁」，收到「" + pageName + "」");
        };
    }

    public static String titleOf(String pageName) {
        return WebApp.BOARD.equals(pathOf(pageName)) ? "總覽看板" : "待派單";
    }

    public static boolean sidebarOpenByDefault(String pageName) {
        return WebApp.TRIAGE.equals(pathOf(pageName));
    }

    public void loginAndOpen(CreatedStaff staff, String pageName) {
        world.session().loginAs(staff);
        openAndWait(pageName);
    }

    public void openAndWait(String pageName) {
        Page current = page();
        boolean[] ended = {false};
        Consumer<Request> onEnded = request -> {
            if (isBoardRequest(request)) {
                ended[0] = true;
            }
        };
        current.onRequestFinished(onEnded);
        current.onRequestFailed(onEnded);
        try {
            world.open(pathOf(pageName));
            current.waitForCondition(() -> ended[0],
                    new Page.WaitForConditionOptions().setTimeout(BOARD_LOAD_TIMEOUT_MS));
        } finally {
            current.offRequestFinished(onEnded);
            current.offRequestFailed(onEnded);
        }
        waitForPage(pageName);
    }

    private static boolean isBoardRequest(Request request) {
        Network.Api board = Network.DESK_BOARD;
        return board.method().equals(request.method()) && request.url().split("[?#]")[0].endsWith(board.path());
    }

    public void waitForPage(String pageName) {
        page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                .setName(titleOf(pageName)).setExact(true).setLevel(1)).waitFor();
    }

    public Locator openSidebarButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiWords.anyLanguage("打開側欄")));
    }

    public Locator collapseSidebarButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiWords.anyLanguage("收合側欄")));
    }

    public boolean sidebarOpen() {
        return collapseSidebarButton().isVisible();
    }

    public void waitForSidebar(boolean open) {
        (open ? collapseSidebarButton() : openSidebarButton()).waitFor();
    }

    public void openSidebar() {
        press(openSidebarButton());
        waitForSidebar(true);
    }

    public void collapseSidebar() {
        press(collapseSidebarButton());
        waitForSidebar(false);
    }

    public Locator sidebar() {
        return page().getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions().setName(UiWords.anyLanguage("櫃檯功能")));
    }

    public Locator toolbar() {
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1))
                .locator("xpath=ancestor::*[self::header or @role='toolbar' or @role='banner'][1]");
    }

    public void waitForLayoutSettled() {
        page().waitForFunction("""
                () => new Promise(resolve => {
                  const size = () => {
                    const root = document.documentElement;
                    return [root.scrollWidth, root.scrollHeight, innerWidth, innerHeight].join('x');
                  };
                  const running = () => document.getAnimations().some(a => a.playState === 'running'
                    && Number.isFinite(a.effect?.getComputedTiming().endTime));
                  const first = size();
                  requestAnimationFrame(() => requestAnimationFrame(() =>
                    resolve(!running() && size() === first)));
                })
                """, null, new Page.WaitForFunctionOptions().setTimeout(5_000));
    }

    public void goToFromSidebar(String pageName) {
        if (!sidebarOpen()) {
            openSidebar();
        }
        Locator item = sidebar()
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions()
                        .setName(Pattern.compile("^" + BrowserRegex.literal(titleOf(pageName)))));
        press(item);
        page().waitForURL(url -> url.split("[?#]")[0].endsWith(pathOf(pageName)));
        waitForPage(pageName);
    }

    public boolean loggedIn() {
        if (!world.hasPage() || !page().url().startsWith(WebApp.baseUrl() + "/")) {
            return false;
        }
        return page().evaluate("key => sessionStorage.getItem(key)", SessionInjector.SESSION_KEY) != null;
    }

    public void press(Locator target) {
        if (world.profile().touch()) {
            target.tap();
        } else {
            target.click();
        }
    }

    private static final double SETTLE_TIMEOUT_MS = 5_000;

    public void pressAndSettle(Locator target) {
        settleAfter(() -> press(target));
    }

    public void settleAfter(Runnable action) {
        Page current = page();
        Set<Request> inFlight = new HashSet<>();
        Consumer<Request> started = request -> {
            if (!request.url().split("[?#]")[0].endsWith(Network.STAFF_STREAM.path())) {
                inFlight.add(request);
            }
        };
        Consumer<Request> ended = inFlight::remove;
        current.onRequest(started);
        current.onRequestFinished(ended);
        current.onRequestFailed(ended);
        try {
            action.run();
            waitForFrames(current);
            try {
                current.waitForCondition(inFlight::isEmpty,
                        new Page.WaitForConditionOptions().setTimeout(SETTLE_TIMEOUT_MS));
            } catch (TimeoutError stillPending) {
            }
            waitForFrames(current);
        } finally {
            current.offRequest(started);
            current.offRequestFinished(ended);
            current.offRequestFailed(ended);
        }
    }

    public Locator topmostPressable(String name) {
        List<Locator> layers = List.of(
                page().getByRole(AriaRole.ALERTDIALOG),
                page().getByRole(AriaRole.DIALOG),
                page().getByRole(AriaRole.COMPLEMENTARY));
        for (Locator layer : layers) {
            Locator shown = layer.filter(new Locator.FilterOptions().setVisible(true));
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

    public void waitUntilLoaded() {
        page().locator("[aria-busy='true']").first()
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));
    }

    public boolean onPage(String pageName) {
        return world.hasPage() && page().url().split("[?#]")[0].endsWith(pathOf(pageName));
    }

    private static Locator pressableIn(Locator layer, String name) {
        for (Pattern pattern : List.of(exactName(name), namePrefix(name))) {
            Locator found = pressables(layer, pattern).filter(new Locator.FilterOptions().setVisible(true));
            if (found.count() > 0) {
                return found.first();
            }
        }
        return null;
    }

    private static Locator pressables(Locator layer, Pattern name) {
        Locator any = null;
        for (AriaRole role : List.of(AriaRole.BUTTON, AriaRole.LINK, AriaRole.ROW, AriaRole.MENUITEM, AriaRole.TAB)) {
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

    private static void waitForFrames(Page current) {
        try {
            current.evaluate("() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))");
        } catch (PlaywrightException navigating) {
            current.waitForLoadState();
        }
    }

    public Locator assignToast() {
        return page().getByRole(AriaRole.STATUS).filter(new Locator.FilterOptions()
                .setHas(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("復原").setExact(true))));
    }

    public Locator sidebarItem(String name) {
        Pattern pattern = Pattern.compile("^" + BrowserRegex.literal(name.strip()) + "(\\s|\\d|$)");
        return sidebar().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(pattern))
                .or(sidebar().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(pattern)));
    }

    public Locator accountCard() {
        return sidebar().getByRole(AriaRole.BUTTON)
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("\\S+ · \\S+")));
    }

    public Locator toolbarControl(String name) {
        Locator bar = toolbar();
        Pattern contains = Pattern.compile(BrowserRegex.literal(name.strip()));
        Locator found = null;
        for (AriaRole role : List.of(AriaRole.BUTTON, AriaRole.LINK, AriaRole.TEXTBOX, AriaRole.SEARCHBOX)) {
            Locator byRole = bar.getByRole(role, new Locator.GetByRoleOptions().setName(contains));
            found = found == null ? byRole : found.or(byRole);
        }
        return found
                .or(bar.getByPlaceholder(name.strip(), new Locator.GetByPlaceholderOptions().setExact(true)))
                .or(bar.getByText(name.strip(), new Locator.GetByTextOptions().setExact(true)));
    }

    public Locator mainContent() {
        return page().getByRole(AriaRole.MAIN);
    }

    public Locator currentScreen() {
        Locator dialogs = page().getByRole(AriaRole.ALERTDIALOG).or(page().getByRole(AriaRole.DIALOG))
                .filter(new Locator.FilterOptions().setVisible(true));
        if (dialogs.count() > 0) {
            return dialogs.last();
        }
        Locator detail = new DetailPanel(world).panel().filter(new Locator.FilterOptions().setVisible(true));
        if (detail.count() > 0) {
            return detail.first();
        }
        return page().locator("body");
    }

    public Locator pageTitle() {
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1));
    }

    public List<String> toolbarLines() {
        return linesOf(toolbar().first().innerText());
    }

    public List<String> visibleTextLines() {
        return linesOf(page().locator("body").innerText());
    }

    public boolean showsText(String text) {
        String wanted = normalizeSpaces(text);
        return visibleTextLines().stream().anyMatch(line -> line.contains(wanted));
    }

    public boolean hasTextAnywhere(String text) {
        return String.join(" ", visibleTextLines()).contains(normalizeSpaces(text));
    }

    public static List<String> linesOf(String innerText) {
        return innerText.lines().map(ShellFrame::normalizeSpaces).filter(line -> !line.isEmpty()).toList();
    }

    public static String normalizeSpaces(String text) {
        return text.replace(' ', ' ').replaceAll("\\s+", " ").strip();
    }

    public Locator shownDialogs() {
        return page().getByRole(AriaRole.ALERTDIALOG).or(page().getByRole(AriaRole.DIALOG))
                .filter(new Locator.FilterOptions().setVisible(true));
    }

    public com.fasterxml.jackson.databind.JsonNode storedLoginResponse() {
        if (!loggedIn()) {
            return null;
        }
        Object stored = page().evaluate("key => sessionStorage.getItem(key)", SessionInjector.SESSION_KEY);
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) stored);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("分頁裡的登入狀態不是 JSON：" + stored, e);
        }
    }

    public static String roleLabel(String roleCode) {
        return switch (roleCode) {
            case "frontdesk" -> "櫃檯";
            case "manager" -> "主管";
            case "duty_manager" -> "值班主管";
            default -> throw new IllegalArgumentException("櫃檯畫面只給櫃檯、主管、值班主管登入，收到角色「" + roleCode + "」");
        };
    }

    public static final String CONNECTION_ALERT_TITLE = "即時連線異常";

    public Locator alertBar(String title) {
        return page().getByRole(AriaRole.STATUS).filter(new Locator.FilterOptions()
                .setHas(page().getByText(title.strip(), new Page.GetByTextOptions().setExact(true))));
    }

    public Locator connectionAlertBar() {
        return alertBar(CONNECTION_ALERT_TITLE);
    }

    public Integer sidebarBadge(String name) {
        Locator item = sidebarItem(name);
        if (item.count() != 1 || !item.isVisible()) {
            return null;
        }
        String rest = normalizeSpaces(item.innerText()).replaceFirst("^" + BrowserRegex.literal(name.strip()), "").strip();
        java.util.regex.Matcher number = Pattern.compile("^(\\d+)").matcher(rest);
        return number.find() ? Integer.valueOf(number.group(1)) : null;
    }
}
