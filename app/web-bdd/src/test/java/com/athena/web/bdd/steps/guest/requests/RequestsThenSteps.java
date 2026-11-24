package com.athena.web.bdd.steps.guest.requests;

import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.guest.GuestFrame;
import com.athena.web.bdd.pages.guest.RequestPage;
import com.athena.web.bdd.pages.guest.RequestsPage;
import com.athena.web.bdd.pages.guest.RequestsPage.TagColor;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.GuestStay;
import com.athena.web.bdd.support.GuestStay.Request;
import com.athena.web.bdd.support.GuestTerms;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestsThenSteps {

    private static final List<String> SECTIONS = List.of("進行中", "已結束");

    private static final Pattern CLOCK_TIME = Pattern.compile("(?<![0-9])[0-9]{1,2}:[0-9]{2}(?![0-9])");

    private static final Pattern RGB = Pattern.compile("rgba?\\(\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*(?:,\\s*([0-9.]+)\\s*)?\\)");

    private static final List<String> MESSAGE_BOX_TITLES = List.of("櫃檯正在看您的需求", "櫃檯回覆", "這個需求已取消");

    private final WebWorld world;

    private final GuestScenario scenario;

    private final GuestStay stay;

    private final GuestFrame frame;

    private final RequestsPage requests;

    private final RequestPage request;

    public RequestsThenSteps(WebWorld world, GuestScenario scenario, GuestStay stay) {
        this.world = world;
        this.scenario = scenario;
        this.stay = stay;
        this.frame = new GuestFrame(world);
        this.requests = new RequestsPage(world);
        this.request = new RequestPage(world);
    }

    @那麼("清單各列如下：")
    public void rowsAre(DataTable table) {
        List<Map<String, String>> expected = table.asMaps();
        frame.eventually(() -> {
            String language = frame.language();
            List<Locator> rows = allRows(language);
            List<String> titles = rows.stream().map(row -> RequestsPage.linesOf(row).get(0)).toList();
            List<String> expectedTitles = expected.stream().map(row -> row.get("標題").strip()).toList();
            if (!titles.equals(expectedTitles)) {
                return "清單由上到下是 " + titles + "，應該剛好是 " + expectedTitles;
            }
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> want = expected.get(i);
                List<String> lines = RequestsPage.linesOf(rows.get(i));
                if (!lines.contains(want.get("第二行").strip())) {
                    return "「" + titles.get(i) + "」那一列的字是 " + lines + "，沒有第二行「" + want.get("第二行").strip() + "」";
                }
                List<String> tags = GuestFrame.tagsIn(rows.get(i), language);
                if (!tags.equals(List.of(want.get("狀態標籤").strip()))) {
                    return "「" + titles.get(i) + "」的狀態標籤是 " + tags + "，不是「" + want.get("狀態標籤").strip() + "」";
                }
            }
            return null;
        });
    }

    @那麼("清單各列的標籤與段落如下：")
    public void tagsAndSectionsAre(DataTable table) {
        List<Map<String, String>> expected = table.asMaps();
        frame.eventually(() -> {
            String language = frame.language();
            for (Map<String, String> want : expected) {
                String title = want.get("標題").strip();
                String section = want.get("清單段落").strip();
                Optional<Locator> row = requests.rowsIn(GuestTerms.in(language, section)).stream()
                        .filter(candidate -> RequestsPage.linesOf(candidate).get(0).equals(title))
                        .findFirst();
                if (row.isEmpty()) {
                    return "「" + section + "」段落底下沒有「" + title + "」";
                }
                List<String> tags = GuestFrame.tagsIn(row.get(), language);
                if (!tags.equals(List.of(want.get("狀態標籤").strip()))) {
                    return "「" + title + "」的狀態標籤是 " + tags + "，不是「" + want.get("狀態標籤").strip() + "」";
                }
            }
            return null;
        });
        for (Map<String, String> want : expected) {
            Request backend = stay.request(scenario.request(want.get("標題").strip()));
            String section = backend.active() ? "進行中" : "已結束";
            assertThat(backend.tag().equals(want.get("狀態標籤").strip()) && section.equals(want.get("清單段落").strip()),
                    "後端「" + backend.title() + "」是「" + backend.situation() + "」，依狀況對照是「" + backend.tag()
                            + "」、在「" + section + "」，不是表格寫的");
        }
    }

    @那麼("每列左邊有依需求類型的圖示")
    public void rowsHaveTypeIcons() {
        Map<String, String> typeByTitle = new HashMap<>();
        for (Request r : stay.requests(scenario.phoneRoom())) {
            typeByTitle.put(r.title(), typeOf(r));
        }
        frame.eventually(() -> {
            List<Locator> rows = allRows(frame.language());
            if (rows.isEmpty()) {
                return "清單上沒有任何一列";
            }
            for (Locator row : rows) {
                String title = RequestsPage.linesOf(row).get(0);
                String type = typeByTitle.get(title);
                if (type == null) {
                    return "後端這次住宿沒有「" + title + "」";
                }
                String icon = RequestsPage.leftIconTypeOf(row);
                if (!icon.equals(type)) {
                    return "「" + title + "」是 " + type + "，左邊的圖示是 " + icon;
                }
            }
            return null;
        });
    }

    @那麼("各狀態標籤的顏色如下：")
    public void tagColorsAre(DataTable table) {
        frame.eventually(() -> {
            String language = frame.language();
            Map<String, TagColor> seen = new HashMap<>();
            for (Map<String, String> want : table.asMaps()) {
                String tag = want.get("狀態標籤").strip();
                String tone = want.get("顏色").strip();
                List<TagColor> colors = requests.tagColors(GuestTerms.in(language, tag));
                if (colors.isEmpty()) {
                    return "畫面上沒有「" + tag + "」標籤";
                }
                for (TagColor color : colors) {
                    String actual = toneOf(color);
                    if (!actual.equals(tone)) {
                        return "「" + tag + "」的底色 " + color.background() + "、字色 " + color.text() + " 看起來是「" + actual
                                + "」，不是「" + tone + "」";
                    }
                }
                seen.put(tag, colors.get(0));
            }
            if (seen.containsKey("已送出") && seen.containsKey("已取消") && !seen.get("已送出").equals(seen.get("已取消"))) {
                return "「已送出」" + seen.get("已送出") + " 與「已取消」" + seen.get("已取消") + " 的色調不同";
            }
            return null;
        });
    }

    @那麼("進度頁的第一張卡是標籤 {string}、標題 {string} 與 {string}")
    public void firstCardIs(String tag, String title, String secondLine) {
        frame.eventually(() -> {
            String language = frame.language();
            Optional<Locator> card = request.card(language);
            if (card.isEmpty()) {
                return "進度頁上找不到第一張卡";
            }
            Optional<String> shownTitle = request.cardTitle();
            if (!shownTitle.equals(Optional.of(title.strip()))) {
                return "第一張卡的標題是 " + shownTitle.orElse("（沒有）") + "，不是「" + title + "」";
            }
            List<String> tags = GuestFrame.tagsIn(card.get(), language);
            if (!tags.equals(List.of(tag.strip()))) {
                return "第一張卡的狀態標籤是 " + tags + "，不是「" + tag + "」";
            }
            List<String> lines = ShellFrame.linesOf(card.get().innerText());
            if (!lines.contains(secondLine.strip())) {
                return "第一張卡的字是 " + lines + "，沒有「" + secondLine + "」";
            }
            String type = typeOf(stay.request(scenario.request(title.strip())));
            Optional<Locator> withIcon = request.cardWithIcon(language);
            String icon = withIcon.map(RequestsPage::leftIconTypeOf).orElse("?沒有圖示");
            if (!icon.equals(type)) {
                return "第一張卡是 " + type + "，左邊的圖示是 " + icon;
            }
            return request.cardTitleFullyShown() ? null : "第一張卡的標題沒有完整顯示";
        });
    }

    @那麼("進度頁沒有狀態訊息框")
    public void noMessageBox() {
        frame.eventually(() -> {
            String drawn = request.drawnProblem();
            if (drawn != null) {
                return drawn;
            }
            List<List<String>> boxes = request.messageBoxes();
            if (!boxes.isEmpty()) {
                return "進度頁上有狀態訊息框 " + boxes;
            }
            List<String> lines = bodyLines();
            List<String> shown = MESSAGE_BOX_TITLES.stream().filter(lines::contains).toList();
            return shown.isEmpty() ? null : "進度頁上看得到訊息框的字 " + shown;
        });
    }

    @那麼("進度頁上唯一的時間是 {string}")
    public void onlyTimeIs(String expected) {
        frame.eventually(() -> {
            String text = String.join("\n", bodyLines());
            List<String> times = new ArrayList<>();
            Matcher matcher = CLOCK_TIME.matcher(text);
            while (matcher.find()) {
                times.add(matcher.group());
            }
            if (times.size() != 1) {
                return "進度頁上的時間是 " + times + "，應該只有「" + expected + "」裡的那一個";
            }
            return text.contains(expected.strip()) && expected.contains(times.get(0)) ? null
                    : "進度頁上的時間「" + times.get(0) + "」不在「" + expected + "」裡";
        });
    }

    private List<Locator> allRows(String language) {
        List<Locator> rows = new ArrayList<>();
        for (String section : SECTIONS) {
            rows.addAll(requests.rowsIn(GuestTerms.in(language, section)));
        }
        return rows;
    }

    private String typeOf(Request r) {
        return String.valueOf(world.backend().queryRows("SELECT type FROM tasks WHERE tenant_id = ? AND id = ?",
                world.defaults().tenantId(), r.id()).get(0).get("type"));
    }

    private List<String> bodyLines() {
        return ShellFrame.linesOf(world.page().locator("body").innerText());
    }

    static String toneOf(TagColor color) {
        double[] bg = hsl(color.background());
        double[] fg = hsl(color.text());
        if (green(bg) || green(fg)) {
            return "已完成色";
        }
        if (brand(bg) && fg[2] >= 0.94) {
            return "處理中色";
        }
        if (brand(bg) && bg[2] >= 0.7) {
            return "已受理色";
        }
        if (bg[1] < 0.15 && bg[2] >= 0.75 && fg[1] < 0.15 && fg[2] <= 0.4) {
            return "中性";
        }
        return "認不出的色調";
    }

    private static boolean green(double[] hsl) {
        return hsl[0] >= 90 && hsl[0] <= 165 && hsl[1] >= 0.15 && hsl[2] < 0.97;
    }

    private static boolean brand(double[] hsl) {
        return hsl[0] >= 170 && hsl[0] <= 205 && hsl[1] >= 0.15;
    }

    private static double[] hsl(String css) {
        Matcher m = RGB.matcher(css);
        if (!m.find()) {
            return new double[] {0, 0, 1};
        }
        if (m.group(4) != null && Double.parseDouble(m.group(4)) == 0) {
            return new double[] {0, 0, 1};
        }
        double r = Double.parseDouble(m.group(1)) / 255;
        double g = Double.parseDouble(m.group(2)) / 255;
        double b = Double.parseDouble(m.group(3)) / 255;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2;
        double d = max - min;
        if (d == 0) {
            return new double[] {0, 0, l};
        }
        double s = d / (1 - Math.abs(2 * l - 1));
        double h = max == r ? 60 * (((g - b) / d) % 6) : max == g ? 60 * ((b - r) / d + 2) : 60 * ((r - g) / d + 4);
        return new double[] {h < 0 ? h + 360 : h, s, l};
    }

    private static void assertThat(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
