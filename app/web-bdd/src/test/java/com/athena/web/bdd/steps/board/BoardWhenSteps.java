package com.athena.web.bdd.steps.board;

import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.BoardPage.CardDrag;
import com.athena.web.bdd.pages.BoardPage.DropResult;
import com.athena.web.bdd.pages.BoardPage.TriedDrag;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.BoundingBox;
import io.cucumber.java.After;
import io.cucumber.java.zh_tw.當;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 「總覽看板」模組的 When 句型。
 *
 * <p>每一句做完都等到規格寫的「等待條件」成立，但最多等 {@value #SETTLE_TIMEOUT_MS} 毫秒；
 * 等不到時照樣往下走，讓接著的 Then 以斷言說出畫面哪裡不對，而不是在這裡以逾時收場。
 */
public class BoardWhenSteps {

    private static final double SETTLE_TIMEOUT_MS = 5_000;

    /** 試著拖一張拖不動的卡片時，給畫面多久時間錯誤地把它拖起來；拖得動的卡片在這段時間內一定已經起拖。 */
    private static final double TRY_DRAG_WATCH_MS = 1_000;

    private static final Pattern ASSIGN_PATH = Pattern.compile(".*/api/v1/desk/tasks/[^/]+/assign");

    private final WebWorld world;

    /** 「按住開始拖」「滑到拖放區上還沒放開」之後還按著的那一次拖曳；場景結束時在原位放開。 */
    private CardDrag held;

    private double heldStartX;

    private double heldStartY;

    public BoardWhenSteps(WebWorld world) {
        this.world = world;
    }

    @當("{string} 點 {string} 數量卡")
    public void pressCountCard(String staff, String countCard) {
        BoardPage board = board();
        Locator tile = board.countCard(countCard);
        if (!tile.isEnabled()) {
            // 沒有在等的單時「等最久」點了沒有反應；Playwright 一般的點擊會一直等它變成可按，所以略過這項等待硬點一下。
            if (world.profile().touch()) {
                tile.tap(new Locator.TapOptions().setForce(true));
            } else {
                tile.click(new Locator.ClickOptions().setForce(true));
            }
            return;
        }
        String pillBefore = board.filterPillText();
        shell().settleAfter(() -> shell().press(tile));
        if (!"等最久".equals(countCard.strip())) {
            settle(() -> !board.filterPillText().equals(pillBefore));
        }
    }

    @當("{string} 在 {string} 輸入 {string}")
    public void typeInto(String staff, String inputName, String content) {
        if (!"搜尋房號".equals(inputName.strip())) {
            throw new IllegalArgumentException("總覽看板只有「搜尋房號」可以輸入，收到「" + inputName + "」");
        }
        BoardPage board = board();
        Locator box = board.roomSearch();
        box.pressSequentially(content);
        // 看板依輸入重畫完成時，篩選膠囊會顯示框裡現在的房號。
        settle(() -> {
            String value = box.inputValue();
            return value.isEmpty() || board.filterPillText().contains("房號 " + value);
        });
    }

    @當("{string} 點控制列的篩選膠囊")
    public void pressFilterPill(String staff) {
        shell().pressAndSettle(board().filterPill());
    }

    @當("{string} 把泳道切到 {string}")
    public void switchLane(String staff, String lane) {
        BoardPage board = board();
        shell().pressAndSettle(board.laneOption(lane));
        settle(() -> board.laneSelected(lane));
    }

    @當("{string} 按住 {string} 的卡片開始拖")
    public void startDragging(String staff, String roomNo) {
        holdFrom(roomNo);
        held = board().startDrag(roomNo);
    }

    @當("{string} 拖著 {string} 的卡片滑到 {string} 上，還沒放開")
    public void dragOver(String staff, String roomNo, String dropZone) {
        BoardPage board = board();
        holdFrom(roomNo);
        held = board.startDrag(roomNo);
        Locator zone = board.dropZone(dropZone).first();
        zone.waitFor();
        held.bringIntoView(zone);
        held.moveToCenterOf(zone);
    }

    @當("{string} 把 {string} 的卡片拖到 {string} 放開")
    public void dropOn(String staff, String roomNo, String dropZone) {
        BoardPage board = board();
        Page page = world.page();
        AtomicReference<Request> sent = new AtomicReference<>();
        AtomicReference<Request> ended = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>();
        Consumer<Request> onSent = request -> {
            if (isAssign(request)) {
                sent.compareAndSet(null, request);
            }
        };
        Consumer<Request> onFinished = request -> {
            if (isAssign(request)) {
                ended.compareAndSet(null, request);
            }
        };
        Consumer<Request> onFailed = request -> {
            if (isAssign(request)) {
                failure.compareAndSet(null, request.failure());
                ended.compareAndSet(null, request);
            }
        };
        page.onRequest(onSent);
        page.onRequestFinished(onFinished);
        page.onRequestFailed(onFailed);
        try {
            shell().settleAfter(() -> board.dragCardToDropZone(roomNo, dropZone));
            settle(() -> ended.get() != null);
        } finally {
            page.offRequest(onSent);
            page.offRequestFinished(onFinished);
            page.offRequestFailed(onFailed);
        }
        settle(() -> board.anyDropZone().count() == 0);
        board.rememberDrop(dropResult(roomNo, dropZone, sent.get(), ended.get(), failure.get()));
    }

    @當("{string} 拖著 {string} 的卡片在拖放區以外的地方放開")
    public void dropOutside(String staff, String roomNo) {
        BoardPage board = board();
        holdFrom(roomNo);
        CardDrag drag = board.startDrag(roomNo);
        // 拖回按下的位置：那裡是「待派單」欄裡卡片自己的位置，離拖放區最遠，也一定在看板上。
        drag.moveTo(heldStartX, heldStartY);
        shell().settleAfter(drag::release);
        settle(() -> board.anyDropZone().count() == 0);
    }

    @當("{string} 試著拖 {string} 的卡片")
    public void tryDrag(String staff, String roomNo) {
        BoardPage board = board();
        int requestsBefore = world.network().sentRequestCount();
        CardDrag drag = board.liftCard(roomNo);
        settle(() -> board.anyDropZone().count() > 0, TRY_DRAG_WATCH_MS);
        TriedDrag tried = new TriedDrag(roomNo,
                board.anyDropZone().count(),
                board.draggingHint().count() > 0,
                board.card(roomNo).count(),
                requestsBefore);
        shell().settleAfter(drag::release);
        board.rememberTriedDrag(tried);
    }

    @當("過了 {int} 秒，{string} 沒有按任何東西")
    public void idleFor(int seconds, String staff) {
        Page page = world.page();
        AtomicBoolean boardRead = new AtomicBoolean();
        Consumer<Request> ended = request -> {
            if (Network.DESK_BOARD.method().equals(request.method())
                    && pathOf(request.url()).endsWith(Network.DESK_BOARD.path())) {
                boardRead.set(true);
            }
        };
        page.onRequestFinished(ended);
        page.onRequestFailed(ended);
        try {
            // 只撥瀏覽器的時鐘：自動再試是畫面自己的計時，後端不必跟著前進。
            // 前置若把時鐘停住了，等這一次看板讀取結束就放開：畫面框架讀到資料後排在 setTimeout(0) 才更新，
            // 時鐘停著畫面不會換。讀取結束才放開，自動再試就只會是快轉觸發的這一次，不會在快轉之前自己先發生。
            shell().settleAfter(() -> {
                try {
                    page.clock().fastForward(seconds * 1_000L);
                    settle(boardRead::get);
                } finally {
                    world.resumePausedClock();
                }
            });
        } finally {
            page.offRequestFinished(ended);
            page.offRequestFailed(ended);
        }
    }

    /** 場景結束時還按著的拖曳在原位放開，不讓按著的指標留到截圖或關分頁時。 */
    @After(order = 100)
    public void releaseHeldDrag() {
        if (held == null || !world.hasPage()) {
            return;
        }
        try {
            held.moveTo(heldStartX, heldStartY);
            held.release();
        } catch (RuntimeException alreadyGone) {
            // 分頁已經壞掉或換頁，沒有東西可以放開；場景的結果以它自己的步驟為準。
        } finally {
            held = null;
        }
    }

    /** 記下卡片在畫面上的中心，之後在原位放開用。先捲進視窗，量到的位置才和起拖時相同。 */
    private void holdFrom(String roomNo) {
        Locator card = board().card(roomNo);
        card.scrollIntoViewIfNeeded();
        BoundingBox box = card.boundingBox();
        heldStartX = box.x + box.width / 2;
        heldStartY = box.y + box.height / 2;
    }

    private static DropResult dropResult(String roomNo, String dropZone, Request sent, Request ended, String failure) {
        if (sent == null) {
            return new DropResult(roomNo, dropZone, null, null, null);
        }
        Integer status = null;
        if (ended != null && failure == null && ended.response() != null) {
            status = ended.response().status();
        }
        return new DropResult(roomNo, dropZone, sent.postData(), status, failure);
    }

    private static boolean isAssign(Request request) {
        return "POST".equals(request.method()) && ASSIGN_PATH.matcher(pathOf(request.url())).matches();
    }

    private static String pathOf(String url) {
        try {
            String path = URI.create(url).getPath();
            return path == null ? "" : path;
        } catch (IllegalArgumentException notAUrl) {
            return "";
        }
    }

    private void settle(BooleanSupplier condition) {
        settle(condition, SETTLE_TIMEOUT_MS);
    }

    private void settle(BooleanSupplier condition, double timeoutMs) {
        try {
            world.page().waitForCondition(condition, new Page.WaitForConditionOptions().setTimeout(timeoutMs));
        } catch (TimeoutError notYet) {
            // 等不到就往下走，理由見類別說明。
        }
    }

    private BoardPage board() {
        return new BoardPage(world);
    }

    private ShellFrame shell() {
        return new ShellFrame(world);
    }
}
