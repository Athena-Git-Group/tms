package com.athena.web.bdd.pages;

import com.athena.web.bdd.support.BrowserRegex;
import com.athena.web.bdd.support.JsResult;
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

public class DetailPanel extends PageObject {

    public DetailPanel(WebWorld world) {
        super(world);
    }

    public Locator panel() {
        return page().getByRole(AriaRole.COMPLEMENTARY, new Page.GetByRoleOptions()
                .setName(UiWords.endsWithAnyLanguage("詳情")));
    }

    public Locator button(String name) {
        return panel().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(name.strip()).setExact(true));
    }

    public void openAndWait(UUID taskId, String roomNo, Runnable open) {
        page().waitForResponse(
                response -> "GET".equals(response.request().method())
                        && response.url().split("[?#]")[0].endsWith("/api/v1/desk/tasks/" + taskId),
                open);
        panel().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName(roomNo))
                .first().waitFor();
    }

    public Locator roomHeading(String roomNo) {
        return panel().getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^" + BrowserRegex.literal(roomNo.strip()) + "(\\s|$)")));
    }

    public boolean loaded() {
        return !"true".equals(panel().first().getAttribute("aria-busy"))
                && panel().locator("[aria-busy='true']").count() == 0;
    }

    public void assignAndWait(UUID taskId, String dept) {
        String assignPath = "/api/v1/desk/tasks/" + taskId + "/assign";
        Response response = page().waitForResponse(
                r -> "POST".equals(r.request().method()) && r.url().split("[?#]")[0].endsWith(assignPath),
                () -> new ShellFrame(world).press(button("派給" + dept.strip())));
        requireOk(response, "派給" + dept.strip());
    }

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

    public void closeAndWait() {
        if (panel().count() > 0 && panel().first().isVisible()) {
            new ShellFrame(world).press(button("關閉"));
        }
        panel().first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN));
    }

    private static void requireOk(Response response, String pressed) {
        if (!response.ok()) {
            throw new IllegalStateException("按「" + pressed + "」後後端回 " + response.status()
                    + "：" + response.text());
        }
    }

    public static final List<String> STATUS_TAGS = List.of("待派單", "待認領", "已接", "進行中", "已完成", "待查房",
            "已完修", "查房通過", "免查房", "已結案", "已回答", "中斷", "延後中", "已取消");

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

    public record TagReading(String text, double top, double bottom) {
    }

    public record HeaderReading(List<String> headings, List<TagReading> tags, List<String> subtitles,
            List<String> originals, List<String> ticketNos) {

        public String roomNo() {
            for (String heading : headings) {
                java.util.regex.Matcher m = ROOM_AT_START.matcher(heading);
                if (m.find()) {
                    return m.group(1);
                }
            }
            return "";
        }

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

    public record TimelineRow(String sentence, String meta) {

        private static final Pattern META = Pattern.compile("^(?:(\\d{1,2}/\\d{1,2}) )?(\\d{1,2}:\\d{2}) · (.+)$");

        public String time() {
            java.util.regex.Matcher m = META.matcher(meta);
            return m.matches() ? m.group(2) : "";
        }

        public String actor() {
            java.util.regex.Matcher m = META.matcher(meta);
            return m.matches() ? m.group(3) : "";
        }
    }

    public record SopReading(String text, Double ratio) {
    }

    public HeaderReading readHeader() {
        Map<String, Object> raw = JsResult.object(panel().first().evaluate(READ_HEADER, STATUS_TAGS));
        List<TagReading> tags = new ArrayList<>();
        for (Map<String, Object> tag : JsResult.objects(raw.get("tags"))) {
            tags.add(new TagReading(JsResult.string(tag.get("text")), JsResult.number(tag.get("top")).doubleValue(),
                    JsResult.number(tag.get("bottom")).doubleValue()));
        }
        return new HeaderReading(JsResult.strings(raw.get("headings")), tags, JsResult.strings(raw.get("subtitles")),
                JsResult.strings(raw.get("originals")), JsResult.strings(raw.get("ticketNos")));
    }

    private Locator region(Object name) {
        Locator.GetByRoleOptions options = new Locator.GetByRoleOptions();
        if (name instanceof Pattern pattern) {
            options.setName(pattern);
        } else {
            options.setName((String) name).setExact(true);
        }
        return panel().getByRole(AriaRole.REGION, options);
    }

    public Locator statusCard() {
        return region("狀態訊息");
    }

    public List<String> statusCardLines() {
        return firstLines(statusCard());
    }

    public Locator sopSection() {
        return region("清潔 SOP");
    }

    public SopReading readSop() {
        Locator section = sopSection();
        if (section.count() == 0) {
            return null;
        }
        Object ratio = section.first().evaluate(READ_PROGRESS);
        return new SopReading(BoardPage.collapse(section.first().innerText()),
                ratio == null ? null : ((Number) ratio).doubleValue());
    }

    public Locator contentSection() {
        return region("內容");
    }

    public List<List<String>> contentRows() {
        Locator section = contentSection();
        if (section.count() == 0) {
            return List.of();
        }
        return JsResult.stringRows(section.first().evaluate(READ_CONTENT_ROWS));
    }

    public Locator timelineSection() {
        return region(Pattern.compile("^歷程"));
    }

    public String headingOf(Locator section) {
        if (section.count() == 0) {
            return "";
        }
        Locator heading = section.first().getByRole(AriaRole.HEADING);
        return heading.count() == 0 ? "" : BoardPage.collapse(heading.first().innerText());
    }

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

    public Locator dispatchArea() {
        return namedButtonArea("派單");
    }

    public Locator managementRow() {
        return namedButtonArea("單據管理");
    }

    public List<String> buttonNamesIn(Locator area) {
        return JsResult.strings(area.getByRole(AriaRole.BUTTON).evaluateAll(READ_BUTTON_NAMES));
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

    private static List<List<String>> readLines(Locator nodes) {
        return JsResult.stringRows(nodes.evaluateAll(READ_LINES));
    }
}
