package com.athena.web.bdd.steps.common;

import com.athena.bddsupport.BackendChannel;
import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.web.bdd.pages.AnswerPanel;
import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.TriagePage;
import com.athena.web.bdd.support.JsResult;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.athena.web.bdd.support.WhenStart;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.BoundingBox;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommonThenSteps {

    private static final long SETTLE_LIMIT_MS = 5_000;

    private static final long NO_TOAST_LIMIT_MS = 2_000;

    private static final long POLL_INTERVAL_MS = 100;

    private static final long AFTER_FORCED_PRESS_MS = 500;

    private static final double BOTTOM_AREA_FROM = 2.0 / 3.0;

    private static final double CENTER_TOLERANCE = 0.1;

    private static final double FULL_WIDTH_TOLERANCE_PX = 2;

    private static final Set<String> DESK_ROLES = Set.of("frontdesk", "manager", "duty_manager");

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String DESK_BOARD_READER = "林佩君";

    private final WebWorld world;

    private final WhenStart whenStart;

    private final ShellFrame shell;

    private final BoardPage board;

    private final TriagePage triage;

    private final DetailPanel detail;

    private final AnswerPanel answer;

    public CommonThenSteps(WebWorld world, WhenStart whenStart) {
        this.world = world;
        this.whenStart = whenStart;
        this.shell = new ShellFrame(world);
        this.board = new BoardPage(world);
        this.triage = new TriagePage(world);
        this.detail = new DetailPanel(world);
        this.answer = new AnswerPanel(world);
    }

    @那麼("畫面上看得到以下文字：")
    public void screenShowsTexts(DataTable table) {
        assertShows(textColumn(table));
    }

    @那麼("畫面上沒有以下文字：")
    public void screenLacksTexts(DataTable table) {
        assertLacks(textColumn(table));
    }

    @那麼("畫面上看得到 {string}")
    public void screenShowsText(String text) {
        assertShows(List.of(text));
    }

    @那麼("畫面上沒有 {string}")
    public void screenLacksText(String text) {
        assertLacks(List.of(text));
    }

    private void assertShows(List<String> texts) {
        eventually(SETTLE_LIMIT_MS, () -> {
            List<String> missing = texts.stream().filter(text -> !shell.showsText(text)).toList();
            return missing.isEmpty() ? null : "畫面上看不到 " + missing + "；畫面上的文字是 " + shell.visibleTextLines();
        });
    }

    private void assertLacks(List<String> texts) {
        eventually(SETTLE_LIMIT_MS, () -> {
            List<String> present = texts.stream().filter(shell::hasTextAnywhere).toList();
            return present.isEmpty() ? null : "畫面上不該有 " + present + "，但找得到";
        });
    }

    @那麼("按鈕 {string} 可以按")
    public void buttonPressable(String name) {
        eventually(SETTLE_LIMIT_MS, () -> {
            Locator button = shell.topmostPressable(name);
            if (!button.isVisible()) {
                return "畫面上看不到按鈕「" + name + "」";
            }
            return button.isEnabled() ? null : "按鈕「" + name + "」是停用的樣子";
        });
        tryPress(shell.topmostPressable(name));
    }

    @那麼("按鈕 {string} 不能按")
    public void buttonNotPressable(String name) {
        eventually(SETTLE_LIMIT_MS, () -> {
            Locator button = shell.topmostPressable(name);
            if (!button.isVisible()) {
                return "畫面上看不到按鈕「" + name + "」";
            }
            return button.isDisabled() ? null : "按鈕「" + name + "」不是停用的樣子";
        });
        Page page = world.page();
        Locator button = shell.topmostPressable(name);
        List<String> screenBefore = shell.visibleTextLines();
        int requestsBefore = world.network().sentRequestCount();
        if (world.profile().touch()) {
            button.tap(new Locator.TapOptions().setForce(true));
        } else {
            button.click(new Locator.ClickOptions().setForce(true));
        }
        page.waitForTimeout(AFTER_FORCED_PRESS_MS);
        List<String> sent = world.network().sentRequestsSince(requestsBefore).stream()
                .filter(request -> request.page() == page)
                .filter(request -> !request.is(Network.DESK_BOARD) && !request.is(Network.STAFF_STREAM))
                .filter(request -> !request.is(Network.GUEST_HOME) && !request.is(Network.GUEST_REQUESTS)
                        && !request.is(Network.GUEST_STREAM))
                .map(request -> request.method() + " " + request.path())
                .toList();
        assertThat(sent.isEmpty(), "硬按停用的「" + name + "」之後送出了請求：" + sent);
        List<String> screenAfter = shell.visibleTextLines();
        assertThat(screenAfter.equals(screenBefore),
                "硬按停用的「" + name + "」之後畫面變了：按之前 " + screenBefore + "，按之後 " + screenAfter);
    }

    @那麼("畫面出現對話框，標題 {string}、內文 {string}、按鈕只有 {string}")
    public void alertDialogShown(String title, String body, String onlyButton) {
        eventually(SETTLE_LIMIT_MS, () -> {
            Locator shown = shell.shownDialogs();
            int count = shown.count();
            if (count == 0) {
                return "畫面上沒有對話框";
            }
            Locator top = shown.nth(count - 1);
            String[] roleAndName = roleAndNameOf(top);
            if (!"alertdialog".equals(roleAndName[0])) {
                return "最上層的對話框不是提醒型（alertdialog），是 " + roleAndName[0];
            }
            if (!title.strip().equals(roleAndName[1])) {
                return "最上層對話框的標題是「" + roleAndName[1] + "」，不是「" + title + "」";
            }
            List<String> lines = ShellFrame.linesOf(top.innerText());
            if (!lines.contains(ShellFrame.normalizeSpaces(body))) {
                return "最上層對話框的內文不是「" + body + "」，對話框上的文字是 " + lines;
            }
            Locator buttons = top.getByRole(AriaRole.BUTTON).filter(new Locator.FilterOptions().setVisible(true));
            Locator named = buttons.and(top.getByRole(AriaRole.BUTTON,
                    new Locator.GetByRoleOptions().setName(onlyButton.strip()).setExact(true)));
            if (buttons.count() != 1 || named.count() != 1) {
                return "最上層對話框應該只有一顆按鈕「" + onlyButton + "」，實際有 " + buttons.count() + " 顆："
                        + buttons.allInnerTexts();
            }
            return null;
        });
    }

    @那麼("畫面上沒有任何對話框")
    public void noDialog() {
        eventually(SETTLE_LIMIT_MS, () -> {
            Locator shown = shell.shownDialogs();
            return shown.count() == 0 ? null : "畫面上還有對話框：" + shown.allInnerTexts();
        });
        List<String> nativeDialogs = whenStart.nativeDialogs();
        assertThat(nativeDialogs.isEmpty(), "跳出過瀏覽器的提示框：" + nativeDialogs);
    }

    @那麼("畫面底部出現浮條 {string} 與 \"復原\"")
    public void assignToastShown(String text) {
        eventually(SETTLE_LIMIT_MS, () -> {
            Locator toast = shell.assignToast().filter(new Locator.FilterOptions().setVisible(true));
            if (toast.count() == 0) {
                return "畫面上沒有帶「復原」的浮條";
            }
            Locator shown = toast.first();
            List<String> lines = ShellFrame.linesOf(shown.innerText());
            String wanted = ShellFrame.normalizeSpaces(text);
            if (lines.stream().noneMatch(line -> line.contains(wanted))) {
                return "浮條上的文字是 " + lines + "，不是「" + text + "」";
            }
            BoundingBox box = shown.boundingBox();
            double viewportHeight = world.page().viewportSize().height;
            if (box == null || box.y + box.height / 2 < viewportHeight * BOTTOM_AREA_FROM) {
                return "浮條不在畫面底部：位置 " + describe(box) + "，視窗高 " + viewportHeight;
            }
            Locator undo = undoOn(shown);
            return undo.isVisible() && undo.isEnabled() ? null : "浮條上的「復原」看不到或是停用的樣子";
        });
        tryPress(undoOn(shell.assignToast().filter(new Locator.FilterOptions().setVisible(true)).first()));
    }

    @那麼("畫面上沒有派單浮條")
    public void noAssignToast() {
        eventually(NO_TOAST_LIMIT_MS, () -> {
            if (shell.assignToast().filter(new Locator.FilterOptions().setVisible(true)).count() > 0) {
                return "畫面上有派單浮條";
            }
            Locator undo = world.page().getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("復原").setExact(true))
                    .filter(new Locator.FilterOptions().setVisible(true));
            return undo.count() == 0 ? null : "畫面上還有「復原」可以按";
        });
    }

    @那麼("後端 {string} 的單在 {string} 待認領")
    public void backendInDeptPool(String roomNo, String deptTerm) {
        UUID taskId = world.tasks().idOf(roomNo);
        String dept = fieldDept(deptTerm);
        String otherDept = "housekeeping".equals(dept) ? "engineering" : "housekeeping";
        eventually(SETTLE_LIMIT_MS, () -> {
            Map<String, Object> row = world.backend().taskRow(taskId);
            if (!"pool".equals(row.get("state")) || !dept.equals(row.get("dept")) || row.get("assignee_id") != null) {
                return "後端 " + roomNo + " 的單不是「" + deptTerm + "待認領、沒有負責人」：" + summary(row);
            }
            if (!inDeptPool(dept, taskId)) {
                return deptTerm + "的待認領清單沒有 " + roomNo;
            }
            return inDeptPool(otherDept, taskId) ? "另一個部門的待認領清單也有 " + roomNo : null;
        });
        eventually(SETTLE_LIMIT_MS, () -> stillShownAsTriage(roomNo)
                ? "後端已經派出去了，畫面上 " + roomNo + " 還在待派單" : null);
    }

    @那麼("後端 {string} 的單仍是待派單，歷程沒有多任何一筆")
    public void backendStillTriage(String roomNo) {
        UUID taskId = world.tasks().idOf(roomNo);
        eventually(SETTLE_LIMIT_MS, () -> {
            Map<String, Object> row = world.backend().taskRow(taskId);
            if (!"triage".equals(row.get("state")) || !"frontdesk".equals(row.get("dept"))
                    || row.get("assignee_id") != null || row.get("answer") != null) {
                return "後端 " + roomNo + " 的單不是「待派單、未分部門、沒有負責人、沒有回覆」：" + summary(row);
            }
            if (inDeptPool("housekeeping", taskId) || inDeptPool("engineering", taskId)) {
                return "房務或工務的待認領清單有 " + roomNo;
            }
            return null;
        });
        JsonNode before = whenStart.timelineBefore(taskId).orElseThrow(() -> new AssertionError(
                "When 之前沒有讀到 " + roomNo + " 的歷程，無法比較有沒有多出一筆"));
        JsonNode now = whenStart.timelineNow(taskId);
        Map<Integer, String> beforeBySeq = typesBySeq(before);
        Map<Integer, String> nowBySeq = typesBySeq(now);
        List<String> added = new ArrayList<>();
        nowBySeq.forEach((seq, type) -> {
            if (!beforeBySeq.containsKey(seq)) {
                added.add("第 " + seq + " 步 " + type);
            }
        });
        assertThat(added.isEmpty(), roomNo + " 的歷程多出了：" + added);
        List<String> removed = new ArrayList<>();
        beforeBySeq.forEach((seq, type) -> {
            if (!nowBySeq.containsKey(seq) && !"triaged".equals(type)) {
                removed.add("第 " + seq + " 步 " + type);
            }
        });
        assertThat(removed.isEmpty(), roomNo + " 的歷程少了派單以外的事件：" + removed);
    }

    @那麼("後端 {string} 的派單紀錄是 {string} 以 {string} 派給 {string}")
    public void backendAssignRecord(String roomNo, String staffName, String method, String deptTerm) {
        UUID taskId = world.tasks().idOf(roomNo);
        CreatedStaff staff = world.staff(staffName);
        String via = switch (method.strip()) {
            case "按鈕" -> "button";
            case "拖曳" -> "drag";
            default -> throw new IllegalArgumentException("派單方式只有按鈕、拖曳，收到「" + method + "」");
        };
        String dept = fieldDept(deptTerm);
        eventually(SETTLE_LIMIT_MS, () -> {
            List<Map<String, Object>> rows = world.backend().queryRows("""
                    SELECT actor_id, actor_role, payload::text AS payload FROM task_events
                    WHERE tenant_id = ? AND task_id = ? AND type = 'triaged' ORDER BY seq DESC LIMIT 1
                    """, world.defaults().tenantId(), taskId);
            if (rows.isEmpty()) {
                return "後端 " + roomNo + " 沒有任何派單事件";
            }
            Map<String, Object> event = rows.get(0);
            JsonNode payload = parse((String) event.get("payload"));
            boolean matches = staff.id().equals(event.get("actor_id"))
                    && staff.role().equals(event.get("actor_role"))
                    && via.equals(payload.path("via").asText(null))
                    && dept.equals(payload.path("dept").asText(null));
            return matches ? null : "後端 " + roomNo + " 最新的派單事件不是 " + staffName + "（" + staff.role() + "）以 "
                    + via + " 派給 " + dept + "：" + event;
        });
    }

    @那麼("後端 {string} 的單已取消，原因是住客收回")
    public void backendGuestWithdrew(String roomNo) {
        UUID taskId = world.tasks().idOf(roomNo);
        eventually(SETTLE_LIMIT_MS, () -> {
            Map<String, Object> row = world.backend().taskRow(taskId);
            return "cancelled".equals(row.get("state")) && "guest_withdrew".equals(row.get("cancel_reason"))
                    ? null : "後端 " + roomNo + " 的單不是住客收回的取消：" + summary(row);
        });
        List<Map<String, Object>> deskEvents = eventsSinceWhen(taskId).stream()
                .filter(event -> DESK_ROLES.contains(String.valueOf(event.get("actor_role"))))
                .toList();
        assertThat(deskEvents.isEmpty(), "櫃檯這次送出的動作在 " + roomNo + " 上留下了事件：" + deskEvents);
    }

    @那麼("後端 {string} 的單已由 {string} 當場回答，沒有進任何部門的待認領")
    public void backendAnsweredBy(String roomNo, String staffName) {
        UUID taskId = world.tasks().idOf(roomNo);
        CreatedStaff staff = world.staff(staffName);
        eventually(SETTLE_LIMIT_MS, () -> {
            Map<String, Object> row = world.backend().taskRow(taskId);
            Object answerText = row.get("answer");
            if (!"done".equals(row.get("state")) || answerText == null || answerText.toString().isBlank()
                    || !"frontdesk".equals(row.get("dept"))) {
                return "後端 " + roomNo + " 的單不是「已完成、有回覆、未分部門」：" + summary(row);
            }
            List<Map<String, Object>> answered = world.backend().queryRows("""
                    SELECT actor_id FROM task_events
                    WHERE tenant_id = ? AND task_id = ? AND type = 'answered' ORDER BY seq DESC LIMIT 1
                    """, world.defaults().tenantId(), taskId);
            if (answered.isEmpty() || !staff.id().equals(answered.get(0).get("actor_id"))) {
                return "後端 " + roomNo + " 的當場回答事件不是 " + staffName + " 做的：" + answered;
            }
            Object triaged = world.backend().queryRows("""
                    SELECT count(*) AS n FROM task_events WHERE tenant_id = ? AND task_id = ? AND type = 'triaged'
                    """, world.defaults().tenantId(), taskId).get(0).get("n");
            if (((Number) triaged).intValue() != 0) {
                return "後端 " + roomNo + " 有派單事件，當場回答的單不該被派出過";
            }
            return inDeptPool("housekeeping", taskId) || inDeptPool("engineering", taskId)
                    ? "房務或工務的待認領清單有 " + roomNo : null;
        });
    }

    @那麼("後端沒有任何資料被改動")
    public void backendUnchanged() {
        List<String> writes = whenStart.requestsSince().stream()
                .filter(Network.SentRequest::isWrite)
                .map(request -> request.method() + " " + request.path())
                .toList();
        assertThat(writes.isEmpty(), "這個案例送出了寫入的請求：" + writes);
        Map<String, String> before = whenStart.snapshot().tableDigests();
        Map<String, String> now = whenStart.tableDigestsNow();
        List<String> changed = before.keySet().stream()
                .filter(table -> !Objects.equals(before.get(table), now.get(table)))
                .toList();
        assertThat(changed.isEmpty(), "後端這幾張表的內容和 When 之前不同：" + changed);
    }

    @那麼("側欄 \"待派單\" 的徽章是 {int}")
    public void triageBadge(int expected) {
        if (!shell.sidebarOpen()) {
            shell.openSidebar();
        }
        eventually(SETTLE_LIMIT_MS, () -> {
            Integer shown = badgeNumber(shell.sidebarItem("待派單").first().innerText());
            if (expected == 0 ? shown != null : !Integer.valueOf(expected).equals(shown)) {
                return "側欄「待派單」的徽章是 " + (shown == null ? "沒有" : shown) + "，不是 " + expected;
            }
            int triageCount = countTasks("state = 'triage'");
            return triageCount == expected ? null : "後端現在待派單有 " + triageCount + " 張，不是 " + expected;
        });
    }

    @那麼("側欄是 {string} 的")
    public void sidebarState(String state) {
        boolean open = switch (state.strip()) {
            case "打開" -> true;
            case "收起" -> false;
            default -> throw new IllegalArgumentException("側欄只有打開、收起兩種，收到「" + state + "」");
        };
        eventually(SETTLE_LIMIT_MS, () -> {
            if (open) {
                if (!shell.sidebar().first().isVisible()) {
                    return "側欄的功能列看不到";
                }
                return shell.collapseSidebarButton().isVisible() ? null : "側欄上沒有「收合側欄」";
            }
            if (!shell.openSidebarButton().isVisible()) {
                return "工具列上沒有「打開側欄」";
            }
            Locator sidebar = shell.sidebar();
            BoundingBox box = sidebar.count() == 0 ? null : sidebar.first().boundingBox();
            return box == null || box.width < 1 ? null : "側欄收起了還佔著 " + box.width + "px 的寬度";
        });
    }

    @那麼("側欄的帳號卡顯示頭像 {string}、姓名 {string} 與 {string}")
    public void accountCardShows(String avatar, String name, String secondLine) {
        eventually(SETTLE_LIMIT_MS, () -> {
            Locator card = shell.accountCard().filter(new Locator.FilterOptions().setVisible(true));
            if (card.count() == 0) {
                return "側欄上看不到帳號卡";
            }
            Set<String> pieces = new HashSet<>(ShellFrame.linesOf(card.first().innerText()));
            List<String> elementTexts = JsResult.strings(card.first().evaluate(
                    "card => [...card.querySelectorAll('*')].map(el => el.innerText || '')"));
            elementTexts.stream().map(ShellFrame::normalizeSpaces).filter(text -> !text.isEmpty()).forEach(pieces::add);
            List<String> missing = new ArrayList<>();
            if (!pieces.contains(ShellFrame.normalizeSpaces(avatar))) {
                missing.add("頭像「" + avatar + "」");
            }
            if (!pieces.contains(ShellFrame.normalizeSpaces(name))) {
                missing.add("姓名「" + name + "」");
            }
            if (!pieces.contains(ShellFrame.normalizeSpaces(secondLine))) {
                missing.add("第二行「" + secondLine + "」");
            }
            return missing.isEmpty() ? null
                    : "帳號卡上沒有剛好是 " + missing + " 的一行或元素，卡上的文字是 " + ShellFrame.linesOf(card.first().innerText());
        });
        JsonNode login = shell.storedLoginResponse();
        assertThat(login != null, "這個分頁沒有登入狀態");
        String fromLogin = ShellFrame.roleLabel(login.path("role").asText()) + " · " + login.path("loginId").asText();
        assertThat(fromLogin.equals(ShellFrame.normalizeSpaces(secondLine)),
                "帳號卡第二行應該是登入回應的角色與員工編號「" + fromLogin + "」，句子寫的是「" + secondLine + "」");
        assertThat(name.strip().equals(login.path("name").asText()),
                "帳號卡的姓名應該是登入回應的「" + login.path("name").asText() + "」");
    }

    @那麼("{string} 的卡片在 {string} 欄")
    public void cardInColumn(String roomNo, String column) {
        String wanted = column.strip();
        if (!BoardPage.COLUMN_NAMES.contains(wanted)) {
            throw new IllegalArgumentException("看板的欄是 " + BoardPage.COLUMN_NAMES + "，收到「" + column + "」");
        }
        UUID taskId = world.tasks().idOf(roomNo);
        eventually(SETTLE_LIMIT_MS, () -> {
            List<String> holding = board.columnsHolding(roomNo);
            return holding.equals(List.of(wanted)) ? null
                    : roomNo + " 的卡片在 " + (holding.isEmpty() ? "看板上找不到" : holding + " 欄") + "，不是只在「" + wanted + "」欄";
        });
        Map<String, Object> row = world.backend().taskRow(taskId);
        String backendColumn = columnOf(row);
        assertThat(backendColumn.equals(wanted),
                "畫面把 " + roomNo + " 放在「" + wanted + "」欄，但後端的狀態該在「" + backendColumn + "」欄：" + summary(row));
    }

    @那麼("{string} 的卡片副標是 {string}")
    public void cardSubtitle(String roomNo, String subtitle) {
        String wanted = ShellFrame.normalizeSpaces(subtitle);
        eventually(SETTLE_LIMIT_MS, () -> {
            Locator card = board.card(roomNo).filter(new Locator.FilterOptions().setVisible(true));
            if (card.count() == 0) {
                return "看板上看不到 " + roomNo + " 的卡片";
            }
            List<String> lines = ShellFrame.linesOf(card.first().innerText());
            return lines.contains(wanted) ? null : roomNo + " 的卡片上沒有一行是「" + subtitle + "」，卡片上的文字是 " + lines;
        });
    }

    @那麼("{string} 的詳情面板是打開的")
    public void detailOpen(String roomNo) {
        eventually(SETTLE_LIMIT_MS, () -> {
            Locator panel = detail.panel().filter(new Locator.FilterOptions().setVisible(true));
            if (panel.count() == 0) {
                return "畫面上沒有詳情面板";
            }
            if (!detail.roomHeading(roomNo).first().isVisible()) {
                return "詳情面板上的房號不是 " + roomNo;
            }
            if (!detail.loaded()) {
                return "詳情面板還在載入";
            }
            BoundingBox box = panel.first().boundingBox();
            double viewportWidth = world.page().viewportSize().width;
            if (box == null || box.x + box.width / 2 <= viewportWidth / 2) {
                return "詳情面板不在畫面右側：位置 " + describe(box);
            }
            if (shell.onPage("總覽看板") && board.openedCard(roomNo).count() == 0) {
                return "看板上 " + roomNo + " 的卡片沒有標成開著詳情面板的樣子";
            }
            return null;
        });
    }

    @那麼("詳情面板是關的")
    public void detailClosed() {
        eventually(SETTLE_LIMIT_MS, () -> {
            if (detail.panel().filter(new Locator.FilterOptions().setVisible(true)).count() > 0) {
                return "畫面上還有詳情面板";
            }
            Locator main = shell.mainContent();
            if (main.count() == 0) {
                return "畫面上找不到內容區";
            }
            BoundingBox box = main.first().boundingBox();
            double viewportWidth = world.page().viewportSize().width;
            return box != null && box.x + box.width >= viewportWidth - FULL_WIDTH_TOLERANCE_PX ? null
                    : "內容區沒有恢復原本的寬度：位置 " + describe(box) + "，視窗寬 " + viewportWidth;
        });
    }

    @那麼("當場回答面板是打開的，標題是 {string}")
    public void answerPanelOpen(String title) {
        eventually(SETTLE_LIMIT_MS, () -> {
            Locator panel = answer.panelTitled(title).filter(new Locator.FilterOptions().setVisible(true));
            if (panel.count() == 0) {
                return "畫面上沒有標題是「" + title + "」的當場回答面板";
            }
            Object modal = panel.first().evaluate(
                    "el => el.getAttribute('aria-modal') === 'true' || (el.matches && el.matches(':modal'))");
            if (!Boolean.TRUE.equals(modal)) {
                return "當場回答面板後面沒有遮罩：它不是蓋住整個畫面的對話框";
            }
            BoundingBox box = panel.first().boundingBox();
            double viewportWidth = world.page().viewportSize().width;
            return box != null && Math.abs(box.x + box.width / 2 - viewportWidth / 2) <= viewportWidth * CENTER_TOLERANCE
                    ? null : "當場回答面板不在畫面中央：位置 " + describe(box) + "，視窗寬 " + viewportWidth;
        });
    }

    @那麼("{string} 仍是登入狀態，畫面是 {string}")
    public void stillLoggedIn(String staffName, String pageName) {
        CreatedStaff staff = world.staff(staffName);
        eventually(SETTLE_LIMIT_MS, () -> {
            JsonNode login = shell.storedLoginResponse();
            if (login == null) {
                return "這個分頁已經沒有登入狀態，網址是 " + world.page().url();
            }
            if (!staff.loginId().equals(login.path("loginId").asText())) {
                return "這個分頁登入的是 " + login.path("loginId").asText() + "，不是 " + staffName;
            }
            String path = world.page().url().split("[?#]")[0];
            if (path.endsWith(WebApp.LOGIN) || !shell.onPage(pageName)) {
                return "畫面不是「" + pageName + "」，網址是 " + world.page().url();
            }
            String title = shell.pageTitle().count() == 0 ? "（沒有頁標題）" : shell.pageTitle().first().innerText().strip();
            return ShellFrame.titleOf(pageName).equals(title) ? null
                    : "頁標題是「" + title + "」，不是「" + ShellFrame.titleOf(pageName) + "」";
        });
        if (shell.accountCard().filter(new Locator.FilterOptions().setVisible(true)).count() == 0
                && shell.openSidebarButton().isVisible()) {
            shell.openSidebar();
        }
        eventually(SETTLE_LIMIT_MS, () -> {
            Locator card = shell.accountCard().filter(new Locator.FilterOptions().setVisible(true));
            if (card.count() == 0) {
                return "側欄上看不到帳號卡";
            }
            String text = card.first().innerText();
            return text.contains(staffName) ? null : "帳號卡不是 " + staffName + "：" + text;
        });
    }

    @那麼("這段期間 {string} 沒有重新整理網頁")
    public void noReload(String staffName) {
        assertThat(!whenStart.pageReloadedSince(), staffName + " 的分頁在這段期間整頁重新載入過");
        int presses = whenStart.pressesSince();
        assertThat(presses == 0, "這段期間有人按了 " + staffName + " 的畫面 " + presses + " 下");
    }

    @那麼("待派單頁的清單沒有 {string}")
    public void triageListLacks(String roomNo) {
        UUID taskId = world.tasks().idOf(roomNo);
        eventually(SETTLE_LIMIT_MS, () -> {
            if (!shell.onPage("待派單頁")) {
                return "畫面不在待派單頁，網址是 " + world.page().url();
            }
            return triage.hasRow(roomNo) ? "待派單頁的清單還有 " + roomNo : null;
        });
        Map<String, Object> row = world.backend().taskRow(taskId);
        assertThat(!"triage".equals(row.get("state")), "畫面上沒有 " + roomNo + " 了，但後端它還是待派單");
    }

    @那麼("待派單頁的清單由上到下是 {}")
    public void triageListOrder(String roomList) {
        List<String> expected = Arrays.stream(roomList.split("、")).map(String::strip).toList();
        eventually(SETTLE_LIMIT_MS, () -> {
            if (!shell.onPage("待派單頁")) {
                return "畫面不在待派單頁，網址是 " + world.page().url();
            }
            List<String> shown = triage.roomsInOrder();
            return shown.equals(expected) ? null : "待派單頁的清單由上到下是 " + shown + "，不是 " + expected;
        });
        List<String> backendOrder = world.backend().queryRows("""
                SELECT r.room_no FROM tasks t JOIN rooms r ON r.tenant_id = t.tenant_id AND r.id = t.room_id
                WHERE t.tenant_id = ? AND t.state = 'triage' ORDER BY t.created_at DESC
                """, world.defaults().tenantId()).stream().map(row -> String.valueOf(row.get("room_no"))).toList();
        assertThat(backendOrder.equals(expected), "後端的待派單依建立時間由新到舊是 " + backendOrder + "，不是 " + expected);
    }

    @那麼("頁副標是 {string}")
    public void pageSubtitle(String text) {
        String wanted = ShellFrame.normalizeSpaces(text);
        eventually(SETTLE_LIMIT_MS, () -> {
            List<String> lines = shell.toolbarLines();
            return lines.contains(wanted) ? null : "工具列上沒有一行是「" + text + "」，工具列上的文字是 " + lines;
        });
        Matcher open = Pattern.compile("(\\d+) 張未結案").matcher(wanted);
        if (open.find()) {
            long backend = BoardPage.openTaskCount(world.backend().getJson("/desk/board", world.staff(DESK_BOARD_READER)));
            assertThat(backend == Integer.parseInt(open.group(1)),
                    "後端看板裡不在「已結案」欄的單有 " + backend + " 張，頁副標寫 " + open.group(1));
        }
        Matcher waiting = Pattern.compile("(\\d+) 張等你派單").matcher(wanted);
        if (waiting.find()) {
            int backend = countTasks("state = 'triage'");
            assertThat(backend == Integer.parseInt(waiting.group(1)), "後端現在待派單有 " + backend + " 張，頁副標寫 " + waiting.group(1));
        }
        if (wanted.equals("目前沒有要派的單")) {
            int backend = countTasks("state = 'triage'");
            assertThat(backend == 0, "頁副標說沒有要派的單，但後端還有 " + backend + " 張待派單");
        }
    }

    private void eventually(long limitMs, Supplier<String> problem) {
        long deadline = System.nanoTime() + limitMs * 1_000_000;
        while (true) {
            String last;
            try {
                last = problem.get();
            } catch (RuntimeException e) {
                last = "讀取時出錯：" + e;
            }
            if (last == null) {
                return;
            }
            if (System.nanoTime() >= deadline) {
                throw new AssertionError(last + "（重讀 " + limitMs / 1000.0 + " 秒仍是如此）");
            }
            pause();
        }
    }

    private void pause() {
        if (world.hasPage()) {
            world.page().waitForTimeout(POLL_INTERVAL_MS);
            return;
        }
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待時被中斷", e);
        }
    }

    private void tryPress(Locator target) {
        if (world.profile().touch()) {
            target.tap(new Locator.TapOptions().setTrial(true).setTimeout(SETTLE_LIMIT_MS));
        } else {
            target.click(new Locator.ClickOptions().setTrial(true).setTimeout(SETTLE_LIMIT_MS));
        }
    }

    private static Locator undoOn(Locator toast) {
        return toast.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("復原").setExact(true));
    }

    private static void assertThat(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static List<String> textColumn(DataTable table) {
        return table.asMaps().stream().map(row -> {
            String text = row.get("文字");
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("表格的「文字」欄必填");
            }
            return text;
        }).toList();
    }

    private static String fieldDept(String deptTerm) {
        String dept = BackendChannel.dept(deptTerm.strip());
        if (!"housekeeping".equals(dept) && !"engineering".equals(dept)) {
            throw new IllegalArgumentException("部門只能是房務或工務，收到「" + deptTerm + "」");
        }
        return dept;
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

    private boolean stillShownAsTriage(String roomNo) {
        if (shell.onPage("待派單頁")) {
            return triage.hasRow(roomNo);
        }
        if (shell.onPage("總覽看板")) {
            return board.columnsHolding(roomNo).contains("待派單");
        }
        return false;
    }

    private static String columnOf(Map<String, Object> row) {
        String state = String.valueOf(row.get("state"));
        return switch (state) {
            case "triage" -> "待派單";
            case "pool" -> "待認領";
            case "accepted" -> "已接";
            case "doing" -> "進行中";
            case "done" -> row.get("ended_at") != null ? "已結案" : "已完成";
            case "interrupted" -> "中斷";
            case "deferred" -> "延後中";
            case "inspected", "closed", "cancelled" -> "已結案";
            default -> throw new IllegalStateException("看不懂後端的狀態「" + state + "」");
        };
    }

    private List<Map<String, Object>> eventsSinceWhen(UUID taskId) {
        return world.backend().queryRows("""
                SELECT seq, type, actor_role, actor_id FROM task_events
                WHERE tenant_id = ? AND task_id = ? AND seq > ? ORDER BY seq
                """, world.defaults().tenantId(), taskId, whenStart.lastSeqBefore(taskId));
    }

    private int countTasks(String condition) {
        Object count = world.backend().queryRows(
                "SELECT count(*) AS n FROM tasks WHERE tenant_id = ? AND " + condition,
                world.defaults().tenantId()).get(0).get("n");
        return ((Number) count).intValue();
    }

    private static Map<Integer, String> typesBySeq(JsonNode timeline) {
        Map<Integer, String> bySeq = new LinkedHashMap<>();
        for (JsonNode entry : timeline) {
            bySeq.put(entry.path("seq").asInt(), entry.path("type").asText());
        }
        return bySeq;
    }

    private static Integer badgeNumber(String itemText) {
        Matcher number = Pattern.compile("(\\d+)").matcher(itemText.replace("待派單", ""));
        return number.find() ? Integer.valueOf(number.group(1)) : null;
    }

    private static String[] roleAndNameOf(Locator element) {
        String firstLine = element.ariaSnapshot().lines().findFirst().orElse("");
        Matcher matcher = Pattern.compile("^-\\s+(\\S+?)(?:\\s+\"((?:[^\"\\\\]|\\\\.)*)\")?:?\\s*$").matcher(firstLine.strip());
        if (!matcher.find()) {
            return new String[] {"", ""};
        }
        String name = matcher.group(2) == null ? "" : matcher.group(2).replace("\\\"", "\"");
        return new String[] {matcher.group(1), name};
    }

    private static JsonNode parse(String json) {
        try {
            return JSON.readTree(json == null ? "{}" : json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("事件內容不是 JSON：" + json, e);
        }
    }

    private static String summary(Map<String, Object> row) {
        Set<String> keys = new HashSet<>(List.of("state", "dept", "type", "assignee_id", "answer", "cancel_reason"));
        StringBuilder text = new StringBuilder("{");
        row.forEach((key, value) -> {
            if (keys.contains(key)) {
                text.append(text.length() > 1 ? ", " : "").append(key).append("=").append(value);
            }
        });
        return text.append("}").toString();
    }

    private static String describe(BoundingBox box) {
        return box == null ? "（看不到）" : "x=" + box.x + " y=" + box.y + " 寬=" + box.width + " 高=" + box.height;
    }
}
