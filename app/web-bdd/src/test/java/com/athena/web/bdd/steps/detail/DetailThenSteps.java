package com.athena.web.bdd.steps.detail;

import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.DetailPanel.HeaderReading;
import com.athena.web.bdd.pages.DetailPanel.SopReading;
import com.athena.web.bdd.pages.DetailPanel.TagReading;
import com.athena.web.bdd.pages.DetailPanel.TimelineRow;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.ScenarioTasks.Entry;
import com.athena.web.bdd.support.WebWorld;
import com.athena.web.bdd.support.WhenStart;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DetailThenSteps {

    private static final double ASSERT_TIMEOUT_MS = 5_000;

    private static final double TOLERANCE_PX = 1;

    private static final Set<String> BACKEND_STATES = Set.of("triage", "pool", "accepted", "doing", "done",
            "inspected", "closed", "deferred", "interrupted", "cancelled");

    private static final List<String> MANAGEMENT_BUTTONS = List.of("取消", "延後…", "指派…", "轉派…");

    private static final String NO_MANAGEMENT_ROW = "整列不出現";

    private static final Map<String, List<String>> MANAGEMENT_BY_ROLE_AND_STATE = Map.ofEntries(
            Map.entry("frontdesk/pool", List.of("取消", "延後…")),
            Map.entry("frontdesk/accepted", List.of()),
            Map.entry("frontdesk/doing", List.of()),
            Map.entry("frontdesk/done", List.of()),
            Map.entry("frontdesk/deferred", List.of()),
            Map.entry("frontdesk/cancelled", List.of()),
            Map.entry("manager/pool", List.of("取消", "延後…", "指派…")),
            Map.entry("manager/accepted", List.of("取消", "延後…", "轉派…")),
            Map.entry("manager/doing", List.of("取消", "延後…", "轉派…")),
            Map.entry("manager/done", List.of()),
            Map.entry("duty_manager/pool", List.of("取消", "延後…", "指派…")),
            Map.entry("duty_manager/accepted", List.of("取消", "延後…", "轉派…")),
            Map.entry("duty_manager/doing", List.of("取消", "延後…", "轉派…")),
            Map.entry("duty_manager/done", List.of()));

    private static final Pattern GUEST_COUNT = Pattern.compile("\\d+\\s*(人|位|名)|人數");

    private static final Pattern GUEST_ROW = Pattern.compile("^\\S+ · \\d{1,2}/\\d{1,2}–\\d{1,2}/\\d{1,2}$|^空房$");

    private static final Pattern CLOCK_TIME = Pattern.compile("(?<!\\d)(\\d{1,2}:\\d{2})(?!\\d)");

    private static final Pattern ELAPSED_MINUTES = Pattern.compile("已(?:等)?\\s*(\\d+)\\s*分");

    private static final Pattern QUOTED = Pattern.compile("「[^」]*」");

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final WebWorld world;

    private final DetailPanel detail;

    private final TimelineBeforeWhen timelineBefore;

    public DetailThenSteps(WebWorld world, TimelineBeforeWhen timelineBefore) {
        this.world = world;
        this.detail = new DetailPanel(world);
        this.timelineBefore = timelineBefore;
    }

    @那麼("詳情面板貼齊畫面右側")
    public void panelDockedRight() {
        BoardPage board = new BoardPage(world);
        eventually("詳情面板沒有貼齊畫面右側", () -> {
            if (detail.panel().count() != 1 || !detail.panel().isVisible()) {
                return "畫面上看不到詳情面板（找到 " + detail.panel().count() + " 個）";
            }
            BoundingBox box = detail.panel().boundingBox();
            var viewport = world.page().viewportSize();
            if (Math.abs(box.x + box.width - viewport.width) > TOLERANCE_PX) {
                return "面板右緣在 " + (box.x + box.width) + "，視窗寬 " + viewport.width;
            }
            if (Math.abs(box.y) > TOLERANCE_PX || Math.abs(box.height - viewport.height) > TOLERANCE_PX) {
                return "面板沒有佔滿高度：上緣 " + box.y + "、高 " + box.height + "，視窗高 " + viewport.height;
            }
            BoundingBox boardBox = board.boardBodyBox();
            if (boardBox == null) {
                return "量不到看板本體的位置";
            }
            if (boardBox.x + boardBox.width > box.x + TOLERANCE_PX) {
                return "看板右緣在 " + (boardBox.x + boardBox.width) + "，伸進了面板（面板左緣 " + box.x + "）";
            }
            return null;
        });
    }

    @那麼("詳情面板由上到下依序是 {string} 與單號、房號 {string} 與狀態標籤、標題 {string}、{string}、狀態訊息卡、{string}、{string}")
    public void panelTopToBottom(String close, String roomNo, String title, String subtitle, String contentTitle,
            String timelineTitle) {
        eventually("詳情面板由上到下的區塊不對", () -> {
            HeaderReading header = detail.readHeader();
            if (header.ticketNos().size() != 1) {
                return "面板上應該剛好一個單號，找到 " + header.ticketNos();
            }
            if (header.tags().size() != 1) {
                return "面板上應該剛好一個狀態標籤，找到 " + header.tags();
            }
            if (!title.equals(header.title())) {
                return "標題是「" + header.title() + "」，應該是「" + title + "」";
            }
            if (!header.subtitles().equals(List.of(subtitle))) {
                return "標題下那一行是 " + header.subtitles() + "，應該是「" + subtitle + "」";
            }
            if (!contentTitle.equals(detail.headingOf(detail.contentSection()))) {
                return "「內容」區塊的標題是「" + detail.headingOf(detail.contentSection()) + "」";
            }
            if (!timelineTitle.equals(detail.headingOf(detail.timelineSection()))) {
                return "歷程區塊的標題是「" + detail.headingOf(detail.timelineSection()) + "」";
            }
            Locator closeButton = detail.button(close);
            Locator ticket = detail.panel().getByText(header.ticketNos().get(0),
                    new Locator.GetByTextOptions().setExact(true));
            Locator room = detail.roomHeading(roomNo);
            if (closeButton.count() != 1 || ticket.count() != 1 || room.count() != 1) {
                return "找不到「" + close + "」鈕、單號或房號 " + roomNo + " 的標題（各找到 " + closeButton.count()
                        + "、" + ticket.count() + "、" + room.count() + " 個）";
            }
            BoundingBox closeBox = closeButton.boundingBox();
            BoundingBox ticketBox = ticket.boundingBox();
            BoundingBox roomBox = room.boundingBox();
            TagReading tag = header.tags().get(0);
            if (!overlapsVertically(closeBox.y, closeBox.y + closeBox.height, ticketBox.y,
                    ticketBox.y + ticketBox.height)) {
                return "單號沒有和「" + close + "」在同一列";
            }
            if (!overlapsVertically(roomBox.y, roomBox.y + roomBox.height, tag.top(), tag.bottom())) {
                return "狀態標籤沒有和房號在同一列";
            }
            Map<String, Locator> blocks = new LinkedHashMap<>();
            blocks.put("房號 " + roomNo, room);
            blocks.put("標題「" + title + "」", detail.panel().getByRole(AriaRole.HEADING,
                    new Locator.GetByRoleOptions().setName(title).setExact(true)));
            blocks.put("「" + subtitle + "」", detail.panel().getByText(subtitle,
                    new Locator.GetByTextOptions().setExact(true)));
            blocks.put("狀態訊息卡", detail.statusCard());
            blocks.put("「" + contentTitle + "」", detail.contentSection());
            blocks.put("「" + timelineTitle + "」", detail.timelineSection());
            String previous = "「" + close + "」與單號";
            double previousTop = Math.max(closeBox.y, ticketBox.y);
            for (Map.Entry<String, Locator> block : blocks.entrySet()) {
                Locator found = block.getValue();
                if (found.count() != 1 || !found.isVisible()) {
                    return "面板上看不到" + block.getKey() + "（找到 " + found.count() + " 個）";
                }
                double top = found.boundingBox().y;
                if (top <= previousTop) {
                    return block.getKey() + "沒有在" + previous + "的下面";
                }
                previous = block.getKey();
                previousTop = top;
            }
            return null;
        });
    }

    @那麼("詳情面板的單號是 {string}")
    public void ticketNo(String ticketNo) {
        eventually("詳情面板的單號不對", () -> {
            List<String> shown = detail.readHeader().ticketNos();
            return shown.equals(List.of(ticketNo)) ? null : "面板上的單號是 " + shown + "，應該只有「" + ticketNo + "」";
        });
        String backendNo = backendDetail().path("displayNo").asText();
        if (!ticketNo.equals(backendNo)) {
            throw new AssertionError("後端這張單的 displayNo 是「" + backendNo + "」，規格寫的是「" + ticketNo + "」");
        }
    }

    @那麼("詳情面板的「內容」如下：")
    public void contentRows(DataTable table) {
        List<List<String>> expected = new ArrayList<>();
        for (Map<String, String> row : table.asMaps()) {
            expected.add(List.of(row.get("列名").strip(), row.get("內容").strip()));
        }
        eventually("詳情面板的「內容」不對", () -> {
            List<List<String>> shown = detail.contentRows();
            return shown.equals(expected) ? null : "畫面上是 " + shown + "，應該是 " + expected;
        });
    }

    @那麼("詳情面板「內容」的 {string} 是 {string}")
    public void contentRow(String name, String value) {
        eventually("詳情面板「內容」的「" + name + "」不對", () -> {
            List<String> values = contentValues(name);
            return values.equals(List.of(value)) ? null : "那一列的值是 " + values + "，應該是「" + value + "」";
        });
    }

    @那麼("詳情面板上沒有住客人數")
    public void noGuestCount() {
        eventually("詳情面板上有住客人數", () -> {
            if (!detail.loaded()) {
                return "面板還在載入";
            }
            Matcher count = GUEST_COUNT.matcher(BoardPage.collapse(detail.panel().innerText()));
            if (count.find()) {
                return "面板上出現「" + count.group() + "」";
            }
            List<String> guest = contentValues("住客");
            if (guest.size() != 1 || !GUEST_ROW.matcher(guest.get(0)).matches()) {
                return "「住客」列應該只有姓名與入住、退房日期，畫面上是 " + guest;
            }
            return null;
        });
    }

    @那麼("詳情面板標題下那一行是 {string}")
    public void subtitle(String text) {
        eventually("詳情面板標題下那一行不對", () -> {
            List<String> shown = detail.readHeader().subtitles();
            return shown.equals(List.of(text)) ? null : "畫面上是 " + shown + "，應該是「" + text + "」";
        });
    }

    @那麼("詳情面板的標題是 {string}")
    public void title(String title) {
        eventually("詳情面板的標題不對", () -> {
            String shown = detail.readHeader().title();
            return title.equals(shown) ? null : "畫面上是「" + shown + "」，應該是「" + title + "」";
        });
    }

    @那麼("詳情面板的原文列是 {string}")
    public void originalLine(String text) {
        eventually("詳情面板的原文列不對", () -> {
            List<String> shown = detail.readHeader().originals();
            return shown.equals(List.of(text)) ? null : "畫面上是 " + shown + "，應該是「" + text + "」";
        });
    }

    @那麼("詳情面板沒有原文列")
    public void noOriginalLine() {
        eventually("詳情面板有原文列", () -> {
            if (!detail.loaded()) {
                return "面板還在載入";
            }
            List<String> shown = detail.readHeader().originals();
            return shown.isEmpty() ? null : "畫面上有 " + shown;
        });
    }

    @那麼("詳情面板的「清潔 SOP」區塊：{word}")
    public void sopSection(String presence) {
        boolean expected = switch (presence.strip()) {
            case "有" -> true;
            case "沒有" -> false;
            default -> throw new IllegalArgumentException("「清潔 SOP」區塊只能寫「有」或「沒有」，收到「" + presence + "」");
        };
        eventually("詳情面板的「清潔 SOP」區塊不對", () -> {
            if (!detail.loaded()) {
                return "面板還在載入";
            }
            boolean shown = detail.sopSection().count() > 0;
            return shown == expected ? null : (shown ? "面板上有「清潔 SOP」區塊" : "面板上找不到「清潔 SOP」區塊");
        });
    }

    @那麼("詳情面板的清潔 SOP 顯示 {string} 與進度條")
    public void sopProgress(String text) {
        JsonNode sop = backendDetail().path("sop");
        if (sop.isMissingNode() || sop.isNull() || sop.path("total").asInt() <= 0) {
            throw new AssertionError("後端這張單沒有清潔 SOP 的進度：" + sop);
        }
        double ratio = sop.path("done").asDouble() / sop.path("total").asDouble();
        eventually("詳情面板的清潔 SOP 不對", () -> {
            SopReading reading = detail.readSop();
            if (reading == null) {
                return "面板上找不到「清潔 SOP」區塊";
            }
            if (!reading.text().contains(text)) {
                return "區塊上的字是「" + reading.text() + "」，沒有「" + text + "」";
            }
            if (reading.ratio() == null) {
                return "區塊裡沒有進度條";
            }
            return Math.abs(reading.ratio() - ratio) < 0.01 ? null
                    : "進度條的比例是 " + reading.ratio() + "，後端是 " + sop.path("done") + " / " + sop.path("total");
        });
    }

    @那麼("詳情面板上沒有急件標記")
    public void noUrgentMark() {
        eventually("詳情面板上有急件標記", () -> {
            if (!detail.loaded()) {
                return "面板還在載入";
            }
            if (detail.panel().innerText().contains("急件")) {
                return "面板上有「急件」字樣";
            }
            int icons = detail.panel().locator("[aria-label*='急件'], img[alt*='急件'], [title*='急件']").count();
            return icons == 0 ? null : "面板上有 " + icons + " 個以「急件」為名的圖示";
        });
    }

    @那麼("詳情面板的狀態標籤是 {string}")
    public void statusTag(String tag) {
        eventually("詳情面板的狀態標籤不對", () -> {
            List<String> shown = detail.readHeader().tags().stream().map(TagReading::text).toList();
            return shown.equals(List.of(tag)) ? null : "畫面上的狀態標籤是 " + shown + "，應該是「" + tag + "」";
        });
        String state = backendDetail().path("state").asText();
        if (!BACKEND_STATES.contains(state)) {
            throw new AssertionError("後端這張單的狀態是「" + state + "」，不在既有的十個值之內");
        }
    }

    @那麼("詳情面板的狀態訊息卡標題是 {string}，說明是 {string}")
    public void statusCard(String title, String description) {
        eventually("詳情面板的狀態訊息卡不對", () -> {
            List<String> lines = detail.statusCardLines();
            if (lines.isEmpty()) {
                return "面板上沒有狀態訊息卡";
            }
            String shownDescription = String.join(" ", lines.subList(1, lines.size()));
            if (!lines.get(0).equals(title) || !shownDescription.equals(description)) {
                return "畫面上的標題是「" + lines.get(0) + "」、說明是「" + shownDescription + "」";
            }
            return null;
        });
        crossCheckWithBackend(title + " " + description);
    }

    @那麼("詳情面板的狀態訊息卡只有標題 {string}，沒有說明")
    public void statusCardTitleOnly(String title) {
        eventually("詳情面板的狀態訊息卡不對", () -> {
            List<String> lines = detail.statusCardLines();
            return lines.equals(List.of(title)) ? null : "狀態訊息卡上是 " + lines + "，應該只有「" + title + "」";
        });
    }

    @那麼("詳情面板沒有狀態訊息卡")
    public void noStatusCard() {
        eventually("詳情面板有狀態訊息卡", () -> {
            if (!detail.loaded()) {
                return "面板還在載入";
            }
            if (detail.statusCard().count() > 0) {
                return "面板上有狀態訊息卡：" + detail.statusCardLines();
            }
            return detail.contentSection().count() == 1 ? null : "標題那一塊下面找不到「內容」";
        });
    }

    @那麼("詳情面板的狀態訊息卡沒有 {string}")
    public void statusCardWithout(String text) {
        eventually("詳情面板的狀態訊息卡有「" + text + "」", () -> {
            List<String> lines = detail.statusCardLines();
            if (lines.isEmpty()) {
                return "面板上沒有狀態訊息卡";
            }
            return String.join(" ", lines).contains(text) ? "狀態訊息卡上是 " + lines : null;
        });
    }

    @那麼("詳情面板的狀態訊息卡寫出經手的主管是 {string}")
    public void statusCardManager(String manager) {
        eventually("詳情面板的狀態訊息卡沒有寫出經手的主管", () -> {
            List<String> lines = detail.statusCardLines();
            if (lines.size() < 2) {
                return "狀態訊息卡沒有說明：" + lines;
            }
            String description = String.join(" ", lines.subList(1, lines.size()));
            return description.contains(manager) ? null : "說明是「" + description + "」，沒有「" + manager + "」";
        });
        JsonNode task = backendDetail();
        String deferredBy = task.path("deferral").path("byName").asText("");
        String cancelledBy = task.path("cancellation").path("byName").asText("");
        if (!manager.equals(deferredBy) && !manager.equals(cancelledBy)) {
            throw new AssertionError("後端記的延後者是「" + deferredBy + "」、取消者是「" + cancelledBy
                    + "」，都不是「" + manager + "」");
        }
    }

    @那麼("詳情面板的歷程標題是 {string}")
    public void timelineTitle(String title) {
        eventually("詳情面板的歷程標題不對", () -> {
            String shown = detail.headingOf(detail.timelineSection());
            return title.equals(shown) ? null : "畫面上是「" + shown + "」，應該是「" + title + "」";
        });
    }

    @那麼("詳情面板的歷程由舊到新包含以下各筆：")
    public void timelineRows(DataTable table) {
        List<List<String>> expected = new ArrayList<>();
        for (Map<String, String> row : table.asMaps()) {
            expected.add(List.of(row.get("句子").strip(), row.get("時間").strip(), row.get("操作者").strip()));
        }
        eventually("詳情面板的歷程不對", () -> {
            List<List<String>> shown = new ArrayList<>();
            for (TimelineRow row : detail.timelineRows()) {
                shown.add(List.of(row.sentence(), row.time(), row.actor()));
            }
            return shown.equals(expected) ? null : "畫面上是 " + shown + "，應該是 " + expected;
        });
        List<String> backendTimes = new ArrayList<>();
        for (JsonNode entry : backendDetail().path("timeline")) {
            backendTimes.add(clockTime(entry.path("at").asText()));
        }
        int next = 0;
        for (List<String> row : expected) {
            while (next < backendTimes.size() && !backendTimes.get(next).equals(row.get(1))) {
                next++;
            }
            if (next == backendTimes.size()) {
                throw new AssertionError("畫面上「" + row.get(0) + "」那一筆的時間 " + row.get(1)
                        + " 在後端歷程裡依序對不到；後端歷程的時間是 " + backendTimes);
            }
            next++;
        }
    }

    @那麼("詳情面板的歷程最新一筆是 {string}，操作者是 {string}")
    public void latestTimelineRow(String sentence, String actor) {
        eventually("詳情面板的歷程最新一筆不對", () -> {
            List<TimelineRow> rows = detail.timelineRows();
            if (rows.isEmpty()) {
                return "歷程上沒有任何一筆";
            }
            TimelineRow last = rows.get(rows.size() - 1);
            return last.sentence().equals(sentence) && last.actor().equals(actor) ? null
                    : "最新一筆是「" + last.sentence() + "」，灰字是「" + last.meta() + "」";
        });
    }

    @那麼("詳情面板的歷程最新一筆的時間與操作者寫的是 {string}")
    public void latestTimelineMeta(String text) {
        eventually("詳情面板的歷程最新一筆的灰字不對", () -> {
            List<TimelineRow> rows = detail.timelineRows();
            if (rows.isEmpty()) {
                return "歷程上沒有任何一筆";
            }
            String meta = rows.get(rows.size() - 1).meta();
            return meta.equals(text) ? null : "最新一筆的灰字是「" + meta + "」，應該是「" + text + "」";
        });
    }

    @那麼("詳情面板的歷程沒有 {string}")
    public void timelineWithout(String sentence) {
        eventually("詳情面板的歷程有「" + sentence + "」", () -> {
            if (!detail.loaded() || detail.timelineSection().count() == 0) {
                return "歷程還沒有出現";
            }
            for (TimelineRow row : detail.timelineRows()) {
                if (row.sentence().equals(sentence)) {
                    return "歷程上有「" + row.sentence() + "」（" + row.meta() + "）";
                }
            }
            return null;
        });
    }

    @那麼("詳情面板的歷程比原本多一筆 {string}，原本的各筆都沒有變")
    public void timelineGrewByOne(String sentence) {
        List<TimelineRow> before = timelineBefore.rows();
        eventually("詳情面板的歷程沒有剛好多一筆「" + sentence + "」", () -> {
            List<TimelineRow> now = detail.timelineRows();
            if (now.size() != before.size() + 1) {
                return "動作之前有 " + before.size() + " 筆，現在有 " + now.size() + " 筆：" + now;
            }
            if (!now.subList(0, before.size()).equals(before)) {
                return "動作之前的各筆變了：之前是 " + before + "，現在是 " + now.subList(0, before.size());
            }
            String added = now.get(now.size() - 1).sentence();
            return added.equals(sentence) ? null : "多出來的那一筆是「" + added + "」";
        });
    }

    @那麼("詳情面板底部的派單區依序是 {string}、{string}、{string} 三格")
    public void dispatchArea(String one, String two, String three) {
        List<String> expected = List.of(one, two, three);
        eventually("詳情面板底部的派單區不對", () -> {
            Locator area = detail.dispatchArea();
            if (area.count() != 1) {
                return "面板上找不到「派單」區（找到 " + area.count() + " 個）";
            }
            List<String> names = detail.buttonNamesIn(area);
            if (!names.equals(expected)) {
                return "派單區的格子是 " + names + "，應該是 " + expected;
            }
            Locator buttons = area.getByRole(AriaRole.BUTTON);
            double firstWidth = buttons.nth(0).boundingBox().width;
            for (int i = 1; i < buttons.count(); i++) {
                double width = buttons.nth(i).boundingBox().width;
                if (Math.abs(width - firstWidth) > TOLERANCE_PX) {
                    return "三格不等寬：「" + names.get(0) + "」寬 " + firstWidth + "，「" + names.get(i) + "」寬 " + width;
                }
            }
            return null;
        });
    }

    @那麼("派單區的格子裡沒有待接數、在班人數與 {string} 小標")
    public void dispatchAreaPlain(String label) {
        eventually("派單區的格子裡有按鈕文字以外的字", () -> {
            Locator area = detail.dispatchArea();
            if (area.count() != 1) {
                return "面板上找不到「派單」區（找到 " + area.count() + " 個）";
            }
            String text = BoardPage.collapse(area.innerText());
            if (text.matches(".*\\d.*")) {
                return "派單區有數字：「" + text + "」";
            }
            if (text.contains("在班") || text.contains(label)) {
                return "派單區有「在班」或「" + label + "」：「" + text + "」";
            }
            String buttonsOnly = String.join(" ", detail.buttonNamesIn(area));
            return text.replace(" ", "").equals(buttonsOnly.replace(" ", "")) ? null
                    : "派單區除了按鈕文字還有別的字：「" + text + "」";
        });
    }

    @那麼("詳情面板沒有「單據管理」動作列")
    public void noManagementRow() {
        eventually("詳情面板有「單據管理」動作列", () -> {
            if (!detail.loaded()) {
                return "面板還在載入";
            }
            if (detail.managementRow().count() > 0) {
                return "面板上有「單據管理」動作列";
            }
            for (String name : MANAGEMENT_BUTTONS) {
                if (detail.button(name).count() > 0) {
                    return "面板上有「" + name + "」";
                }
            }
            return null;
        });
    }

    @那麼("詳情面板沒有派單區，也沒有 {string}")
    public void noDispatchArea(String button) {
        eventually("詳情面板還有派單區或「" + button + "」", () -> {
            if (!detail.loaded()) {
                return "面板還在載入";
            }
            for (String name : List.of("派給房務", "派給工務", button)) {
                if (detail.button(name).count() > 0) {
                    return "面板上有「" + name + "」";
                }
            }
            return detail.dispatchArea().count() == 0 ? null : "面板上還有「派單」區";
        });
    }

    @那麼("詳情面板的「單據管理」動作列是：{}")
    public void managementRow(String row) {
        List<String> expected = NO_MANAGEMENT_ROW.equals(row.strip()) ? List.of()
                : List.of(row.strip().split("、"));
        eventually("詳情面板的「單據管理」動作列不對", () -> {
            if (!detail.loaded()) {
                return "面板還在載入";
            }
            Locator area = detail.managementRow();
            if (expected.isEmpty()) {
                if (area.count() > 0) {
                    return "應該整列不出現，畫面上有 " + detail.buttonNamesIn(area);
                }
                for (String name : MANAGEMENT_BUTTONS) {
                    if (detail.button(name).count() > 0) {
                        return "應該整列不出現，面板上有「" + name + "」";
                    }
                }
                return null;
            }
            if (area.count() != 1) {
                return "面板上找不到「單據管理」動作列（找到 " + area.count() + " 個）";
            }
            List<String> names = detail.buttonNamesIn(area);
            return names.equals(expected) ? null : "動作列是 " + names + "，應該是 " + expected;
        });
        JsonNode login = new ShellFrame(world).storedLoginResponse();
        if (login == null) {
            throw new AssertionError("這個分頁沒有登入狀態，無法對照登入者的角色");
        }
        String key = login.path("role").asText() + "/" + backendDetail().path("state").asText();
        List<String> byRule = MANAGEMENT_BY_ROLE_AND_STATE.get(key);
        if (byRule != null && !byRule.equals(expected)) {
            throw new AssertionError("登入者角色與單的狀態是 " + key + "，動作列應該是 " + byRule + "，規格寫的是 " + expected);
        }
    }

    private List<String> contentValues(String name) {
        List<String> values = new ArrayList<>();
        for (List<String> row : detail.contentRows()) {
            if (row.get(0).equals(name)) {
                values.add(row.get(1));
            }
        }
        return values;
    }

    private void crossCheckWithBackend(String cardText) {
        JsonNode task = backendDetail();
        String text = QUOTED.matcher(cardText).replaceAll("");
        Set<String> names = new HashSet<>();
        for (String path : List.of("assigneeName", "accepted/byName", "accepted/previousAssigneeName",
                "deferral/byName", "cancellation/byName", "answeredByName", "createdByName", "repair/verifiedByName")) {
            JsonNode node = task.at("/" + path);
            if (node.isTextual()) {
                names.add(node.asText());
            }
        }
        for (String staffName : world.defaults().staffByName().keySet()) {
            if (text.contains(staffName) && !names.contains(staffName)) {
                throw new AssertionError("狀態訊息卡寫了「" + staffName + "」，後端這張單記著的人只有 " + names);
            }
        }
        List<OffsetDateTime> moments = new ArrayList<>();
        for (String path : List.of("createdAt", "waitingSince", "acceptedAt", "accepted/at", "startedAt", "doneAt",
                "interruptedAt", "endedAt", "updatedAt")) {
            JsonNode node = task.at("/" + path);
            if (node.isTextual()) {
                moments.add(OffsetDateTime.parse(node.asText()));
            }
        }
        Set<String> clockTimes = new HashSet<>();
        for (OffsetDateTime moment : moments) {
            clockTimes.add(clockTime(moment.toString()));
        }
        Matcher clock = CLOCK_TIME.matcher(text);
        while (clock.find()) {
            String shown = clock.group(1).length() == 4 ? "0" + clock.group(1) : clock.group(1);
            if (!clockTimes.contains(shown)) {
                throw new AssertionError("狀態訊息卡寫了 " + shown + "，後端這張單的時間點是 " + clockTimes);
            }
        }
        OffsetDateTime serverTime = OffsetDateTime.parse(task.path("serverTime").asText());
        Matcher minutes = ELAPSED_MINUTES.matcher(text);
        while (minutes.find()) {
            long shown = Long.parseLong(minutes.group(1));
            boolean matches = moments.stream().anyMatch(moment ->
                    Math.abs(Duration.between(moment, serverTime).toMinutes() - shown) <= 1);
            if (!matches) {
                throw new AssertionError("狀態訊息卡寫了「" + minutes.group() + "」，和後端這張單任何一個時間點到 "
                        + serverTime + " 的分鐘數都對不上");
            }
        }
    }

    private JsonNode backendDetail() {
        return world.backend().getJson("/desk/tasks/" + shownTaskId(), world.staff(WhenStart.BACKEND_READER));
    }

    private UUID shownTaskId() {
        HeaderReading header = detail.readHeader();
        String roomNo = header.roomNo();
        if (roomNo.isEmpty()) {
            throw new AssertionError("詳情面板上找不到房號，無法知道它顯示的是哪一張單：" + header.headings());
        }
        List<Entry> entries = world.tasks().entriesIn(roomNo);
        if (entries.size() == 1) {
            return entries.get(0).taskId();
        }
        String title = header.title();
        for (Entry entry : entries) {
            if (entry.title() != null && (entry.title().equals(title) || ("「" + entry.title() + "」").equals(title))) {
                return entry.taskId();
            }
        }
        throw new AssertionError("詳情面板顯示 " + roomNo + " 房的「" + title + "」，這個場景在這間房準備的單是 "
                + entries.stream().map(Entry::title).toList());
    }

    private static String clockTime(String isoMoment) {
        return OffsetDateTime.parse(isoMoment).atZoneSameInstant(ZoneId.systemDefault()).format(HH_MM);
    }

    private static boolean overlapsVertically(double top1, double bottom1, double top2, double bottom2) {
        return top1 < bottom2 && top2 < bottom1;
    }

    private void eventually(String what, Supplier<String> problem) {
        String[] last = {null};
        try {
            world.page().waitForCondition(() -> (last[0] = problem.get()) == null,
                    new Page.WaitForConditionOptions().setTimeout(ASSERT_TIMEOUT_MS));
        } catch (TimeoutError timeout) {
            throw new AssertionError(what + "：" + last[0], timeout);
        }
    }
}
