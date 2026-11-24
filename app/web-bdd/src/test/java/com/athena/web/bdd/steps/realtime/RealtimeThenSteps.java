package com.athena.web.bdd.steps.realtime;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.BoardPage.CardReading;
import com.athena.web.bdd.pages.BoardPage.TileReading;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.TriagePage;
import com.athena.web.bdd.support.JsResult;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.Network.SentRequest;
import com.athena.web.bdd.support.StepPhaseListener;
import com.athena.web.bdd.support.WebWorld;
import com.athena.web.bdd.support.WhenStart;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.BoundingBox;
import io.cucumber.java.Before;
import io.cucumber.java.zh_tw.那麼;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class RealtimeThenSteps {

    private static final double ASSERT_TIMEOUT_MS = 5_000;

    private static final double TOLERANCE_PX = 1;

    private static final int POLL_PERIODS_TO_WATCH = 3;

    private static final double AFTER_FAST_FORWARD_MS = 300;

    private static final double SETTLE_AFTER_WATCH_MS = 1_000;

    private static final double STILL_AFTER_MS = 2_000;

    private static final List<String> COUNTED_TILES = List.of("待派單", "待認領", "處理中");

    private static final String CARD_GONE_FROM_BOARD = "{int} 秒內看板上沒有 {string} 的卡片";

    private static final String NO_FULL_BOARD_REREAD = "這段期間畫面沒有重新讀取整份看板";

    private static final double NO_ALERT_AFTER_RECONNECT_MS = 2_000;

    private static final String PRESSABLES_IN = """
            bar => [...bar.querySelectorAll([
              'button', 'a[href]', 'input:not([type=hidden])', 'select', 'textarea', 'summary',
              '[role=button]', '[role=link]', '[role=tab]', '[role=menuitem]', '[role=option]',
              '[role=checkbox]', '[role=radio]', '[role=switch]', '[tabindex]:not([tabindex="-1"])',
            ].join(','))].filter(el => {
              const r = el.getBoundingClientRect();
              return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== 'hidden';
            }).map(el => (el.getAttribute('aria-label') || el.innerText || el.tagName.toLowerCase()).trim())
            """;

    private final WebWorld world;

    private final ShellFrame shell;

    private final BoardPage board;

    private final TriagePage triage;

    private final WhenStart whenStart;

    private final LiveUpdateTiming timing;

    private final RealtimeScenario scenario;

    private String subtitleAtWhenStart;

    private RuntimeException subtitleCaptureFailure;

    private final Map<String, List<String>> columnsAtWhenStart = new HashMap<>();

    private final Map<String, String> tilesAtWhenStart = new HashMap<>();

    private final LiveConnection live;

    public RealtimeThenSteps(WebWorld world, WhenStart whenStart, LiveUpdateTiming timing, RealtimeScenario scenario,
            LiveConnection live) {
        this.world = world;
        this.live = live;
        this.scenario = scenario;
        this.shell = new ShellFrame(world);
        this.board = new BoardPage(world);
        this.triage = new TriagePage(world);
        this.whenStart = whenStart;
        this.timing = timing;
    }

    @Before(order = 11)
    public void rememberSubtitleAtWhenStart() {
        whenStart.alsoCapture(this::captureSubtitle);
    }

    @Before(order = 12)
    public void rememberBoardAtWhenStart() {
        if (StepPhaseListener.currentScenarioUses(CARD_GONE_FROM_BOARD)
                || StepPhaseListener.currentScenarioUses(NO_FULL_BOARD_REREAD)) {
            whenStart.alsoCapture(this::captureBoard);
        }
    }

    private void captureBoard() {
        if (!world.hasPage()) {
            return;
        }
        Page page = world.page();
        scenario.markResyncSeenAtWhenStart(live.resyncReceived(page));
        if (!shell.onPage("總覽看板")) {
            return;
        }
        for (var entry : world.tasks().all()) {
            columnsAtWhenStart.put(entry.roomNo(), board.columnsHolding(entry.roomNo()));
        }
        for (String tile : COUNTED_TILES) {
            String number = board.readTile(tile).number();
            if (number != null && number.matches("\\d+")) {
                tilesAtWhenStart.put(tile, number);
            }
        }
    }

    private void captureSubtitle() {
        try {
            if (world.hasPage() && (shell.onPage("總覽看板") || shell.onPage("待派單頁")) && shell.pageTitle().count() > 0) {
                subtitleAtWhenStart = triage.pageSubtitleText();
            }
        } catch (RuntimeException e) {
            subtitleCaptureFailure = e;
        }
    }

    @那麼("{int} 秒內 {string} 的卡片出現在 {string} 欄")
    public void cardAppearsInColumn(int seconds, String roomNo, String column) {
        if (!BoardPage.COLUMN_NAMES.contains(column.strip())) {
            throw new IllegalArgumentException("看板的欄只有 " + BoardPage.COLUMN_NAMES + "，收到「" + column + "」");
        }
        withinSeconds(seconds, "「" + roomNo + "」的卡片沒有跟上到「" + column + "」欄", () -> {
            List<String> holding = board.columnsHolding(roomNo);
            if (holding.equals(List.of(column.strip()))) {
                return null;
            }
            return holding.isEmpty() ? "看板上任何一欄都找不到這張卡片" : "卡片在 " + holding;
        });
        requireUntouchedSinceOrigin();
    }

    @那麼("{string} 的卡片仍在 {string} 欄")
    public void cardStillInColumn(String roomNo, String column) {
        if (!BoardPage.COLUMN_NAMES.contains(column.strip())) {
            throw new IllegalArgumentException("看板的欄只有 " + BoardPage.COLUMN_NAMES + "，收到「" + column + "」");
        }
        world.page().waitForTimeout(STILL_AFTER_MS);
        List<String> holding = board.columnsHolding(roomNo);
        if (!holding.equals(List.of(column.strip()))) {
            throw new AssertionError("「" + roomNo + "」的卡片應該仍在「" + column + "」欄，現在"
                    + (holding.isEmpty() ? "看板上任何一欄都找不到它" : "在 " + holding));
        }
    }

    @那麼(CARD_GONE_FROM_BOARD)
    public void cardGoneFromBoard(int seconds, String roomNo) {
        List<String> before = columnsAtWhenStart.getOrDefault(roomNo, List.of());
        withinSeconds(seconds, "「" + roomNo + "」的卡片沒有從看板上拿掉", () -> {
            List<String> holding = board.columnsHolding(roomNo);
            if (!holding.isEmpty()) {
                return "卡片還在 " + holding;
            }
            for (String tile : COUNTED_TILES) {
                String was = tilesAtWhenStart.get(tile);
                if (was == null || BoardPage.columnsFilteredBy(tile).stream().noneMatch(before::contains)) {
                    continue;
                }
                String expected = String.valueOf(Integer.parseInt(was) - 1);
                String now = board.readTile(tile).number();
                if (!expected.equals(now)) {
                    return "「" + tile + "」數量卡是 " + now + "，應該從 " + was + " 少算成 " + expected;
                }
            }
            return null;
        });
        requireUntouchedSinceOrigin();
    }

    @那麼(NO_FULL_BOARD_REREAD)
    public void noFullBoardReread() {
        Page tab = world.page();
        tab.waitForTimeout(STILL_AFTER_MS);
        int from = scenario.boardRequestsFrom().orElseGet(() -> whenStart.snapshot().requestCount());
        boolean resyncAfterGiven = scenario.boardRequestsFrom().isEmpty()
                && !scenario.resyncSeenAtWhenStart().orElse(false);
        long reads = boardReadsSince(tab, from);
        long allowed = resyncAfterGiven ? 1 : 0;
        if (reads > allowed) {
            throw new AssertionError("這段期間畫面讀了 " + reads + " 次整份看板"
                    + (resyncAfterGiven ? "，扣掉後端剛連上時要求的那一次仍然多了" : ""));
        }
    }

    @那麼("{int} 秒內畫面重新讀取了一次整份看板")
    public void fullBoardReread(int seconds) {
        Page tab = world.page();
        int from = timing.origin().requestCount();
        withinSeconds(seconds, "畫面沒有重新讀取整份看板",
                () -> boardReadsSince(tab, from) > 0 ? null : "這個分頁沒有送出看板請求");
    }

    private long boardReadsSince(Page tab, int requestCount) {
        return world.network().sentRequestsSince(requestCount).stream()
                .filter(request -> request.page() == tab && request.is(Network.DESK_BOARD))
                .count();
    }

    @那麼("{int} 秒內 {string} 的卡片有負責人頭像 {string}，等待膠囊是 {string}")
    public void cardShowsAvatarAndPill(int seconds, String roomNo, String avatar, String pill) {
        withinSeconds(seconds, "「" + roomNo + "」的卡片沒有跟上", () -> {
            List<CardReading> cards = board.readCardsOf(roomNo);
            if (cards.size() != 1) {
                return "看板上這間房的卡片應該剛好一張，找到 " + cards.size() + " 張";
            }
            CardReading card = cards.get(0);
            if (!avatar.strip().equals(card.avatar())) {
                return "負責人頭像是「" + card.avatar() + "」，不是「" + avatar + "」（卡片：" + card.text() + "）";
            }
            if (!BoardPage.collapse(pill).equals(card.pillText())) {
                return "等待膠囊是「" + card.pillText() + "」，不是「" + pill + "」";
            }
            return null;
        });
    }

    @那麼("{int} 秒內側欄 {string} 的徽章與 {string} 數量卡都變成 {int}")
    public void badgeAndCountCardBecome(int seconds, String feature, String countCard, int number) {
        openSidebarIfCollapsed();
        String expected = String.valueOf(number);
        withinSeconds(seconds, "側欄「" + feature + "」的徽章與「" + countCard + "」數量卡沒有都變成 " + number, () -> {
            Integer badge = shell.sidebarBadge(feature);
            if (!Objects.equals(badge, number)) {
                return "徽章是 " + (badge == null ? "（沒有數字）" : badge);
            }
            TileReading tile = board.readTile(countCard);
            return expected.equals(tile.number()) ? null : "數量卡的數字是「" + tile.number() + "」（" + tile.text() + "）";
        });
        requireBackendTriageCount(feature, number);
    }

    @那麼("{int} 秒內待派單頁的清單沒有 {string}，側欄 {string} 的徽章是 {int}")
    public void triageListLacksRoom(int seconds, String roomNo, String feature, int number) {
        requireOnPage("待派單頁");
        openSidebarIfCollapsed();
        withinSeconds(seconds, "待派單頁沒有跟上「" + roomNo + "」的變化", () -> {
            if (triage.hasRow(roomNo)) {
                return "清單上還有「" + roomNo + "」這一列（現在由上到下是 " + triage.roomsInOrder() + "）";
            }
            Integer badge = shell.sidebarBadge(feature);
            return Objects.equals(badge, number) ? null
                    : "側欄「" + feature + "」的徽章是 " + (badge == null ? "（沒有數字）" : badge);
        });
        requireBackendTriageCount(feature, number);
    }

    @那麼("{int} 秒內待派單頁的清單第一筆是 {string}")
    public void triageListStartsWith(int seconds, String roomNo) {
        requireOnPage("待派單頁");
        withinSeconds(seconds, "待派單頁的清單第一筆不是「" + roomNo + "」", () -> {
            List<String> rooms = triage.roomsInOrder();
            if (rooms.isEmpty()) {
                return "清單上沒有任何一列";
            }
            return roomNo.strip().equals(rooms.get(0)) ? null : "清單由上到下是 " + rooms;
        });
    }

    @那麼("工具列下方出現提醒列 {string}，說明是 {string}")
    public void alertBarShown(String title, String description) {
        Locator bar = shell.alertBar(title);
        eventually("工具列下方沒有提醒列「" + title + "」／「" + description + "」", () -> {
            if (bar.count() != 1 || !bar.isVisible()) {
                return "找到 " + bar.count() + " 條寫著「" + title + "」的提醒列，或它看不到";
            }
            Locator descriptionText = bar.getByText(description.strip(), new Locator.GetByTextOptions().setExact(true));
            if (descriptionText.count() == 0) {
                return "提醒列的字是「" + ShellFrame.normalizeSpaces(bar.innerText()) + "」，沒有說明「" + description + "」";
            }
            Locator toolbar = shell.toolbar().first();
            if (toolbar.count() == 0) {
                return "畫面上找不到工具列";
            }
            BoundingBox barBox = bar.boundingBox();
            BoundingBox toolbarBox = toolbar.boundingBox();
            double toolbarBottom = toolbarBox.y + toolbarBox.height;
            if (barBox.y < toolbarBottom - TOLERANCE_PX) {
                return "提醒列（上緣 " + barBox.y + "）沒有在工具列（下緣 " + toolbarBottom + "）的下方";
            }
            Locator firstContent = firstContentBlock();
            if (firstContent.count() > 0) {
                BoundingBox contentBox = firstContent.first().boundingBox();
                double barBottom = barBox.y + barBox.height;
                if (contentBox != null && contentBox.y < barBottom - TOLERANCE_PX) {
                    return "這一頁的內容（上緣 " + contentBox.y + "）高過提醒列（下緣 " + barBottom + "）";
                }
            }
            return subtitleProblem(title, description);
        });
    }

    @那麼("提醒列上沒有任何按鈕")
    public void alertBarHasNoButtons() {
        Locator bar = shell.connectionAlertBar();
        eventually("提醒列上不應該有可以按的東西", () -> {
            if (bar.count() != 1 || !bar.isVisible()) {
                return "畫面上看不到提醒列「" + ShellFrame.CONNECTION_ALERT_TITLE + "」";
            }
            List<String> pressables = JsResult.strings(bar.evaluate(PRESSABLES_IN));
            return pressables.isEmpty() ? null : "提醒列上有可以按的東西：" + pressables;
        });
    }

    @那麼("這段期間提醒列一次也沒有出現")
    public void alertNeverShown() {
        boolean seen = scenario.alertSeenDuringReconnect().orElseThrow(() -> new IllegalStateException(
                "「當」步驟沒有監看提醒列，這一句要接在「即時連線中斷，第一次重連就成功」之後"));
        if (seen) {
            throw new AssertionError("從切斷即時連線到重連成功之間，提醒列「" + ShellFrame.CONNECTION_ALERT_TITLE + "」出現過");
        }
        Page page = world.page();
        page.waitForTimeout(NO_ALERT_AFTER_RECONNECT_MS);
        Locator bar = shell.connectionAlertBar();
        if (bar.count() > 0 && bar.first().isVisible()) {
            throw new AssertionError("重連成功 " + (long) (NO_ALERT_AFTER_RECONNECT_MS / 1000) + " 秒之後，畫面上出現了提醒列「"
                    + ShellFrame.CONNECTION_ALERT_TITLE + "」");
        }
    }

    @那麼("這段期間 {string} 沒有按任何東西")
    public void staffPressedNothing(String staffName) {
        CreatedStaff staff = world.staff(staffName);
        JsonNode login = shell.storedLoginResponse();
        String loginId = login == null ? "" : login.path("loginId").asText("");
        if (!loginId.isEmpty() && !loginId.equals(staff.loginId())) {
            throw new AssertionError("開著的分頁登入的是 " + loginId + "，不是「" + staffName + "」（" + staff.loginId() + "）");
        }
        int presses = whenStart.pressesSince();
        if (presses != 0) {
            throw new AssertionError("前置走完之後「" + staffName + "」的分頁被按了 " + presses + " 下");
        }
        if (whenStart.pageReloadedSince()) {
            throw new AssertionError("前置走完之後「" + staffName + "」的分頁重新整理過");
        }
        Page tab = whenStart.snapshot().page();
        List<String> writes = whenStart.requestsSince().stream()
                .filter(request -> request.page() == tab && request.isWrite())
                .map(request -> request.method() + " " + request.path())
                .toList();
        if (!writes.isEmpty()) {
            throw new AssertionError("前置走完之後「" + staffName + "」的分頁送出了寫入：" + writes);
        }
    }

    @那麼("畫面沒有每 {int} 秒定時讀取整份看板")
    public void noPeriodicBoardReload(int seconds) {
        Page tab = world.page();
        int before = world.network().sentRequestCount();
        for (int period = 0; period < POLL_PERIODS_TO_WATCH; period++) {
            world.clocks().elapse(tab, Duration.ofSeconds(seconds));
            tab.waitForTimeout(AFTER_FAST_FORWARD_MS);
        }
        tab.waitForTimeout(SETTLE_AFTER_WATCH_MS);
        long boardReads = world.network().sentRequestsSince(before).stream()
                .filter(request -> request.page() == tab && request.is(Network.DESK_BOARD))
                .count();
        if (boardReads != 0) {
            throw new AssertionError("瀏覽器時鐘推進 " + POLL_PERIODS_TO_WATCH + " 個 " + seconds
                    + " 秒、期間沒有即時訊號，畫面卻讀了 " + boardReads + " 次看板，還在定時讀取");
        }
    }

    private void withinSeconds(int seconds, String failure, Supplier<String> problem) {
        String last = poll(Math.max(timing.remainingMillis(seconds), 1), problem);
        if (last != null) {
            throw new AssertionError(failure + "（從送出起 " + seconds + " 秒內）：" + last);
        }
    }

    private void eventually(String failure, Supplier<String> problem) {
        String last = poll(ASSERT_TIMEOUT_MS, problem);
        if (last != null) {
            throw new AssertionError(failure + "：" + last);
        }
    }

    private String poll(double timeoutMs, Supplier<String> problem) {
        String[] last = {"還沒檢查過"};
        try {
            world.page().waitForCondition(() -> {
                try {
                    last[0] = problem.get();
                } catch (PlaywrightException | IllegalStateException unreadable) {
                    last[0] = "畫面讀不到：" + unreadable.getMessage();
                }
                return last[0] == null;
            }, new Page.WaitForConditionOptions().setTimeout(timeoutMs));
            return null;
        } catch (TimeoutError expired) {
            return last[0];
        }
    }

    private void requireUntouchedSinceOrigin() {
        Page tab = world.page();
        List<SentRequest> since = world.network().sentRequestsSince(timing.origin().requestCount());
        List<String> touched = since.stream()
                .filter(request -> request.page() == tab && (request.documentLoad() || request.isWrite()))
                .map(request -> request.method() + " " + request.path())
                .toList();
        if (!touched.isEmpty()) {
            throw new AssertionError("等畫面跟上的期間，這個分頁重新整理過或送出了寫入：" + touched);
        }
    }

    private void openSidebarIfCollapsed() {
        if (!shell.sidebarOpen()) {
            shell.openSidebar();
        }
    }

    private void requireOnPage(String pageName) {
        if (!shell.onPage(pageName)) {
            throw new AssertionError("目前的分頁不在" + pageName + "，而在 " + world.page().url());
        }
    }

    private void requireBackendTriageCount(String feature, int number) {
        if (!"待派單".equals(feature.strip())) {
            return;
        }
        long triageCount = ((Number) world.backend().queryRows(
                "SELECT count(*) AS n FROM tasks WHERE tenant_id = ? AND state = 'triage'",
                world.defaults().tenantId()).get(0).get("n")).longValue();
        if (triageCount != number) {
            throw new AssertionError("畫面的徽章是 " + number + "，但後端目前有 " + triageCount + " 張待派單的單");
        }
    }

    private Locator firstContentBlock() {
        return shell.onPage("待派單頁") ? triage.allRowActions().first() : board.tileRow();
    }

    private String subtitleProblem(String title, String description) {
        if (subtitleCaptureFailure != null) {
            throw new AssertionError("「當」開始時沒讀到頁副標，無法比對它有沒有變：" + subtitleCaptureFailure,
                    subtitleCaptureFailure);
        }
        if (subtitleAtWhenStart == null) {
            throw new AssertionError("「當」開始時沒有開著總覽看板或待派單頁，沒有記到頁副標，無法比對它有沒有變");
        }
        String now = triage.pageSubtitleText();
        if (now.contains(title.strip()) || now.contains(description.strip())) {
            return "提醒列的字跑進了頁副標：「" + now + "」";
        }
        return subtitleAtWhenStart.equals(now) ? null
                : "頁副標從「" + subtitleAtWhenStart + "」變成了「" + now + "」";
    }
}
