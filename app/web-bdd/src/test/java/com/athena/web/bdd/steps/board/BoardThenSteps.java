package com.athena.web.bdd.steps.board;

import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.BoardPage.CardReading;
import com.athena.web.bdd.pages.BoardPage.HeaderReading;
import com.athena.web.bdd.pages.BoardPage.LaneReading;
import com.athena.web.bdd.pages.BoardPage.TileReading;
import com.athena.web.bdd.pages.BoardPage.TriedDrag;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.ScenarioTasks.Entry;
import com.athena.web.bdd.support.WebWorld;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BoardThenSteps {

    private static final long POLL_LIMIT_MS = 5_000;

    private static final String BOARD_READER = "林佩君";

    private static final Pattern MINUTES = Pattern.compile("(\\d+) 分");

    private final WebWorld world;

    private final BoardPage board;

    public BoardThenSteps(WebWorld world) {
        this.world = world;
        this.board = new BoardPage(world);
    }

    @那麼("總覽看板由上到下依序是 {int} 張數量卡、看板控制列與看板本體")
    public void boardLayoutTopToBottom(int tileCount) {
        eventually(() -> {
            int tiles = board.tileRow().getByRole(AriaRole.BUTTON).count();
            check(tiles == tileCount, "數量卡應該是 " + tileCount + " 張，畫面上是 " + tiles + " 張");
            BoundingBox tileRow = board.tileRow().boundingBox();
            BoundingBox controls = world.page().getByRole(AriaRole.GROUP,
                    new Page.GetByRoleOptions().setName("泳道").setExact(true)).boundingBox();
            BoundingBox body = board.boardBodyBox();
            check(tileRow.y < controls.y && controls.y < body.y,
                    "由上到下應該是數量卡、控制列、看板本體，實際上緣依序是 " + tileRow.y + "、" + controls.y + "、" + body.y);
        });
    }

    @那麼("看板由左到右的欄是 {string}、{string}、{string}、{string}、{string}、{string}、{string}、{string}")
    public void columnsLeftToRight(String c1, String c2, String c3, String c4, String c5, String c6, String c7,
            String c8) {
        List<String> expected = List.of(c1, c2, c3, c4, c5, c6, c7, c8);
        eventually(() -> {
            List<HeaderReading> headers = board.readHeaders();
            List<String> names = headers.stream().map(HeaderReading::column).toList();
            check(names.equals(expected), "欄頭由左到右應該是 " + expected + "，畫面上是 " + names);
            for (int i = 1; i < headers.size(); i++) {
                check(headers.get(i).left() > headers.get(i - 1).left(),
                        "「" + names.get(i) + "」應該在「" + names.get(i - 1) + "」的右邊");
            }
        });
    }

    @那麼("看板各欄的單如下：")
    public void cardsPerColumn(DataTable table) {
        eventually(() -> {
            JsonNode deskBoard = deskBoard();
            for (Map<String, String> row : table.asMaps()) {
                String column = cell(row, "欄");
                Set<String> expected = new LinkedHashSet<>(splitList(cell(row, "欄裡的單")));
                Set<String> actual = roomsIn(column);
                check(actual.equals(expected), "「" + column + "」欄的卡片應該是 " + expected + "，畫面上是 " + actual);
                Integer count = headerOf(column).count();
                int expectedCount = Integer.parseInt(cell(row, "欄頭數量"));
                check(Objects.equals(count, expectedCount),
                        "「" + column + "」欄頭數量應該是 " + expectedCount + "，畫面上是 " + count);
                for (String room : actual) {
                    List<String> backendColumns = tasksOfRoom(deskBoard, room).stream()
                            .map(BoardPage::columnOfTask).toList();
                    check(backendColumns.contains(column), "「" + room + "」在畫面的「" + column
                            + "」欄，但後端這間房的單依狀況對照落在 " + backendColumns);
                }
            }
        });
    }

    @那麼("{string} 欄沒有任何卡片")
    public void columnHasNoCards(String column) {
        eventually(() -> {
            Set<String> rooms = roomsIn(column);
            check(rooms.isEmpty(), "「" + column + "」欄應該沒有卡片，畫面上有 " + rooms);
            Integer count = headerOf(column).count();
            check(Objects.equals(count, 0), "「" + column + "」欄頭數量應該是 0，畫面上是 " + count);
        });
    }

    @那麼("看板上沒有 {string}、{string}、{string} 的卡片")
    public void boardHasNoCardsOf(String r1, String r2, String r3) {
        List<String> rooms = List.of(r1, r2, r3);
        eventually(() -> {
            Set<String> shown = allRoomsOnBoard();
            for (String room : rooms) {
                check(!shown.contains(room), "看板上不應該有「" + room + "」的卡片");
            }
            Set<String> backendIds = new HashSet<>();
            deskBoard().path("tasks").forEach(task -> backendIds.add(task.path("id").asText()));
            for (String room : rooms) {
                for (Entry entry : world.tasks().entriesIn(room)) {
                    check(!backendIds.contains(entry.taskId().toString()),
                            "後端看板的回應不應該有「" + room + "」的單（" + entry.title() + "）");
                }
            }
        });
    }

    @那麼("各欄的空欄文字如下：")
    public void emptyColumnTexts(DataTable table) {
        eventually(() -> {
            for (Map<String, String> row : table.asMaps()) {
                String column = cell(row, "欄");
                Locator cells = board.column(column);
                check(cells.count() > 0, "畫面上找不到「" + column + "」欄");
                for (int i = 0; i < cells.count(); i++) {
                    Locator one = cells.nth(i);
                    check(board.cardsIn(one).count() == 0, "「" + column + "」欄應該沒有卡片");
                    String text = BoardPage.collapse(one.innerText());
                    Locator heading = one.getByRole(AriaRole.HEADING);
                    if (heading.count() > 0) {
                        String headerText = BoardPage.collapse(heading.first().innerText());
                        text = text.startsWith(headerText) ? text.substring(headerText.length()).strip() : text;
                    }
                    String expected = cell(row, "空欄時的文字");
                    check(text.equals(expected), "「" + column + "」欄的空欄文字應該是「" + expected + "」，畫面上是「" + text + "」");
                }
            }
        });
    }

    @那麼("各欄由上到下的順序如下：")
    public void orderWithinColumns(DataTable table) {
        eventually(() -> {
            for (Map<String, String> row : table.asMaps()) {
                String column = cell(row, "欄");
                List<String> expected = splitList(cell(row, "由上到下"));
                List<String> actual = cardsTopToBottom(column).stream().map(CardReading::roomNo).toList();
                check(actual.equals(expected), "「" + column + "」欄由上到下應該是 " + expected + "，畫面上是 " + actual);
            }
        });
    }

    @那麼("四張數量卡依序如下：")
    public void fourTilesInOrder(DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        eventually(() -> {
            List<TileReading> tiles = board.readTiles();
            check(tiles.size() == rows.size(), "數量卡應該有 " + rows.size() + " 張，畫面上有 " + tiles.size() + " 張");
            for (int i = 0; i < rows.size(); i++) {
                TileReading tile = tiles.get(i);
                String description = cell(rows.get(i), "說明");
                String number = cell(rows.get(i), "數字");
                check(tile.description().equals(description),
                        "第 " + (i + 1) + " 張數量卡的說明應該是「" + description + "」，畫面上是「" + tile.description() + "」");
                check(tile.number().equals(number),
                        "第 " + (i + 1) + " 張數量卡的數字應該是「" + number + "」，畫面上是「" + tile.number() + "」");
            }
            List<List<String>> columnsOfTile = List.of(List.of("待派單"), List.of("待認領"), List.of("已接", "進行中"));
            for (int i = 0; i < Math.min(3, tiles.size()); i++) {
                List<String> columns = columnsOfTile.get(i);
                if (columns.stream().anyMatch(column -> board.column(column).count() == 0)) {
                    continue;
                }
                int cards = columns.stream().mapToInt(column -> board.cardsIn(board.column(column)).count()).sum();
                check(tiles.get(i).number().equals(String.valueOf(cards)), "第 " + (i + 1) + " 張數量卡的數字是「"
                        + tiles.get(i).number() + "」，但看板上 " + columns + " 共有 " + cards + " 張卡片");
            }
        });
    }

    @那麼("{string} 數量卡的說明是 {string}，數字是 {string}")
    public void tileDescriptionAndNumber(String tileName, String description, String number) {
        eventually(() -> {
            TileReading tile = board.readTile(tileName);
            check(tile.description().equals(description),
                    "「" + tileName + "」數量卡的說明應該是「" + description + "」，畫面上是「" + tile.description() + "」");
            check(tile.number().equals(number),
                    "「" + tileName + "」數量卡的數字應該是「" + number + "」，畫面上是「" + tile.number() + "」");
        });
    }

    @那麼("{string} 數量卡的數字顏色是 {string}")
    public void tileNumberColor(String tileName, String color) {
        String tone = toneOf(color);
        eventually(() -> {
            TileReading tile = board.readTile(tileName);
            check(tone.equals(tile.tone()),
                    "「" + tileName + "」數量卡的數字應該是" + color + "（" + tone + "），畫面上標的是「" + tile.tone() + "」");
        });
    }

    @那麼("四張數量卡上都沒有 {string}")
    public void tilesWithout(String text) {
        eventually(() -> {
            List<TileReading> tiles = board.readTiles();
            check(tiles.size() == 4, "數量卡應該有 4 張，畫面上有 " + tiles.size() + " 張");
            for (TileReading tile : tiles) {
                check(!tile.text().contains(text), "數量卡「" + tile.text() + "」不應該有「" + text + "」");
            }
        });
    }

    @那麼("看板 {int} 個欄都在，控制列沒有篩選膠囊")
    public void allColumnsAndNoFilter(int columnCount) {
        eventually(() -> {
            List<String> names = board.columnNames();
            check(names.size() == columnCount && new HashSet<>(names).size() == columnCount,
                    "看板應該有 " + columnCount + " 個欄，畫面上是 " + names);
            check(board.filterPill().count() == 0, "控制列不應該有篩選膠囊，畫面上是「" + board.filterPillText() + "」");
            for (TileReading tile : board.readTiles()) {
                check(!tile.pressed(), "數量卡「" + tile.text() + "」不應該是選中的樣子");
            }
        });
    }

    @那麼("{int} 個欄頭都有色點、欄名與數量")
    public void headersHaveDotNameCount(int columnCount) {
        eventually(() -> {
            List<HeaderReading> headers = board.readHeaders();
            check(headers.size() == columnCount, "欄頭應該有 " + columnCount + " 個，畫面上有 " + headers.size() + " 個");
            for (HeaderReading header : headers) {
                check(BoardPage.COLUMN_NAMES.contains(header.column()), "欄頭「" + header.column() + "」不是看板的欄名");
                check(header.hasDot(), "「" + header.column() + "」欄頭沒有色點");
                check(header.count() != null, "「" + header.column() + "」欄頭沒有數量");
            }
        });
    }

    @那麼("欄頭的數量與警示如下：")
    public void headerCountsAndWarnings(DataTable table) {
        eventually(() -> {
            for (Map<String, String> row : table.asMaps()) {
                String column = cell(row, "欄");
                HeaderReading header = headerOf(column);
                int count = Integer.parseInt(cell(row, "數量"));
                check(Objects.equals(header.count(), count),
                        "「" + column + "」欄頭數量應該是 " + count + "，畫面上是 " + header.count());
                String warning = cell(row, "警示");
                check(header.warning().equals(warning), warning.isEmpty()
                        ? "「" + column + "」欄頭除了色點、欄名、數量不應該有其他字，畫面上多了「" + header.warning() + "」"
                        : "「" + column + "」欄頭的警示應該是「" + warning + "」，畫面上是「" + header.warning() + "」");
            }
        });
    }

    @那麼("各張卡片的部門、類型、來源與 VIP 標如下：")
    public void cardDeptTypeSourceVip(DataTable table) {
        eventually(() -> {
            JsonNode deskBoard = deskBoard();
            for (Map<String, String> row : table.asMaps()) {
                String room = cell(row, "房號");
                CardReading card = onlyCardOf(room);
                String dept = cell(row, "部門");
                String type = cell(row, "類型");
                String source = cell(row, "來源圖示");
                boolean vip = yes(cell(row, "VIP 標"));
                check(dept.equals(card.dept()), "「" + room + "」卡片的部門應該是「" + dept + "」，畫面上是「" + card.dept() + "」");
                check(BoardPage.collapse(type).equals(card.type()),
                        "「" + room + "」卡片的類型應該是「" + type + "」，畫面上是「" + card.type() + "」");
                check(card.labels().contains(source),
                        "「" + room + "」卡片應該有名稱是「" + source + "」的來源圖示，畫面上的圖示名稱是 " + card.labels());
                for (String other : SOURCES.values()) {
                    if (!other.equals(source)) {
                        check(!card.labels().contains(other), "「" + room + "」卡片不應該有「" + other + "」的來源圖示");
                    }
                }
                check(!card.text().contains(source), "「" + room + "」卡片的來源圖示旁不應該有文字「" + source + "」");
                check(card.vip() == vip, "「" + room + "」卡片" + (vip ? "應該" : "不應該") + "有「VIP」標");

                JsonNode task = onlyTaskOfRoom(deskBoard, room);
                check(dept.equals(DEPTS.get(task.path("dept").asText())), "「" + room + "」後端的部門是 " + task.path("dept"));
                check(type.replace("·", "").strip().equals(TYPES.get(task.path("type").asText())),
                        "「" + room + "」後端的類型是 " + task.path("type"));
                check(source.equals(SOURCES.get(task.path("source").asText())),
                        "「" + room + "」後端的來源是 " + task.path("source"));
                check(task.path("vip").asBoolean() == vip, "「" + room + "」後端的 vip 是 " + task.path("vip"));
            }
        });
    }

    @那麼("各張卡片的標題與副標如下：")
    public void cardTitlesAndSubtitles(DataTable table) {
        eventually(() -> {
            for (Map<String, String> row : table.asMaps()) {
                String room = cell(row, "房號");
                CardReading card = onlyCardOf(room);
                String title = cell(row, "標題");
                String subtitle = cell(row, "副標");
                check(card.middle().startsWith(title),
                        "「" + room + "」卡片的標題應該是「" + title + "」，畫面上標題那一段是「" + card.middle() + "」");
                String actualSubtitle = subtitleOf(card, title);
                check(actualSubtitle.equals(subtitle), subtitle.isEmpty()
                        ? "「" + room + "」卡片不應該有副標，畫面上是「" + actualSubtitle + "」"
                        : "「" + room + "」卡片的副標應該是「" + subtitle + "」，畫面上是「" + actualSubtitle + "」");
            }
        });
    }

    @那麼("{string} 的卡片顯示 {string} 與進度條")
    public void cardShowsSopProgress(String room, String text) {
        Matcher fraction = Pattern.compile("(\\d+)/(\\d+)").matcher(text);
        if (!fraction.find()) {
            throw new IllegalArgumentException("SOP 文字應該寫成「SOP 已做/總數」，收到「" + text + "」");
        }
        int done = Integer.parseInt(fraction.group(1));
        int total = Integer.parseInt(fraction.group(2));
        eventually(() -> {
            CardReading card = onlyCardOf(room);
            check(card.text().contains(text), "「" + room + "」卡片上應該有「" + text + "」，畫面上是「" + card.text() + "」");
            check(card.hasBar(), "「" + room + "」卡片上應該有進度條");
            check(card.barRatio() != null && Math.abs(card.barRatio() - (double) done / total) < 0.01,
                    "「" + room + "」卡片的進度條比例應該是 " + done + "/" + total + "，畫面上是 " + card.barRatio());
            JsonNode sop = onlyTaskOfRoom(deskBoard(), room).path("sop");
            check(sop.path("done").asInt(-1) == done && sop.path("total").asInt(-1) == total,
                    "「" + room + "」後端的 SOP 進度是 " + sop);
        });
    }

    @那麼("{string} 的卡片沒有 SOP 進度")
    public void cardHasNoSop(String room) {
        eventually(() -> {
            CardReading card = onlyCardOf(room);
            check(!card.text().contains("SOP"), "「" + room + "」卡片上不應該有「SOP」，畫面上是「" + card.text() + "」");
            check(!card.hasBar(), "「" + room + "」卡片上不應該有進度條");
        });
    }

    @那麼("各張卡片的負責人頭像如下：")
    public void cardAvatars(DataTable table) {
        eventually(() -> {
            JsonNode deskBoard = deskBoard();
            for (Map<String, String> row : table.asMaps()) {
                String room = cell(row, "房號");
                String avatar = cell(row, "頭像");
                CardReading card = onlyCardOf(room);
                check(card.avatar().equals(avatar), avatar.isEmpty()
                        ? "「" + room + "」卡片不應該有頭像，畫面上是「" + card.avatar() + "」"
                        : "「" + room + "」卡片的頭像應該是「" + avatar + "」，畫面上是「" + card.avatar() + "」");
                JsonNode assignee = onlyTaskOfRoom(deskBoard, room).path("assigneeName");
                if (avatar.isEmpty()) {
                    check(assignee.isMissingNode() || assignee.isNull(), "「" + room + "」後端有負責人 " + assignee);
                } else {
                    check(assignee.asText("").endsWith(avatar),
                            "「" + room + "」的頭像「" + avatar + "」應該是後端負責人姓名的最後一個字，後端是 " + assignee);
                }
            }
        });
    }

    @那麼("各張卡片的等待膠囊如下：")
    public void waitPills(DataTable table) {
        eventually(() -> {
            JsonNode deskBoard = deskBoard();
            for (Map<String, String> row : table.asMaps()) {
                String room = cell(row, "房號");
                String text = cell(row, "膠囊文字");
                String color = cell(row, "顏色");
                CardReading card = onlyCardOf(room);
                check(card.pillText().equals(text),
                        "「" + room + "」卡片的膠囊應該是「" + text + "」，畫面上是「" + card.pillText() + "」");
                check(toneOf(color).equals(card.pillTone()),
                        "「" + room + "」卡片的膠囊應該是" + color + "，畫面上標的是「" + card.pillTone() + "」");
                checkWaitingMinutes(room, card.pillText(), deskBoard);
            }
        });
    }

    @那麼("{string} 欄的卡片上都沒有 {string}")
    public void columnCardsWithout(String column, String text) {
        eventually(() -> {
            for (CardReading card : board.readCardsIn(board.column(column))) {
                check(!card.text().contains(text),
                        "「" + column + "」欄的「" + card.roomNo() + "」卡片上不應該有「" + text + "」：" + card.text());
            }
        });
    }

    @那麼("{string} 的卡片上沒有急件標記")
    public void cardHasNoUrgentMark(String room) {
        eventually(() -> {
            CardReading card = onlyCardOf(room);
            check(!card.text().contains("急件"), "「" + room + "」卡片上不應該有「急件」：" + card.text());
            check(card.labels().stream().noneMatch(label -> label != null && label.contains("急件")),
                    "「" + room + "」卡片上不應該有急件圖示，圖示名稱是 " + card.labels());
            JsonNode deskBoard = deskBoard();
            JsonNode task = onlyTaskOfRoom(deskBoard, room);
            String column = BoardPage.columnOfTask(task);
            if ("待派單".equals(column) || "待認領".equals(column)) {
                long minutes = waitedMinutes(task, deskBoard);
                String tone = minutes >= 20 ? "red" : minutes >= 10 ? "orange" : "gray";
                check(tone.equals(card.pillTone()), "「" + room + "」等了 " + minutes + " 分，膠囊應該是 " + tone
                        + "，畫面上標的是「" + card.pillTone() + "」");
            }
            List<String> expected = expectedOrder(column, deskBoard);
            List<String> actual = cardsTopToBottom(column).stream().map(CardReading::roomNo).toList();
            check(actual.equals(expected), "「" + column + "」欄應該照一般的規則排成 " + expected + "，畫面上是 " + actual);
        });
    }

    @那麼("^看板只剩 (.+) 這幾欄$")
    public void onlyColumns(String columnList) {
        List<String> expected = splitList(columnList);
        eventually(() -> {
            List<String> names = board.columnNames();
            check(names.equals(expected), "看板的欄應該是 " + expected + "，畫面上是 " + names);
        });
    }

    @那麼("控制列只有一顆篩選膠囊，文字是 {string}")
    public void singleFilterPill(String text) {
        eventually(() -> {
            List<String> pills = board.filterPill().allInnerTexts().stream().map(BoardPage::collapse).toList();
            check(pills.equals(List.of(text)), "控制列應該只有一顆篩選膠囊「" + text + "」，畫面上是 " + pills);
        });
    }

    @那麼("^看板上只有這些房號的卡片：(.+)$")
    public void onlyRooms(String roomList) {
        Set<String> expected = new HashSet<>(splitList(roomList));
        eventually(() -> {
            Set<String> actual = allRoomsOnBoard();
            check(actual.equals(expected), "看板上的卡片房號應該是 " + expected + "，畫面上是 " + actual);
        });
    }

    @那麼("^看板上只有這些標題的卡片：(.+)$")
    public void onlyTitles(String titleList) {
        List<String> expected = splitList(titleList);
        eventually(() -> {
            List<CardReading> cards = allCardsOnBoard();
            check(cards.size() == expected.size(),
                    "看板上應該有 " + expected.size() + " 張卡片，畫面上有 " + cards.size() + " 張");
            List<String> remaining = new ArrayList<>(expected);
            remaining.sort(Comparator.comparingInt(String::length).reversed());
            for (CardReading card : cards) {
                String matched = remaining.stream().filter(title -> card.middle().startsWith(title)).findFirst()
                        .orElseThrow(() -> new AssertionError("「" + card.roomNo() + "」卡片的標題不在清單 " + expected
                                + " 裡：" + card.middle()));
                remaining.remove(matched);
            }
        });
    }

    @那麼("{string} 裡的字是 {string}")
    public void inputValueIs(String input, String value) {
        eventually(() -> {
            String actual = inputNamed(input).inputValue();
            check(actual.equals(value), "「" + input + "」裡的字應該是「" + value + "」，畫面上是「" + actual + "」");
        });
    }

    @那麼("{string} 是空的")
    public void inputIsEmpty(String input) {
        eventually(() -> {
            String actual = inputNamed(input).inputValue();
            check(actual.isEmpty(), "「" + input + "」應該是空的，畫面上是「" + actual + "」");
        });
    }

    @那麼("頁副標的未結案張數是 {int}")
    public void subtitleOpenCount(int count) {
        eventually(() -> {
            Locator subtitle = board.openCountSubtitle();
            check(subtitle.count() > 0, "工具列上找不到「N 張未結案」的頁副標");
            Matcher number = Pattern.compile("(\\d+) 張未結案").matcher(subtitle.first().innerText());
            check(number.find() && Integer.parseInt(number.group(1)) == count,
                    "頁副標的未結案張數應該是 " + count + "，畫面上是「" + subtitle.first().innerText() + "」");
            long open = BoardPage.openTaskCount(deskBoard());
            check(open == count, "全館不在「已結案」欄的張數依後端是 " + open + "，不是 " + count);
        });
    }

    @那麼("^看板的泳道由上到下是 (.+)$")
    public void lanesTopToBottom(String laneList) {
        List<String> expected = splitList(laneList);
        eventually(() -> {
            List<String> actual = board.readLanes().stream().map(LaneReading::name).toList();
            check(actual.equals(expected), "泳道由上到下應該是 " + expected + "，畫面上是 " + actual);
            if (expected.stream().allMatch(name -> name.endsWith(" 樓"))) {
                List<String> floors = new ArrayList<>();
                deskBoard().path("floors").forEach(floor -> floors.add(floor.asInt() + " 樓"));
                check(actual.equals(floors), "樓層泳道應該等於後端的樓層 " + floors + "，畫面上是 " + actual);
            }
        });
    }

    @那麼("各泳道的副標如下：")
    public void laneSubtitles(DataTable table) {
        eventually(() -> {
            List<LaneReading> lanes = board.readLanes();
            for (Map<String, String> row : table.asMaps()) {
                String lane = cell(row, "泳道");
                String subtitle = cell(row, "副標");
                LaneReading reading = lanes.stream().filter(l -> l.name().equals(lane)).findFirst()
                        .orElseThrow(() -> new AssertionError("畫面上找不到「" + lane + "」泳道，泳道是 " + lanes));
                check(reading.subtitle().equals(subtitle),
                        "「" + lane + "」泳道的副標應該是「" + subtitle + "」，畫面上是「" + reading.subtitle() + "」");
                Matcher number = Pattern.compile("(\\d+) 張未結案").matcher(subtitle);
                if (number.find()) {
                    int open = 0;
                    for (String column : BoardPage.COLUMN_NAMES) {
                        if (!"已結案".equals(column)) {
                            open += board.cardsIn(board.columnIn(board.lane(lane), column)).count();
                        }
                    }
                    check(open == Integer.parseInt(number.group(1)), "「" + lane + "」泳道顯示中、不在「已結案」欄的卡片有 "
                            + open + " 張，和副標「" + subtitle + "」不符");
                }
            }
        });
    }

    @那麼("{string} 這一列的 {string} 欄有 {string}")
    public void laneColumnHasTitle(String lane, String column, String title) {
        eventually(() -> {
            List<CardReading> cards = board.readCardsIn(board.columnIn(board.lane(lane), column));
            check(cards.stream().anyMatch(card -> card.middle().contains(title)), "「" + lane + "」這一列的「" + column
                    + "」欄應該有標題含「" + title + "」的卡片，畫面上是 " + cards.stream().map(CardReading::text).toList());
        });
    }

    @那麼("看板沒有泳道標題")
    public void noLaneHeadings() {
        eventually(() -> {
            List<LaneReading> lanes = board.readLanes();
            check(lanes.isEmpty(), "看板不應該有泳道標題，畫面上有 " + lanes);
            int shown = 0;
            for (String column : BoardPage.COLUMN_NAMES) {
                int cells = board.column(column).count();
                check(cells <= 1, "「" + column + "」欄應該只有一格，畫面上有 " + cells + " 格");
                shown += cells;
            }
            check(shown > 0, "畫面上找不到任何一欄");
        });
    }

    @那麼("看板控制列的提示文字是 {string}")
    public void controlHint(String text) {
        eventually(() -> {
            Locator hint = world.page().getByText(text, new Page.GetByTextOptions().setExact(true));
            check(hint.count() > 0 && hint.first().isVisible(), "控制列的提示文字應該是「" + text + "」");
            if (!BoardPage.DRAGGING_HINT.equals(text)) {
                check(board.draggingHint().count() == 0, "控制列不應該是拖曳中的那一句提示");
            }
        });
    }

    @那麼("{string} 欄出現以下拖放區：")
    public void dropZonesInColumn(String column, DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        eventually(() -> {
            Locator cells = board.column(column);
            check(cells.count() > 0, "畫面上找不到「" + column + "」欄");
            for (Map<String, String> row : rows) {
                String zone = cell(row, "拖放區");
                Locator inColumn = cells.getByText(zone, new Locator.GetByTextOptions().setExact(true));
                check(inColumn.count() == 1, "「" + column + "」欄應該剛好有一個「" + zone + "」拖放區，畫面上有 " + inColumn.count() + " 個");
                String description = board.dropZoneDescription(inColumn.first());
                check(description.equals(cell(row, "說明")),
                        "拖放區「" + zone + "」的說明應該是「" + cell(row, "說明") + "」，畫面上是「" + description + "」");
                Locator cards = board.cardsIn(cells.first());
                if (cards.count() > 0) {
                    check(inColumn.first().boundingBox().y < cards.first().boundingBox().y,
                            "拖放區「" + zone + "」應該在「" + column + "」欄最上面");
                }
            }
            int total = board.anyDropZone().count();
            check(total == rows.size(), "整個看板應該只有「" + column + "」欄的 " + rows.size() + " 個拖放區，畫面上有 " + total + " 個");
        });
    }

    @那麼("拖放區 {string} 的說明是 {string}")
    public void dropZoneDescription(String zone, String description) {
        eventually(() -> {
            Locator title = board.dropZone(zone);
            check(title.count() == 1, "畫面上應該剛好有一個「" + zone + "」拖放區，畫面上有 " + title.count() + " 個");
            String actual = board.dropZoneDescription(title.first());
            check(actual.equals(description), "拖放區「" + zone + "」的說明應該是「" + description + "」，畫面上是「" + actual + "」");
        });
    }

    @那麼("看板上沒有任何拖放區")
    public void noDropZones() {
        eventually(() -> check(board.anyDropZone().count() == 0,
                "看板上不應該有拖放區，畫面上有 " + board.anyDropZone().allInnerTexts()));
    }

    @那麼("{string} 的卡片沒有被拖起來，看板上沒有任何拖放區")
    public void cardNotLifted(String room) {
        TriedDrag tried = board.lastTriedDrag()
                .orElseThrow(() -> new IllegalStateException("這個分頁沒有「試著拖」的紀錄，這一句要接在「試著拖」之後"));
        if (!tried.roomNo().equals(room)) {
            throw new IllegalStateException("最近一次試著拖的是「" + tried.roomNo() + "」，不是「" + room + "」");
        }
        check(tried.dropZonesDuring() == 0, "手勢期間看板上出現了 " + tried.dropZonesDuring() + " 個拖放區");
        check(!tried.draggingHintDuring(), "手勢期間控制列換成了拖曳中的那一句提示");
        eventually(() -> {
            int usual = board.card(room).count();
            check(usual == 1, "「" + room + "」的卡片應該只有一張，畫面上有 " + usual + " 張");
            check(tried.cardCopiesDuring() <= usual, "手勢期間「" + room + "」的卡片有 " + tried.cardCopiesDuring()
                    + " 張，多出來的是跟著指標走的卡片");
            check(board.anyDropZone().count() == 0, "看板上不應該有拖放區");
        });
        List<String> assigns = world.network().sentRequestsSince(tried.requestsBefore()).stream()
                .filter(request -> request.is(Network.DESK_ASSIGN)).map(Network.SentRequest::path).toList();
        check(assigns.isEmpty(), "試著拖之後不應該送出派單請求，實際送了 " + assigns);
    }

    @那麼("各泳道 {string} 欄的拖放區如下：")
    public void dropZonesPerLane(String column, DataTable table) {
        eventually(() -> {
            for (Map<String, String> row : table.asMaps()) {
                String lane = cell(row, "泳道");
                Set<String> expected = new HashSet<>(splitList(cell(row, "拖放區")));
                Locator cells = board.columnIn(board.lane(lane), column);
                check(cells.count() > 0, "畫面上找不到「" + lane + "」這一列的「" + column + "」欄");
                Set<String> actual = new HashSet<>();
                for (String zone : List.of("給房務", "給工務")) {
                    if (cells.getByText(zone, new Locator.GetByTextOptions().setExact(true)).count() > 0) {
                        actual.add(zone);
                    }
                }
                check(actual.equals(expected), "「" + lane + "」這一列的「" + column + "」欄的拖放區應該是 " + expected
                        + "，畫面上是 " + actual);
            }
        });
    }

    @那麼("每一欄是灰色的骨架卡片，數量卡的數字位置是骨架")
    public void skeletonBoard() {
        eventually(() -> {
            List<HeaderReading> headers = board.readHeaders();
            List<String> names = headers.stream().map(HeaderReading::column).toList();
            check(names.equals(BoardPage.COLUMN_NAMES), "8 個欄頭的欄名都要在，畫面上是 " + names);
            for (HeaderReading header : headers) {
                check(header.count() == null && header.warning().isEmpty(),
                        "「" + header.column() + "」欄頭的數量位置應該是空白，畫面上是 " + header.count() + " " + header.warning());
            }
            for (String column : BoardPage.COLUMN_NAMES) {
                Locator cells = board.column(column);
                check(cells.count() > 0, "畫面上找不到「" + column + "」欄");
                Locator one = cells.first();
                check(board.cardsIn(one).count() == 0, "「" + column + "」欄還在載入，不應該有真的卡片");
                boolean skeleton = "true".equals(one.evaluate("el => el.getAttribute('aria-busy')"))
                        || one.locator("[aria-busy='true']").count() > 0;
                check(skeleton, "「" + column + "」欄應該是骨架卡片（標成 aria-busy）");
            }
            List<TileReading> tiles = board.readTiles();
            check(tiles.size() == 4, "數量卡應該有 4 張，畫面上有 " + tiles.size() + " 張");
            for (TileReading tile : tiles) {
                check(tile.busy(), "數量卡「" + tile.text() + "」的數字位置應該是骨架");
                check(tile.number().isEmpty() && !tile.text().matches("^[\\d—].*"),
                        "數量卡「" + tile.text() + "」不應該顯示數字（包括 0）");
            }
        });
    }

    private static final Map<String, String> DEPTS =
            Map.of("frontdesk", "未分部門", "housekeeping", "房務", "engineering", "工務");

    private static final Map<String, String> TYPES =
            Map.of("amenity", "備品", "repair", "修繕", "cleaning", "打掃", "other", "其他");

    private static final Map<String, String> SOURCES =
            Map.of("guest", "住客掃碼", "desk", "櫃檯開單", "system", "系統排房", "staff", "同仁報修");

    private static String toneOf(String color) {
        return switch (color.strip()) {
            case "灰色", "一般" -> "gray";
            case "橘色" -> "orange";
            case "紅色" -> "red";
            case "綠色" -> "green";
            default -> throw new IllegalArgumentException("顏色只接受灰色、一般、橘色、紅色、綠色，收到「" + color + "」");
        };
    }

    private void eventually(Runnable check) {
        long deadline = System.currentTimeMillis() + POLL_LIMIT_MS;
        while (true) {
            try {
                check.run();
                return;
            } catch (AssertionError | PlaywrightException e) {
                if (System.currentTimeMillis() >= deadline) {
                    throw e;
                }
                world.page().waitForTimeout(100);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** 表格的格子；Cucumber 把空格子當成 null，這裡一律換成空字串。 */
    private static String cell(Map<String, String> row, String column) {
        String value = row.get(column);
        return value == null ? "" : value.strip();
    }

    private static boolean yes(String value) {
        return switch (value) {
            case "有", "是" -> true;
            case "沒有", "否", "" -> false;
            default -> throw new IllegalArgumentException("只接受有或沒有，收到「" + value + "」");
        };
    }

    private static List<String> splitList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("、")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private HeaderReading headerOf(String column) {
        return board.readHeaders().stream().filter(header -> header.column().equals(column)).findFirst()
                .orElseThrow(() -> new AssertionError("畫面上找不到「" + column + "」的欄頭"));
    }

    private Set<String> roomsIn(String column) {
        return board.readCardsIn(board.column(column)).stream().map(CardReading::roomNo)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<CardReading> cardsTopToBottom(String column) {
        List<CardReading> cards = new ArrayList<>(board.readCardsIn(board.column(column)));
        cards.sort(Comparator.comparingDouble(CardReading::top));
        return cards;
    }

    private List<CardReading> allCardsOnBoard() {
        List<CardReading> cards = new ArrayList<>();
        for (String column : BoardPage.COLUMN_NAMES) {
            cards.addAll(board.readCardsIn(board.column(column)));
        }
        return cards;
    }

    private Set<String> allRoomsOnBoard() {
        return allCardsOnBoard().stream().map(CardReading::roomNo).collect(Collectors.toCollection(HashSet::new));
    }

    private CardReading onlyCardOf(String room) {
        List<CardReading> cards = board.readCardsOf(room);
        check(cards.size() == 1, "「" + room + "」的卡片應該剛好一張，畫面上有 " + cards.size() + " 張");
        return cards.get(0);
    }

    private static String subtitleOf(CardReading card, String title) {
        String rest = card.middle().startsWith(title) ? card.middle().substring(title.length()) : card.middle();
        return rest.replaceAll("SOP \\d+/\\d+", "").strip();
    }

    private Locator inputNamed(String name) {
        if ("搜尋房號".equals(name.strip())) {
            return board.roomSearch();
        }
        return world.page().getByLabel(name.strip(), new Page.GetByLabelOptions().setExact(true));
    }

    private JsonNode deskBoard() {
        return world.backend().getJson("/desk/board", world.staff(BOARD_READER));
    }

    private List<JsonNode> tasksOfRoom(JsonNode deskBoard, String room) {
        Set<String> ids = world.tasks().entriesIn(room).stream().map(Entry::taskId).map(UUID::toString)
                .collect(Collectors.toSet());
        List<JsonNode> found = new ArrayList<>();
        deskBoard.path("tasks").forEach(task -> {
            boolean registered = ids.contains(task.path("id").asText());
            boolean sameRoom = ids.isEmpty() && room.equals(task.path("roomNo").asText());
            if (registered || sameRoom) {
                found.add(task);
            }
        });
        return found;
    }

    private JsonNode onlyTaskOfRoom(JsonNode deskBoard, String room) {
        List<JsonNode> tasks = tasksOfRoom(deskBoard, room);
        check(tasks.size() == 1, "後端看板裡「" + room + "」應該剛好一張單，有 " + tasks.size() + " 張");
        return tasks.get(0);
    }

    private static long waitedMinutes(JsonNode task, JsonNode deskBoard) {
        OffsetDateTime now = OffsetDateTime.parse(deskBoard.path("serverTime").asText());
        OffsetDateTime since = OffsetDateTime.parse(task.path("waitingSince").asText());
        return Duration.between(since, now).toMinutes();
    }

    private void checkWaitingMinutes(String room, String pillText, JsonNode deskBoard) {
        JsonNode task = onlyTaskOfRoom(deskBoard, room);
        String state = task.path("state").asText();
        if (!"triage".equals(state) && !"pool".equals(state)) {
            return;
        }
        long minutes = waitedMinutes(task, deskBoard);
        if ("剛剛".equals(pillText)) {
            check(minutes == 0, "「" + room + "」膠囊寫「剛剛」，但依後端已等 " + minutes + " 分");
            return;
        }
        Matcher shown = MINUTES.matcher(pillText);
        check(shown.find(), "「" + room + "」膠囊「" + pillText + "」沒有分鐘數");
        long onPill = Long.parseLong(shown.group(1)) + (pillText.startsWith("逾時") ? 10 : 0);
        check(onPill == minutes, "「" + room + "」膠囊「" + pillText + "」的分鐘數和後端算出的已等 " + minutes + " 分不符");
    }

    private List<String> expectedOrder(String column, JsonNode deskBoard) {
        List<JsonNode> tasks = new ArrayList<>();
        deskBoard.path("tasks").forEach(task -> {
            if (column.equals(BoardPage.columnOfTask(task))) {
                tasks.add(task);
            }
        });
        Comparator<JsonNode> order = switch (column) {
            case "待派單" -> Comparator.comparing((JsonNode t) -> OffsetDateTime.parse(t.path("createdAt").asText()))
                    .reversed();
            case "待認領" -> Comparator.comparing((JsonNode t) -> OffsetDateTime.parse(t.path("waitingSince").asText()));
            default -> Comparator.comparing((JsonNode t) -> OffsetDateTime.parse(t.path("updatedAt").asText()))
                    .reversed();
        };
        tasks.sort(order);
        return tasks.stream().map(task -> task.path("roomNo").asText()).toList();
    }
}
