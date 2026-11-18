package com.athena.web.bdd.steps.board;

import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import io.cucumber.java.zh_tw.假設;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** 「總覽看板」模組的 Given 句型。 */
public class BoardGivenSteps {

    /**
     * 停住瀏覽器時鐘時，停在瀏覽器此刻再往後這麼多毫秒。
     * 讀時間與下指令之間瀏覽器的時鐘還在走，停的時刻若早於瀏覽器的現在，Playwright 會拒絕往回停。
     */
    private static final long CLOCK_PAUSE_MARGIN_MS = 200;

    private final WebWorld world;

    private final ShellFrame shell;

    private final BoardPage board;

    public BoardGivenSteps(WebWorld world) {
        this.world = world;
        this.shell = new ShellFrame(world);
        this.board = new BoardPage(world);
    }

    /** 點那張數量卡，等看板只剩它對應的欄。 */
    @假設("{string} 已在總覽看板點了 {string} 數量卡")
    public void clickedCountCard(String staffName, String countCard) {
        ensureOnBoard(staffName);
        filterByCountCard(countCard);
    }

    /** 點數量卡，再在「搜尋房號」逐字輸入房號，等框裡的字就是那個房號。 */
    @假設("{string} 已在總覽看板點了 {string} 數量卡、搜尋房號 {string}")
    public void clickedCountCardAndSearched(String staffName, String countCard, String roomNo) {
        ensureOnBoard(staffName);
        filterByCountCard(countCard);
        board.roomSearch().pressSequentially(roomNo);
        world.page().waitForCondition(() -> roomNo.equals(board.roomSearch().inputValue()));
    }

    /** 點泳道分段鈕的那一顆，等它變成選中的樣子；分段鈕的選中與看板依泳道重畫是同一次畫面更新。 */
    @假設("{string} 已把泳道切到 {string}")
    public void switchedLane(String staffName, String lane) {
        ensureOnBoard(staffName);
        shell.press(board.laneOption(lane));
        world.page().waitForCondition(() -> board.laneSelected(lane));
    }

    /**
     * 在總覽看板把那間房的卡片拖到對應的拖放區放開，等派單請求成功、浮條出現。
     * 浮條 5 秒後自己消失，瀏覽器時鐘照真實速度走，接下來的步驟要在這 5 秒內做完。
     */
    @假設("{string} 剛在 {string} 把 {string} 拖給 {string}，派單浮條還在")
    public void justDraggedTo(String staffName, String pageName, String roomNo, String dept) {
        if (!WebApp.BOARD.equals(ShellFrame.pathOf(pageName))) {
            throw new IllegalArgumentException("拖曳派單只在總覽看板，收到「" + pageName + "」");
        }
        ensureOnBoard(staffName);
        UUID taskId = world.tasks().idOf(roomNo);
        String zone = BoardPage.dropZoneFor(dept);
        Response response = world.page().waitForResponse(
                r -> isAssignOf(r, taskId),
                () -> board.dragCardToDropZone(roomNo, zone));
        if (!response.ok()) {
            throw new AssertionError("拖給" + dept + "的派單請求沒有成功：" + response.status() + " " + response.text());
        }
        shell.assignToast().waitFor();
    }

    /**
     * 讓看板資料在送出時斷線，以這位員工登入並開總覽看板，等「讀不到資料」出現；
     * 先停住瀏覽器時鐘、再取消攔截，順序反過來的話，自動再試可能在兩步之間就先成功了。
     */
    @假設("{string} 打開總覽看板時讀取失敗，之後網路已經恢復")
    public void boardLoadFailedThenRecovered(String staffName) {
        Page page = world.page();
        world.session().loginAs(page, world.staff(staffName));
        world.network().disconnect(page, Network.DESK_BOARD);
        world.open(WebApp.BOARD);
        board.loadFailedTitle().waitFor();
        pauseBrowserClock(page);
        world.network().passThrough(page, Network.DESK_BOARD);
    }

    /** 還沒登入時先以這位員工登入並開總覽看板；已登入但不在總覽看板時換過去。 */
    private void ensureOnBoard(String staffName) {
        if (!shell.loggedIn()) {
            shell.loginAndOpen(world.staff(staffName), "總覽看板");
        } else if (!world.page().url().split("[?#]")[0].endsWith(WebApp.BOARD)) {
            shell.openAndWait("總覽看板");
        }
    }

    private void filterByCountCard(String countCard) {
        shell.press(board.countCard(countCard));
        board.waitForColumns(BoardPage.columnsFilteredBy(countCard));
    }

    private static boolean isAssignOf(Response response, UUID taskId) {
        String path = response.url().split("[?#]")[0];
        return Network.DESK_ASSIGN.method().equals(response.request().method())
                && path.endsWith("/api/v1/desk/tasks/" + taskId + "/assign");
    }

    /**
     * 把瀏覽器時鐘停在此刻，計時器不再到期，所以畫面的自動再試不會在這一步和下一步之間自己發生；
     * 再把後端撥到同一個時刻，讓之後「過了 N 秒」從兩邊相同的時刻往後推，推滿 N 秒時自動再試一定到期。
     *
     * <p>時鐘不能一直停著：畫面框架讀到資料後是排在 {@code setTimeout(0)} 才更新畫面，時鐘停著時畫面不會換，
     * 拖曳也起不了拖。所以這裡在 {@link WebWorld} 記下「時鐘停住了」，由引發讀取的那一步放開：
     * 「過了 N 秒」快轉完、等那一次看板讀取結束後放開；按「重試」在確認送出看板讀取後放開；
     * 都沒有放開時，場景結束時放開。
     */
    private void pauseBrowserClock(Page page) {
        long browserNow = ((Number) page.evaluate("Date.now()")).longValue() + CLOCK_PAUSE_MARGIN_MS;
        page.clock().pauseAt(browserNow);
        world.clockPausedUntilTriggered(page);
        world.clocks().moveTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(browserNow), ZoneId.systemDefault()));
    }
}
