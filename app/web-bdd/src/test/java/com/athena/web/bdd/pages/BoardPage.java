package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.BrowserRegex;
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

/** 總覽看板（路徑 {@code /board}）：數量卡、控制列、各欄與卡片。 */
public class BoardPage extends PageObject {

    public BoardPage(WebWorld world) {
        super(world);
    }

    /**
     * 看板上這間房的卡片。卡片是一顆按鈕，可及性名稱由房號開頭，
     * 所以用「房號後面接空白或結尾」比對，305 不會連 3051 一起找到。
     */
    public Locator card(String roomNo) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(roomNo) + "(\\s|$)")));
    }

    /** 看板由左到右的 8 個欄名。 */
    public static final List<String> COLUMN_NAMES =
            List.of("待派單", "待認領", "已接", "進行中", "已完成", "中斷", "延後中", "已結案");

    /** 從一個欄頭往上找，第一個可以橫向捲動的區塊就是看板本體。 */
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

    /**
     * 這一欄的欄頭。欄頭是標題，可及性名稱由欄名開頭，後面接數量與警示，
     * 所以用「欄名後面接空白或結尾」比對。
     */
    public Locator columnHeader(String column) {
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(column) + "(\\s|$)")));
    }

    /** 看板上現在有的欄頭，不論在不在視窗內。 */
    public Locator columnHeaders() {
        String names = COLUMN_NAMES.stream().map(BrowserRegex::literal).collect(Collectors.joining("|"));
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^(" + names + ")(\\s|$)")));
    }

    /**
     * 看板本體：放各欄、可以橫向捲動的那一塊。它沒有自己的可及性名稱，
     * 所以從使用者看得到的欄頭往上找第一個可以橫向捲動的區塊。
     */
    public ElementHandle boardBody() {
        Locator anyHeader = columnHeaders().first();
        anyHeader.waitFor();
        return anyHeader.evaluateHandle(BODY_OF_HEADER).asElement();
    }

    /** 看板本體在視窗上的位置與大小。 */
    public BoundingBox boardBodyBox() {
        return boardBody().boundingBox();
    }

    /** 4 張數量卡排成的那一列：以「處理中」這張數量卡往上一層取整列。 */
    public Locator tileRow() {
        return countCard("處理中").locator("xpath=..");
    }

    /** 各欄現在的寬度，以欄頭的寬度為準，依欄名由左到右；不在畫面上的欄不列。 */
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
     * 把看板本體橫向捲到底，等捲動位置真的到了最右邊。滑鼠的瀏覽器設定用游標在看板上往右滾。
     *
     * <p>觸控的瀏覽器設定直接把捲動位置設到最右邊，因為 WebKit 送不出瀏覽器自己產生的滑動手勢，
     * 測試自己送的觸控事件也不會讓瀏覽器捲動。看板最後停的位置與手指滑到底相同，手指滑動的手感要在真機上驗。
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

    /** 看板右緣「還有 N 個狀態」這一類按鈕，不論 N 是多少。 */
    public Locator moreStatesButtons() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^還有 \\d+ 個狀態$")));
    }

    /**
     * 數量卡。數量卡是一顆按鈕，可及性名稱是「數字 說明」，例如「2 待認領（房務 1・工務 1）」；
     * 數字還在讀取時只有說明。用說明的開頭分辨四張卡，「等最久」沒有在等的單時說明是「沒有在等的單」。
     * 卡片的名稱是「房號 標題，狀態，等待文字」，不會以這幾種說明結尾或接在數字後面，所以不會被誤找到。
     */
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

    /** 點了這張數量卡之後看板只留下的欄；「等最久」不篩選，不在這裡。 */
    public static List<String> columnsFilteredBy(String countCard) {
        return switch (countCard.strip()) {
            case "待派單" -> List.of("待派單");
            case "待認領" -> List.of("待認領");
            case "處理中" -> List.of("已接", "進行中");
            default -> throw new IllegalArgumentException(
                    "只有待派單、待認領、處理中三張數量卡會篩選看板，收到「" + countCard + "」");
        };
    }

    /** 看板現在有的欄名，由左到右；欄頭的文字以哪一個欄名開頭就是哪一欄。 */
    public List<String> columnNames() {
        List<String> names = new ArrayList<>();
        for (String text : columnHeaders().allInnerTexts()) {
            String header = text.strip();
            COLUMN_NAMES.stream().filter(header::startsWith).findFirst().ifPresent(names::add);
        }
        return names;
    }

    /** 等到看板的欄剛好是這幾欄，順序相同。 */
    public void waitForColumns(List<String> expected) {
        page().waitForCondition(() -> columnNames().equals(expected));
    }

    /** 工具列的「搜尋房號」輸入框。 */
    public Locator roomSearch() {
        return page().getByLabel("搜尋房號", new Page.GetByLabelOptions().setExact(true));
    }

    /** 控制列的泳道分段鈕裡的那一顆（不分、部門、樓層）。分段鈕是名稱為「泳道」的一組按鈕。 */
    public Locator laneOption(String lane) {
        return page().getByRole(AriaRole.GROUP, new Page.GetByRoleOptions().setName("泳道").setExact(true))
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(lane.strip()).setExact(true));
    }

    /** 這顆泳道鈕是不是選中的樣子。 */
    public boolean laneSelected(String lane) {
        return "true".equals(laneOption(lane).getAttribute("aria-pressed"));
    }

    /** 第一次讀取看板失敗時，看板本體位置顯示的標題。 */
    public Locator loadFailedTitle() {
        return page().getByText("讀不到資料", new Page.GetByTextOptions().setExact(true));
    }

    /** 規格的部門說法（房務、工務）換成拖放區的名稱（給房務、給工務）。 */
    public static String dropZoneFor(String dept) {
        return switch (dept.strip()) {
            case "房務" -> "給房務";
            case "工務" -> "給工務";
            default -> throw new IllegalArgumentException("拖曳只能派給房務或工務，收到「" + dept + "」");
        };
    }

    /** 拖曳中出現在「待認領」欄最上面的拖放區，第一行是名稱（給房務、給工務）。 */
    public Locator dropZone(String name) {
        return page().getByText(name.strip(), new Page.GetByTextOptions().setExact(true));
    }

    /** 看板上任何一個拖放區。 */
    public Locator anyDropZone() {
        return page().getByText(Pattern.compile("^給(房務|工務)$"));
    }

    /** 按住這間房的卡片、拖到這個拖放區、放開。拖放區不在視窗內時把卡片拖向視窗邊緣讓看板捲過去。 */
    public void dragCardToDropZone(String roomNo, String zoneName) {
        CardDrag drag = startDrag(roomNo);
        Locator zone = dropZone(zoneName).first();
        zone.waitFor();
        drag.bringIntoView(zone);
        drag.moveToCenterOf(zone);
        drag.release();
    }

    /**
     * 在這間房的卡片上按住並拖離原位、不放開，等拖放區出現。回傳的物件用來接著移動與放開。
     *
     * <p>兩組瀏覽器設定的做法不同（理由見 implement-notes 的 T005）：滑鼠用 {@code page.mouse()}，
     * 移動超過 6px 起拖；觸控在頁面裡送觸控種類的指標事件，按住超過 0.25 秒再移動才起拖。
     * 瀏覽器時鐘停住時起不了拖，呼叫端不要在停住時鐘之後拖。
     */
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

    /** 起拖的第一段移動：滑鼠要超過 6px 才起拖，觸控按住夠久之後的第一個移動就起拖。 */
    private static final double DRAG_START_OFFSET_PX = 12;

    /** 觸控按住多久再移動；產品的起拖條件是 0.25 秒，多留一點餘裕。 */
    private static final double TOUCH_HOLD_MS = 350;

    /** 拖放區不在視窗內時，最多把卡片往視窗邊緣推幾次讓看板捲動。 */
    private static final int MAX_EDGE_SCROLL_STEPS = 60;

    /** 測試自己送的觸控指標用這個編號，和瀏覽器自己的指標分開。 */
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

    /** 對指標所在位置最上層的元素送一個觸控種類的指標事件。 */
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

    /** 一次進行中的拖曳：記著指標現在的位置，依瀏覽器設定用滑鼠或觸控指標移動與放開。 */
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

        /** 指標移到這個位置，按住不放。 */
        public void moveTo(double toX, double toY) {
            x = toX;
            y = toY;
            if (touch) {
                touchEvent("pointermove");
            } else {
                page().mouse().move(x, y, new Mouse.MoveOptions().setSteps(2));
            }
        }

        /** 指標移到這個元素的正中間，按住不放。 */
        public void moveToCenterOf(Locator target) {
            BoundingBox box = target.boundingBox();
            moveTo(box.x + box.width / 2, box.y + box.height / 2);
        }

        /** 目標在視窗左右兩側之外時，把指標停在那一側的邊緣，讓看板自己往那邊捲，直到目標整個進到視窗。 */
        public void bringIntoView(Locator target) {
            double width = page().viewportSize().width;
            for (int step = 0; step < MAX_EDGE_SCROLL_STEPS; step++) {
                BoundingBox box = target.boundingBox();
                boolean beyondRight = box.x + box.width > width;
                boolean beyondLeft = box.x < 0;
                if (!beyondRight && !beyondLeft) {
                    return;
                }
                // 每次差 1px，指標才算有移動，拖曳套件才會再判斷一次要不要捲。
                double edge = beyondRight ? width - 8 - (step % 2) : 8 + (step % 2);
                moveTo(edge, y);
                page().waitForTimeout(50);
            }
            throw new IllegalStateException("拖著卡片推到視窗邊緣，看板仍沒有捲到拖放區");
        }

        /** 在指標現在的位置放開。 */
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

    /** 拖曳中控制列右側換上的提示文字。 */
    public static final String DRAGGING_HINT = "放到「待認領」狀態的房務或工務，就完成派單";

    /** 控制列上的篩選膠囊：一顆按鈕，文字以「只看：」或「房號 」開頭，點了一次清掉數量卡篩選與房號搜尋。 */
    public Locator filterPill() {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^(只看：|房號 )")));
    }

    /** 控制列上所有篩選膠囊的文字接在一起；沒有膠囊時是空字串。用來判斷篩選有沒有跟著操作變。 */
    public String filterPillText() {
        return String.join("|", filterPill().allInnerTexts());
    }

    /** 控制列上拖曳中的那一句提示。 */
    public Locator draggingHint() {
        return page().getByText(DRAGGING_HINT, new Page.GetByTextOptions().setExact(true));
    }

    /**
     * 在這間房的卡片上做起拖的手勢（滑鼠按住並移動、手指按住再移動），回傳時指標仍按著。
     * 拖不動的卡片不會出現拖放區，所以這個方法不等拖放區；要等拖放區的拖曳用 {@link #startDrag(String)}。
     */
    public CardDrag liftCard(String roomNo) {
        Locator target = card(roomNo);
        target.scrollIntoViewIfNeeded();
        BoundingBox box = target.boundingBox();
        CardDrag drag = new CardDrag(box.x + box.width / 2, box.y + box.height / 2);
        drag.press();
        drag.moveTo(drag.x + DRAG_START_OFFSET_PX, drag.y + DRAG_START_OFFSET_PX);
        return drag;
    }

    /**
     * 試著拖一張卡片時，手勢還按著的那一刻畫面的樣子。
     * 檢查「卡片沒有被拖起來」要看手勢期間的畫面；放開之後拖放區與提示一定不在，那時再看就分不出對錯。
     *
     * @param dropZonesDuring 手勢期間看板上的拖放區個數
     * @param draggingHintDuring 手勢期間控制列是不是換成拖曳中的提示
     * @param cardCopiesDuring 手勢期間名稱以這個房號開頭的卡片張數；卡片被拖起來時，跟著指標走的複本會讓張數變多
     * @param requestsBefore 手勢開始前這個場景送出過幾個請求，之後的請求用 {@code Network.sentRequestsSince} 取
     */
    public record TriedDrag(String roomNo, int dropZonesDuring, boolean draggingHintDuring,
            int cardCopiesDuring, int requestsBefore) {
    }

    /**
     * 拖放派單送出的請求與結果。
     *
     * @param requestBody 派單請求的內容（{@code dept}、{@code via}）；沒有送出時是 {@code null}
     * @param status 回應的狀態碼；斷線或沒有送出時是 {@code null}
     * @param failure 斷線時瀏覽器給的原因；其他情形是 {@code null}
     */
    public record DropResult(String roomNo, String dropZone, String requestBody, Integer status, String failure) {
    }

    /** 以分頁分開記，分頁關掉就跟著丟掉，不會帶到下一個場景。 */
    private static final Map<Page, TriedDrag> TRIED_DRAGS = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<Page, DropResult> DROPS = Collections.synchronizedMap(new WeakHashMap<>());

    public void rememberTriedDrag(TriedDrag tried) {
        TRIED_DRAGS.put(page(), tried);
    }

    /** 這個分頁最近一次「試著拖」時記下的樣子。 */
    public Optional<TriedDrag> lastTriedDrag() {
        return Optional.ofNullable(TRIED_DRAGS.get(page()));
    }

    public void rememberDrop(DropResult drop) {
        DROPS.put(page(), drop);
    }

    /** 這個分頁最近一次拖放派單的請求與結果。 */
    public Optional<DropResult> lastDrop() {
        return Optional.ofNullable(DROPS.get(page()));
    }

    /** 這間房的卡片現在放在哪幾欄，依欄由左到右；正常只會有一欄。 */
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

    /**
     * 開著詳情面板的那張卡片。畫面上它有品牌色外框；給輔助科技的說法是卡片這顆按鈕標成「已展開」，
     * 因為它控制的詳情面板正開著。
     */
    public Locator openedCard(String roomNo) {
        return page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(roomNo) + "(\\s|$)"))
                .setExpanded(true));
    }

    /*
     * 以下是看板結果句讀畫面用的方法：欄、卡片、數量卡、欄頭、泳道與頁副標。
     *
     * 畫面結構的假設（Phase 4 照著做）：
     * - 每一欄是一個區塊（region），可及性名稱以欄名開頭，通常就用欄頭標題命名。有泳道時，每一列泳道裡的每一欄各是一個。
     * - 有泳道時每一列泳道也是一個區塊，可及性名稱以泳道名開頭；泳道標題是標題，文字是「{名稱} {N} 張未結案」。
     * - 顏色以 data-tone 標記，值和 lib/wait-pill.ts 的 WaitPillTone 同一組（gray、orange、red、green），不比對色碼。
     *   卡片上只有等待膠囊帶 data-tone；數量卡上只有數字帶 data-tone。
     * - 載入中的骨架標成 aria-busy="true"，和 ShellFrame.waitUntilLoaded 同一個約定。
     * - 數量卡選中時 aria-pressed="true"，和泳道鈕同一個約定。
     * 這裡的讀法都不等元素出現，讀到什麼算什麼；要等畫面更新由呼叫端輪詢。
     */

    /** 卡片按鈕的可及性名稱以房號開頭。 */
    private static final Pattern CARD_NAME = Pattern.compile("^\\d+(\\s|$)");

    /** 泳道的名稱：未分部門、房務、工務，或「{N} 樓」。 */
    private static final Pattern LANE_NAME = Pattern.compile("^(未分部門|房務|工務|\\d+ 樓)(\\s|$)");

    /** 卡片扣掉第一行之後的字：標題、副標、SOP，最後一行是部門、「· 類型」與負責人頭像的字。來源圖示沒有文字。 */
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

    /** 欄頭的文字、有沒有色點（不帶文字、有底色的小點，或帶底色的偽元素）、左緣位置。 */
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

    /** 把畫面文字的換行與連續空白壓成一個空白：同一段字在不同版面下斷行不同，比對時不該因此不相符。 */
    public static String collapse(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    private static void requireColumn(String column) {
        if (!COLUMN_NAMES.contains(column.strip())) {
            throw new IllegalArgumentException("看板的欄只有 " + COLUMN_NAMES + "，收到「" + column + "」");
        }
    }

    /** 整頁的範圍，給「在哪個範圍裡找」的方法用。 */
    public Locator wholePage() {
        return page().locator("body");
    }

    /** 這個範圍裡名稱為這個欄名的欄；有泳道時一欄會有好幾格，全部都在。 */
    public Locator columnIn(Locator scope, String column) {
        requireColumn(column);
        return scope.getByRole(AriaRole.REGION, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(column.strip()) + "(\\s|$)")));
    }

    /** 整個看板上這一欄（有泳道時是每一列裡的這一欄）。 */
    public Locator column(String column) {
        return columnIn(wholePage(), column);
    }

    /** 這一列泳道。 */
    public Locator lane(String lane) {
        return page().getByRole(AriaRole.REGION, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(lane.strip()) + "(\\s|$)")));
    }

    /** 看板上的泳道標題，由上到下。 */
    public Locator laneHeadings() {
        return page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(LANE_NAME));
    }

    /** 這個範圍裡的卡片，依畫面上的先後。 */
    public Locator cardsIn(Locator scope) {
        return scope.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(CARD_NAME));
    }

    /**
     * 一張卡片上看得到的東西，給逐項比對卡片內容的結果句用。
     * {@code text} 是卡片上全部的字（換行與連續空白壓成一個空白）；{@code middle} 是第一行（房號、「VIP」、膠囊）
     * 與最後一行（部門、類型、頭像）之間的字，也就是標題、副標與 SOP；最後一行讀不出來時 {@code dept}、{@code type}
     * 是 {@code null}。{@code labels} 是卡片裡圖示給螢幕報讀軟體的名稱，來源圖示的名稱從這裡找。
     */
    public record CardReading(String roomNo, String text, String pillText, String pillTone, List<String> labels,
            boolean hasBar, Double barRatio, double top, boolean vip, String middle, String dept, String type,
            String avatar) {
    }

    /** 讀這個範圍裡的每一張卡片，依畫面上的先後。 */
    public List<CardReading> readCardsIn(Locator scope) {
        return readCards(cardsIn(scope));
    }

    /** 讀這間房的卡片；同一間房在畫面上有幾張就回幾張。 */
    public List<CardReading> readCardsOf(String roomNo) {
        return readCards(card(roomNo));
    }

    @SuppressWarnings("unchecked")
    private static List<CardReading> readCards(Locator cards) {
        List<CardReading> readings = new ArrayList<>();
        for (Object raw : (List<Object>) cards.evaluateAll(READ_CARDS)) {
            readings.add(parseCard((Map<String, Object>) raw));
        }
        return readings;
    }

    @SuppressWarnings("unchecked")
    private static CardReading parseCard(Map<String, Object> card) {
        String text = collapse((String) card.get("text"));
        String pillText = collapse((String) card.get("pillText"));
        String pillTone = (String) card.get("pillTone");
        List<String> labels = (List<String>) card.get("labels");
        boolean hasBar = Boolean.TRUE.equals(card.get("hasBar"));
        Double ratio = card.get("ratio") == null ? null : ((Number) card.get("ratio")).doubleValue();
        double top = ((Number) card.get("top")).doubleValue();
        String roomNo = text.isEmpty() ? "" : text.split(" ", 2)[0];
        // 第一行是房號、「VIP」標與等待膠囊，「VIP」與膠囊的先後不限。
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

    /**
     * 一張數量卡上看得到的東西，給比對數量卡的結果句用。
     * {@code number} 是帶顏色標記的數字那一處的字，數字位置是骨架時是空的；{@code busy} 表示數量卡上有載入中的骨架；
     * {@code pressed} 表示這張卡是選中的樣子。
     */
    public record TileReading(String text, String number, String tone, boolean busy, boolean pressed) {

        /** 說明：數量卡上的字扣掉數字。 */
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

    /** 由左到右讀 4 張數量卡。 */
    public List<TileReading> readTiles() {
        return readTiles(tileRow().getByRole(AriaRole.BUTTON));
    }

    /** 讀這一張數量卡（待派單、待認領、處理中、等最久）；找不到或不只一張時直接失敗。 */
    public TileReading readTile(String name) {
        List<TileReading> found = readTiles(countCard(name));
        if (found.size() != 1) {
            throw new AssertionError("「" + name + "」數量卡應該剛好一張，畫面上有 " + found.size() + " 張");
        }
        return found.get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<TileReading> readTiles(Locator tiles) {
        List<TileReading> readings = new ArrayList<>();
        for (Object raw : (List<Object>) tiles.evaluateAll(READ_TILES)) {
            Map<String, Object> tile = (Map<String, Object>) raw;
            readings.add(new TileReading(collapse((String) tile.get("text")), collapse((String) tile.get("number")),
                    (String) tile.get("tone"), Boolean.TRUE.equals(tile.get("busy")),
                    Boolean.TRUE.equals(tile.get("pressed"))));
        }
        return readings;
    }

    /**
     * 一個欄頭上看得到的東西，給比對欄頭的結果句用。
     * 數量位置空白（載入中）時 {@code count} 是 {@code null}；沒有警示時 {@code warning} 是空字串；
     * {@code left} 是欄頭左緣在視窗上的位置，用來確認欄由左到右的先後。
     */
    public record HeaderReading(String column, Integer count, String warning, boolean hasDot, double left) {
    }

    /** 讀看板上現在的每一個欄頭，依畫面上的先後。 */
    @SuppressWarnings("unchecked")
    public List<HeaderReading> readHeaders() {
        List<HeaderReading> readings = new ArrayList<>();
        for (Object raw : (List<Object>) columnHeaders().evaluateAll(READ_HEADERS)) {
            Map<String, Object> header = (Map<String, Object>) raw;
            java.util.regex.Matcher parts = HEADER_TEXT.matcher(collapse((String) header.get("text")));
            if (!parts.matches()) {
                continue;
            }
            readings.add(new HeaderReading(parts.group(1),
                    parts.group(2) == null ? null : Integer.valueOf(parts.group(2)), parts.group(3).strip(),
                    Boolean.TRUE.equals(header.get("dot")), ((Number) header.get("left")).doubleValue()));
        }
        return readings;
    }

    /** 泳道標題的名稱與旁邊的副標（「{N} 張未結案」）。 */
    public record LaneReading(String name, String subtitle) {
    }

    /** 讀看板上的每一個泳道標題，由上到下。 */
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

    /** 拖放區的第二行說明：拖放區名稱那一行所在的整塊，扣掉名稱。 */
    public String dropZoneDescription(Locator zoneTitle) {
        String title = collapse(zoneTitle.innerText());
        String whole = collapse((String) zoneTitle.evaluate("title => title.parentElement.innerText"));
        return whole.startsWith(title) ? whole.substring(title.length()).strip() : whole;
    }

    /** 工具列上寫著「{N} 張未結案」的頁副標。 */
    public Locator openCountSubtitle() {
        return new ShellFrame(world).toolbar().getByText(Pattern.compile("\\d+ 張未結案"));
    }

    /**
     * 後端的一張 {@code DeskTask} 依介面根「單的狀況對照」該落在哪一欄，用來確認畫面上卡片所在的欄和後端一致。
     * 完成的單當場回答過的在「已結案」，其餘在「已完成」；查房通過、免查、驗收結案、取消都在「已結案」。
     */
    public static String columnOfTask(com.fasterxml.jackson.databind.JsonNode task) {
        String state = task.path("state").asText();
        return switch (state) {
            case "triage" -> "待派單";
            case "pool" -> "待認領";
            case "accepted" -> "已接";
            case "doing" -> "進行中";
            case "done" -> task.path("answered").asBoolean() ? "已結案" : "已完成";
            case "interrupted" -> "中斷";
            case "deferred" -> "延後中";
            case "inspected", "closed", "cancelled" -> "已結案";
            default -> throw new IllegalStateException("後端的單有沒見過的狀態「" + state + "」：" + task);
        };
    }

    /**
     * 頁副標的「N 張未結案」依後端該是多少：後端看板（{@code getDeskBoard}）裡不落在「已結案」欄的單。
     * 不能用 {@code ended_at} 有沒有值來算，因為備品完成後有寫結束時刻，卻仍在「已完成」欄、仍算未結案。
     */
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
