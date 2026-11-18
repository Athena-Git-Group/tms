package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.BrowserRegex;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** 單筆詳情面板：從畫面右側滑出來的那一塊。 */
public class DetailPanel extends PageObject {

    public DetailPanel(WebWorld world) {
        super(world);
    }

    /**
     * 詳情面板本身：畫面右側的補充區塊，名稱以「詳情」結尾。
     * 名稱跟著介面語言換，所以四種語言的「詳情」都認得，介面不是中文時也找得到。
     */
    public Locator panel() {
        return page().getByRole(AriaRole.COMPLEMENTARY, new Page.GetByRoleOptions()
                .setName(UiWords.endsWithAnyLanguage("詳情")));
    }

    /** 詳情面板裡名稱完全相同的按鈕，例如底部派單區的「派給房務」「當場回答…」。 */
    public Locator button(String name) {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(name.strip()).setExact(true));
    }

    /**
     * 等到詳情面板顯示的是這一張單、內容已經載入完成。
     * 單筆詳情的請求要在點卡片之前就開始等，所以由呼叫端把點的動作交進來。
     */
    public void openAndWait(UUID taskId, String roomNo, Runnable open) {
        page().waitForResponse(
                response -> "GET".equals(response.request().method())
                        && response.url().split("[?#]")[0].endsWith("/api/v1/desk/tasks/" + taskId),
                open);
        panel().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName(roomNo))
                .first().waitFor();
    }

    /** 面板上寫著這個房號的標題；面板顯示的是別間房時找不到。 */
    public Locator roomHeading(String roomNo) {
        return panel().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(roomNo.strip()) + "(\\s|$)")));
    }

    /** 面板的內容是不是已經載入完成：面板裡沒有標成載入中（{@code aria-busy="true"}）的地方，面板本身也不是。 */
    public boolean loaded() {
        return !"true".equals(panel().first().getAttribute("aria-busy"))
                && panel().locator("[aria-busy='true']").count() == 0;
    }

    /**
     * 在面板底部的派單區按「派給{部門}」，等這張單的派單請求回來而且成功。
     * 請求要在按之前就開始等，才不會錯過。
     */
    public void assignAndWait(UUID taskId, String dept) {
        String assignPath = "/api/v1/desk/tasks/" + taskId + "/assign";
        Response response = page().waitForResponse(
                r -> "POST".equals(r.request().method()) && r.url().split("[?#]")[0].endsWith(assignPath),
                () -> new ShellFrame(world).press(button("派給" + dept.strip())));
        requireOk(response, "派給" + dept.strip());
    }

    /**
     * 在畫面底部的派單浮條上按「復原」，等這張單的復原請求回來而且成功，再等浮條消失。
     * 浮條不在面板裡，而是貼在畫面底部，所以從整個畫面找。
     */
    public void undoAssignAndWait(UUID taskId) {
        ShellFrame shell = new ShellFrame(world);
        String undoPath = "/api/v1/desk/tasks/" + taskId + "/assign/undo";
        Locator undo = shell.assignToast().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName("復原").setExact(true));
        Response response = page().waitForResponse(
                r -> "POST".equals(r.request().method()) && r.url().split("[?#]")[0].endsWith(undoPath),
                () -> shell.press(undo));
        requireOk(response, "復原");
        shell.assignToast().first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN));
    }

    /** 面板還開著就按「關閉」，等面板從畫面上收起。已經收起來的話不做事。 */
    public void closeAndWait() {
        if (panel().count() > 0 && panel().first().isVisible()) {
            new ShellFrame(world).press(button("關閉"));
        }
        panel().first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN));
    }

    /** 後端回的不是成功就停下來，並寫出按的是哪一顆與後端回了什麼。 */
    private static void requireOk(Response response, String pressed) {
        if (!response.ok()) {
            throw new IllegalStateException("按「" + pressed + "」後後端回 " + response.status()
                    + "：" + response.text());
        }
    }

    /*
     * 以下是單筆詳情結果句讀畫面用的方法。
     *
     * 畫面結構的假設（Phase 4 照著做）：
     * - 面板上半部（關閉列、房號列、標題、原文列、標題下那一行）不包在任何具名區塊裡。
     *   房號是一個標題（heading），文字以房號開頭；這張單的標題是另一個標題；狀態標籤是一段只寫標籤文字的字，
     *   可以放在房號標題裡面或旁邊。單號是一段只寫單號（例 T-1053、W-0132）的字。
     * - 狀態訊息卡、「清潔 SOP」、「內容」、歷程各是一個區塊（region，例如帶 aria-label 或 aria-labelledby 的 section），
     *   可及性名稱分別是「狀態訊息」「清潔 SOP」「內容」與以「歷程」開頭的歷程標題。
     *   狀態訊息卡第一行是標題、其餘是說明；圖示沒有文字。
     * - 「內容」的每一列是描述清單的一組 dt／dd：dt 是列名、dd 是值。
     * - 歷程是一個清單，每一筆是一個清單項目：上面一句話，最後一行是灰字「{時間} · {操作者}」。
     * - 「清潔 SOP」區塊裡的進度條是 progressbar（或 progress 元素），帶目前值與上下限。
     * - 底部的派單區與「單據管理」動作列各是一組按鈕（group 或 region），名稱分別是「派單」「單據管理」。
     * 這裡的讀法都不等元素出現，讀到什麼算什麼；要等畫面更新由呼叫端輪詢。
     */

    /** 狀態標籤可能的文字。 */
    public static final List<String> STATUS_TAGS = List.of("待派單", "待認領", "已接", "進行中", "已完成", "待查房",
            "已完修", "查房通過", "免查房", "已結案", "已回答", "中斷", "延後中", "已取消");

    /** 讀面板上半部用的頁內程式：只算不在具名區塊、清單項目、按鈕裡的字；同一段字只取最內層那一個元素。 */
    private static final String READ_HEADER = """
            (panel, tags) => {
              const text = el => (el.innerText || '').replace(/\\s+/g, ' ').trim();
              const sectioned = el => !!(el.parentElement && el.parentElement.closest(
                'section[aria-label], section[aria-labelledby], [role=region], [role=group], li, button, [role=button]'));
              const all = [...panel.querySelectorAll('*')];
              const innermost = (test, outsideOnly) => all.filter(el => test(text(el))
                && !(outsideOnly && sectioned(el))
                && ![...el.children].some(child => test(text(child))));
              const tagSet = new Set(tags);
              const headings = all.filter(el => (/^H[1-6]$/.test(el.tagName) || el.getAttribute('role') === 'heading')
                && !sectioned(el)).map(text);
              const tagNodes = innermost(t => tagSet.has(t), true).map(el => {
                const r = el.getBoundingClientRect();
                return { text: text(el), top: r.top, bottom: r.bottom };
              });
              return {
                headings,
                tags: tagNodes,
                subtitles: innermost(t => /^.+ · .+建立$/.test(t), true).map(text),
                originals: innermost(t => /^[^\\s：]*原文：/.test(t), false).map(text),
                ticketNos: innermost(t => /^[A-Z]-\\d+$/.test(t), false).map(text),
              };
            }
            """;

    private static final String READ_LINES = """
            nodes => nodes.map(node => (node.innerText || '').split('\\n')
              .map(line => line.replace(/\\s+/g, ' ').trim()).filter(line => line.length > 0))
            """;

    private static final String READ_CONTENT_ROWS = """
            region => [...region.querySelectorAll('dt')].map(dt => {
              const norm = s => (s || '').replace(/\\s+/g, ' ').trim();
              let dd = dt.nextElementSibling;
              while (dd && dd.tagName !== 'DD') dd = dd.nextElementSibling;
              return [norm(dt.innerText), dd ? norm(dd.innerText) : ''];
            })
            """;

    private static final String READ_PROGRESS = """
            region => {
              const bar = region.querySelector('[role="progressbar"], progress');
              if (!bar) return null;
              const now = Number(bar.getAttribute('aria-valuenow') ?? bar.value);
              const min = Number(bar.getAttribute('aria-valuemin') ?? 0);
              const max = Number(bar.getAttribute('aria-valuemax') ?? bar.max ?? 100);
              return max > min ? (now - min) / (max - min) : null;
            }
            """;

    private static final String READ_BUTTON_NAMES = """
            buttons => buttons.map(b => (b.getAttribute('aria-label') || b.innerText || '').replace(/\\s+/g, ' ').trim())
            """;

    /** 面板上半部的一個狀態標籤與它在畫面上的上下緣。 */
    public record TagReading(String text, double top, double bottom) {
    }

    /**
     * 面板上半部讀到的字。
     *
     * @param headings 不在具名區塊裡的標題，依畫面先後：房號標題、這張單的標題
     * @param tags 狀態標籤，正常剛好一個
     * @param subtitles 標題下那一行（「… · … 建立」），正常剛好一個
     * @param originals 整個面板上以「原文：」結尾的字開頭的列
     * @param ticketNos 整個面板上的單號
     */
    public record HeaderReading(List<String> headings, List<TagReading> tags, List<String> subtitles,
            List<String> originals, List<String> ticketNos) {

        /** 房號標題開頭的房號；找不到時是空字串。 */
        public String roomNo() {
            for (String heading : headings) {
                java.util.regex.Matcher m = ROOM_AT_START.matcher(heading);
                if (m.find()) {
                    return m.group(1);
                }
            }
            return "";
        }

        /** 這張單的標題：房號標題以外的第一個標題；沒有時是 {@code null}。 */
        public String title() {
            String room = roomNo();
            for (String heading : headings) {
                if (room.isEmpty() || !ROOM_AT_START.matcher(heading).find()) {
                    return heading;
                }
            }
            return null;
        }
    }

    private static final Pattern ROOM_AT_START = Pattern.compile("^(\\d+)(\\s|$)");

    /** 一筆歷程：一句話與下面那行灰字。 */
    public record TimelineRow(String sentence, String meta) {

        private static final Pattern META = Pattern.compile("^(?:(\\d{1,2}/\\d{1,2}) )?(\\d{1,2}:\\d{2}) · (.+)$");

        /** 灰字裡的時刻（HH:mm）；格式不對時是空字串。 */
        public String time() {
            java.util.regex.Matcher m = META.matcher(meta);
            return m.matches() ? m.group(2) : "";
        }

        /** 灰字裡的操作者；格式不對時是空字串。 */
        public String actor() {
            java.util.regex.Matcher m = META.matcher(meta);
            return m.matches() ? m.group(3) : "";
        }
    }

    /** 「清潔 SOP」區塊的文字與進度條的比例；沒有進度條時比例是 {@code null}。 */
    public record SopReading(String text, Double ratio) {
    }

    /** 讀面板上半部。 */
    @SuppressWarnings("unchecked")
    public HeaderReading readHeader() {
        Map<String, Object> raw = (Map<String, Object>) panel().first().evaluate(READ_HEADER, STATUS_TAGS);
        List<TagReading> tags = new ArrayList<>();
        for (Object item : (List<Object>) raw.get("tags")) {
            Map<String, Object> tag = (Map<String, Object>) item;
            tags.add(new TagReading((String) tag.get("text"), ((Number) tag.get("top")).doubleValue(),
                    ((Number) tag.get("bottom")).doubleValue()));
        }
        return new HeaderReading((List<String>) raw.get("headings"), tags, (List<String>) raw.get("subtitles"),
                (List<String>) raw.get("originals"), (List<String>) raw.get("ticketNos"));
    }

    /** 面板裡這個名稱的區塊；{@code name} 是字串時完全相同，是 {@link Pattern} 時照它比對。 */
    private Locator region(Object name) {
        Locator.GetByRoleOptions options = new Locator.GetByRoleOptions();
        if (name instanceof Pattern pattern) {
            options.setName(pattern);
        } else {
            options.setName((String) name).setExact(true);
        }
        return panel().getByRole(AriaRole.REGION, options);
    }

    /** 狀態訊息卡。 */
    public Locator statusCard() {
        return region("狀態訊息");
    }

    /** 狀態訊息卡的每一行字：第一行是標題，其餘是說明；沒有訊息卡時是空的。 */
    public List<String> statusCardLines() {
        return firstLines(statusCard());
    }

    /** 「清潔 SOP」區塊。 */
    public Locator sopSection() {
        return region("清潔 SOP");
    }

    /** 讀「清潔 SOP」區塊；沒有這個區塊時是 {@code null}。 */
    public SopReading readSop() {
        Locator section = sopSection();
        if (section.count() == 0) {
            return null;
        }
        Object ratio = section.first().evaluate(READ_PROGRESS);
        return new SopReading(BoardPage.collapse(section.first().innerText()),
                ratio == null ? null : ((Number) ratio).doubleValue());
    }

    /** 「內容」區塊。 */
    public Locator contentSection() {
        return region("內容");
    }

    /** 「內容」由上到下每一列的列名與值；沒有這個區塊時是空的。 */
    @SuppressWarnings("unchecked")
    public List<List<String>> contentRows() {
        Locator section = contentSection();
        if (section.count() == 0) {
            return List.of();
        }
        return (List<List<String>>) section.first().evaluate(READ_CONTENT_ROWS);
    }

    /** 歷程區塊：名稱以「歷程」開頭。 */
    public Locator timelineSection() {
        return region(Pattern.compile("^歷程"));
    }

    /** 某個區塊的標題文字；區塊或標題不存在時是空字串。 */
    public String headingOf(Locator section) {
        if (section.count() == 0) {
            return "";
        }
        Locator heading = section.first().getByRole(AriaRole.HEADING);
        return heading.count() == 0 ? "" : BoardPage.collapse(heading.first().innerText());
    }

    /** 歷程由上到下（舊到新）的每一筆；沒有歷程區塊時是空的。 */
    public List<TimelineRow> timelineRows() {
        Locator section = timelineSection();
        if (section.count() == 0) {
            return List.of();
        }
        List<TimelineRow> rows = new ArrayList<>();
        for (List<String> lines : readLines(section.first().getByRole(AriaRole.LISTITEM))) {
            if (lines.isEmpty()) {
                continue;
            }
            String meta = lines.size() > 1 ? lines.get(lines.size() - 1) : "";
            String sentence = String.join(" ", lines.subList(0, Math.max(1, lines.size() - 1)));
            rows.add(new TimelineRow(sentence, meta));
        }
        return rows;
    }

    /** 底部的「派單」區。 */
    public Locator dispatchArea() {
        return namedButtonArea("派單");
    }

    /** 底部的「單據管理」動作列。 */
    public Locator managementRow() {
        return namedButtonArea("單據管理");
    }

    /** 這個範圍裡每一顆按鈕的名稱，依畫面先後。 */
    @SuppressWarnings("unchecked")
    public List<String> buttonNamesIn(Locator area) {
        return (List<String>) area.getByRole(AriaRole.BUTTON).evaluateAll(READ_BUTTON_NAMES);
    }

    private Locator namedButtonArea(String name) {
        return panel().getByRole(AriaRole.GROUP, new Locator.GetByRoleOptions().setName(name).setExact(true))
                .or(region(name));
    }

    private List<String> firstLines(Locator section) {
        if (section.count() == 0) {
            return List.of();
        }
        List<List<String>> all = readLines(section.first());
        return all.isEmpty() ? List.of() : all.get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> readLines(Locator nodes) {
        return (List<List<String>>) nodes.evaluateAll(READ_LINES);
    }
}
