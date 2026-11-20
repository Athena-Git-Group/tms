package com.athena.web.bdd.steps.common;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.web.bdd.pages.AccountPanel;
import com.athena.web.bdd.pages.AnswerPanel;
import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.TriagePage;
import com.athena.web.bdd.steps.guest.realtime.RealtimeWhenSteps;
import com.athena.web.bdd.support.GuestScenario;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.ScenarioTasks.Entry;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.TimeoutError;
import io.cucumber.java.zh_tw.當;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** 跨模組共用的 When 句型。 */
public class CommonWhenSteps {

    /** 時鐘停住時按下去之後，最多等這麼久看它有沒有送出看板讀取。 */
    private static final double BOARD_READ_AFTER_PRESS_TIMEOUT_MS = 5_000;

    private final WebWorld world;

    private final ShellFrame shell;

    private final BoardPage board;

    private final TriagePage triage;

    private final DetailPanel detail;

    private final AnswerPanel answer;

    private final AccountPanel account;

    private final GuestScenario guestScenario;

    private final RealtimeWhenSteps guestRealtime;

    public CommonWhenSteps(WebWorld world, GuestScenario guestScenario, RealtimeWhenSteps guestRealtime) {
        this.world = world;
        this.guestScenario = guestScenario;
        this.guestRealtime = guestRealtime;
        this.shell = new ShellFrame(world);
        this.board = new BoardPage(world);
        this.triage = new TriagePage(world);
        this.detail = new DetailPanel(world);
        this.answer = new AnswerPanel(world);
        this.account = new AccountPanel(world);
    }

    /**
     * 以這位員工登入（已登入就沿用）並開這一頁，等資料載入完成、沒有骨架。
     * 寫面板時先開總覽看板：詳情面板與當場回答面板開的是案例裡第一張待派單，帳號面板從側欄的帳號卡打開。
     */
    @當("{string} 登入後打開 {string}")
    public void loginAndOpen(String staffName, String target) {
        CreatedStaff staff = world.staff(staffName);
        switch (target.strip()) {
            case "詳情面板" -> {
                openPage(staff, "總覽看板");
                openFirstTriageDetail();
            }
            case "當場回答面板" -> {
                openPage(staff, "總覽看板");
                openFirstTriageDetail();
                shell.pressAndSettle(detail.button("當場回答…"));
                answer.panel().waitFor();
            }
            case "帳號面板" -> {
                openPage(staff, "總覽看板");
                account.openFromSidebar(staff.name());
            }
            default -> openPage(staff, target);
        }
    }

    /**
     * 同上，但不等看板資料：外框畫出來、看板資料的請求已經發出而且還沒有回應就往下走。
     * 案例在這之前會先把看板資料扣住，所以這裡等到請求真的被扣住，才確定畫面正在等資料。
     */
    @當("{string} 登入後打開 {string}，資料還沒回來")
    public void loginAndOpenBeforeData(String staffName, String pageName) {
        if (!shell.loggedIn()) {
            world.session().loginAs(world.staff(staffName));
        }
        world.open(ShellFrame.pathOf(pageName));
        shell.waitForPage(pageName);
        world.network().waitUntilHeld(Network.DESK_BOARD);
    }

    /**
     * 在最上面那一層（對話框、面板、頁面的順序）按名稱相符、看得到的那一顆，
     * 等這一下引發的請求與畫面變化結束。點了沒有反應的入口不會多等。
     */
    @當("{string} 按 {string}")
    public void press(String staffName, String name) {
        Locator target = shell.topmostPressable(name);
        if (!world.clockPaused()) {
            shell.pressAndSettle(target);
            return;
        }
        shell.settleAfter(() -> pressThenResumeClock(target));
    }

    /**
     * 前置停住了瀏覽器時鐘（例如讀取失敗後的「重試」案例）時用：按下去，確認這一下真的送出了看板讀取，再放開時鐘。
     * 不放開的話，畫面框架讀到資料後排在 {@code setTimeout(0)} 的更新不會跑，畫面永遠不換。
     * 等不到看板讀取時不放開，讓時鐘停到場景結束：放開的話，畫面自己的自動再試會補上，結果句就分不出是不是按了才讀的。
     */
    private void pressThenResumeClock(Locator target) {
        Page page = world.page();
        AtomicBoolean boardReadSent = new AtomicBoolean();
        Consumer<Request> onRequest = request -> {
            if (Network.DESK_BOARD.method().equals(request.method())
                    && request.url().split("[?#]")[0].endsWith(Network.DESK_BOARD.path())) {
                boardReadSent.set(true);
            }
        };
        page.onRequest(onRequest);
        try {
            shell.press(target);
            try {
                page.waitForCondition(boardReadSent::get,
                        new Page.WaitForConditionOptions().setTimeout(BOARD_READ_AFTER_PRESS_TIMEOUT_MS));
            } catch (TimeoutError notSent) {
                return;
            }
            world.resumePausedClock();
        } finally {
            page.offRequest(onRequest);
        }
    }

    /** 總覽看板點該房的卡片；待派單頁點該房那一列動作以外的地方。等面板滑出並載入完成。 */
    @當("{string} 打開 {string} 的詳情面板")
    public void openDetail(String staffName, String roomNo) {
        Locator target = shell.onPage("待派單頁") ? triage.rowOpener(roomNo) : board.card(roomNo);
        detail.openAndWait(world.tasks().idOf(roomNo), roomNo, () -> shell.press(target));
    }

    /** 側欄收起時先打開，再點那一頁，等那一頁載入完成。 */
    @當("{string} 從側欄換到 {string}")
    public void switchFromSidebar(String staffName, String pageName) {
        shell.goToFromSidebar(pageName);
        shell.waitUntilLoaded();
    }

    /**
     * 在待派單頁按該房那一列的動作。派單時等請求結束與畫面變化；
     * 當場回答時等面板打開、常用回覆載入完成。請求本身由 {@code Network} 記下，結果由後續的 Then 讀後端確認。
     */
    @當("{string} 在待派單頁按 {string} 這一列的 {string}")
    public void pressRowAction(String staffName, String roomNo, String action) {
        shell.pressAndSettle(triage.action(roomNo, action));
        if (action.strip().startsWith("當場回答")) {
            answer.panel().waitFor();
        }
    }

    /**
     * 以房務、工務同仁或主管的身分直接在後端做這個動作，等後端回應成功。
     * 櫃檯畫面靠即時訊號自己更新，這裡不碰畫面、不重新整理。
     * 住客掃碼頁的規格有一字不差的句子，第三個參數是需求標題，在住客場景交給住客的即時更新步驟。
     */
    @當("{string} 在手機上{word} {string}")
    public void actOnPhone(String staffName, String action, String roomNo) {
        if (guestScenario.active()) {
            guestRealtime.staffActs(staffName, action, roomNo);
            return;
        }
        CreatedStaff staff = world.staff(staffName);
        UUID taskId = world.tasks().idOf(roomNo);
        switch (action) {
            case "認領" -> world.backend().postTaskAction(taskId, "claim", staff);
            case "開始" -> world.backend().postTaskAction(taskId, "start", staff);
            case "完成" -> world.backend().postTaskAction(taskId, "complete", staff);
            case "查房通過" -> world.backend().passInspectionAs(taskId, staff);
            default -> throw new IllegalArgumentException(
                    "手機上的動作只接受認領、開始、完成、查房通過，收到「" + action + "」");
        }
    }

    /** 已登入就沿用登入狀態直接開這一頁，否則先放進登入狀態；都等看板資料回來、骨架消失。 */
    private void openPage(CreatedStaff staff, String pageName) {
        if (shell.loggedIn()) {
            shell.openAndWait(pageName);
        } else {
            shell.loginAndOpen(staff, pageName);
        }
        shell.waitUntilLoaded();
    }

    /** 在總覽看板點案例裡第一張待派單的卡片，等詳情面板載入完成。 */
    private void openFirstTriageDetail() {
        Entry first = world.tasks().firstIn("待派單").orElseThrow(() -> new IllegalStateException(
                "要打開面板，案例裡至少要有一張狀況是「待派單」的單"));
        detail.openAndWait(first.taskId(), first.roomNo(), () -> shell.press(board.card(first.roomNo())));
    }
}
