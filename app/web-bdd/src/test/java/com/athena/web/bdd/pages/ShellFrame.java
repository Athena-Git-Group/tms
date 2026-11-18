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

/** 登入後每一頁共用的外框：側欄、工具列、提醒列、對話框與畫面底部的浮條。 */
public class ShellFrame extends PageObject {

    public ShellFrame(WebWorld world) {
        super(world);
    }

    /** 看板資料最久等這麼久；後端與開發伺服器都在本機，正常幾百毫秒內就回來。 */
    private static final double BOARD_LOAD_TIMEOUT_MS = 10_000;

    /** 規格裡的頁名換成櫃檯 Web 的路徑：總覽看板、待派單頁。 */
    public static String pathOf(String pageName) {
        return switch (pageName.strip()) {
            case "總覽看板" -> WebApp.BOARD;
            case "待派單頁" -> WebApp.TRIAGE;
            default -> throw new IllegalArgumentException("頁只接受「總覽看板」或「待派單頁」，收到「" + pageName + "」");
        };
    }

    /** 這一頁在工具列上的頁標題。 */
    public static String titleOf(String pageName) {
        return WebApp.BOARD.equals(pathOf(pageName)) ? "總覽看板" : "待派單";
    }

    /** 規格的說法裡，沒有自己選過側欄時，總覽看板的側欄收起、待派單頁的打開。 */
    public static boolean sidebarOpenByDefault(String pageName) {
        return WebApp.TRIAGE.equals(pathOf(pageName));
    }

    /** 不經過登入頁，以這位員工的登入回應放進目前的分頁，再開這一頁並等它載入完成。 */
    public void loginAndOpen(CreatedStaff staff, String pageName) {
        world.session().loginAs(staff);
        openAndWait(pageName);
    }

    /**
     * 開這一頁的網址，等到看板資料這一次讀取結束、頁標題出現才算載入完成。
     * 兩頁的清單都來自同一份看板資料，所以等的是同一支請求；請求要在開網址之前就開始等，才不會錯過。
     * 只等回應的話，案例先讓看板資料斷線時永遠等不到，因為斷線的請求只會失敗、不會有回應。
     */
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

    /** 等到工具列上的頁標題是這一頁。 */
    public void waitForPage(String pageName) {
        page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                .setName(titleOf(pageName)).setExact(true).setLevel(1)).waitFor();
    }

    /** 工具列上的「打開側欄」，側欄收起時才看得到。名稱跟著介面語言換，四種語言的寫法都認得。 */
    public Locator openSidebarButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiWords.anyLanguage("打開側欄")));
    }

    /** 側欄上的「收合側欄」，側欄打開時才看得到。名稱跟著介面語言換，四種語言的寫法都認得。 */
    public Locator collapseSidebarButton() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(UiWords.anyLanguage("收合側欄")));
    }

    public boolean sidebarOpen() {
        return collapseSidebarButton().isVisible();
    }

    /** 等到側欄是指定的樣子。 */
    public void waitForSidebar(boolean open) {
        (open ? collapseSidebarButton() : openSidebarButton()).waitFor();
    }

    /** 按工具列的「打開側欄」，等側欄展開。 */
    public void openSidebar() {
        press(openSidebarButton());
        waitForSidebar(true);
    }

    /** 按側欄上的「收合側欄」，等側欄收起。 */
    public void collapseSidebar() {
        press(collapseSidebarButton());
        waitForSidebar(false);
    }

    /** 側欄本身：名稱是「櫃檯功能」（四種介面語言的寫法都認得）的導覽區塊，收起時看不到。 */
    public Locator sidebar() {
        return page().getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions().setName(UiWords.anyLanguage("櫃檯功能")));
    }

    /** 工具列：放著頁標題的那一條，也就是頁標題往上最近的 header、工具列或 banner 區塊。 */
    public Locator toolbar() {
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1))
                .locator("xpath=ancestor::*[self::header or @role='toolbar' or @role='banner'][1]");
    }

    /**
     * 等版面重排完：連續兩個畫面更新之間，整份文件的寬高沒有再變，而且沒有正在跑的轉場或動畫。
     * 側欄展開收起、視窗改尺寸之後，看板的欄寬是跟著轉場一路變的，轉場跑完才是最後的樣子。
     * 不會結束的動畫（例如載入中的閃動）不算，否則永遠等不到。
     */
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

    /** 從側欄換到另一頁：側欄收起時先打開，再點那一項，等那一頁出現。 */
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

    /** 這個分頁裡有沒有登入狀態。還沒開過分頁、或分頁不在櫃檯 Web 底下時當成沒有。 */
    public boolean loggedIn() {
        if (!world.hasPage() || !page().url().startsWith(WebApp.baseUrl() + "/")) {
            return false;
        }
        return page().evaluate("key => sessionStorage.getItem(key)", SessionInjector.SESSION_KEY) != null;
    }

    /** 觸控的瀏覽器設定用點觸，其餘用滑鼠點一下。 */
    public void press(Locator target) {
        if (world.profile().touch()) {
            target.tap();
        } else {
            target.click();
        }
    }

    /** 按一下之後最久等這麼久讓引發的請求結束；被測試刻意扣住的請求不會結束，等滿就往下走，交給後面的斷言。 */
    private static final double SETTLE_TIMEOUT_MS = 5_000;

    /** 按了之後等它引發的請求與畫面變化結束。點了沒有反應時只多等兩個畫面更新。 */
    public void pressAndSettle(Locator target) {
        settleAfter(() -> press(target));
    }

    /**
     * 做完這個動作後，等這段期間目前分頁發出的請求都結束（成功、失敗或斷線都算），再等兩個畫面更新讓畫面畫完。
     * 即時訊號的事件串流是一直開著的連線，不算在內。
     */
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
                // 例如看板資料被案例刻意扣住：畫面就停在等待中的樣子，由後面的斷言判斷。
            }
            waitForFrames(current);
        } finally {
            current.offRequest(started);
            current.offRequestFinished(ended);
            current.offRequestFailed(ended);
        }
    }

    /**
     * 最上面那一層裡名稱相符、看得到、可以按的東西：按鈕、連結、可點的列、選單項目或分頁標籤。
     * 層的順序是提示對話框、對話框（當場回答面板、帳號面板）、詳情面板，最後才是整頁；
     * 每一層先找名稱完全相同的，再找名稱以它開頭、後面接空白的（例如側欄的「待派單」後面接著徽章數字）。
     * 哪一層都找不到時回整頁的找法，讓 Playwright 自己等到它出現或逾時報錯。
     */
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

    /** 等到資料載入完成、畫面上沒有標成載入中（{@code aria-busy="true"}）的骨架。 */
    public void waitUntilLoaded() {
        page().locator("[aria-busy='true']").first()
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));
    }

    /** 目前的分頁是不是停在這一頁（總覽看板、待派單頁）。 */
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

    /** 讓瀏覽器畫兩個畫面更新；分頁正在換頁時改等新頁面載入。 */
    private static void waitForFrames(Page current) {
        try {
            current.evaluate("() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))");
        } catch (PlaywrightException navigating) {
            current.waitForLoadState();
        }
    }

    /**
     * 派單後畫面底部的浮條：「已派給{部門}（{房號}）」加一顆「復原」。
     * 浮條的角色是 {@code status}，但控制列的提示文字與提醒列也是，所以用上面有「復原」這一點分辨。
     */
    public Locator assignToast() {
        return page().getByRole(AriaRole.STATUS).filter(new Locator.FilterOptions()
                .setHas(page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("復原").setExact(true))));
    }


    /**
     * 側欄上的這一項：名稱以它開頭的連結或按鈕。「待派單」的名稱後面可能接著徽章的數字，
     * 所以名稱只要求以它開頭、後面接空白、數字或結尾，「待派單」不會連「待派單頁」一起找到。
     */
    public Locator sidebarItem(String name) {
        Pattern pattern = Pattern.compile("^" + BrowserRegex.literal(name.strip()) + "(\\s|\\d|$)");
        return sidebar().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(pattern))
                .or(sidebar().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(pattern)));
    }

    /** 側欄最下面的帳號卡：側欄裡寫著「{角色} · {員工編號}」的那顆按鈕。 */
    public Locator accountCard() {
        return sidebar().getByRole(AriaRole.BUTTON)
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("\\S+ · \\S+")));
    }

    /** 工具列上的這一個東西：名稱裡有這幾個字的按鈕、連結或輸入框，或是提示字、文字剛好是它的東西。 */
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

    /** 內容區：側欄以外、放工具列與這一頁內容的主要區塊。 */
    public Locator mainContent() {
        return page().getByRole(AriaRole.MAIN);
    }

    /**
     * 現在最上面的那個畫面：開著對話框（當場回答面板、帳號面板）時是最後開的那一個，
     * 否則開著詳情面板時是詳情面板，都沒有時是整頁。
     */
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

    /** 工具列上的頁標題。 */
    public Locator pageTitle() {
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1));
    }

    /** 工具列上看得到的文字，一行一筆：頁標題一行、頁副標一行。 */
    public List<String> toolbarLines() {
        return linesOf(toolbar().first().innerText());
    }

    /**
     * 整個畫面看得到的文字，一行一筆。
     * 取的是瀏覽器實際畫出來的文字：藏起來的元素（不顯示或不可見）不算，捲出視窗外的部分算。
     */
    public List<String> visibleTextLines() {
        return linesOf(page().locator("body").innerText());
    }

    /** 畫面上看得到這段字：照字面出現在同一行裡，不是拼湊相鄰兩行得來的。 */
    public boolean showsText(String text) {
        String wanted = normalizeSpaces(text);
        return visibleTextLines().stream().anyMatch(line -> line.contains(wanted));
    }

    /**
     * 畫面上任何地方有沒有這段字，跨行拼起來也算。
     * 比 {@link #showsText(String)} 寬鬆，給「畫面上沒有」的檢查用：寧可多抓到，不要漏掉一段被拆成兩行的字。
     */
    public boolean hasTextAnywhere(String text) {
        return String.join(" ", visibleTextLines()).contains(normalizeSpaces(text));
    }

    /** 把一段畫面文字切成行，每行的連續空白併成一個、去掉頭尾空白，空行不要。 */
    public static List<String> linesOf(String innerText) {
        return innerText.lines().map(ShellFrame::normalizeSpaces).filter(line -> !line.isEmpty()).toList();
    }

    /** 不換行空白當成一般空白，連續空白併成一個，去掉頭尾空白。 */
    public static String normalizeSpaces(String text) {
        return text.replace(' ', ' ').replaceAll("\\s+", " ").strip();
    }

    /** 畫面上看得到的對話框與提示對話框，依在頁面上的先後。 */
    public Locator shownDialogs() {
        return page().getByRole(AriaRole.ALERTDIALOG).or(page().getByRole(AriaRole.DIALOG))
                .filter(new Locator.FilterOptions().setVisible(true));
    }

    /** 這個分頁現在存著的登入回應；分頁不在櫃檯 Web 底下或沒有登入狀態時是 {@code null}。 */
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

    /** 登入回應的角色在畫面上的中文，帳號卡第二行用。 */
    public static String roleLabel(String roleCode) {
        return switch (roleCode) {
            case "frontdesk" -> "櫃檯";
            case "manager" -> "主管";
            case "duty_manager" -> "值班主管";
            default -> throw new IllegalArgumentException("櫃檯畫面只給櫃檯、主管、值班主管登入，收到角色「" + roleCode + "」");
        };
    }

    /** 即時連線異常時工具列下方那條提醒列的標題。 */
    public static final String CONNECTION_ALERT_TITLE = "即時連線異常";

    /**
     * 工具列下方的提醒列：角色是 {@code status}、裡面寫著這個標題的那一塊。
     * 派單浮條與控制列的提示也是 {@code status}，用標題分辨。
     */
    public Locator alertBar(String title) {
        return page().getByRole(AriaRole.STATUS).filter(new Locator.FilterOptions()
                .setHas(page().getByText(title.strip(), new Page.GetByTextOptions().setExact(true))));
    }

    /** 即時連線異常的提醒列。 */
    public Locator connectionAlertBar() {
        return alertBar(CONNECTION_ALERT_TITLE);
    }

    /**
     * 側欄這一項右側徽章上的數字；側欄上找不到這一項、或這一項沒有帶數字時是 {@code null}。
     * 徽章的數字接在項目名稱後面，所以取名稱之後的那一段數字。側欄收起時讀不到，呼叫端要先打開。
     */
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
