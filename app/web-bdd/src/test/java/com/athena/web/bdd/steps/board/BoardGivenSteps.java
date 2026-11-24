package com.athena.web.bdd.steps.board;

import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebApp;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import io.cucumber.java.zh_tw.假設;
import java.time.Duration;
import java.util.UUID;

public class BoardGivenSteps {

    /**
     * 停住瀏覽器時鐘時，停在瀏覽器此刻再往後這麼多毫秒。
     * 讀時間與下指令之間瀏覽器的時鐘還在走，停的時刻若早於瀏覽器的現在，Playwright 會拒絕往回停。
     */
    private static final Duration CLOCK_PAUSE_MARGIN = Duration.ofMillis(200);

    private final WebWorld world;

    private final ShellFrame shell;

    private final BoardPage board;

    public BoardGivenSteps(WebWorld world) {
        this.world = world;
        this.shell = new ShellFrame(world);
        this.board = new BoardPage(world);
    }

    @假設("{string} 已在總覽看板點了 {string} 數量卡")
    public void clickedCountCard(String staffName, String countCard) {
        ensureOnBoard(staffName);
        filterByCountCard(countCard);
    }

    @假設("{string} 已在總覽看板點了 {string} 數量卡、搜尋房號 {string}")
    public void clickedCountCardAndSearched(String staffName, String countCard, String roomNo) {
        ensureOnBoard(staffName);
        filterByCountCard(countCard);
        board.roomSearch().pressSequentially(roomNo);
        world.page().waitForCondition(() -> roomNo.equals(board.roomSearch().inputValue()));
    }

    @假設("{string} 已把泳道切到 {string}")
    public void switchedLane(String staffName, String lane) {
        ensureOnBoard(staffName);
        shell.press(board.laneOption(lane));
        world.page().waitForCondition(() -> board.laneSelected(lane));
    }

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

    @假設("{string} 打開總覽看板時讀取失敗，之後網路已經恢復")
    public void boardLoadFailedThenRecovered(String staffName) {
        Page page = world.page();
        world.session().loginAs(page, world.staff(staffName));
        world.network().disconnect(page, Network.DESK_BOARD);
        world.open(WebApp.BOARD);
        board.loadFailedTitle().waitFor();
        world.clocks().pauseBrowser(page, CLOCK_PAUSE_MARGIN);
        world.network().passThrough(page, Network.DESK_BOARD);
    }

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
}
