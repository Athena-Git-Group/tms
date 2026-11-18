package com.athena.web.bdd.steps.answer;

import com.athena.bddsupport.BackendChannel;
import com.athena.web.bdd.pages.AnswerPanel;
import com.athena.web.bdd.pages.ShellFrame;
import com.athena.web.bdd.support.Network;
import com.athena.web.bdd.support.WebWorld;
import com.microsoft.playwright.Page;
import io.cucumber.java.zh_tw.假設;

/** 「當場回答」模組的 Given 句型。 */
public class AnswerGivenSteps {

    /** 撞單那一句沒說回覆框裡打了什麼，規格只要求先輸入回覆；用這一句。 */
    static final String CONFLICT_REPLY = "馬上請同仁過去";

    /** 撞單那一句沒說是誰先派走、派給哪個部門時，照規格的預設：林佩君派給工務。 */
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

    /** 還沒登入時先以這位員工登入並開待派單頁，再按該房那一列的「當場回答…」，等面板與常用回覆都載入完成。 */
    @假設("{string} 開著 {string} 的當場回答面板")
    public void answerPanelOpen(String staffName, String roomNo) {
        openPanel(staffName, roomNo);
    }

    /** 同上，再把這段文字照字面（前後空白也照打）打進回覆框。 */
    @假設("{string} 開著 {string} 的當場回答面板，回覆框裡是 {string}")
    public void answerPanelOpenWithReply(String staffName, String roomNo, String reply) {
        openPanel(staffName, roomNo);
        answer.typeIntoReply(reply);
    }

    /** 同第一句，再點那一則常用回覆。 */
    @假設("{string} 開著 {string} 的當場回答面板，已點常用回覆 {string}")
    public void answerPanelOpenWithQuickReply(String staffName, String roomNo, String title) {
        openPanel(staffName, roomNo);
        answer.pickQuickReply(title);
    }

    /** 在待派單頁打開面板、輸入回覆、按「回覆並結案」，等後端收下、面板關閉。 */
    @假設("{string} 已當場回答 {string}，回覆是 {string}")
    public void alreadyAnswered(String staffName, String roomNo, String reply) {
        openPanel(staffName, roomNo);
        answer.typeIntoReply(reply);
        answer.submitAndWaitClosed();
    }

    /**
     * 打開面板並輸入回覆，先讓這個分頁停止跟上後端，再由同事在後端把單派走，最後才按「回覆並結案」，
     * 等撞單的對話框出現。暫停一定要在同事派單之前，否則畫面可能先收到即時訊號，就撞不到單了。
     *
     * <p>對話框出現之後，這一步讓分頁重新跟上後端。
     * 接下來的句子會檢查清單上已經沒有這張單，畫面一直停著的話，那些檢查會因為測試的攔截而失敗，不是因為產品。
     */
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

    /** 打開面板並輸入回覆，讓送出的請求斷線後按「回覆並結案」；「沒有送出」的對話框出現之後取消攔截。 */
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

    /** 沒登入就先登入並開待派單頁，已登入但不在待派單頁就開過去；再從該房那一列打開面板。 */
    private void openPanel(String staffName, String roomNo) {
        if (!shell.loggedIn()) {
            shell.loginAndOpen(world.staff(staffName), "待派單頁");
        } else if (!shell.onPage("待派單頁")) {
            shell.openAndWait("待派單頁");
        }
        answer.openFromTriageRow(roomNo);
    }
}
