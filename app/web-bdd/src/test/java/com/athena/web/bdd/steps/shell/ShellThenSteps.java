package com.athena.web.bdd.steps.shell;

import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.SessionInjector;
import com.athena.web.bdd.support.WebWorld;
import com.athena.web.bdd.support.WhenStart;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.JSHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.ViewportSize;
import io.cucumber.java.zh_tw.那麼;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「共用外框」模組的 Then 句型：側欄與工具列的內容、尚未開放的入口點了沒有反應，
 * 以及兩種視窗尺寸與兩種輸入方式下的版面。
 *
 * <p>版面的句子以 Playwright 量到的位置與大小判斷，量的是使用者看得到的元素（欄頭、側欄、工具列），
 * 不比對色碼或 CSS 類別。畫面更新要時間，所以每一句都在 5 秒內反覆檢查，最後一次還不成立才算失敗。
 */
public class ShellThenSteps {

    /** 畫面斷言最多等這麼久，是共通實作約定的輪詢上限。 */
    private static final double ASSERT_TIMEOUT_MS = 5_000;

    /** 瀏覽器的版面座標有小數，捨入後可能差到 1px，量位置時容許這麼多誤差。 */
    private static final double TOLERANCE_PX = 1;

    /** 外框的最小寬度（`ui/ui-plan.md`）；視窗比它窄時外框停在這個寬度，多出來的靠整頁捲軸。 */
    private static final double SHELL_MIN_WIDTH_PX = 1180;

    /** 側欄的寬度；側欄收起時，看板多出來的寬度就是這麼多。 */
    private static final double SIDEBAR_WIDTH_PX = 300;

    /** 看板第一欄與最後一欄離內容區左右邊界的固定邊距。 */
    private static final double BOARD_SIDE_MARGIN_PX = 16;

    /** 手指要按得準，可以按的東西最矮只能到這個高度。 */
    private static final double MIN_PRESSABLE_HEIGHT_PX = 44;

    /** 只有這兩種角色的側欄上有「設定」。 */
    private static final Set<String> ROLES_WITH_SETTINGS = Set.of("manager", "duty_manager");

    /** 這一版畫出來但點了沒有反應的入口。 */
    private static final List<String> NOT_YET_OPEN_ENTRIES = List.of("通知板", "遺失物", "設定", "代開單");

    /** 班次的說法。側欄副標只寫飯店名稱，出現這些字就代表帶了班次。 */
    private static final Pattern SHIFT_NAME = Pattern.compile("(早|中|晚|夜|大夜)班");

    private static final Pattern MORE_STATES = Pattern.compile("^還有 (\\d+) 個狀態$");

    /**
     * 畫面上算「可以按的東西」的元素：原生的按鈕、連結、輸入框，自己宣告成按鈕、分頁標籤、
     * 選單項目等角色的元素，以及可以用鍵盤移到的元素。
     */
    private static final String PRESSABLE_SELECTOR = String.join(", ",
            "button", "a[href]", "input:not([type=hidden])", "select", "textarea", "summary",
            "[role=button]", "[role=link]", "[role=tab]", "[role=menuitem]", "[role=option]",
            "[role=checkbox]", "[role=radio]", "[role=switch]", "[tabindex]:not([tabindex='-1'])");

    /** 頁面裡共用的兩個判斷：元素看不看得到、可以按的東西叫什麼名字。 */
    private static final String PAGE_HELPERS = """
            const shown = el => {
              const r = el.getBoundingClientRect();
              if (r.width <= 0 || r.height <= 0) return false;
              return el.checkVisibility ? el.checkVisibility({ opacityProperty: true, visibilityProperty: true })
                : getComputedStyle(el).visibility !== 'hidden';
            };
            const nameOf = el => (el.getAttribute('aria-label') || el.innerText || el.value
              || el.getAttribute('placeholder') || el.tagName.toLowerCase()).replace(/\\s+/g, ' ').trim().slice(0, 60);
            """;

    private final WebWorld world;

    private final ShellFrame shell;

    private final BoardPage board;

    private final LayoutBeforeResize layoutBeforeResize;

    private final WhenStart whenStart;

    public ShellThenSteps(WebWorld world, LayoutBeforeResize layoutBeforeResize, WhenStart whenStart) {
        this.world = world;
        this.shell = new ShellFrame(world);
        this.board = new BoardPage(world);
        this.layoutBeforeResize = layoutBeforeResize;
        this.whenStart = whenStart;
    }

    /**
     * 側欄各項的文字與上下順序都要相符，副標就是登入回應的飯店名稱、後面不帶班次名。
     * 上下順序用每一項在畫面上的垂直中心比，後一項一定要在前一項的下面。
     */
    @那麼("側欄由上到下是標題 {string}、副標 {string}、功能 {string}、{string}、{string}、{string} 與帳號卡")
    public void sidebarTopToBottom(String title, String subtitle, String one, String two, String three, String four) {
        String hotelName = loginField("hotelName");
        if (!subtitle.equals(hotelName)) {
            throw new AssertionError("側欄副標應該是登入回應的飯店名稱「" + hotelName + "」，規格寫的是「" + subtitle + "」");
        }
        Locator sidebar = shell.sidebar();
        Map<String, Locator> items = new LinkedHashMap<>();
        items.put("標題「" + title + "」", sidebar.getByText(title, new Locator.GetByTextOptions().setExact(true)));
        items.put("副標「" + subtitle + "」", sidebar.getByText(subtitle, new Locator.GetByTextOptions().setExact(true)));
        for (String feature : List.of(one, two, three, four)) {
            items.put("功能「" + feature + "」", shell.sidebarItem(feature));
        }
        items.put("帳號卡", shell.accountCard());
        eventually("側欄由上到下的順序不對", () -> {
            String previousName = null;
            double previousCenter = Double.NEGATIVE_INFINITY;
            for (Map.Entry<String, Locator> item : items.entrySet()) {
                Locator found = item.getValue();
                if (found.count() != 1 || !found.isVisible()) {
                    return "側欄上看不到" + item.getKey() + "（找到 " + found.count() + " 個）";
                }
                BoundingBox box = found.boundingBox();
                double center = box.y + box.height / 2;
                if (center <= previousCenter) {
                    return item.getKey() + "沒有在" + previousName + "的下面";
                }
                previousName = item.getKey();
                previousCenter = center;
            }
            Matcher shift = SHIFT_NAME.matcher(sidebar.innerText());
            return shift.find() ? "側欄上出現了班次名「" + shift.group() + "」" : null;
        });
    }

    /** 側欄那一項右側沒有徽章，連 0 也不顯示；後端此時也真的沒有待派單的單。 */
    @那麼("側欄 {string} 沒有徽章")
    public void sidebarItemHasNoBadge(String feature) {
        Locator item = shell.sidebarItem(feature);
        eventually("側欄「" + feature + "」不應該有徽章", () -> {
            if (item.count() != 1 || !item.isVisible()) {
                return "側欄上看不到「" + feature + "」";
            }
            String text = item.innerText().replaceAll("\\s+", " ").strip();
            return text.matches(".*\\d.*") ? "那一項顯示的是「" + text + "」，帶著數字" : null;
        });
        long triage = ((Number) world.backend().queryRows(
                "SELECT count(*) AS n FROM tasks WHERE tenant_id = ? AND state = 'triage'",
                world.defaults().tenantId()).get(0).get("n")).longValue();
        if (triage != 0) {
            throw new AssertionError("後端還有 " + triage + " 張待派單的單，側欄卻沒有徽章");
        }
    }

    /**
     * 「有」時側欄看得到那一項，「沒有」時側欄整個找不到。
     * 「設定」另外要與登入回應的角色一致：只有主管與值班主管有。
     */
    @那麼("^側欄上(有|沒有) \"([^\"]*)\"$")
    public void sidebarHasOrNot(String hasOrNot, String item) {
        boolean expected = "有".equals(hasOrNot);
        if ("設定".equals(item.strip())) {
            String role = loginField("role");
            boolean byRole = ROLES_WITH_SETTINGS.contains(role);
            if (byRole != expected) {
                throw new AssertionError("登入回應的角色是 " + role + "，側欄上「設定」應該"
                        + (byRole ? "有" : "沒有") + "，規格寫的是「" + hasOrNot + "」");
            }
        }
        Locator sidebar = shell.sidebar();
        Locator found = shell.sidebarItem(item);
        Locator anyText = sidebar.getByText(item.strip(), new Locator.GetByTextOptions().setExact(true));
        eventually("側欄上應該" + hasOrNot + "「" + item + "」", () -> {
            if (!sidebar.isVisible()) {
                return "側欄沒有打開";
            }
            if (expected) {
                return found.count() > 0 && found.first().isVisible() ? null : "側欄上看不到「" + item + "」";
            }
            return found.count() == 0 && anyText.count() == 0 ? null : "側欄上找得到「" + item + "」";
        });
    }

    /**
     * 點過之後那一項底下沒有多出子項目：它沒有標成展開，它和帳號卡之間也沒有任何可以按的東西。
     * 帳號卡在側欄最下面，展開的子項目一定會出現在這一段。
     */
    @那麼("側欄的 {string} 底下沒有展開任何項目")
    public void sidebarItemNotExpanded(String item) {
        Locator found = shell.sidebarItem(item);
        Locator accountCard = shell.accountCard();
        eventually("側欄「" + item + "」底下不應該展開項目", () -> {
            if (found.count() != 1 || !found.isVisible()) {
                return "側欄上看不到「" + item + "」";
            }
            if ("true".equals(found.getAttribute("aria-expanded"))) {
                return "「" + item + "」標成了展開";
            }
            BoundingBox itemBox = found.boundingBox();
            double top = itemBox.y + itemBox.height;
            double bottom = accountCard.count() > 0 && accountCard.first().isVisible()
                    ? accountCard.first().boundingBox().y
                    : Double.POSITIVE_INFINITY;
            List<String> between = new ArrayList<>();
            Locator pressables = shell.sidebar().locator(PRESSABLE_SELECTOR);
            for (int i = 0; i < pressables.count(); i++) {
                Locator candidate = pressables.nth(i);
                BoundingBox box = candidate.isVisible() ? candidate.boundingBox() : null;
                if (box != null && box.y >= top - TOLERANCE_PX && box.y + box.height <= bottom + TOLERANCE_PX) {
                    between.add(candidate.innerText().strip());
                }
            }
            return between.isEmpty() ? null : "「" + item + "」底下多出了 " + between;
        });
    }

    /**
     * 網址是該頁、工具列的頁標題相符；而且是在同一個外框裡換頁，從 When 開始到現在分頁沒有整頁重新載入過。
     * 整頁重新載入時側欄與工具列會重畫一次，使用者會看到整個畫面閃一下。
     */
    @那麼("畫面是 {string}，頁標題是 {string}")
    public void screenIsPageWithTitle(String pageName, String title) {
        waitForPathAndTitle(pageName, title);
        if (whenStart.pageReloadedSince()) {
            throw new AssertionError("換到「" + pageName + "」時整頁重新載入了，側欄與工具列不是同一個外框");
        }
    }

    @那麼("工具列沒有 {string}，有 {string}")
    public void toolbarLacksAndHas(String missing, String present) {
        Locator absent = shell.toolbarControl(missing).filter(new Locator.FilterOptions().setVisible(true));
        Locator shown = shell.toolbarControl(present).filter(new Locator.FilterOptions().setVisible(true));
        eventually("工具列應該沒有「" + missing + "」、有「" + present + "」", () -> {
            if (absent.count() > 0) {
                return "工具列上有「" + missing + "」";
            }
            return shown.count() > 0 ? null : "工具列上看不到「" + present + "」";
        });
    }

    @那麼("工具列有 {string} 與 {string}")
    public void toolbarHasBoth(String first, String second) {
        eventually("工具列應該有「" + first + "」與「" + second + "」", () -> {
            for (String name : List.of(first, second)) {
                if (shell.toolbarControl(name).filter(new Locator.FilterOptions().setVisible(true)).count() == 0) {
                    return "工具列上看不到「" + name + "」";
                }
            }
            return null;
        });
    }

    /**
     * 網址與頁標題不變；沒有新的分頁、面板、對話框或提示；尚未開放的入口也沒有變成選中、展開或按下的樣子。
     * 步驟這裡不知道被點的是哪一個入口，所以把這幾個入口都檢查一遍。
     */
    @那麼("畫面仍是 {string}，沒有開任何頁面、面板或對話框")
    public void stillOnPageWithNothingOpened(String pageName) {
        waitForPathAndTitle(pageName, ShellFrame.titleOf(pageName));
        Page page = world.page();
        int pages = page.context().pages().size();
        if (pages != 1) {
            throw new AssertionError("點了之後多開了分頁，現在有 " + pages + " 個");
        }
        if (whenStart.pageReloadedSince()) {
            throw new AssertionError("點了之後整頁重新載入了");
        }
        Locator opened = page.getByRole(AriaRole.DIALOG)
                .or(page.getByRole(AriaRole.ALERTDIALOG))
                .or(page.getByRole(AriaRole.ALERT))
                .or(new DetailPanel(world).panel())
                .filter(new Locator.FilterOptions().setVisible(true));
        if (opened.count() > 0) {
            throw new AssertionError("畫面上開了面板、對話框或提示：" + opened.first().innerText().strip());
        }
        for (String entry : NOT_YET_OPEN_ENTRIES) {
            Locator found = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(entry))
                    .or(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(entry)));
            for (int i = 0; i < found.count(); i++) {
                Locator candidate = found.nth(i);
                for (String state : List.of("aria-current", "aria-expanded", "aria-pressed", "aria-selected")) {
                    String value = candidate.getAttribute(state);
                    if (value != null && !"false".equals(value)) {
                        throw new AssertionError("「" + entry + "」點了之後變成 " + state + "=" + value + " 的樣子");
                    }
                }
            }
        }
    }

    /**
     * 看板本體現在的寬度要比側欄打開時多 300px。
     * 前面按「收合側欄」的是共用的按鈕句，沒有記下收合前的寬度，所以這裡先量現在的寬度，
     * 再打開側欄量一次、收回去。收回去是為了讓後面的步驟看到的仍是他自己收起的側欄。
     */
    @那麼("看板的可用寬度比側欄打開時多出側欄的寬度")
    public void boardGainedSidebarWidth() {
        shell.waitForLayoutSettled();
        double collapsed = board.boardBodyBox().width;
        shell.openSidebar();
        shell.waitForLayoutSettled();
        double opened = board.boardBodyBox().width;
        shell.collapseSidebar();
        shell.waitForLayoutSettled();
        double gained = collapsed - opened;
        if (Math.abs(gained - SIDEBAR_WIDTH_PX) > TOLERANCE_PX) {
            throw new AssertionError("側欄收起時看板寬 " + collapsed + "px、打開時寬 " + opened
                    + "px，多出 " + gained + "px，應該多出側欄的寬度 " + SIDEBAR_WIDTH_PX + "px");
        }
    }

    /**
     * 整份文件不超過視窗、沒有整頁捲軸；這個畫面（面板就是面板本身）四邊都在視窗內；
     * 頁面沒有縮放；畫面上每一顆可以按的東西至少 44px 高。
     */
    @那麼("這個畫面完整顯示在視窗內，沒有整頁的捲軸，也不需要縮放")
    public void screenFitsViewport() {
        shell.waitForLayoutSettled();
        Locator screen = shell.currentScreen();
        ViewportSize viewport = world.page().viewportSize();
        eventually("這個畫面應該完整顯示在視窗內", () -> {
            Map<String, Number> doc = documentSize();
            if (doc.get("scrollWidth").doubleValue() > doc.get("innerWidth").doubleValue()
                    || doc.get("scrollHeight").doubleValue() > doc.get("innerHeight").doubleValue()) {
                return "整份文件是 " + doc.get("scrollWidth") + " × " + doc.get("scrollHeight")
                        + "，超過視窗 " + doc.get("innerWidth") + " × " + doc.get("innerHeight") + "，會出現整頁捲軸";
            }
            if (Math.abs(doc.get("scale").doubleValue() - 1) > 0.001
                    || doc.get("innerWidth").intValue() != viewport.width) {
                return "頁面有縮放：縮放比例 " + doc.get("scale") + "，版面寬 " + doc.get("innerWidth")
                        + "，視窗寬 " + viewport.width;
            }
            BoundingBox box = screen.boundingBox();
            if (!inside(box, 0, 0, viewport.width, viewport.height)) {
                return "這個畫面有一邊超出視窗：" + describe(box);
            }
            @SuppressWarnings("unchecked")
            List<String> tooShort = (List<String>) screen.evaluate("root => {" + PAGE_HELPERS + """
                      return [...root.querySelectorAll(SELECTOR)].filter(shown)
                        .filter(el => el.getBoundingClientRect().height < MIN_HEIGHT - 0.5)
                        .map(el => nameOf(el) + '（高 ' + Math.round(el.getBoundingClientRect().height) + 'px）');
                    }
                    """.replace("SELECTOR", jsString(PRESSABLE_SELECTOR))
                    .replace("MIN_HEIGHT", String.valueOf(MIN_PRESSABLE_HEIGHT_PX)));
            return tooShort.isEmpty() ? null : "這些可以按的東西不到 44px 高：" + tooShort;
        });
    }

    /** 看板右緣有那顆按鈕，上面的數字等於現在在看板右邊看不到的欄數。 */
    @那麼("看板右緣有按鈕 {string}")
    public void boardRightEdgeHasButton(String buttonName) {
        Matcher matcher = MORE_STATES.matcher(buttonName.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("按鈕要寫成「還有 N 個狀態」，收到「" + buttonName + "」");
        }
        int number = Integer.parseInt(matcher.group(1));
        Locator button = world.page().getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(buttonName.strip()).setExact(true));
        eventually("看板右緣應該有按鈕「" + buttonName + "」", () -> {
            String atEdge = buttonAtRightEdge(button, buttonName);
            if (atEdge != null) {
                return atEdge;
            }
            int hidden = columnsHiddenOnTheRight();
            return hidden == number ? null : "看板右邊看不到的欄有 " + hidden + " 個，按鈕上寫的是 " + number;
        });
    }

    @那麼("看板右緣沒有「還有 N 個狀態」的按鈕")
    public void boardRightEdgeHasNoMoreStatesButton() {
        Locator visible = board.moreStatesButtons().filter(new Locator.FilterOptions().setVisible(true));
        eventually("看板右緣不應該有「還有 N 個狀態」的按鈕", () ->
                visible.count() == 0 ? null : "看得到按鈕「" + visible.first().innerText().strip() + "」");
    }

    /**
     * 看板本體的橫向捲動位置比按之前多一欄的寬度，右緣按鈕的文字相符。
     * 一欄的寬度是相鄰兩個欄頭左緣的距離，含欄距。
     * 前面按按鈕的是共用的按鈕句，沒有記下按之前的捲動位置；這一句接在剛打開的看板後面，
     * 按之前捲動位置在最左邊，所以多一欄就是捲動位置剛好等於一欄的寬度。
     */
    @那麼("看板往右捲了一欄，看板右緣有按鈕 {string}")
    public void boardScrolledOneColumn(String buttonName) {
        ElementHandle body = board.boardBody();
        Locator button = world.page().getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(buttonName.strip()).setExact(true));
        eventually("看板應該往右捲了一欄", () -> {
            double step = board.columnHeader(BoardPage.COLUMN_NAMES.get(1)).boundingBox().x
                    - board.columnHeader(BoardPage.COLUMN_NAMES.get(0)).boundingBox().x;
            double scrolled = ((Number) body.evaluate("body => body.scrollLeft")).doubleValue();
            if (Math.abs(scrolled - step) > TOLERANCE_PX) {
                return "看板往右捲了 " + scrolled + "px，一欄是 " + step + "px";
            }
            return buttonAtRightEdge(button, buttonName);
        });
    }

    /** 那一欄的欄頭完整落在視窗內，也沒有被看板本體的捲動範圍切掉。 */
    @那麼("看得到 {string} 欄")
    public void columnVisible(String column) {
        eventually("應該看得到「" + column + "」欄", () -> fullyVisibleProblem(column, visibleBoardArea()));
    }

    /** 該數量的欄頭都完整落在視窗內，每一欄的寬度都大於視窗調整前記下的寬度。 */
    @那麼("{int} 個欄都看得到，每一欄都比視窗調整前寬")
    public void allColumnsVisibleAndWider(int count) {
        Map<String, Double> before = layoutBeforeResize.snapshot().columnWidths();
        eventually(count + " 個欄應該都看得到並且變寬", () -> {
            int present = board.columnHeaders().count();
            if (present != count) {
                return "看板上有 " + present + " 個欄，不是 " + count + " 個";
            }
            double[] area = visibleBoardArea();
            for (String column : board.columnNames()) {
                String problem = fullyVisibleProblem(column, area);
                if (problem != null) {
                    return problem;
                }
                Double old = before.get(column);
                if (old == null) {
                    return "視窗調整前沒有記到「" + column + "」欄的寬度";
                }
                double now = board.columnHeader(column).boundingBox().width;
                if (now <= old) {
                    return "「" + column + "」欄現在寬 " + now + "px，調整前是 " + old + "px，沒有變寬";
                }
            }
            return null;
        });
    }

    /** 第一欄的左緣、最後一欄的右緣，離內容區的左右邊界都剛好是 16px 的邊距。 */
    @那麼("看板貼齊內容區的左右兩側，沒有置中留白")
    public void boardSpansContentArea() {
        eventually("看板應該貼齊內容區的左右兩側", () -> {
            List<String> columns = board.columnNames();
            if (columns.isEmpty()) {
                return "看板上沒有欄";
            }
            BoundingBox content = shell.mainContent().boundingBox();
            BoundingBox first = board.columnHeader(columns.get(0)).boundingBox();
            BoundingBox last = board.columnHeader(columns.get(columns.size() - 1)).boundingBox();
            double left = first.x - content.x;
            double right = content.x + content.width - (last.x + last.width);
            if (Math.abs(left - BOARD_SIDE_MARGIN_PX) > TOLERANCE_PX
                    || Math.abs(right - BOARD_SIDE_MARGIN_PX) > TOLERANCE_PX) {
                return "第一欄離內容區左邊 " + left + "px、最後一欄離右邊 " + right
                        + "px，兩邊都應該是 " + BOARD_SIDE_MARGIN_PX + "px";
            }
            return null;
        });
    }

    /**
     * 視窗比支援的尺寸還小時，使用者要靠整頁捲動才看得到被切掉的區塊，這一句確認那些區塊捲得到。
     * 捲完不捲回來，因為後面比對排法的句子用文件座標比，捲動位置不影響它。
     */
    @那麼("畫面出現整頁的捲軸，捲動後看得到其餘的內容")
    public void pageScrollsToRevealRest() {
        Page page = world.page();
        ViewportSize viewport = page.viewportSize();
        Map<String, Number> doc = documentSize();
        if (doc.get("scrollWidth").doubleValue() <= doc.get("clientWidth").doubleValue()
                && doc.get("scrollHeight").doubleValue() <= doc.get("clientHeight").doubleValue()) {
            throw new AssertionError("整份文件是 " + doc.get("scrollWidth") + " × " + doc.get("scrollHeight")
                    + "，沒有超過視窗，不會出現整頁捲軸");
        }
        Set<String> cutOff = new LinkedHashSet<>();
        blocksOnViewport().forEach((name, box) -> {
            if (!inside(box, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, viewport.width, viewport.height)) {
                cutOff.add(name);
            }
        });
        if (cutOff.isEmpty()) {
            throw new AssertionError("文件比視窗大，但側欄、工具列、數量卡與看板都已經在視窗內，捲動後沒有其餘的內容");
        }
        page.evaluate("() => window.scrollTo(document.documentElement.scrollWidth, document.documentElement.scrollHeight)");
        page.waitForFunction("() => Math.ceil(scrollX + innerWidth) >= document.documentElement.scrollWidth - 1"
                + " && Math.ceil(scrollY + innerHeight) >= document.documentElement.scrollHeight - 1",
                null, new Page.WaitForFunctionOptions().setTimeout(ASSERT_TIMEOUT_MS));
        Map<String, BoundingBox> after = blocksOnViewport();
        for (String name : cutOff) {
            BoundingBox box = after.get(name);
            if (!inside(box, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, viewport.width, viewport.height)) {
                throw new AssertionError("捲到底之後「" + name + "」仍有一部分在視窗外：" + describe(box));
            }
        }
    }

    /**
     * 側欄、工具列、數量卡與看板沒有換成另一套版面：看得到的區塊相同，兩兩之間左右、上下的先後不變，
     * 上緣位置與高度和調整前相同。寬度只會跟著視窗縮窄，不比調整前寬，而且外框不窄於最小寬度 1180。
     * 調整前記的是沒有捲動時的視窗座標，也就是文件座標，所以現在的視窗座標要加上整頁的捲動位置再比。
     */
    @那麼("側欄、工具列、數量卡與看板的排法與視窗調整前相同")
    public void layoutSameAsBeforeResize() {
        Map<String, BoundingBox> before = layoutBeforeResize.snapshot().blocks();
        Map<String, BoundingBox> now = blocksOnViewport();
        @SuppressWarnings("unchecked")
        List<Number> scroll = (List<Number>) world.page().evaluate("() => [scrollX, scrollY]");
        if (!before.keySet().equals(now.keySet())) {
            throw new AssertionError("看得到的區塊變了：調整前是 " + before.keySet() + "，現在是 " + now.keySet());
        }
        Map<String, BoundingBox> onDocument = new LinkedHashMap<>();
        for (Map.Entry<String, BoundingBox> entry : now.entrySet()) {
            BoundingBox box = entry.getValue();
            BoundingBox moved = new BoundingBox();
            moved.x = box.x + scroll.get(0).doubleValue();
            moved.y = box.y + scroll.get(1).doubleValue();
            moved.width = box.width;
            moved.height = box.height;
            onDocument.put(entry.getKey(), moved);
        }
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, BoundingBox> entry : before.entrySet()) {
            BoundingBox old = entry.getValue();
            BoundingBox box = onDocument.get(entry.getKey());
            if (Math.abs(box.y - old.y) > TOLERANCE_PX || Math.abs(box.height - old.height) > TOLERANCE_PX) {
                changed.add(entry.getKey() + " 的上緣或高度變了：調整前 " + describe(old) + "，現在 " + describe(box));
            }
            if (box.width > old.width + TOLERANCE_PX) {
                changed.add(entry.getKey() + " 比調整前寬：調整前 " + describe(old) + "，現在 " + describe(box));
            }
        }
        for (String first : before.keySet()) {
            for (String second : before.keySet()) {
                if (first.equals(second)) {
                    continue;
                }
                String was = relativeOrder(before.get(first), before.get(second));
                String is = relativeOrder(onDocument.get(first), onDocument.get(second));
                if (!was.equals(is)) {
                    changed.add(first + " 相對於 " + second + " 的位置從「" + was + "」變成「" + is + "」");
                }
            }
        }
        double frameLeft = Double.POSITIVE_INFINITY;
        double frameRight = Double.NEGATIVE_INFINITY;
        for (BoundingBox box : onDocument.values()) {
            frameLeft = Math.min(frameLeft, box.x);
            frameRight = Math.max(frameRight, box.x + box.width);
        }
        if (frameRight - frameLeft < SHELL_MIN_WIDTH_PX - TOLERANCE_PX) {
            changed.add("外框寬 " + Math.round(frameRight - frameLeft) + "，窄於最小寬度 " + (int) SHELL_MIN_WIDTH_PX);
        }
        if (!changed.isEmpty()) {
            throw new AssertionError("排法和視窗調整前不同：" + changed);
        }
    }

    /** 第一塊在第二塊的左邊或右邊、上面或下面；兩個方向都重疊時記成「重疊」。容許 1px。 */
    private static String relativeOrder(BoundingBox first, BoundingBox second) {
        String horizontal = first.x + first.width <= second.x + TOLERANCE_PX ? "左邊"
                : second.x + second.width <= first.x + TOLERANCE_PX ? "右邊" : "左右重疊";
        String vertical = first.y + first.height <= second.y + TOLERANCE_PX ? "上面"
                : second.y + second.height <= first.y + TOLERANCE_PX ? "下面" : "上下重疊";
        return horizontal + "、" + vertical;
    }

    /**
     * 只在游標停留時才出現的文字或按鈕，用手指的人永遠看不到；這一句要保證畫面上沒有這種東西。
     * 先在沒有游標停留時記下整份文件看得到的文字與可以按的東西，再把游標逐一停到這個畫面的每一個元素上，
     * 和沒有游標時記下的比，多出來的就是只靠游標才看得到的內容。
     *
     * <p>每次記之前把有終點的轉場與動畫直接跑完，否則延遲淡入的提示會被當成沒有出現。
     * 提示可能被放在畫面的最外層、不在面板裡面，所以記的範圍是整份文件，游標停的範圍才是這個畫面。
     */
    @那麼("這個畫面上每一項內容與每一顆可以按的東西，不用游標移上去就看得到、按得到")
    public void nothingOnlyOnHover() {
        Page page = world.page();
        shell.waitForLayoutSettled();
        moveMouseAway(page);
        Set<String> baseline = new HashSet<>(visibleContent(page));
        JSHandle targets = shell.currentScreen().evaluateHandle("root => {" + PAGE_HELPERS + """
                  const seen = new Set();
                  return [root, ...root.querySelectorAll('*')].filter(el => {
                    if (el.closest('svg') && el.tagName.toLowerCase() !== 'svg') return false;
                    if (!shown(el)) return false;
                    const r = el.getBoundingClientRect();
                    const key = [r.left, r.top, r.width, r.height].map(Math.round).join(',');
                    if (seen.has(key)) return false;
                    seen.add(key);
                    return true;
                  });
                }
                """);
        String baselineScroll = scrollPositions(page);
        List<String> problems = new ArrayList<>();
        try {
            for (JSHandle handle : targets.getProperties().values()) {
                ElementHandle element = handle.asElement();
                if (element == null) {
                    continue;
                }
                // 停游標前瀏覽器會先把元素捲進視窗，捲動本身就可能改變畫面上的字（例如看板的「還有 N 個狀態」）。
                // 所以停上去之後捲動位置若和記對照時不同，就移開游標、在新的捲動位置重記一次對照再停一次；
                // 元素已經在視窗裡，第二次不會再捲，前後兩次只差在游標有沒有停在它上面。
                boolean replaced = false;
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        element.hover(new ElementHandle.HoverOptions().setForce(true).setTimeout(2_000));
                    } catch (PlaywrightException gone) {
                        // 畫面重畫時舊元素會被換掉；換上來的新元素也在清單裡，不必再停一次。
                        replaced = true;
                        break;
                    }
                    if (scrollPositions(page).equals(baselineScroll)) {
                        break;
                    }
                    moveMouseAway(page);
                    shell.waitForLayoutSettled();
                    baseline = new HashSet<>(visibleContent(page));
                    baselineScroll = scrollPositions(page);
                }
                if (replaced) {
                    continue;
                }
                Set<String> added = new LinkedHashSet<>(visibleContent(page));
                added.removeAll(baseline);
                if (!added.isEmpty()) {
                    problems.add("游標停在「" + element.evaluate("el => {" + PAGE_HELPERS + " return nameOf(el); }")
                            + "」上時多出 " + added);
                }
            }
        } finally {
            targets.dispose();
            moveMouseAway(page);
        }
        if (!problems.isEmpty()) {
            throw new AssertionError("有只在游標移上去才出現的內容：" + String.join("；", problems));
        }
    }

    /** 等到網址是這一頁、工具列的頁標題相符；換頁與停在原頁的兩句都靠它確認畫面停在哪一頁。 */
    private void waitForPathAndTitle(String pageName, String title) {
        String path = ShellFrame.pathOf(pageName);
        Locator heading = shell.toolbar().getByRole(AriaRole.HEADING,
                new Locator.GetByRoleOptions().setName(title).setExact(true).setLevel(1));
        eventually("畫面應該是「" + pageName + "」", () -> {
            String current = world.page().url().split("[?#]")[0];
            if (!current.endsWith(path)) {
                return "網址是 " + current + "，不是 " + path;
            }
            return heading.count() == 1 && heading.isVisible() ? null : "工具列的頁標題不是「" + title + "」";
        });
    }

    /** 那顆按鈕看得到，而且在看板本體的右半邊、右緣沒有超出看板；不是的話回傳原因。 */
    private String buttonAtRightEdge(Locator button, String buttonName) {
        if (button.count() != 1 || !button.isVisible()) {
            return "看不到按鈕「" + buttonName + "」";
        }
        BoundingBox body = board.boardBodyBox();
        BoundingBox box = button.boundingBox();
        double center = box.x + box.width / 2;
        double bodyRight = body.x + body.width;
        if (center < body.x + body.width / 2 || box.x + box.width > bodyRight + TOLERANCE_PX) {
            return "按鈕「" + buttonName + "」不在看板右緣：按鈕 " + describe(box) + "，看板 " + describe(body);
        }
        return null;
    }

    /** 欄頭沒有完整落在看板右側看得到的範圍內的欄數，只露出一部分的也算看不到。 */
    private int columnsHiddenOnTheRight() {
        double right = visibleBoardArea()[2];
        int hidden = 0;
        for (String column : board.columnNames()) {
            BoundingBox box = board.columnHeader(column).boundingBox();
            if (box.x + box.width > right + TOLERANCE_PX) {
                hidden++;
            }
        }
        return hidden;
    }

    /**
     * 看板裡看得到的範圍，以左、上、右、下四個視窗座標回傳。
     * 它是看板本體扣掉捲軸後的內側再和視窗取交集，因為欄頭在這兩者任一個之外都會被切掉。
     */
    private double[] visibleBoardArea() {
        @SuppressWarnings("unchecked")
        List<Number> inner = (List<Number>) board.boardBody().evaluate("""
                body => {
                  const r = body.getBoundingClientRect();
                  return [r.left + body.clientLeft, r.top + body.clientTop,
                          r.left + body.clientLeft + body.clientWidth, r.top + body.clientTop + body.clientHeight];
                }
                """);
        ViewportSize viewport = world.page().viewportSize();
        return new double[] {
                Math.max(0, inner.get(0).doubleValue()),
                Math.max(0, inner.get(1).doubleValue()),
                Math.min(viewport.width, inner.get(2).doubleValue()),
                Math.min(viewport.height, inner.get(3).doubleValue())};
    }

    /** 這一欄的欄頭完整落在看得到的範圍內時回傳 null，否則回傳原因。 */
    private String fullyVisibleProblem(String column, double[] area) {
        Locator header = board.columnHeader(column);
        if (header.count() == 0) {
            return "看板上沒有「" + column + "」欄";
        }
        BoundingBox box = header.first().boundingBox();
        return inside(box, area[0], area[1], area[2], area[3])
                ? null : "「" + column + "」欄的欄頭沒有完整落在視窗內：" + describe(box);
    }

    /** 側欄、工具列、數量卡、看板現在的視窗座標，名稱與 {@link LayoutBeforeResize} 的相同；看不到的區塊不列。 */
    private Map<String, BoundingBox> blocksOnViewport() {
        Map<String, BoundingBox> blocks = new LinkedHashMap<>();
        putIfVisible(blocks, LayoutBeforeResize.SIDEBAR, shell.sidebar());
        putIfVisible(blocks, LayoutBeforeResize.TOOLBAR, shell.toolbar());
        putIfVisible(blocks, LayoutBeforeResize.TILES, board.tileRow());
        if (board.columnHeaders().count() > 0) {
            blocks.put(LayoutBeforeResize.BOARD, board.boardBodyBox());
        }
        return blocks;
    }

    private static void putIfVisible(Map<String, BoundingBox> blocks, String name, Locator block) {
        if (block.count() > 0 && block.first().isVisible()) {
            blocks.put(name, block.first().boundingBox());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Number> documentSize() {
        return (Map<String, Number>) world.page().evaluate("""
                () => ({
                  scrollWidth: document.documentElement.scrollWidth,
                  scrollHeight: document.documentElement.scrollHeight,
                  clientWidth: document.documentElement.clientWidth,
                  clientHeight: document.documentElement.clientHeight,
                  innerWidth: innerWidth,
                  innerHeight: innerHeight,
                  scale: window.visualViewport ? visualViewport.scale : 1,
                })
                """);
    }

    /** 整份文件裡看得到的文字與可以按的東西的名稱。記之前先把有終點的轉場與動畫跑完。 */
    @SuppressWarnings("unchecked")
    private List<String> visibleContent(Page page) {
        return (List<String>) page.evaluate("() => {" + PAGE_HELPERS + """
                  for (const animation of document.getAnimations()) {
                    const end = animation.effect?.getComputedTiming().endTime;
                    if (Number.isFinite(end)) {
                      try { animation.finish(); } catch (e) { }
                    }
                  }
                  const found = new Set();
                  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
                  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
                    const text = node.textContent.replace(/\\s+/g, ' ').trim();
                    if (text && node.parentElement && shown(node.parentElement)) found.add('文字「' + text + '」');
                  }
                  for (const el of document.body.querySelectorAll(SELECTOR)) {
                    if (shown(el)) found.add('可以按的「' + nameOf(el) + '」');
                  }
                  return [...found];
                }
                """.replace("SELECTOR", jsString(PRESSABLE_SELECTOR)));
    }

    /** 視窗與每一個捲動過的區塊目前捲到哪裡，用來判斷停游標時畫面有沒有被捲動。 */
    private static String scrollPositions(Page page) {
        return (String) page.evaluate("""
                () => {
                  const positions = [];
                  for (const el of document.querySelectorAll('*')) {
                    if (el.scrollLeft || el.scrollTop) positions.push(el.scrollLeft + ',' + el.scrollTop);
                  }
                  return scrollX + ',' + scrollY + ';' + positions.sort().join(';');
                }
                """);
    }

    /** 游標停在視窗外時，畫面上沒有任何元素處在游標停留的樣子。 */
    private static void moveMouseAway(Page page) {
        page.mouse().move(-1, -1);
    }

    /** 這個分頁登入回應裡的某個欄位。登入狀態存的是登入回應的 JSON 原樣。 */
    private String loginField(String field) {
        Object value = world.page().evaluate(
                "([key, field]) => { const raw = sessionStorage.getItem(key); return raw ? JSON.parse(raw)[field] ?? null : null; }",
                List.of(SessionInjector.SESSION_KEY, field));
        if (value == null) {
            throw new AssertionError("這個分頁的登入回應裡沒有 " + field);
        }
        return value.toString();
    }

    /** 在 5 秒內反覆檢查，檢查回傳 null 代表成立；時間到還不成立時以最後一次的原因失敗。 */
    private void eventually(String what, Supplier<String> problem) {
        String[] last = {null};
        try {
            world.page().waitForCondition(() -> (last[0] = problem.get()) == null,
                    new Page.WaitForConditionOptions().setTimeout(ASSERT_TIMEOUT_MS));
        } catch (TimeoutError timeout) {
            throw new AssertionError(what + "：" + last[0], timeout);
        }
    }

    private static boolean inside(BoundingBox box, double left, double top, double right, double bottom) {
        return box != null
                && box.x >= left - TOLERANCE_PX && box.y >= top - TOLERANCE_PX
                && box.x + box.width <= right + TOLERANCE_PX && box.y + box.height <= bottom + TOLERANCE_PX;
    }

    private static String describe(BoundingBox box) {
        if (box == null) {
            return "（量不到位置）";
        }
        return String.format("(%.0f, %.0f) %.0f × %.0f", box.x, box.y, box.width, box.height);
    }

    private static String jsString(String text) {
        return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
