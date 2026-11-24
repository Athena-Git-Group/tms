package com.athena.web.bdd.steps.answer;

import com.athena.bddsupport.BackendChannel;
import com.athena.web.bdd.pages.AnswerPanel;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import io.cucumber.java.zh_tw.假設;

public class AnswerGivenSteps {

    static final String CONFLICT_REPLY = "馬上請同仁過去";

    static final String CONFLICT_COLLEAGUE = "林佩君";

    static final String CONFLICT_DEPT = "工務";

    private final WebWorld world;

    private final ShellFrame shell;

    private final AnswerPanel answer;

    public AnswerGivenSteps(WebWorld world) {
        this.world = world;
        this.shell = new ShellFrame(world);
        this.answer = new AnswerPanel(world);
    }

    @假設("{string} 開著 {string} 的當場回答面板")
    public void answerPanelOpen(String staffName, String roomNo) {
        openPanel(staffName, roomNo);
    }

    @假設("{string} 開著 {string} 的當場回答面板，回覆框裡是 {string}")
    public void answerPanelOpenWithReply(String staffName, String roomNo, String reply) {
        openPanel(staffName, roomNo);
        answer.typeIntoReply(reply);
    }

    @假設("{string} 開著 {string} 的當場回答面板，已點常用回覆 {string}")
    public void answerPanelOpenWithQuickReply(String staffName, String roomNo, String title) {
        openPanel(staffName, roomNo);
        answer.pickQuickReply(title);
    }

    @假設("{string} 已當場回答 {string}，回覆是 {string}")
    public void alreadyAnswered(String staffName, String roomNo, String reply) {
        openPanel(staffName, roomNo);
        answer.typeIntoReply(reply);
        answer.submitAndWaitClosed();
    }

    @假設("{string} 送出 {string} 的當場回答時撞單，「這單已經被派走了」的提示開著")
    public void answerConflictNoticeOpen(String staffName, String roomNo) {
        openPanel(staffName, roomNo);
        answer.typeIntoReply(CONFLICT_REPLY);
        Page page = world.page();
        world.network().pauseLiveUpdates(page);
        world.backend().deskAssign(world.tasks().idOf(roomNo), world.staff(CONFLICT_COLLEAGUE),
                BackendChannel.dept(CONFLICT_DEPT), null);
        shell.press(answer.submitButton());
        answer.notice("這單已經被派走了").waitFor();
        world.network().resumeLiveUpdates(page);
    }

    @假設("{string} 送出 {string} 的當場回答 {string} 時網路中斷，「沒有送出」的提示開著")
    public void answerOfflineNoticeOpen(String staffName, String roomNo, String reply) {
        openPanel(staffName, roomNo);
        answer.typeIntoReply(reply);
        Page page = world.page();
        world.network().disconnect(page, Network.DESK_ANSWER);
        shell.press(answer.submitButton());
        answer.notice("沒有送出").waitFor();
        world.network().passThrough(page, Network.DESK_ANSWER);
    }

    private void openPanel(String staffName, String roomNo) {
        if (!shell.loggedIn()) {
            shell.loginAndOpen(world.staff(staffName), "待派單頁");
        } else if (!shell.onPage("待派單頁")) {
            shell.openAndWait("待派單頁");
        }
        answer.openFromTriageRow(roomNo);
    }
}
