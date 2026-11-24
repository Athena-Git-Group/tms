package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.BrowserRegex;
import com.athena.web.bdd.support.JsResult;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BoardPage extends PageObject {

    public BoardPage(WebWorld world) {
        super(world);
    }

    public Locator card(String roomNo) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(roomNo) + "(\\s|$)")));
    }

    public static final List<String> COLUMN_NAMES =
            List.of("待派單", "待認領", "已接", "進行中", "已完成", "中斷", "延後中", "已結案");

    private static final String BODY_OF_HEADER = """
            header => {
              for (let node = header.parentElement; node; node = node.parentElement) {
                const overflow = getComputedStyle(node).overflowX;
                if (overflow === 'auto' || overflow === 'scroll') return node;
              }
              throw new Error('欄頭往上找不到可以橫向捲動的看板本體');
            }
            """;

    private static final String AT_RIGHT_END = "body => body.scrollLeft + body.clientWidth >= body.scrollWidth - 1";

    public Locator columnHeader(String column) {
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(column) + "(\\s|$)")));
    }

    public Locator columnHeaders() {
        String names = COLUMN_NAMES.stream().map(BrowserRegex::literal).collect(Collectors.joining("|"));
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^(" + names + ")(\\s|$)")));
    }

    public ElementHandle boardBody() {
        Locator anyHeader = columnHeaders().first();
        anyHeader.waitFor();
        return anyHeader.evaluateHandle(BODY_OF_HEADER).asElement();
    }

    public BoundingBox boardBodyBox() {
        return boardBody().boundingBox();
    }

    public Locator tileRow() {
        return countCard("處理中").locator("xpath=..");
    }

    public Map<String, Double> columnWidths() {
        Map<String, Double> widths = new LinkedHashMap<>();
        for (String column : COLUMN_NAMES) {
            Locator header = columnHeader(column);
            if (header.count() > 0) {
                widths.put(column, header.first().boundingBox().width);
            }
        }
        return widths;
    }

    /**
     * 觸控的瀏覽器設定直接把捲動位置設到最右邊，因為 WebKit 送不出瀏覽器自己產生的滑動手勢，
     * 測試自己送的觸控事件也不會讓瀏覽器捲動。
     */
    public void scrollBoardToRightEnd() {
        ElementHandle body = boardBody();
        if (world.profile().touch()) {
            body.evaluate("body => body.scrollTo({ left: body.scrollWidth - body.clientWidth })");
        } else {
            body.scrollIntoViewIfNeeded();
            BoundingBox box = body.boundingBox();
            double visibleHeight = Math.min(box.height, page().viewportSize().height - box.y);
            page().mouse().move(box.x + box.width / 2, box.y + visibleHeight / 2);
            double rest = ((Number) body.evaluate(
                    "body => body.scrollWidth - body.clientWidth - body.scrollLeft")).doubleValue();
            page().mouse().wheel(rest + 100, 0);
        }
        page().waitForFunction(AT_RIGHT_END, body, new Page.WaitForFunctionOptions().setTimeout(5_000));
    }

    public Locator moreStatesButtons() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^還有 \\d+ 個狀態$")));
    }

    public Locator countCard(String name) {
        String description = switch (name.strip()) {
            case "待派單" -> "待派單$";
            case "待認領" -> "待認領（";
            case "處理中" -> "處理中（";
            case "等最久" -> "(等最久 · |沒有在等的單)";
            default -> throw new IllegalArgumentException(
                    "數量卡只有待派單、待認領、處理中、等最久，收到「" + name + "」");
        };
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("(^|\\s)" + description)));
    }

    public static List<String> columnsFilteredBy(String countCard) {
        return switch (countCard.strip()) {
            case "待派單" -> List.of("待派單");
            case "待認領" -> List.of("待認領");
            case "處理中" -> List.of("已接", "進行中");
            default -> throw new IllegalArgumentException(
                    "只有待派單、待認領、處理中三張數量卡會篩選看板，收到「" + countCard + "」");
        };
    }

    public List<String> columnNames() {
        List<String> names = new ArrayList<>();
        for (String text : columnHeaders().allInnerTexts()) {
            String header = text.strip();
            COLUMN_NAMES.stream().filter(header::startsWith).findFirst().ifPresent(names::add);
        }
        return names;
    }

    public void waitForColumns(List<String> expected) {
        page().waitForCondition(() -> columnNames().equals(expected));
    }

    public Locator roomSearch() {
        return page().getByLabel("搜尋房號", new Page.GetByLabelOptions().setExact(true));
    }

    public Locator laneOption(String lane) {
        return page().getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName("泳道").setExact(true))
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(lane.strip()).setExact(true));
    }

    public boolean laneSelected(String lane) {
        return "true".equals(laneOption(lane).getAttribute("aria-pressed"));
    }

    public Locator loadFailedTitle() {
        return page().getByText("讀不到資料", new Page.GetByTextOptions().setExact(true));
    }

    public static String dropZoneFor(String dept) {
        return switch (dept.strip()) {
            case "房務" -> "給房務";
            case "工務" -> "給工務";
            default -> throw new IllegalArgumentException("拖曳只能派給房務或工務，收到「" + dept + "」");
        };
    }

    public Locator dropZone(String name) {
        return page().getByText(name.strip(), new Page.GetByTextOptions().setExact(true));
    }

    public Locator anyDropZone() {
        return page().getByText(Pattern.compile("^給(房務|工務)$"));
    }

    public void dragCardToDropZone(String roomNo, String zoneName) {
        CardDrag drag = startDrag(roomNo);
        Locator zone = dropZone(zoneName).first();
        zone.waitFor();
        drag.bringIntoView(zone);
        drag.moveToCenterOf(zone);
        drag.release();
    }

    public CardDrag startDrag(String roomNo) {
        Locator target = card(roomNo);
        target.scrollIntoViewIfNeeded();
        BoundingBox box = target.boundingBox();
        CardDrag drag = new CardDrag(box.x + box.width / 2, box.y + box.height / 2);
        drag.press();
        drag.moveTo(drag.x + DRAG_START_OFFSET_PX, drag.y + DRAG_START_OFFSET_PX);
        anyDropZone().first().waitFor();
        return drag;
    }

    private static final double DRAG_START_OFFSET_PX = 12;

    private static final double TOUCH_HOLD_MS = 350;

    private static final int MAX_EDGE_SCROLL_STEPS = 60;

    private static final int TEST_POINTER_ID = 9001;

    /**
     * 測試送的指標事件不是瀏覽器產生的，對它呼叫指標 capture 會出錯，拖曳套件就取消這次拖曳。
     * 所以只對測試專用的指標編號略過 capture，真的滑鼠與點一下不受影響。重複執行不會重複包。
     */
    private static final String SKIP_TEST_POINTER_CAPTURE = """
            id => {
              if (window.__webBddTestPointerCapture) return;
              window.__webBddTestPointerCapture = true;
              const proto = Element.prototype;
              const set = proto.setPointerCapture;
              const release = proto.releasePointerCapture;
              const has = proto.hasPointerCapture;
              proto.setPointerCapture = function (p) { if (p === id) return; return set.call(this, p); };
              proto.releasePointerCapture = function (p) { if (p === id) return; return release.call(this, p); };
              proto.hasPointerCapture = function (p) { return p === id ? false : has.call(this, p); };
            }
            """;

    private static final String DISPATCH_TOUCH_POINTER = """
            ([type, x, y, id]) => {
              const target = document.elementFromPoint(x, y) || document.body;
              const pressed = type !== 'pointerup';
              const changed = type !== 'pointermove';
              target.dispatchEvent(new PointerEvent(type, {
                bubbles: true, cancelable: true, composed: true,
                pointerId: id, pointerType: 'touch', isPrimary: true,
                clientX: x, clientY: y,
                button: changed ? 0 : -1, buttons: pressed ? 1 : 0,
              }));
            }
            """;

    public final class CardDrag {

        private final boolean touch = world.profile().touch();

        private double x;

        private double y;

        private CardDrag(double x, double y) {
            this.x = x;
            this.y = y;
        }

        private void press() {
            if (touch) {
                page().evaluate(SKIP_TEST_POINTER_CAPTURE, TEST_POINTER_ID);
                touchEvent("pointerdown");
                page().waitForTimeout(TOUCH_HOLD_MS);
            } else {
                page().mouse().move(x, y);
                page().mouse().down();
            }
        }

        public void moveTo(double toX, double toY) {
            x = toX;
            y = toY;
            if (touch) {
                touchEvent("pointermove");
            } else {
                page().mouse().move(x, y, new Mouse.MoveOptions().setSteps(2));
            }
        }

        public void moveToCenterOf(Locator target) {
            BoundingBox box = target.boundingBox();
            moveTo(box.x + box.width / 2, box.y + box.height / 2);
        }

        public void bringIntoView(Locator target) {
            double width = page().viewportSize().width;
            for (int step = 0; step < MAX_EDGE_SCROLL_STEPS; step++) {
                BoundingBox box = target.boundingBox();
                boolean beyondRight = box.x + box.width > width;
                boolean beyondLeft = box.x < 0;
                if (!beyondRight && !beyondLeft) {
                    return;
                }
                double edge = beyondRight ? width - 8 - (step % 2) : 8 + (step % 2);
                moveTo(edge, y);
                page().waitForTimeout(50);
            }
            throw new IllegalStateException("拖著卡片推到視窗邊緣，看板仍沒有捲到拖放區");
        }

        public void release() {
            if (touch) {
                touchEvent("pointerup");
            } else {
                page().mouse().up();
            }
        }

        private void touchEvent(String type) {
            page().evaluate(DISPATCH_TOUCH_POINTER, List.of(type, x, y, TEST_POINTER_ID));
        }
    }

    public static final String DRAGGING_HINT = "放到「待認領」狀態的房務或工務，就完成派單";

    public Locator filterPill() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^(只看：|房號 )")));
    }

    public String filterPillText() {
        return String.join("|", filterPill().allInnerTexts());
    }

    public Locator draggingHint() {
        return page().getByText(DRAGGING_HINT, new Page.GetByTextOptions().setExact(true));
    }

    public CardDrag liftCard(String roomNo) {
        Locator target = card(roomNo);
        target.scrollIntoViewIfNeeded();
        BoundingBox box = target.boundingBox();
        CardDrag drag = new CardDrag(box.x + box.width / 2, box.y + box.height / 2);
        drag.press();
        drag.moveTo(drag.x + DRAG_START_OFFSET_PX, drag.y + DRAG_START_OFFSET_PX);
        return drag;
    }

    public record TriedDrag(String roomNo, int dropZonesDuring, boolean draggingHintDuring,
            int cardCopiesDuring, int requestsBefore) {
    }

    public record DropResult(String roomNo, String dropZone, String requestBody, Integer status, String failure) {
    }

    private static final Map<Page, TriedDrag> TRIED_DRAGS = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<Page, DropResult> DROPS = Collections.synchronizedMap(new WeakHashMap<>());

    public void rememberTriedDrag(TriedDrag tried) {
        TRIED_DRAGS.put(page(), tried);
    }

    public Optional<TriedDrag> lastTriedDrag() {
        return Optional.ofNullable(TRIED_DRAGS.get(page()));
    }

    public void rememberDrop(DropResult drop) {
        DROPS.put(page(), drop);
    }

    public Optional<DropResult> lastDrop() {
        return Optional.ofNullable(DROPS.get(page()));
    }

    public List<String> columnsHolding(String roomNo) {
        List<String> holding = new ArrayList<>();
        for (String name : COLUMN_NAMES) {
            Locator inColumn = column(name).getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                    .setName(Pattern.compile("^" + BrowserRegex.literal(roomNo) + "(\\s|$)")));
            if (inColumn.count() > 0) {
                holding.add(name);
            }
        }
        return holding;
    }

    public Locator openedCard(String roomNo) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(roomNo) + "(\\s|$)"))
                .setExpanded(true));
    }

    private static final Pattern CARD_NAME = Pattern.compile("^\\d+(\\s|$)");

    private static final Pattern LANE_NAME = Pattern.compile("^(未分部門|房務|工務|\\d+ 樓)(\\s|$)");

    private static final Pattern CARD_FOOT =
            Pattern.compile("^(.*?)\\s*(未分部門|房務|工務)\\s*·\\s*(打掃|修繕|備品|其他)\\s*(\\S*)$");

    private static final Pattern HEADER_TEXT = Pattern.compile("^(\\S+)\\s*(\\d+)?\\s*(.*)$");

    private static final Pattern LANE_TEXT = Pattern.compile("^(未分部門|房務|工務|\\d+ 樓)\\s*(.*)$");

    private static final String READ_CARDS = """
            cards => cards.map(card => {
              const pill = card.querySelector('[data-tone]');
              const bar = card.querySelector('[role="progressbar"], progress');
              const labels = [...card.querySelectorAll('[aria-label], img[alt]')]
                .map(node => node.getAttribute('aria-label') ?? node.getAttribute('alt'));
              let ratio = null;
              if (bar) {
                const now = Number(bar.getAttribute('aria-valuenow') ?? bar.value);
                const min = Number(bar.getAttribute('aria-valuemin') ?? 0);
                const max = Number(bar.getAttribute('aria-valuemax') ?? bar.max ?? 100);
                ratio = max > min ? (now - min) / (max - min) : null;
              }
              return {
                text: card.innerText,
                pillText: pill ? pill.innerText : '',
                pillTone: pill ? (pill.dataset.tone ?? '') : '',
                labels,
                hasBar: !!bar,
                ratio,
                top: card.getBoundingClientRect().top,
              };
            })
            """;

    private static final String READ_TILES = """
            tiles => tiles.map(tile => {
              const number = tile.querySelector('[data-tone]');
              return {
                text: tile.innerText,
                number: number ? number.innerText : '',
                tone: number ? (number.dataset.tone ?? '') : '',
                busy: tile.getAttribute('aria-busy') === 'true' || !!tile.querySelector('[aria-busy="true"]'),
                pressed: tile.getAttribute('aria-pressed') === 'true',
              };
            })
            """;

    private static final String READ_HEADERS = """
            headers => headers.map(header => {
              const filled = (el, pseudo) => {
                const bg = getComputedStyle(el, pseudo).backgroundColor;
                return !!bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)';
              };
              const dot = [header, ...header.querySelectorAll('*')].some(el => {
                if (el !== header && el.textContent.trim() === '') {
                  const r = el.getBoundingClientRect();
                  if (r.width > 0 && r.width <= 24 && r.height > 0 && r.height <= 24 && filled(el)) return true;
                }
                return ['::before', '::after'].some(pseudo => {
                  const content = getComputedStyle(el, pseudo).content;
                  return content && content !== 'none' && content !== 'normal' && filled(el, pseudo);
                });
              });
              return { text: header.innerText, dot, left: header.getBoundingClientRect().left };
            })
            """;

    public static String collapse(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    private static void requireColumn(String column) {
        if (!COLUMN_NAMES.contains(column.strip())) {
            throw new IllegalArgumentException("看板的欄只有 " + COLUMN_NAMES + "，收到「" + column + "」");
        }
    }

    public Locator wholePage() {
        return page().locator("body");
    }

    public Locator columnIn(Locator scope, String column) {
        requireColumn(column);
        return scope.getByRole(AriaRole.REGION, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(column.strip()) + "(\\s|$)")));
    }

    public Locator column(String column) {
        return columnIn(wholePage(), column);
    }

    public Locator lane(String lane) {
        return page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(lane.strip()) + "(\\s|$)")));
    }

    public Locator laneHeadings() {
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(LANE_NAME));
    }

    public Locator cardsIn(Locator scope) {
        return scope.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CARD_NAME));
    }

    public record CardReading(String roomNo, String text, String pillText, String pillTone, List<String> labels,
            boolean hasBar, Double barRatio, double top, boolean vip, String middle, String dept, String type,
            String avatar) {
    }

    public List<CardReading> readCardsIn(Locator scope) {
        return readCards(cardsIn(scope));
    }

    public List<CardReading> readCardsOf(String roomNo) {
        return readCards(card(roomNo));
    }

    private static List<CardReading> readCards(Locator cards) {
        List<CardReading> readings = new ArrayList<>();
        for (Map<String, Object> card : JsResult.objects(cards.evaluateAll(READ_CARDS))) {
            readings.add(parseCard(card));
        }
        return readings;
    }

    private static CardReading parseCard(Map<String, Object> card) {
        String text = collapse(JsResult.string(card.get("text")));
        String pillText = collapse(JsResult.string(card.get("pillText")));
        String pillTone = JsResult.string(card.get("pillTone"));
        List<String> labels = card.get("labels") == null ? null : JsResult.strings(card.get("labels"));
        boolean hasBar = Boolean.TRUE.equals(card.get("hasBar"));
        Double ratio = card.get("ratio") == null ? null : JsResult.number(card.get("ratio")).doubleValue();
        double top = JsResult.number(card.get("top")).doubleValue();
        String roomNo = text.isEmpty() ? "" : text.split(" ", 2)[0];
        String rest = text.substring(roomNo.length()).strip();
        boolean vip = false;
        boolean pillSeen = pillText.isEmpty();
        for (int round = 0; round < 2; round++) {
            if (!vip && (rest.equals("VIP") || rest.startsWith("VIP "))) {
                vip = true;
                rest = rest.substring(3).strip();
            }
            if (!pillSeen && rest.startsWith(pillText)) {
                pillSeen = true;
                rest = rest.substring(pillText.length()).strip();
            }
        }
        java.util.regex.Matcher foot = CARD_FOOT.matcher(rest);
        if (foot.matches()) {
            return new CardReading(roomNo, text, pillText, pillTone, labels, hasBar, ratio, top, vip,
                    foot.group(1).strip(), foot.group(2), "· " + foot.group(3), foot.group(4));
        }
        return new CardReading(roomNo, text, pillText, pillTone, labels, hasBar, ratio, top, vip, rest, null, null,
                "");
    }

    public record TileReading(String text, String number, String tone, boolean busy, boolean pressed) {

        public String description() {
            if (number.isEmpty()) {
                return text;
            }
            if (text.startsWith(number)) {
                return text.substring(number.length()).strip();
            }
            if (text.endsWith(number)) {
                return text.substring(0, text.length() - number.length()).strip();
            }
            return collapse(text.replaceFirst(BrowserRegex.literal(number), ""));
        }
    }

    public List<TileReading> readTiles() {
        return readTiles(tileRow().getByRole(AriaRole.BUTTON));
    }

    public TileReading readTile(String name) {
        List<TileReading> found = readTiles(countCard(name));
        if (found.size() != 1) {
            throw new AssertionError("「" + name + "」數量卡應該剛好一張，畫面上有 " + found.size() + " 張");
        }
        return found.get(0);
    }

    private static List<TileReading> readTiles(Locator tiles) {
        List<TileReading> readings = new ArrayList<>();
        for (Map<String, Object> tile : JsResult.objects(tiles.evaluateAll(READ_TILES))) {
            readings.add(new TileReading(collapse(JsResult.string(tile.get("text"))),
                    collapse(JsResult.string(tile.get("number"))),
                    JsResult.string(tile.get("tone")), Boolean.TRUE.equals(tile.get("busy")),
                    Boolean.TRUE.equals(tile.get("pressed"))));
        }
        return readings;
    }

    public record HeaderReading(String column, Integer count, String warning, boolean hasDot, double left) {
    }

    public List<HeaderReading> readHeaders() {
        List<HeaderReading> readings = new ArrayList<>();
        for (Map<String, Object> header : JsResult.objects(columnHeaders().evaluateAll(READ_HEADERS))) {
            java.util.regex.Matcher parts = HEADER_TEXT.matcher(collapse(JsResult.string(header.get("text"))));
            if (!parts.matches()) {
                continue;
            }
            readings.add(new HeaderReading(parts.group(1),
                    parts.group(2) == null ? null : Integer.valueOf(parts.group(2)), parts.group(3).strip(),
                    Boolean.TRUE.equals(header.get("dot")), JsResult.number(header.get("left")).doubleValue()));
        }
        return readings;
    }

    public record LaneReading(String name, String subtitle) {
    }

    public List<LaneReading> readLanes() {
        List<LaneReading> readings = new ArrayList<>();
        for (String text : laneHeadings().allInnerTexts()) {
            java.util.regex.Matcher parts = LANE_TEXT.matcher(collapse(text));
            if (parts.matches()) {
                readings.add(new LaneReading(parts.group(1), parts.group(2).strip()));
            }
        }
        return readings;
    }

    public String dropZoneDescription(Locator zoneTitle) {
        String title = collapse(zoneTitle.innerText());
        String whole = collapse((String) zoneTitle.evaluate("title => title.parentElement.innerText"));
        return whole.startsWith(title) ? whole.substring(title.length()).strip() : whole;
    }

    public Locator openCountSubtitle() {
        return new ShellFrame(world).toolbar().getByText(Pattern.compile("\\d+ 張未結案"));
    }

    public static String columnOfTask(com.fasterxml.jackson.databind.JsonNode task) {
        String state = task.path("state").asText();
        return switch (state) {
            case "triage" -> "待派單";
            case "pool" -> "待認領";
            case "accepted" -> "已接";
            case "doing" -> "進行中";
            case "done" -> task.path("endedAt").isTextual() ? "已結案" : "已完成";
            case "interrupted" -> "中斷";
            case "deferred" -> "延後中";
            case "inspected", "closed", "cancelled" -> "已結案";
            default -> throw new IllegalStateException("後端的單有沒見過的狀態「" + state + "」：" + task);
        };
    }

    public static long openTaskCount(com.fasterxml.jackson.databind.JsonNode deskBoard) {
        long open = 0;
        for (com.fasterxml.jackson.databind.JsonNode task : deskBoard.path("tasks")) {
            if (!"已結案".equals(columnOfTask(task))) {
                open++;
            }
        }
        return open;
    }
}
