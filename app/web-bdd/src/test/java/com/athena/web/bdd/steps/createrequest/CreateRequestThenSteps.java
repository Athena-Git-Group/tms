package com.athena.web.bdd.steps.createrequest;

import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.BoardPage.CardReading;
import com.athena.web.bdd.pages.CreateRequestPanel;
import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.LoginPage;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.UiWords.Language;
import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.athena.web.bdd.support.WhenStart;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CreateRequestThenSteps {

    private static final double ASSERT_TIMEOUT_MS = 5_000;

    private static final double TOLERANCE_PX = 2;

    private static final double MIN_TARGET_PX = 44;

    private static final double SUBMIT_BOTTOM_GAP_PX = 32;

    private static final String BRAND = "rgb(12, 118, 128)";

    private static final List<String> ERROR_COLORS = List.of("rgb(180, 35, 24)", "rgb(255, 59, 48)");

    private static final Pattern STAY_LINE = Pattern.compile("^(\\d{1,4}) · \\d{1,2}/\\d{1,2}–\\d{1,2}/\\d{1,2}$");

    private static final Pattern UNDER_ROW_CELL = Pattern.compile("「([^」]*)」");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebWorld world;

    private final CreateRequestScenario scenario;

    private final CreateRequestPanel panel;

    private final ShellFrame shell;

    private final BoardPage board;

    public CreateRequestThenSteps(WebWorld world, CreateRequestScenario scenario) {
        this.world = world;
        this.scenario = scenario;
        this.panel = new CreateRequestPanel(world);
        this.shell = new ShellFrame(world);
        this.board = new BoardPage(world);
    }

    @那麼("畫面中間打開代開單面板，面板外面是暗色遮罩")
    public void panelOpenWithBackdrop() {
        eventually("代開單面板沒有在畫面中間打開", () -> {
            Locator dialogs = shell.shownDialogs();
            if (dialogs.count() == 0) {
                return "畫面上沒有對話框";
            }
            Locator top = dialogs.last();
            if (panel.panel().and(top).count() != 1) {
                return "最上層的對話框不是代開單面板";
            }
            if (!"true".equals(top.getAttribute("aria-modal"))) {
                return "代開單面板沒有標成 aria-modal";
            }
            BoundingBox box = top.boundingBox();
            int[] viewport = viewport();
            if (box == null) {
                return "代開單面板沒有畫出來";
            }
            double dx = Math.abs(box.x + box.width / 2 - viewport[0] / 2.0);
            double dy = Math.abs(box.y + box.height / 2 - viewport[1] / 2.0);
            if (dx > TOLERANCE_PX || dy > TOLERANCE_PX) {
                return "面板沒有置中：中心偏了 " + Math.round(dx) + "×" + Math.round(dy) + "px";
            }
            Locator backdrop = panel.backdrop().filter(new Locator.FilterOptions().setVisible(true));
            if (backdrop.count() != 1) {
                return "面板外沒有名稱是「關閉」的遮罩（找到 " + backdrop.count() + " 個）";
            }
            BoundingBox cover = backdrop.boundingBox();
            if (cover.x > TOLERANCE_PX || cover.y > TOLERANCE_PX
                    || cover.x + cover.width < viewport[0] - TOLERANCE_PX
                    || cover.y + cover.height < viewport[1] - TOLERANCE_PX) {
                return "遮罩沒有蓋住整個視窗：" + describe(cover);
            }
            return null;
        });
    }

    @那麼("代開單面板左上的關閉鈕名稱是 {string}")
    public void closeButtonName(String name) {
        eventually("代開單面板左上的關閉鈕不對", () -> {
            Locator byName = panel.panel().getByRole(AriaRole.BUTTON,
                    new Locator.GetByRoleOptions().setName(name.strip()).setExact(true));
            Locator byText = panel.panel().getByRole(AriaRole.BUTTON).filter(new Locator.FilterOptions()
                    .setHasText(Pattern.compile("^\\s*" + com.athena.web.bdd.support.BrowserRegex.literal(name.strip()) + "\\s*$")));
            Locator button = byName.or(byText).filter(new Locator.FilterOptions().setVisible(true));
            if (button.count() != 1) {
                return "面板上名稱是「" + name + "」的按鈕有 " + button.count() + " 顆";
            }
            BoundingBox box = button.boundingBox();
            BoundingBox title = panel.title().boundingBox();
            BoundingBox whole = panel.panel().boundingBox();
            if (title == null || whole == null) {
                return "面板或面板標題沒有畫出來";
            }
            if (box.x + box.width > title.x + TOLERANCE_PX) {
                return "「" + name + "」不在標題左邊：鈕 " + describe(box) + "，標題 " + describe(title);
            }
            if (box.x > whole.x + whole.width / 3 || box.y > title.y + title.height + TOLERANCE_PX) {
                return "「" + name + "」不在面板左上：鈕 " + describe(box) + "，面板 " + describe(whole);
            }
            return null;
        });
    }

    @那麼("代開單面板由上到下是房號欄、建議房號、{string}、{string}，送出鈕固定在底部")
    public void panelSections(String itemsTitle, String otherTitle) {
        eventually("代開單面板的區塊不對", () -> {
            Locator items = panel.panel().getByText(itemsTitle.strip(), new Locator.GetByTextOptions().setExact(true));
            Locator other = panel.panel().getByText(otherTitle.strip(), new Locator.GetByTextOptions().setExact(true));
            if (items.count() != 1) {
                return "找不到區塊標題「" + itemsTitle + "」";
            }
            if (other.count() != 1) {
                return "找不到區塊標題「" + otherTitle + "」";
            }
            List<Double> tops = new ArrayList<>();
            tops.add(panel.roomInput().boundingBox().y);
            Locator suggestions = panel.suggestions();
            if (suggestions.count() > 0) {
                tops.add(suggestions.first().boundingBox().y);
            }
            tops.add(items.boundingBox().y);
            tops.add(other.boundingBox().y);
            for (int i = 1; i < tops.size(); i++) {
                if (tops.get(i) < tops.get(i - 1)) {
                    return "區塊的上下順序不對（上緣依序是 " + tops + "）";
                }
            }
            BoundingBox submit = panel.submitButton().boundingBox();
            BoundingBox whole = panel.panel().boundingBox();
            if (submit == null || whole == null) {
                return "送出鈕或面板沒有畫出來";
            }
            double gap = whole.y + whole.height - (submit.y + submit.height);
            if (gap < -TOLERANCE_PX || gap > SUBMIT_BOTTOM_GAP_PX) {
                return "送出鈕不在面板底部：離面板底緣 " + Math.round(gap) + "px";
            }
            if (Boolean.TRUE.equals(panel.submitButton().evaluate(INSIDE_SCROLLER, panel.panel().elementHandle()))) {
                return "送出鈕在可以捲動的內容裡";
            }
            return null;
        });
    }

    @那麼("房號欄是空的，提示字是 {string}")
    public void roomEmptyWithPlaceholder(String placeholder) {
        eventually("房號欄不對", () -> {
            String value = panel.roomValue();
            String shown = panel.roomInput().getAttribute("placeholder");
            if (!value.isEmpty()) {
                return "房號欄是「" + value + "」，不是空的";
            }
            return placeholder.equals(shown) ? null : "提示字是「" + shown + "」，不是「" + placeholder + "」";
        });
    }

    @那麼("房號欄是空的")
    public void roomEmpty() {
        eventually("房號欄不是空的", () -> panel.roomValue().isEmpty() ? null : "房號欄是「" + panel.roomValue() + "」");
    }

    @那麼("房號欄是 {string}")
    public void roomIs(String roomNo) {
        eventually("房號欄不對", () -> {
            String value = panel.roomValue();
            return value.equals(roomNo) ? null : "房號欄是「" + value + "」，應該是「" + roomNo + "」";
        });
    }

    @那麼("常用品項依序如下，每一格都沒有徽章：")
    public void itemsInOrder(DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        eventually("常用品項不對", () -> {
            List<String> expected = rows.stream().map(row -> row.get("品名").strip()).toList();
            if (!expected.equals(CreateRequestPanel.ITEMS)) {
                return "表格的品名 " + expected + " 不是面板的 12 個品項 " + CreateRequestPanel.ITEMS;
            }
            List<List<String>> tiles = panel.tilesInOrder(Language.TRADITIONAL_CHINESE);
            for (int i = 0; i < rows.size(); i++) {
                String name = rows.get(i).get("品名").strip();
                String dept = rows.get(i).get("部門").strip();
                List<String> lines = tiles.get(i);
                if (!lines.contains(name) || !lines.contains(dept)) {
                    return "「" + name + "」那一格的字是 " + lines + "，應該有品名「" + name + "」與部門字「" + dept + "」";
                }
                if (panel.badgeOf(name) != null || panel.looksSelected(name)) {
                    return "「" + name + "」那一格有徽章或是選取的樣子";
                }
            }
            String order = readingOrderProblem();
            if (order != null) {
                return order;
            }
            return panel.panel().getByRole(AriaRole.BUTTON).filter(new Locator.FilterOptions()
                    .setHasText(Pattern.compile("^\\s*\\d+\\s*$"))).count() == 0 ? null : "面板上有數字徽章";
        });
    }

    @那麼("{int} 個品項都是 {int}")
    public void allItemsZero(int itemCount, int quantity) {
        if (itemCount != CreateRequestPanel.ITEMS.size() || quantity != 0) {
            throw new IllegalArgumentException("這一句只寫「12 個品項都是 0」，收到 " + itemCount + " 個品項都是 " + quantity);
        }
        eventually("品項不是全部沒選", () -> {
            for (String item : CreateRequestPanel.ITEMS) {
                if (panel.itemTile(item).count() != 1) {
                    return "找不到「" + item + "」那一格";
                }
                if (panel.quantityOf(item) != 0 || panel.badgeOf(item) != null || panel.looksSelected(item)) {
                    return "「" + item + "」不是沒選的樣子";
                }
                if (!panel.underRowOf(item).isEmpty() || panel.decreaseButton(item).count() > 0
                        || panel.removeButton(item).count() > 0) {
                    return "「" + item + "」底下有 " + panel.underRowOf(item);
                }
            }
            return null;
        });
    }

    @那麼("其他需求是空的")
    public void otherRequestEmpty() {
        eventually("其他需求不是空的", () -> {
            String value = panel.otherRequestBox().inputValue();
            if (!value.isEmpty()) {
                return "其他需求是「" + value + "」";
            }
            return panel.sendToLabel().count() == 0 && panel.otherRequestDeptGroup().count() == 0 ? null
                    : "其他需求底下有「這段要給」";
        });
    }

    @那麼("代開單面板沒有送出摘要")
    public void noSummary() {
        eventually("代開單面板有送出摘要", () -> {
            Locator summary = panel.summary().filter(new Locator.FilterOptions().setVisible(true));
            return summary.count() == 0 ? null : "摘要是「" + summary.first().innerText() + "」";
        });
    }

    @那麼("送出摘要是 {string}")
    public void summaryIs(String expected) {
        eventually("送出摘要不對", () -> {
            Locator summary = panel.summary().filter(new Locator.FilterOptions().setVisible(true));
            if (summary.count() != 1) {
                return "面板上的摘要有 " + summary.count() + " 段";
            }
            String text = ShellFrame.normalizeSpaces(summary.innerText().replace('\n', ' '));
            if (!text.equals(ShellFrame.normalizeSpaces(expected))) {
                return "摘要是「" + text + "」";
            }
            BoundingBox box = summary.boundingBox();
            BoundingBox submit = panel.submitButton().boundingBox();
            return box.y + box.height <= submit.y + TOLERANCE_PX ? null : "摘要不在送出鈕上方";
        });
    }

    @那麼("各品項的樣子如下：")
    public void itemLooks(DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        eventually("品項的樣子不對", () -> {
            for (Map<String, String> row : rows) {
                String item = row.get("品項").strip();
                String badge = blankToNull(row.get("徽章"));
                List<String> under = parseUnderRow(row.get("底下"));
                String shownBadge = panel.badgeOf(item);
                if (!Objects.equals(badge, shownBadge)) {
                    return "「" + item + "」的徽章是 " + shownBadge + "，應該是 " + badge;
                }
                if (panel.looksSelected(item) != (badge != null)) {
                    return "「" + item + "」" + (badge != null ? "不是" : "是") + "選取的樣子";
                }
                List<String> shownUnder = panel.underRowOf(item);
                if (!shownUnder.equals(under)) {
                    return "「" + item + "」底下是 " + shownUnder + "，應該是 " + under;
                }
                if (under.equals(List.of("移除")) && !fullWidthOfCard(item)) {
                    return "「" + item + "」底下的「移除」不是滿寬的一顆鈕";
                }
            }
            return null;
        });
    }

    @那麼("各項的無障礙名稱如下：")
    public void accessibleNames(DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        eventually("無障礙名稱不對", () -> {
            for (Map<String, String> row : rows) {
                String item = row.get("品項").strip();
                String element = row.get("元素").strip();
                String name = row.get("無障礙名稱").strip();
                Locator named = panel.buttonNamed(name);
                Locator target = switch (element) {
                    case "格子" -> panel.itemTile(item).and(named);
                    case "−", "+", "移除" -> panel.itemCard(item).getByRole(AriaRole.BUTTON,
                            new Locator.GetByRoleOptions().setName(name).setExact(true));
                    default -> throw new IllegalArgumentException("元素只有格子、−、+、移除，收到「" + element + "」");
                };
                if (target.count() != 1) {
                    return "「" + item + "」的「" + element + "」不是名稱為「" + name + "」的按鈕";
                }
                if (!element.equals("格子") && target.and(panel.itemTile(item)).count() > 0) {
                    return "名稱為「" + name + "」的是「" + item + "」的格子，不是底下的「" + element + "」";
                }
                if (!element.equals("格子") && !textOf(target).equals(element)) {
                    return "名稱為「" + name + "」的按鈕上寫的是「" + textOf(target) + "」，不是「" + element + "」";
                }
            }
            return null;
        });
    }

    @那麼("建議房號依序如下：")
    public void suggestionsAre(DataTable table) {
        List<List<String>> expected = table.asMaps().stream()
                .map(row -> List.of(row.get("房號").strip(), row.get("住客").strip()))
                .toList();
        eventually("建議房號不對", () -> {
            List<List<String>> shown = panel.readSuggestions();
            return shown.equals(expected) ? null : "建議是 " + shown + "，應該是 " + expected;
        });
    }

    @那麼("建議房號有 {int} 筆，依房號由小到大，全部是有住客的房")
    public void suggestionsAreSmallestOccupied(int count) {
        Map<String, String> occupied = occupiedRooms();
        List<List<String>> expected = occupied.entrySet().stream()
                .sorted((a, b) -> Integer.compare(Integer.parseInt(a.getKey()), Integer.parseInt(b.getKey())))
                .limit(count)
                .map(entry -> List.of(entry.getKey(), entry.getValue()))
                .toList();
        if (expected.size() < count) {
            throw new AssertionError("房間清單裡有住客的房只有 " + occupied.size() + " 間，湊不到 " + count + " 筆");
        }
        eventually("建議房號不對", () -> {
            List<List<String>> shown = panel.readSuggestions();
            return shown.equals(expected) ? null : "建議是 " + shown + "，應該是有住客的房裡最小的 " + count + " 間 " + expected;
        });
    }

    @那麼("沒有建議房號")
    public void noSuggestions() {
        eventually("房號欄底下還有建議", () -> {
            if (panel.suggestions().count() > 0) {
                return "建議有 " + panel.readSuggestions();
            }
            return panel.busy().count() == 0 ? null : "建議的位置還是骨架";
        });
    }

    @那麼("^住客卡是頭像 \"([^\"]*)\"、\"([^\"]*)\"、\"([^\"]*)\"，(有|沒有) VIP 標$")
    public void guestCard(String avatar, String name, String stayLine, String vipWord) {
        boolean vip = "有".equals(vipWord);
        eventually("住客卡不對", () -> {
            List<String> lines = panel.guestCardLines();
            if (lines.isEmpty()) {
                return "面板上沒有住客卡";
            }
            List<String> rest = new ArrayList<>(lines);
            boolean hasVip = rest.remove("VIP");
            if (hasVip != vip) {
                return "住客卡" + (vip ? "沒有" : "有") + "「VIP」標：" + lines;
            }
            List<String> expected = List.of(avatar, name, stayLine);
            return rest.equals(expected) ? null : "住客卡上是 " + lines + "，應該是 " + expected + (vip ? " 加「VIP」" : "");
        });
        Matcher room = STAY_LINE.matcher(stayLine);
        if (!room.matches()) {
            throw new IllegalArgumentException("住客卡第二行要寫成「{房號} · {入住 M/D}–{退房 M/D}」，收到「" + stayLine + "」");
        }
        JsonNode listed = deskRoom(room.group(1));
        JsonNode guest = listed.path("guest");
        if (!listed.path("occupied").asBoolean() || !name.equals(guest.path("name").asText())
                || guest.path("vip").asBoolean() != vip) {
            throw new AssertionError("房間清單裡 " + room.group(1) + " 的住客是 " + guest + "，和住客卡（" + name
                    + (vip ? "，VIP" : "") + "）不一致");
        }
    }

    @那麼("沒有住客卡")
    public void noGuestCard() {
        eventually("面板上有住客卡", () -> panel.guestCardStayLine().count() == 0 ? null
                : "住客卡是 " + panel.guestCardLines());
    }

    @那麼("空房提示是 {string}")
    public void vacantNoticeIs(String text) {
        eventually("空房提示不對", () -> {
            Locator notice = panel.panel().getByText(text.strip(), new Locator.GetByTextOptions().setExact(true));
            if (notice.count() != 1 || !notice.isVisible()) {
                Locator any = panel.vacantNotice();
                return any.count() == 0 ? "面板上沒有空房提示" : "空房提示是「" + any.first().innerText() + "」";
            }
            BoundingBox box = notice.boundingBox();
            return box.y >= panel.roomInput().boundingBox().y ? null : "空房提示不在房號欄底下";
        });
        String roomNo = panel.roomValue();
        JsonNode listed = deskRoom(roomNo);
        if (listed.path("occupied").asBoolean() || !listed.path("guest").isMissingNode() && !listed.path("guest").isNull()) {
            throw new AssertionError("房間清單裡 " + roomNo + " 有在住住客：" + listed);
        }
    }

    @那麼("沒有空房提示")
    public void noVacantNotice() {
        eventually("面板上有空房提示", () -> panel.vacantNotice().count() == 0 ? null
                : "空房提示是「" + panel.vacantNotice().first().innerText() + "」");
    }

    @那麼("代開單面板上沒有任何紅字的錯誤提示")
    public void noErrorOnPanel() {
        eventually("代開單面板上有錯誤提示", () -> {
            if (panel.panel().locator("[aria-invalid='true']").count() > 0) {
                return "有欄位標成 aria-invalid";
            }
            if (panel.panel().getByRole(AriaRole.ALERT).count() > 0) {
                return "面板上有 alert：" + panel.panel().getByRole(AriaRole.ALERT).allInnerTexts();
            }
            if (world.page().getByRole(AriaRole.ALERTDIALOG).filter(new Locator.FilterOptions().setVisible(true)).count() > 0
                    || shell.shownDialogs().count() != 1) {
                return "畫面上除了代開單面板還有別的對話框";
            }
            Object red = panel.panel().evaluate("""
                    (panel, colors) => [...panel.querySelectorAll('*')]
                      .filter(el => el.offsetParent !== null && [...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim()))
                      .filter(el => colors.includes(getComputedStyle(el).color))
                      .map(el => el.innerText.trim())
                    """, ERROR_COLORS);
            return ((List<?>) red).isEmpty() ? null : "有錯誤色的字：" + red;
        });
    }

    @那麼("畫面上只有工具列有「代開單」鈕")
    public void onlyToolbarHasCreateRequest() {
        eventually("「代開單」鈕不只在工具列", () -> {
            Locator all = world.page().getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("代開單").setExact(true));
            if (all.count() != 1) {
                return "畫面上名稱是「代開單」的按鈕有 " + all.count() + " 顆";
            }
            return shell.toolbar().getByRole(AriaRole.BUTTON,
                    new Locator.GetByRoleOptions().setName("代開單").setExact(true)).count() == 1 ? null
                    : "那一顆「代開單」不在工具列上";
        });
    }

    @那麼("工具列上的「代開單」是唯一的實心主按鈕")
    public void createRequestIsOnlyPrimary() {
        eventually("工具列的實心主按鈕不對", () -> {
            Object result = shell.toolbar().evaluate("""
                    (bar, brand) => [...bar.querySelectorAll('button, [role="button"], a')]
                      .filter(el => el.offsetParent !== null)
                      .filter(el => getComputedStyle(el).backgroundColor === brand)
                      .map(el => (el.getAttribute('aria-label') || el.innerText || '').trim())
                    """, BRAND);
            List<?> solid = (List<?>) result;
            return solid.equals(List.of("代開單")) ? null : "工具列上品牌色底的按鈕是 " + solid;
        });
    }

    @那麼("代開單面板是關的")
    public void panelClosed() {
        eventually("代開單面板還開著", () -> {
            if (panel.panel().filter(new Locator.FilterOptions().setVisible(true)).count() > 0) {
                return "代開單面板還在";
            }
            return panel.backdrop().filter(new Locator.FilterOptions().setVisible(true)).count() == 0 ? null
                    : "遮罩還在";
        });
    }

    @那麼("其他需求框的標籤是 {string}，提示字是 {string}")
    public void otherRequestBoxLabel(String label, String placeholder) {
        eventually("其他需求框不對", () -> {
            Locator box = panel.panel().getByRole(AriaRole.TEXTBOX,
                    new Locator.GetByRoleOptions().setName(label.strip()).setExact(true));
            if (box.count() != 1) {
                return "面板上無障礙標籤是「" + label + "」的文字框有 " + box.count() + " 個";
            }
            Object multiline = box.evaluate(
                    "el => el.tagName === 'TEXTAREA' || el.getAttribute('aria-multiline') === 'true'");
            if (!Boolean.TRUE.equals(multiline)) {
                return "其他需求不是多行文字框";
            }
            String shown = box.getAttribute("placeholder");
            return placeholder.equals(shown) ? null : "提示字是「" + shown + "」";
        });
    }

    @那麼("其他需求框底下沒有「這段要給」")
    public void noSendTo() {
        eventually("其他需求框底下有「這段要給」", () ->
                panel.sendToLabel().count() == 0 && panel.otherRequestDeptGroup().count() == 0 ? null
                        : "還看得到「這段要給」或部門選擇");
    }

    @那麼("其他需求框底下是「這段要給」與「房務」「工務」，選中的是 {string}")
    public void sendToWithSelected(String dept) {
        eventually("「這段要給」不對", () -> {
            if (panel.sendToLabel().filter(new Locator.FilterOptions().setVisible(true)).count() != 1) {
                return "看不到「這段要給」";
            }
            Locator housekeeping = panel.otherRequestDept("房務");
            Locator engineering = panel.otherRequestDept("工務");
            if (housekeeping.count() != 1 || engineering.count() != 1) {
                return "部門選項不是「房務」「工務」兩顆";
            }
            BoundingBox box = panel.otherRequestBox().boundingBox();
            BoundingBox first = housekeeping.boundingBox();
            BoundingBox second = engineering.boundingBox();
            if (first.y < box.y + box.height - TOLERANCE_PX) {
                return "部門選項不在其他需求框底下";
            }
            if (first.x > second.x) {
                return "部門選項的順序不是房務、工務";
            }
            String pressedHousekeeping = housekeeping.getAttribute("aria-pressed");
            String pressedEngineering = engineering.getAttribute("aria-pressed");
            boolean wantHousekeeping = "房務".equals(dept.strip());
            return ("true".equals(pressedHousekeeping) == wantHousekeeping)
                    && ("true".equals(pressedEngineering) == !wantHousekeeping)
                    && pressedHousekeeping != null && pressedEngineering != null ? null
                    : "選中的樣子不對：房務 aria-pressed=" + pressedHousekeeping + "，工務 aria-pressed=" + pressedEngineering;
        });
    }

    @那麼("這組部門選項的無障礙名稱是 {string}")
    public void deptGroupName(String name) {
        eventually("部門選項的群組名稱不對", () -> {
            Locator named = panel.panel().getByRole(AriaRole.GROUP,
                    new Locator.GetByRoleOptions().setName(name.strip()).setExact(true));
            return named.and(panel.otherRequestDeptGroup()).count() == 1 ? null
                    : "「房務」「工務」所在的群組名稱不是「" + name + "」";
        });
    }

    @那麼("其他需求框裡只有前 {int} 個字")
    public void otherRequestTruncated(int count) {
        String typed = scenario.typedOtherText();
        int end = typed.offsetByCodePoints(0, Math.min(count, typed.codePointCount(0, typed.length())));
        String expected = typed.substring(0, end);
        eventually("其他需求框的字不對", () -> {
            String value = panel.otherRequestBox().inputValue();
            return value.equals(expected) ? null : "框裡是「" + value + "」（" + value.codePointCount(0, value.length())
                    + " 個字），應該是前 " + count + " 個字「" + expected + "」";
        });
    }

    @那麼("其他需求沒有字數計數")
    public void noCharacterCounter() {
        eventually("其他需求有字數計數", () -> {
            Matcher counter = Pattern.compile("\\d+\\s*/\\s*60(?!\\d)|\\d+\\s*字").matcher(panel.panel().innerText());
            return counter.find() ? "面板上有「" + counter.group() + "」" : null;
        });
    }

    @那麼("類似單提醒如下：")
    public void similarNoticeIs(DataTable table) {
        List<String> expected = table.asMaps().stream().map(row -> ShellFrame.normalizeSpaces(row.get("提醒內容"))).toList();
        eventually("類似單提醒不對", () -> {
            List<String> lines = panel.similarTasksLines();
            return lines.equals(expected) ? null : "提醒是 " + lines + "，應該是 " + expected;
        });
    }

    @那麼("類似單提醒有以下各行：")
    public void similarNoticeRows(DataTable table) {
        List<String> expected = table.asMaps().stream()
                .map(row -> ShellFrame.normalizeSpaces(row.get("提醒的一行"))).toList();
        eventually("類似單提醒不對", () -> {
            List<String> lines = panel.similarTasksLines();
            if (lines.size() < 2) {
                return lines.isEmpty() ? "面板上沒有類似單提醒" : "提醒只有 " + lines;
            }
            List<String> middle = lines.subList(1, lines.size() - 1);
            return middle.equals(expected) ? null : "提醒裡的單是 " + middle + "，應該是 " + expected;
        });
    }

    @那麼("類似單提醒有一行以 {string} 開頭")
    public void similarNoticeHasLineStarting(String prefix) {
        eventually("類似單提醒沒有這一行", () -> {
            List<String> lines = panel.similarTasksLines();
            if (lines.isEmpty()) {
                return "面板上沒有類似單提醒";
            }
            return lines.stream().anyMatch(line -> line.startsWith(prefix)) ? null : "提醒是 " + lines;
        });
    }

    @那麼("沒有類似單提醒")
    public void noSimilarNotice() {
        eventually("面板上有類似單提醒", () -> {
            List<String> lines = panel.similarTasksLines();
            return lines.isEmpty() ? null : "提醒是 " + lines;
        });
    }

    @那麼("代開單面板的介面文字都是 {string}")
    public void panelLanguage(String languageName) {
        Language language = Language.of(languageName);
        eventually("代開單面板的介面文字不是" + languageName, () -> {
            List<String> problems = new ArrayList<>();
            expectText(problems, "標題", panel.title().innerText(), CreateRequestPanel.word(language, "代開單"));
            expectCount(problems, "房號標籤", panel.panel().getByRole(AriaRole.TEXTBOX, new Locator.GetByRoleOptions()
                    .setName(CreateRequestPanel.word(language, "房號")).setExact(true)));
            expectText(problems, "房號提示字", panel.roomInput().getAttribute("placeholder"),
                    CreateRequestPanel.word(language, "輸入房號，例如 305"));
            for (String section : List.of("常用品項（點一下加一份，會依品項自動分到房務或工務）", "其他需求（選填）")) {
                expectCount(problems, "區塊標題", panel.panel().getByText(CreateRequestPanel.word(language, section),
                        new Locator.GetByTextOptions().setExact(true)));
            }
            Map<String, Integer> chosen = new LinkedHashMap<>();
            List<List<String>> tiles = panel.tilesInOrder(language);
            for (int i = 0; i < CreateRequestPanel.ITEMS.size(); i++) {
                String item = CreateRequestPanel.ITEMS.get(i);
                String dept = CreateRequestPanel.ENGINEERING_ITEMS.contains(item) ? "工務" : "房務";
                List<String> lines = tiles.get(i);
                if (!lines.contains(CreateRequestPanel.word(language, item))
                        || !lines.contains(CreateRequestPanel.word(language, dept))) {
                    problems.add("「" + item + "」那一格是 " + lines);
                }
                int quantity = panel.quantityOf(item);
                if (quantity > 0) {
                    chosen.put(item, quantity);
                }
            }
            Locator summary = panel.summary().filter(new Locator.FilterOptions().setVisible(true));
            if (!chosen.isEmpty()) {
                String text = summary.count() == 1 ? ShellFrame.normalizeSpaces(summary.innerText().replace('\n', ' ')) : "（沒有摘要）";
                expectText(problems, "送出摘要", text, expectedSummary(language, chosen));
            }
            expectText(problems, "送出鈕", ShellFrame.normalizeSpaces(panel.submitButton().innerText()),
                    CreateRequestPanel.submitLabel(language, chosen.size()));
            if (language != Language.TRADITIONAL_CHINESE) {
                String allText = panel.panel().innerText();
                Matcher chinese = Pattern.compile("(房號|常用品項|其他需求|送出|會開|房務|工務|毛巾)").matcher(allText);
                if (chinese.find()) {
                    problems.add("面板上夾雜繁體中文「" + chinese.group() + "」");
                }
            }
            return problems.isEmpty() ? null : String.join("；", problems);
        });
    }

    @那麼("代開單面板上的內容與按送出之前一樣")
    public void panelContentUnchanged() {
        CreateRequestScenario.PanelSnapshot before = scenario.snapshot();
        eventually("代開單面板上的內容和按送出之前不一樣", () -> {
            if (!panel.isOpen()) {
                return "代開單面板已經關了";
            }
            CreateRequestScenario.PanelSnapshot now = CreateRequestScenario.PanelSnapshot.of(panel.readContent());
            return now.equals(before) ? null : "現在是 " + now + "，按送出之前是 " + before;
        });
    }

    @那麼("畫面換到總覽看板，數量卡篩選與搜尋房號是空的，沒有開著任何詳情面板")
    public void backOnCleanBoard() {
        DetailPanel detail = new DetailPanel(world);
        eventually("畫面沒有換到乾淨的總覽看板", () -> {
            if (!shell.onPage("總覽看板")) {
                return "網址是 " + world.page().url();
            }
            Locator heading = shell.toolbar().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions()
                    .setName(ShellFrame.titleOf("總覽看板")).setExact(true).setLevel(1));
            if (heading.count() != 1) {
                return "工具列的頁標題不是「" + ShellFrame.titleOf("總覽看板") + "」";
            }
            List<String> pressed = board.readTiles().stream().filter(BoardPage.TileReading::pressed)
                    .map(BoardPage.TileReading::text).toList();
            if (!pressed.isEmpty()) {
                return "數量卡 " + pressed + " 是選中的樣子";
            }
            if (board.filterPill().count() > 0) {
                return "控制列有篩選膠囊「" + board.filterPillText() + "」";
            }
            String search = board.roomSearch().inputValue();
            if (!search.isEmpty()) {
                return "「搜尋房號」是「" + search + "」";
            }
            return detail.panel().filter(new Locator.FilterOptions().setVisible(true)).count() == 0 ? null
                    : "畫面上還開著詳情面板";
        });
    }

    @那麼("{string} 欄多了以下這次開出的卡片，外框高亮 {double} 秒後恢復：")
    public void newCardsHighlighted(String column, double seconds, DataTable table) {
        List<List<String>> expected = table.asMaps().stream()
                .map(row -> List.of(row.get("房號").strip(), row.get("標題").strip())).toList();
        JsonNode result = scenario.lastCreateResult();
        List<List<String>> responded = new ArrayList<>();
        for (JsonNode task : result.path("tasks")) {
            responded.add(List.of(task.path("roomNo").asText(), task.path("title").asText()));
        }
        if (!new HashSet<>(responded).equals(new HashSet<>(expected)) || responded.size() != expected.size()) {
            throw new AssertionError("代開單回應的卡片是 " + responded + "，不是表格的 " + expected);
        }
        eventually("「" + column + "」欄沒有這次開出的卡片，或它們沒有高亮", () -> {
            for (List<String> card : expected) {
                Locator found = cardIn(column, card.get(0), card.get(1));
                if (found.count() != 1) {
                    return "「" + column + "」欄裡 " + card.get(0) + "「" + card.get(1) + "」的卡片有 " + found.count() + " 張";
                }
                if (!Boolean.TRUE.equals(found.evaluate(HIGHLIGHTED))) {
                    return card.get(0) + "「" + card.get(1) + "」不是高亮的樣子";
                }
            }
            int highlighted = panel.createdHighlights().count();
            return highlighted == expected.size() ? null : "看板上高亮的卡片有 " + highlighted + " 張，應該剛好 " + expected.size() + " 張";
        });
        world.page().clock().fastForward(Math.round(seconds * 1000) + 100);
        eventually("這次開出的卡片過了 " + seconds + " 秒還在高亮", () -> {
            for (List<String> card : expected) {
                Locator found = cardIn(column, card.get(0), card.get(1));
                if (found.count() != 1 || Boolean.TRUE.equals(found.evaluate(HIGHLIGHTED))) {
                    return card.get(0) + "「" + card.get(1) + "」還是高亮的樣子，或已不在「" + column + "」欄";
                }
            }
            return panel.createdHighlights().count() == 0 ? null : "看板上還有高亮的卡片";
        });
    }

    @那麼("這次開出的卡片如下：")
    public void newCardsLook(DataTable table) {
        JsonNode result = scenario.lastCreateResult();
        Map<String, UUID> idByTitle = new LinkedHashMap<>();
        for (JsonNode task : result.path("tasks")) {
            idByTitle.put(task.path("title").asText(), UUID.fromString(task.path("id").asText()));
        }
        List<Map<String, String>> rows = table.asMaps();
        eventually("這次開出的卡片不對", () -> {
            for (Map<String, String> row : rows) {
                String title = row.get("標題").strip();
                List<CardReading> cards = board.readCardsIn(board.wholePage()).stream()
                        .filter(card -> card.middle().startsWith(title)).toList();
                if (cards.size() != 1) {
                    return "看板上標題是「" + title + "」的卡片有 " + cards.size() + " 張";
                }
                CardReading card = cards.get(0);
                if (!row.get("部門").strip().equals(card.dept()) || !("· " + row.get("類型").strip()).equals(card.type())) {
                    return "「" + title + "」底部是「" + card.dept() + " " + card.type() + "」";
                }
                if (!card.labels().contains(row.get("來源圖示").strip())) {
                    return "「" + title + "」的圖示名稱是 " + card.labels() + "，沒有「" + row.get("來源圖示").strip() + "」";
                }
                if (!card.avatar().isEmpty()) {
                    return "「" + title + "」有負責人頭像「" + card.avatar() + "」";
                }
            }
            return null;
        });
        JsonNode deskBoard = world.backend().getJson("/desk/board", world.staff(WhenStart.BACKEND_READER));
        for (Map<String, String> row : rows) {
            UUID id = idByTitle.get(row.get("標題").strip());
            if (id == null) {
                throw new AssertionError("代開單回應裡沒有標題是「" + row.get("標題").strip() + "」的單：" + idByTitle.keySet());
            }
            JsonNode task = findTask(deskBoard, id);
            if (task == null || !task.path("waitingSince").asText().equals(task.path("createdAt").asText())) {
                throw new AssertionError("後端「" + row.get("標題").strip() + "」的等待起點不是建立的時刻：" + task);
            }
        }
    }

    @那麼("畫面上沒有任何浮條，也沒有「復原」可以按")
    public void noToastNoUndo() {
        eventually("畫面上有浮條或「復原」", () -> {
            if (shell.assignToast().count() > 0) {
                return "畫面底部有浮條";
            }
            Locator undo = world.page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("復原").setExact(true));
            if (undo.count() > 0) {
                return "畫面上有「復原」";
            }
            Locator alerts = world.page().getByRole(AriaRole.ALERT).filter(new Locator.FilterOptions().setVisible(true));
            return alerts.count() == 0 ? null : "畫面上有提示 " + alerts.allInnerTexts();
        });
    }

    @那麼("後端這次開出的單如下：")
    public void backendCreated(DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        Map<UUID, Map<String, Object>> created = eventuallyCreated(rows.size());
        Map<String, List<Map<String, Object>>> createdByTitle = new LinkedHashMap<>();
        created.values().forEach(task ->
                createdByTitle.computeIfAbsent(String.valueOf(task.get("title")), key -> new ArrayList<>()).add(task));
        Map<String, Long> actualTitles = titleCounts(created.values().stream().map(task -> String.valueOf(task.get("title"))).toList());
        Map<String, Long> expectedTitles = titleCounts(rows.stream().map(row -> row.get("標題").strip()).toList());
        if (!actualTitles.equals(expectedTitles) || created.size() != rows.size()) {
            throw new AssertionError("後端這次開出的單（標題：張數）是 " + actualTitles + "，應該剛好是 " + expectedTitles);
        }
        Map<String, Integer> nextOfTitle = new HashMap<>();
        List<String> problems = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String title = row.get("標題").strip();
            int index = nextOfTitle.merge(title, 1, Integer::sum) - 1;
            Map<String, Object> task = createdByTitle.get(title).get(index);
            UUID id = (UUID) task.get("id");
            Map<String, Object> ticket = repairTicketOf(id);
            String no = (String) task.get("task_no");
            expectCell(problems, title, row, "房號", task.get("room_no"));
            expectCell(problems, title, row, "部門", task.get("dept"));
            expectCell(problems, title, row, "類型", task.get("type"));
            expectCell(problems, title, row, "狀態", task.get("state"));
            expectCell(problems, title, row, "來源", task.get("source"));
            expectCell(problems, title, row, "待認領來源分流", task.get("pool_source"));
            String assignee = blankToNull(row.get("負責人"));
            if (assignee != null) {
                Object actual = task.get("assignee_id");
                Object expectedId = "沒有".equals(assignee) ? null : world.staff(assignee).id();
                if (!Objects.equals(expectedId, actual)) {
                    problems.add("「" + title + "」的負責人是 " + actual + "，應該是" + assignee);
                }
            }
            String prefix = blankToNull(row.get("單號開頭"));
            boolean repair = "W-".equals(prefix) || ticket != null && prefix == null;
            if ("T-".equals(prefix)) {
                if (no == null || !no.startsWith("T-")) {
                    problems.add("「" + title + "」的單號是 " + no + "，應該是 T- 開頭");
                }
                if (ticket != null) {
                    problems.add("「" + title + "」是 T- 的單，卻有工務單 " + ticket.get("ticket_no"));
                }
            }
            if (repair) {
                if ("W-".equals(prefix) && no != null) {
                    problems.add("「" + title + "」是工務單，任務的 task_no 應該是空的，卻是 " + no);
                }
                if (ticket == null) {
                    problems.add("「" + title + "」沒有工務單");
                } else {
                    if ("W-".equals(prefix) && !String.valueOf(ticket.get("ticket_no")).startsWith("W-")) {
                        problems.add("「" + title + "」的工務單號是 " + ticket.get("ticket_no"));
                    }
                    expectCell(problems, title, row, "修繕分類", ticket.get("category"));
                    expectCell(problems, title, row, "說明", ticket.get("description"));
                    String reporter = blankToNull(row.get("報修人"));
                    if (reporter != null && !Objects.equals(world.staff(reporter).id(), ticket.get("reporter_id"))) {
                        problems.add("「" + title + "」的報修人是 " + ticket.get("reporter_id") + "，應該是" + reporter);
                    }
                }
            } else if (blankToNull(row.get("修繕分類")) != null || blankToNull(row.get("說明")) != null
                    || blankToNull(row.get("報修人")) != null) {
                problems.add("「" + title + "」沒有工務單，表格卻寫了工務單的欄位");
            }
            List<String> events = world.backend().queryRows(
                    "SELECT type FROM task_events WHERE tenant_id = ? AND task_id = ? ORDER BY seq",
                    world.defaults().tenantId(), id).stream().map(event -> String.valueOf(event.get("type"))).toList();
            if (!events.equals(List.of("created"))) {
                problems.add("「" + title + "」的事件是 " + events + "，應該只有一筆 created、沒有 triaged");
            }
            String dept = String.valueOf(task.get("dept"));
            if (("housekeeping".equals(dept) || "engineering".equals(dept)) && "pool".equals(task.get("state"))
                    && !inDeptPool(dept, id)) {
                problems.add("「" + title + "」不在" + dept + "的待認領清單裡");
            }
        }
        if (!problems.isEmpty()) {
            throw new AssertionError("後端這次開出的單不對：" + String.join("；", problems));
        }
    }

    @那麼("後端這次一張單都沒有開出")
    public void backendCreatedNothing() {
        CreateRequestScenario.BackendBefore before = scenario.backendBefore();
        world.page().waitForTimeout(300);
        CreateRequestScenario.BackendBefore now = scenario.readBackend();
        Set<UUID> added = new HashSet<>(now.taskIds());
        added.removeAll(before.taskIds());
        if (!added.isEmpty() || now.taskIds().size() != before.taskIds().size()) {
            throw new AssertionError("後端多了 " + added.size() + " 張單：" + added);
        }
        if (now.repairTickets() != before.repairTickets()) {
            throw new AssertionError("後端的工務單從 " + before.repairTickets() + " 張變成 " + now.repairTickets() + " 張");
        }
        for (String dept : List.of("housekeeping", "engineering")) {
            JsonNode pool = world.backend().getJson("/pool?dept=" + dept, world.staff(WhenStart.BACKEND_READER));
            for (JsonNode item : pool.path("items")) {
                UUID id = UUID.fromString(item.path("taskId").asText());
                if (!before.taskIds().contains(id)) {
                    throw new AssertionError(dept + " 的待認領多了一張單 " + id);
                }
            }
        }
        if (panel.createdHighlights().count() > 0) {
            throw new AssertionError("看板上有這次開出的高亮卡片");
        }
        if (shell.loggedIn() && (shell.onPage("總覽看板"))) {
            int known = (int) before.taskIds().stream().count();
            int cards = BoardPage.COLUMN_NAMES.stream()
                    .mapToInt(column -> board.cardsIn(board.column(column)).count())
                    .sum();
            if (cards > known) {
                throw new AssertionError("看板上有 " + cards + " 張卡片，多於後端 When 之前的 " + known + " 張單");
            }
        }
    }

    @那麼("後端 {string} 只有這次開出的 {int} 張單，沒有重複")
    public void deskTasksNotDuplicated(String roomNo, int count) {
        List<String> titles = new ArrayList<>();
        eventually("後端 " + roomNo + " 代開的單不對", () -> {
            titles.clear();
            titles.addAll(deskTaskTitles(roomNo));
            if (titles.size() != count) {
                return "代開的單有 " + titles.size() + " 張：" + titles;
            }
            return new HashSet<>(titles).size() == titles.size() ? null : "標題有重複：" + titles;
        });
        if (shell.loggedIn() && shell.onPage("總覽看板")) {
            eventually("看板上 " + roomNo + " 的卡片有重複", () -> {
                List<String> shown = board.readCardsOf(roomNo.strip()).stream().map(CardReading::middle).toList();
                for (String title : titles) {
                    long same = shown.stream().filter(middle -> middle.startsWith(title)).count();
                    if (same != 1) {
                        return "「" + title + "」的卡片有 " + same + " 張";
                    }
                }
                return null;
            });
        }
    }

    @那麼("後端 {string} 現在有 {int} 張 {string} 的單")
    public void tasksWithMenuKey(String roomNo, int count, String item) {
        eventually("後端 " + roomNo + " 的「" + item + "」單不對", () -> {
            int found = 0;
            for (Map<String, Object> row : world.backend().queryRows("""
                    SELECT t.note FROM tasks t JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                     WHERE t.tenant_id = ? AND r.room_no = ? AND t.ended_at IS NULL
                    """, world.defaults().tenantId(), roomNo.strip())) {
                if (item.strip().equals(menuKeyOf((String) row.get("note")))) {
                    found++;
                }
            }
            return found == count ? null : "有 " + found + " 張";
        });
    }

    @那麼("送出還沒有結果之前，送出鈕不能按，最後只送出一次")
    public void submitLockedUntilResult() {
        CreateRequestScenario.RapidPresses presses = scenario.rapidPresses();
        if (presses.disabledAtEachPress().isEmpty() || presses.disabledAtEachPress().contains(false)) {
            throw new AssertionError("還沒有結果之前，送出鈕不是每一次都是停用的樣子：" + presses.disabledAtEachPress());
        }
        if (!presses.busyWhileWaiting()) {
            throw new AssertionError("等回應的時候送出鈕沒有標成 aria-busy");
        }
        if (presses.requestsSent() != 1) {
            throw new AssertionError("這段期間送出了 " + presses.requestsSent() + " 個代開單請求，應該只有 1 個");
        }
    }

    @那麼("畫面回到登入頁，沒有跳任何提示")
    public void backToLoginSilently() {
        LoginPage login = new LoginPage(world);
        eventually("畫面沒有安靜地回到登入頁", () -> {
            if (!login.onLoginPath()) {
                return "網址是 " + world.page().url();
            }
            Object session = world.page().evaluate("key => sessionStorage.getItem(key)",
                    com.athena.web.bdd.support.SessionInjector.SESSION_KEY);
            if (session != null) {
                return "分頁裡還有登入憑證";
            }
            return shell.shownDialogs().count() == 0 ? null : "畫面上有對話框 " + shell.shownDialogs().allInnerTexts();
        });
    }

    @那麼("代開單面板完整顯示在視窗內，內容在面板裡捲動，送出鈕一直看得到")
    public void panelFitsAndScrolls() {
        eventually("代開單面板沒有完整顯示", () -> {
            int[] viewport = viewport();
            BoundingBox box = panel.panel().boundingBox();
            if (box == null || box.x < -TOLERANCE_PX || box.y < -TOLERANCE_PX
                    || box.x + box.width > viewport[0] + TOLERANCE_PX || box.y + box.height > viewport[1] + TOLERANCE_PX) {
                return "面板超出視窗：" + (box == null ? "沒有畫出來" : describe(box));
            }
            Object pageScrolls = world.page().evaluate(
                    "() => document.documentElement.scrollHeight > innerHeight + 1 || document.documentElement.scrollWidth > innerWidth + 1");
            return Boolean.TRUE.equals(pageScrolls) ? "整頁有捲軸" : null;
        });
        Object scrollable = panel.panel().evaluate(MARK_SCROLLER);
        if (!Boolean.TRUE.equals(scrollable)) {
            throw new AssertionError("面板裡沒有內容比可用高度高、可以捲動的區塊");
        }
        for (String end : List.of("top", "bottom")) {
            panel.panel().evaluate("(panel, end) => { const s = panel.querySelector('[data-web-bdd-scroller]');"
                    + " s.scrollTop = end === 'top' ? 0 : s.scrollHeight; }", end);
            panel.settle();
            int[] viewport = viewport();
            BoundingBox submit = panel.submitButton().boundingBox();
            if (submit == null || submit.y < 0 || submit.y + submit.height > viewport[1] + TOLERANCE_PX
                    || submit.x < 0 || submit.x + submit.width > viewport[0] + TOLERANCE_PX) {
                throw new AssertionError("內容捲到" + ("top".equals(end) ? "最上" : "最下") + "時送出鈕不完整在視窗內："
                        + (submit == null ? "看不到" : describe(submit)));
            }
        }
        panel.panel().evaluate("panel => panel.querySelector('[data-web-bdd-scroller]')?.removeAttribute('data-web-bdd-scroller')");
    }

    @那麼("代開單面板上的品項格子、加減鈕、移除鈕、建議房號、部門選項與送出鈕都至少 {int} × {int}")
    public void touchTargetsLargeEnough(int width, int height) {
        List<Locator> targets = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String item : CreateRequestPanel.ITEMS) {
            targets.add(panel.itemTile(item));
            names.add("「" + item + "」格子");
            if (panel.decreaseButton(item).count() > 0) {
                targets.add(panel.decreaseButton(item));
                names.add("「" + item + "」的「−」");
                targets.add(panel.increaseButton(item));
                names.add("「" + item + "」的「+」");
            }
            if (panel.removeButton(item).count() > 0) {
                targets.add(panel.removeButton(item));
                names.add("「" + item + "」的「移除」");
            }
        }
        if (panel.decreaseButton("毛巾").count() == 0 && panel.removeButton("冷氣").count() == 0) {
            throw new AssertionError("面板上沒有任何加減鈕或移除鈕可以量");
        }
        int suggestions = panel.suggestions().count();
        if (suggestions == 0) {
            throw new AssertionError("面板上沒有建議房號可以量");
        }
        for (int i = 0; i < suggestions; i++) {
            targets.add(panel.suggestions().nth(i));
            names.add("建議房號第 " + (i + 1) + " 顆");
        }
        for (String dept : List.of("房務", "工務")) {
            targets.add(panel.otherRequestDept(dept));
            names.add("「" + dept + "」選項");
        }
        targets.add(panel.submitButton());
        names.add("送出鈕");
        List<String> small = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            Locator target = targets.get(i);
            if (target.count() != 1) {
                small.add(names.get(i) + "找不到（" + target.count() + " 個）");
                continue;
            }
            target.scrollIntoViewIfNeeded();
            BoundingBox box = target.boundingBox();
            if (box == null || box.width + 0.5 < width || box.height + 0.5 < height) {
                small.add(names.get(i) + (box == null ? "沒有畫出來" : " " + Math.round(box.width) + "×" + Math.round(box.height)));
            }
        }
        if (!small.isEmpty()) {
            throw new AssertionError("小於 " + width + "×" + height + " 的：" + String.join("、", small));
        }
    }

    private static final String INSIDE_SCROLLER = """
            (el, panel) => {
              for (let node = el.parentElement; node && node !== panel; node = node.parentElement) {
                const overflow = getComputedStyle(node).overflowY;
                if (overflow === 'auto' || overflow === 'scroll') return true;
              }
              return false;
            }
            """;

    private static final String MARK_SCROLLER = """
            panel => {
              const scroller = [panel, ...panel.querySelectorAll('*')].find(el => {
                const overflow = getComputedStyle(el).overflowY;
                return (overflow === 'auto' || overflow === 'scroll') && el.scrollHeight > el.clientHeight + 1;
              });
              if (!scroller) return false;
              scroller.setAttribute('data-web-bdd-scroller', '');
              return true;
            }
            """;

    private static final String HIGHLIGHTED =
            "el => !!(el.closest('[data-highlight=\"created\"]') || el.querySelector('[data-highlight=\"created\"]'))";

    private Locator cardIn(String column, String roomNo, String title) {
        return board.cardsIn(board.column(column.strip())).filter(new Locator.FilterOptions()
                .setHasText(title)).and(board.card(roomNo));
    }

    private String readingOrderProblem() {
        List<double[]> positions = panel.tilePositions();
        for (int i = 1; i < positions.size(); i++) {
            double[] previous = positions.get(i - 1);
            double[] current = positions.get(i);
            if (previous == null || current == null) {
                return "有品項格子沒有畫出來";
            }
            boolean sameRow = Math.abs(previous[1] - current[1]) <= TOLERANCE_PX;
            boolean nextRow = current[1] > previous[1] + TOLERANCE_PX;
            if (!(sameRow && current[0] > previous[0] || nextRow)) {
                return "「" + CreateRequestPanel.ITEMS.get(i) + "」不在「" + CreateRequestPanel.ITEMS.get(i - 1)
                        + "」之後（左上到右下）";
            }
        }
        return null;
    }

    private boolean fullWidthOfCard(String item) {
        BoundingBox button = panel.removeButton(item).boundingBox();
        BoundingBox card = panel.itemCard(item).boundingBox();
        return button != null && card != null && button.width >= card.width - 32;
    }

    private static List<String> parseUnderRow(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> cells = new ArrayList<>();
        Matcher matcher = UNDER_ROW_CELL.matcher(text);
        while (matcher.find()) {
            cells.add(matcher.group(1));
        }
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("「底下」要寫成「−」「2」「+」或「移除」，收到「" + text + "」");
        }
        return cells;
    }

    private static String textOf(Locator locator) {
        return ShellFrame.normalizeSpaces(locator.innerText());
    }

    private Map<String, String> occupiedRooms() {
        Map<String, String> rooms = new LinkedHashMap<>();
        for (JsonNode room : roomsList()) {
            if (room.path("occupied").asBoolean()) {
                rooms.put(room.path("roomNo").asText(), room.path("guest").path("name").asText());
            }
        }
        return rooms;
    }

    private JsonNode deskRoom(String roomNo) {
        for (JsonNode room : roomsList()) {
            if (roomNo.equals(room.path("roomNo").asText())) {
                return room;
            }
        }
        throw new AssertionError("房間清單裡沒有 " + roomNo);
    }

    private JsonNode roomsList() {
        JsonNode rooms = world.backend().listDeskRooms(world.staff(WhenStart.BACKEND_READER));
        return rooms.has("rooms") ? rooms.path("rooms") : rooms;
    }

    private Map<UUID, Map<String, Object>> eventuallyCreated(int expectedCount) {
        Set<UUID> before = scenario.backendBefore().taskIds();
        Map<UUID, Map<String, Object>> created = new LinkedHashMap<>();
        Supplier<String> read = () -> {
            created.clear();
            for (Map<String, Object> row : world.backend().queryRows("""
                    SELECT t.*, r.room_no FROM tasks t JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                     WHERE t.tenant_id = ? ORDER BY t.created_at, t.id
                    """, world.defaults().tenantId())) {
                UUID id = (UUID) row.get("id");
                if (!before.contains(id)) {
                    created.put(id, row);
                }
            }
            return created.size() >= expectedCount ? null : "後端只多了 " + created.size() + " 張";
        };
        try {
            world.page().waitForCondition(() -> read.get() == null,
                    new Page.WaitForConditionOptions().setTimeout(ASSERT_TIMEOUT_MS));
        } catch (TimeoutError timeout) {
        }
        return created;
    }

    private static Map<String, Long> titleCounts(List<String> titles) {
        Map<String, Long> counts = new TreeMap<>();
        titles.forEach(title -> counts.merge(title, 1L, Long::sum));
        return counts;
    }

    private Map<String, Object> repairTicketOf(UUID taskId) {
        List<Map<String, Object>> rows = world.backend().queryRows(
                "SELECT * FROM repair_tickets WHERE tenant_id = ? AND task_id = ?", world.defaults().tenantId(), taskId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean inDeptPool(String dept, UUID taskId) {
        JsonNode pool = world.backend().getJson("/pool?dept=" + dept, world.staff(WhenStart.BACKEND_READER));
        for (JsonNode item : pool.path("items")) {
            if (taskId.toString().equals(item.path("taskId").asText())) {
                return true;
            }
        }
        return false;
    }

    private List<String> deskTaskTitles(String roomNo) {
        return world.backend().queryRows("""
                SELECT t.title FROM tasks t JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                 WHERE t.tenant_id = ? AND r.room_no = ? AND t.source = 'desk' ORDER BY t.created_at
                """, world.defaults().tenantId(), roomNo.strip()).stream()
                .map(row -> String.valueOf(row.get("title"))).toList();
    }

    private static JsonNode findTask(JsonNode deskBoard, UUID id) {
        for (JsonNode task : deskBoard.path("tasks")) {
            if (id.toString().equals(task.path("id").asText())) {
                return task;
            }
        }
        return null;
    }

    private static String menuKeyOf(String note) {
        if (note == null || !note.strip().startsWith("{")) {
            return null;
        }
        try {
            JsonNode parsed = JSON.readTree(note);
            return parsed.hasNonNull("menu_key") ? parsed.path("menu_key").asText() : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException notJson) {
            return null;
        }
    }

    private static String expectedSummary(Language language, Map<String, Integer> chosen) {
        List<String> housekeeping = new ArrayList<>();
        List<String> engineering = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : chosen.entrySet()) {
            String name = CreateRequestPanel.word(language, entry.getKey());
            if (CreateRequestPanel.ENGINEERING_ITEMS.contains(entry.getKey())) {
                engineering.add(name);
            } else {
                housekeeping.add(name + " ×" + entry.getValue());
            }
        }
        String separator = CreateRequestPanel.listSeparator(language);
        return CreateRequestPanel.summaryText(language, chosen.size(),
                housekeeping.isEmpty() ? null : String.join(separator, housekeeping),
                engineering.isEmpty() ? null : String.join(separator, engineering));
    }

    private void expectCell(List<String> problems, String title, Map<String, String> row, String column, Object actual) {
        String expected = blankToNull(row.get(column));
        if (expected != null && !expected.equals(actual == null ? null : String.valueOf(actual))) {
            problems.add("「" + title + "」的" + column + "是 " + actual + "，應該是 " + expected);
        }
    }

    private static void expectText(List<String> problems, String what, String actual, String expected) {
        String shown = actual == null ? null : ShellFrame.normalizeSpaces(actual);
        if (!expected.equals(shown)) {
            problems.add(what + "是「" + shown + "」，應該是「" + expected + "」");
        }
    }

    private static void expectCount(List<String> problems, String what, Locator locator) {
        if (locator.count() != 1) {
            problems.add(what + "不是該語言的寫法");
        }
    }

    private int[] viewport() {
        Object size = world.page().evaluate("() => [innerWidth, innerHeight]");
        List<?> values = (List<?>) size;
        return new int[] {((Number) values.get(0)).intValue(), ((Number) values.get(1)).intValue()};
    }

    private static String describe(BoundingBox box) {
        return "(" + Math.round(box.x) + ", " + Math.round(box.y) + ", " + Math.round(box.width) + "×"
                + Math.round(box.height) + ")";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void eventually(String what, Supplier<String> problem) {
        String[] last = {null};
        try {
            world.page().waitForCondition(() -> {
                try {
                    return (last[0] = problem.get()) == null;
                } catch (com.microsoft.playwright.PlaywrightException | NullPointerException notYet) {
                    last[0] = "畫面還沒畫好：" + notYet.getMessage();
                    return false;
                }
            }, new Page.WaitForConditionOptions().setTimeout(ASSERT_TIMEOUT_MS));
        } catch (TimeoutError timeout) {
            throw new AssertionError(what + "：" + last[0], timeout);
        }
    }
}
