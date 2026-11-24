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

public class CommonWhenSteps {

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

    @當("{string} 登入後打開 {string}，資料還沒回來")
    public void loginAndOpenBeforeData(String staffName, String pageName) {
        if (!shell.loggedIn()) {
            world.session().loginAs(world.staff(staffName));
        }
        world.open(ShellFrame.pathOf(pageName));
        shell.waitForPage(pageName);
        world.network().waitUntilHeld(Network.DESK_BOARD);
    }

    @當("{string} 按 {string}")
    public void press(String staffName, String name) {
        Locator target = shell.topmostPressable(name);
        if (!world.clocks().browserPaused()) {
            shell.pressAndSettle(target);
            return;
        }
        shell.settleAfter(() -> world.clocks().releaseIfTriggered(() -> pressAndSeeBoardRead(target)));
    }

    private boolean pressAndSeeBoardRead(Locator target) {
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
                return true;
            } catch (TimeoutError notSent) {
                return false;
            }
        } finally {
            page.offRequest(onRequest);
        }
    }

    @當("{string} 打開 {string} 的詳情面板")
    public void openDetail(String staffName, String roomNo) {
        Locator target = shell.onPage("待派單頁") ? triage.rowOpener(roomNo) : board.card(roomNo);
        detail.openAndWait(world.tasks().idOf(roomNo), roomNo, () -> shell.press(target));
    }

    @當("{string} 從側欄換到 {string}")
    public void switchFromSidebar(String staffName, String pageName) {
        shell.goToFromSidebar(pageName);
        shell.waitUntilLoaded();
    }

    @當("{string} 在待派單頁按 {string} 這一列的 {string}")
    public void pressRowAction(String staffName, String roomNo, String action) {
        shell.pressAndSettle(triage.action(roomNo, action));
        if (action.strip().startsWith("當場回答")) {
            answer.panel().waitFor();
        }
    }

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

    private void openPage(CreatedStaff staff, String pageName) {
        if (shell.loggedIn()) {
            shell.openAndWait(pageName);
        } else {
            shell.loginAndOpen(staff, pageName);
        }
        shell.waitUntilLoaded();
    }

    private void openFirstTriageDetail() {
        Entry first = world.tasks().firstIn("待派單").orElseThrow(() -> new IllegalStateException(
                "要打開面板，案例裡至少要有一張狀況是「待派單」的單"));
        detail.openAndWait(first.taskId(), first.roomNo(), () -> shell.press(board.card(first.roomNo())));
    }
}
