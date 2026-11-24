package com.athena.web.bdd.steps.triage;

import com.athena.web.bdd.support.BrowserRegex;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.TriagePage;
import com.athena.web.bdd.pages.TriagePage.Control;
import com.athena.web.bdd.support.ScenarioTasks;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TriageThenSteps {

    private static final double ASSERT_TIMEOUT_MS = 5_000;

    private static final double TOLERANCE_PX = 1;

    private static final List<String> ROW_ACTION_NAMES = List.of("派給房務", "派給工務", "當場回答…");

    private static final Pattern HEADCOUNT = Pattern.compile("在班|\\d+\\s*(人|位)");

    private static final Pattern FLOOR = Pattern.compile("^\\d+ 樓$");

    private static final Pattern GUEST_TEXT = Pattern.compile("^「.*」$");

    private static final Pattern ORIGINAL_TEXT = Pattern.compile("原文：");

    private static final Pattern SOURCE = Pattern.compile("^·.*·$");

    private static final Pattern WAITED = Pattern.compile("^已等");

    private static final Pattern TODAY_COUNTS = Pattern.compile("今天已派單 (\\d+) 單、當場回答 (\\d+) 單");

    private final WebWorld world;

    private final ShellFrame shell;

    private final TriagePage triage;

    private final WhenStart whenStart;

    public TriageThenSteps(WebWorld world, WhenStart whenStart) {
        this.world = world;
        this.shell = new ShellFrame(world);
        this.triage = new TriagePage(world);
        this.whenStart = whenStart;
    }

    @那麼("待派單頁由上到下依序是頁首、需求清單與頁尾說明")
    public void headerListFooterInOrder() {
        eventually("待派單頁由上到下應該是頁首、需求清單與頁尾說明", () -> {
            List<String> rooms = triage.roomsInOrder();
            if (rooms.isEmpty()) {
                return "清單上沒有任何一列";
            }
            if (triage.header().count() == 0) {
                return "找不到頁首（寫著房務與工務待接張數的地方）";
            }
            if (triage.footer().count() == 0) {
                return "找不到頁尾說明（以「" + TriagePage.FOOTER_LEAD + "」開頭的那段字）";
            }
            BoundingBox header = triage.header().boundingBox();
            BoundingBox first = triage.row(rooms.get(0)).boundingBox();
            BoundingBox last = triage.row(rooms.get(rooms.size() - 1)).boundingBox();
            BoundingBox footer = triage.footer().boundingBox();
            if (header == null || first == null || last == null || footer == null) {
                return "頁首、第一列、最後一列或頁尾說明沒有畫在畫面上";
            }
            if (header.y + header.height > first.y + TOLERANCE_PX) {
                return "頁首的下緣（" + bottom(header) + "）低於第一列 " + rooms.get(0) + " 的上緣（" + first.y + "）";
            }
            if (last.y + last.height > footer.y + TOLERANCE_PX) {
                return "最後一列 " + rooms.get(rooms.size() - 1) + " 的下緣（" + bottom(last)
                        + "）低於頁尾說明的上緣（" + footer.y + "）";
            }
            return null;
        });
    }

    @那麼("待派單頁的頁首沒有在班人數")
    public void headerHasNoHeadcount() {
        eventually("待派單頁的頁首應該只有兩段待接張數與一句操作說明", () -> {
            if (triage.header().count() == 0) {
                return "找不到頁首（寫著房務與工務待接張數的地方）";
            }
            String text = ShellFrame.normalizeSpaces(triage.header().innerText());
            List<String> loads = new ArrayList<>();
            Matcher load = TriagePage.DEPT_LOAD.matcher(text);
            while (load.find()) {
                loads.add(load.group(1));
            }
            if (!loads.equals(List.of("房務", "工務"))) {
                return "頁首的待接張數應該是房務、工務各一段，實際是 " + loads + "；頁首全文「" + text + "」";
            }
            String rest = ShellFrame.normalizeSpaces(TriagePage.DEPT_LOAD.matcher(text).replaceAll(" "));
            if (rest.isEmpty()) {
                return "頁首除了待接張數之外應該還有一句操作說明，實際沒有；頁首全文「" + text + "」";
            }
            if (HEADCOUNT.matcher(rest).find()) {
                return "頁首不應該有在班人數，實際寫著「" + rest + "」";
            }
            return null;
        });
    }

    @那麼("{string} 在整頁只出現一次，位置在頁首")
    public void onlyOnceInHeader(String text) {
        String wanted = ShellFrame.normalizeSpaces(text);
        eventually("「" + wanted + "」應該在整頁只出現一次，而且在頁首", () -> {
            int times = occurrences(String.join("\n", shell.visibleTextLines()), wanted);
            if (times != 1) {
                return "整頁出現了 " + times + " 次";
            }
            if (triage.header().count() == 0
                    || !ShellFrame.normalizeSpaces(triage.header().innerText()).contains(wanted)) {
                return "它不在頁首（寫著房務與工務待接張數的地方）";
            }
            for (String room : triage.roomsInOrder()) {
                if (ShellFrame.normalizeSpaces(triage.row(room).innerText()).contains(wanted)) {
                    return room + " 這一列上也有這句話";
                }
            }
            return null;
        });
    }

    @那麼("待派單頁 {string} 這一列的內容如下：")
    public void rowContent(String roomNo, DataTable table) {
        List<Map<String, String>> items = table.asMaps();
        eventually("待派單頁 " + roomNo + " 這一列的內容不對", () -> {
            if (!triage.hasRow(roomNo)) {
                return "清單上沒有 " + roomNo + " 這一列";
            }
            List<String> problems = new ArrayList<>();
            for (Map<String, String> item : items) {
                String name = Objects.requireNonNull(item.get("項目"), "「項目」欄必填").strip();
                String expected = item.get("內容") == null ? "" : ShellFrame.normalizeSpaces(item.get("內容"));
                String problem = rowItemProblem(roomNo, name, expected);
                if (problem != null) {
                    problems.add(problem);
                }
            }
            return problems.isEmpty() ? null : String.join("；", problems);
        });
    }

    @那麼("待派單頁 {string} 這一列的等待時間寫的是 {string}")
    public void rowWaitText(String roomNo, String text) {
        String expected = ShellFrame.normalizeSpaces(text);
        eventually("待派單頁 " + roomNo + " 這一列「已等」後面應該是「" + expected + "」", () -> {
            if (!triage.hasRow(roomNo)) {
                return "清單上沒有 " + roomNo + " 這一列";
            }
            List<String> waited = triage.textsMatching(triage.row(roomNo), WAITED);
            if (waited.size() != 1) {
                return "這一列以「已等」開頭的文字應該剛好一段，實際是 " + waited;
            }
            String after = ShellFrame.normalizeSpaces(waited.get(0).substring("已等".length()));
            return expected.equals(after) ? null : "實際是「" + after + "」";
        });
    }

    @那麼("待派單頁 {string} 這一列沒有 {string}")
    public void rowLacksText(String roomNo, String text) {
        String unwanted = ShellFrame.normalizeSpaces(text);
        eventually("待派單頁 " + roomNo + " 這一列不應該有「" + unwanted + "」", () -> {
            if (!triage.hasRow(roomNo)) {
                return "清單上沒有 " + roomNo + " 這一列";
            }
            String rowText = ShellFrame.normalizeSpaces(triage.row(roomNo).innerText());
            return rowText.contains(unwanted) ? "這一列的文字是「" + rowText + "」" : null;
        });
    }

    @那麼("待派單頁各列的住客如下：")
    public void rowGuests(DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        eventually("待派單頁各列的住客不對", () -> {
            List<String> problems = new ArrayList<>();
            for (Map<String, String> row : rows) {
                String roomNo = Objects.requireNonNull(row.get("房號"), "「房號」欄必填").strip();
                String guest = Objects.requireNonNull(row.get("住客名"), "「住客名」欄必填").strip();
                boolean vipExpected = yesNo(row.get("VIP 標"));
                if (!triage.hasRow(roomNo)) {
                    problems.add("清單上沒有 " + roomNo + " 這一列");
                    continue;
                }
                Locator rowLocator = triage.row(roomNo);
                if (triage.exactText(rowLocator, guest).count() == 0) {
                    problems.add(roomNo + " 這一列沒有住客名「" + guest + "」，這一列的文字是「"
                            + ShellFrame.normalizeSpaces(rowLocator.innerText()) + "」");
                }
                boolean vipShown = triage.exactText(rowLocator, "VIP").count() > 0;
                if (vipShown != vipExpected) {
                    problems.add(roomNo + " 這一列" + (vipExpected ? "應該有" : "不應該有") + "「VIP」標");
                }
            }
            return problems.isEmpty() ? null : String.join("；", problems);
        });
    }

    @那麼("待派單頁每一列右側的動作都只有 {string}、{string}、{string}")
    public void rowActionsExactly(String one, String two, String three) {
        List<Control> expected = List.of(button(one), button(two), button(three));
        eventually("待派單頁每一列右側的動作應該只有 " + expected, () -> {
            List<String> rooms = triage.roomsInOrder();
            if (rooms.isEmpty()) {
                return "清單上沒有任何一列";
            }
            List<String> problems = new ArrayList<>();
            for (String room : rooms) {
                List<Control> actual = triage.controlsIn(triage.actions(room));
                if (!actual.equals(expected)) {
                    problems.add(room + " 這一列的動作是 " + actual);
                    continue;
                }
                BoundingBox opener = triage.rowOpener(room).first().boundingBox();
                BoundingBox actions = triage.actions(room).boundingBox();
                if (opener == null || actions == null) {
                    problems.add(room + " 這一列的動作或左側那一塊沒有畫在畫面上");
                } else if (actions.x + TOLERANCE_PX < opener.x + opener.width) {
                    problems.add(room + " 這一列的動作不在右側：動作的左緣（" + actions.x
                            + "）在左側那一塊的右緣（" + (opener.x + opener.width) + "）左邊");
                }
            }
            return problems.isEmpty() ? null : String.join("；", problems);
        });
    }

    @那麼("待派單頁沒有拆單、代開這張單或派給其他部門的動作")
    public void noOtherRowActions() {
        eventually("待派單頁的列上不應該有拆單、代開這張單或派給其他部門的動作", () -> {
            List<String> rooms = triage.roomsInOrder();
            if (rooms.isEmpty()) {
                return "清單上沒有任何一列";
            }
            List<String> problems = new ArrayList<>();
            for (String room : rooms) {
                Pattern opener = Pattern.compile("^" + BrowserRegex.literal(room) + "(\\s|$)");
                List<Control> extra = triage.controlsIn(triage.row(room)).stream()
                        .filter(control -> !(control.role().equals("button")
                                && (ROW_ACTION_NAMES.contains(control.name())
                                        || opener.matcher(control.name()).find())))
                        .toList();
                if (!extra.isEmpty()) {
                    problems.add(room + " 這一列多了 " + extra);
                }
            }
            return problems.isEmpty() ? null : String.join("；", problems);
        });
    }

    @那麼("待派單頁的三顆動作都沒有 {string} 小標")
    public void rowActionsWithoutTag(String text) {
        String unwanted = ShellFrame.normalizeSpaces(text);
        eventually("待派單頁的三顆動作應該外觀相同、都沒有「" + unwanted + "」小標", () -> {
            List<String> rooms = triage.roomsInOrder();
            if (rooms.isEmpty()) {
                return "清單上沒有任何一列";
            }
            List<String> problems = new ArrayList<>();
            for (String room : rooms) {
                Locator buttons = triage.actions(room).getByRole(AriaRole.BUTTON);
                Set<String> looks = new HashSet<>();
                for (int i = 0; i < buttons.count(); i++) {
                    looks.add(String.valueOf(buttons.nth(i).evaluate(BUTTON_LOOK)));
                }
                if (looks.size() != 1) {
                    problems.add(room + " 這一列三顆按鈕的外觀不一樣：" + looks);
                } else if (looks.iterator().next().startsWith("0px") || looks.iterator().next().contains("|none|")) {
                    problems.add(room + " 這一列的按鈕沒有外框：" + looks);
                }
                String rowText = ShellFrame.normalizeSpaces(triage.row(room).innerText());
                if (rowText.contains(unwanted)) {
                    problems.add(room + " 這一列上有「" + unwanted + "」：" + rowText);
                }
            }
            return problems.isEmpty() ? null : String.join("；", problems);
        });
    }

    private static final String BUTTON_LOOK = """
            b => { const s = getComputedStyle(b);
                   return [s.borderTopWidth, s.borderTopStyle, s.borderTopColor, s.backgroundColor, s.color].join('|'); }
            """;

    @那麼("待派單頁的空狀態是 {string}，說明是 {string}")
    public void emptyState(String title, String description) {
        String wantedTitle = ShellFrame.normalizeSpaces(title);
        String wantedDescription = ShellFrame.normalizeSpaces(description);
        eventually("待派單頁的空狀態應該是「" + wantedTitle + "」，說明「" + wantedDescription + "」", () -> {
            if (triage.allRowActions().count() > 0) {
                return "清單上還有 " + triage.roomsInOrder();
            }
            Locator empty = triage.emptyState(wantedTitle, wantedDescription);
            if (empty.count() == 0) {
                return "畫面上沒有同時寫著這個標題與說明的地方";
            }
            if (empty.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setLevel(1)).count() > 0) {
                return "標題與說明不在同一個區塊裡";
            }
            if (triage.exactText(empty, wantedTitle).count() == 0) {
                return "空狀態的標題不是剛好「" + wantedTitle + "」：" + ShellFrame.normalizeSpaces(empty.innerText());
            }
            if (triage.exactText(empty, wantedDescription).count() == 0) {
                return "空狀態的說明不是剛好「" + wantedDescription + "」：" + ShellFrame.normalizeSpaces(empty.innerText());
            }
            return null;
        });

        JsonNode myToday = world.backend().getJson("/desk/board", loggedInStaff()).path("myToday");
        int assigned = myToday.path("assignedCount").asInt(-1);
        int answered = myToday.path("answeredCount").asInt(-1);
        Matcher counts = TODAY_COUNTS.matcher(wantedDescription);
        boolean countsWritten = counts.find();
        int shownAssigned = countsWritten ? Integer.parseInt(counts.group(1)) : 0;
        int shownAnswered = countsWritten ? Integer.parseInt(counts.group(2)) : 0;
        if (assigned != shownAssigned || answered != shownAnswered) {
            throw new AssertionError("說明裡的張數應該等於後端看板的今天張數：說明寫派單 " + shownAssigned
                    + "、回答 " + shownAnswered + "，後端 myToday 是派單 " + assigned + "、回答 " + answered);
        }
    }

    @那麼("清單的位置是灰色的骨架列，頁副標是空的")
    public void skeletonRowsAndBlankSubtitle() {
        eventually("清單的位置應該是骨架列，頁副標是空的", () -> {
            Locator skeletons = triage.skeletons().filter(new Locator.FilterOptions().setVisible(true));
            if (skeletons.count() == 0) {
                return "畫面上沒有標成載入中的骨架";
            }
            if (triage.allRowActions().count() > 0) {
                return "清單上已經有真的列 " + triage.roomsInOrder();
            }
            if (triage.header().count() > 0) {
                return "已經有頁首的待接張數";
            }
            if (page().getByText(TriagePage.FOOTER_LEAD).count() > 0) {
                return "已經有頁尾說明";
            }
            for (String emptyTitle : List.of("都派完了", "目前沒有要派的單")) {
                if (shell.hasTextAnywhere(emptyTitle)) {
                    return "已經顯示空狀態「" + emptyTitle + "」";
                }
            }
            String subtitle = triage.pageSubtitleText();
            return subtitle.isEmpty() ? null : "頁副標應該是空的，實際是「" + subtitle + "」";
        });
    }

    @那麼("後端沒有因為這次派單多出任何一張單")
    public void noNewTask() {
        Set<UUID> before = new HashSet<>(whenStart.snapshot().lastSeqByTask().keySet());
        world.tasks().all().stream().map(ScenarioTasks.Entry::taskId).forEach(before::add);
        List<Map<String, Object>> now = world.backend().queryRows(
                "SELECT id, title FROM tasks WHERE tenant_id = ?", world.defaults().tenantId());
        List<String> added = now.stream()
                .filter(row -> !before.contains((UUID) row.get("id")))
                .map(row -> "「" + row.get("title") + "」")
                .toList();
        if (!added.isEmpty() || now.size() != before.size()) {
            throw new AssertionError("後端的單應該和派單前一樣是 " + before.size() + " 張，現在是 " + now.size()
                    + " 張；多出來的是 " + added);
        }
    }

    @那麼("後端 {string} 只有一筆派單紀錄，派單的人是 {string}")
    public void singleTriagedEvent(String roomNo, String staffName) {
        UUID taskId = world.tasks().idOf(roomNo);
        CreatedStaff staff = world.staff(staffName);
        List<Map<String, Object>> triaged = eventsOf(taskId, "triaged");
        if (triaged.size() != 1) {
            throw new AssertionError("後端 " + roomNo + " 的派單紀錄應該剛好一筆，實際 " + triaged.size() + " 筆：" + triaged);
        }
        Object actor = triaged.get(0).get("actor_id");
        if (!staff.id().equals(actor)) {
            throw new AssertionError("後端 " + roomNo + " 的派單紀錄應該是 " + staffName + "（" + staff.id()
                    + "）派的，實際操作者是 " + actor + "（角色 " + triaged.get(0).get("actor_role") + "）");
        }
    }

    @那麼("後端 {string} 的單由 {string} 負責，狀態是已接")
    public void acceptedBy(String roomNo, String colleagueName) {
        UUID taskId = world.tasks().idOf(roomNo);
        CreatedStaff colleague = world.staff(colleagueName);
        Map<String, Object> task = world.backend().taskRow(taskId);
        List<String> problems = new ArrayList<>();
        if (!"accepted".equals(task.get("state"))) {
            problems.add("狀態應該是 accepted，實際是 " + task.get("state"));
        }
        if (!colleague.id().equals(task.get("assignee_id"))) {
            problems.add("負責人應該是 " + colleagueName + "（" + colleague.id() + "），實際是 " + task.get("assignee_id"));
        }
        List<Map<String, Object>> undone = eventsOf(taskId, "triage_undone");
        if (!undone.isEmpty()) {
            problems.add("不應該有復原派單的紀錄，實際有 " + undone.size() + " 筆");
        }
        if (!problems.isEmpty()) {
            throw new AssertionError("後端 " + roomNo + " 的單不對：" + String.join("；", problems));
        }
    }

    private String rowItemProblem(String roomNo, String item, String expected) {
        Locator row = triage.row(roomNo);
        Locator opener = triage.rowOpener(roomNo);
        return switch (item) {
            case "房號" -> exactItemProblem(opener, item, expected);
            case "住客名" -> exactItemProblem(row, item, expected);
            case "樓層" -> patternItemProblem(opener, FLOOR, item, expected);
            case "住客原話" -> patternItemProblem(row, GUEST_TEXT, item, expected);
            case "原文列" -> patternItemProblem(row, ORIGINAL_TEXT, item, expected);
            case "來源" -> patternItemProblem(row, SOURCE, item, expected);
            case "等待時間" -> patternItemProblem(row, WAITED, item, expected);
            default -> throw new IllegalArgumentException("「項目」只收房號、樓層、住客原話、原文列、住客名、來源、等待時間，收到「"
                    + item + "」");
        };
    }

    private String exactItemProblem(Locator scope, String item, String expected) {
        if (expected.isEmpty()) {
            throw new IllegalArgumentException("「" + item + "」每一列都有，內容不能留空");
        }
        return triage.exactText(scope, expected).count() > 0 ? null
                : item + "應該是「" + expected + "」，找不到文字剛好是它的地方";
    }

    private String patternItemProblem(Locator scope, Pattern pattern, String item, String expected) {
        List<String> found = triage.textsMatching(scope, pattern);
        if (expected.isEmpty()) {
            return found.isEmpty() ? null : item + "不應該出現，實際有 " + found;
        }
        return found.contains(expected) ? null : item + "應該是「" + expected + "」，實際是 " + found;
    }

    private List<Map<String, Object>> eventsOf(UUID taskId, String type) {
        return world.backend().queryRows("""
                SELECT seq, actor_id, actor_role FROM task_events
                WHERE tenant_id = ? AND task_id = ? AND type = ?
                ORDER BY seq
                """, world.defaults().tenantId(), taskId, type);
    }

    private CreatedStaff loggedInStaff() {
        JsonNode session = shell.storedLoginResponse();
        if (session == null || session.path("name").asText("").isEmpty()) {
            throw new AssertionError("這個分頁沒有登入狀態，讀不到是誰的今天張數");
        }
        return world.staff(session.path("name").asText());
    }

    private Page page() {
        return world.page();
    }

    private static Control button(String name) {
        return new Control("button", name.strip());
    }

    private static boolean yesNo(String value) {
        return switch (value == null ? "" : value.strip()) {
            case "有" -> true;
            case "沒有" -> false;
            default -> throw new IllegalArgumentException("「VIP 標」只收有或沒有，收到「" + value + "」");
        };
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int from = haystack.indexOf(needle); from >= 0; from = haystack.indexOf(needle, from + needle.length())) {
            count++;
        }
        return count;
    }

    private static double bottom(BoundingBox box) {
        return box.y + box.height;
    }

    private void eventually(String what, Supplier<String> problem) {
        String[] last = {null};
        try {
            page().waitForCondition(() -> (last[0] = problem.get()) == null,
                    new Page.WaitForConditionOptions().setTimeout(ASSERT_TIMEOUT_MS));
        } catch (TimeoutError timeout) {
            throw new AssertionError(what + "：" + last[0], timeout);
        }
    }
}
