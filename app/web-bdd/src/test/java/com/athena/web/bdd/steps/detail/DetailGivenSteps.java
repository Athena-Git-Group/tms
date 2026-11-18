package com.athena.web.bdd.steps.detail;

import com.athena.bddsupport.BackendChannel.CreatedStaff;
import com.athena.web.bdd.pages.BoardPage;
import com.athena.web.bdd.pages.DetailPanel;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.pages.TriagePage;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Locator;
import io.cucumber.java.zh_tw.假設;
import java.util.UUID;

/** 「單筆詳情」模組的 Given 句型。 */
public class DetailGivenSteps {

    private final WebWorld world;

    private final ShellFrame shell;

    private final BoardPage board;

    private final TriagePage triage;

    private final DetailPanel detail;

    public DetailGivenSteps(WebWorld world) {
        this.world = world;
        this.shell = new ShellFrame(world);
        this.board = new BoardPage(world);
        this.triage = new TriagePage(world);
        this.detail = new DetailPanel(world);
    }

    /**
     * 以這位員工登入（已登入就沿用）並開這一頁，打開該房的詳情面板按「派給{部門}」，
     * 再在浮條上按「復原」，等復原成功、浮條消失後關閉面板。
     * 做完之後這張單回到待派單，歷程上不留這一次派單。
     */
    @假設("{string} 剛在 {string} 把 {string} 派給 {string} 又按了「復原」")
    public void assignedThenUndone(String staffName, String pageName, String roomNo, String dept) {
        CreatedStaff staff = world.staff(staffName);
        if (shell.loggedIn()) {
            shell.openAndWait(pageName);
        } else {
            shell.loginAndOpen(staff, pageName);
        }
        shell.waitUntilLoaded();

        UUID taskId = world.tasks().idOf(roomNo);
        Locator opener = shell.onPage("待派單頁") ? triage.rowOpener(roomNo) : board.card(roomNo);
        detail.openAndWait(taskId, roomNo, () -> shell.press(opener));
        detail.assignAndWait(taskId, dept);
        detail.undoAssignAndWait(taskId);
        detail.closeAndWait();
        shell.waitUntilLoaded();
    }
}
