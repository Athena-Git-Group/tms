package com.athena.app.bdd.steps.taskexecution;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.當;

/** 任務執行模組的 When。 */
public class TaskExecutionWhenSteps {

    private final AppiumWorld world;

    public TaskExecutionWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：在任務頁 SOP 清單點該項的「跳過」。權威狀態落地：後端 {@code tasksSopCheck}
     * （{@code POST /tasks/{id}/sop-check}，result=skipped）成功，{@code task_sop_checks}
     * 該項為 skipped；不會阻擋後續完成操作。回寫：SOP 清單畫面。
     */
    @當("{string} 在 App 跳過 {string} 的 SOP 項 {string}")
    public void skipSop(String staffName, String task, String sopItem) {
        throw new PendingException();
    }

    /**
     * 怎麼做：在任務頁點「延後」開啟延後面板，點該預設原因，再點確認。權威狀態落地：等同
     * 後端 {@code tasksDefer} 語意，{@code hold_reason}＝該原因文字，{@code deferred_by_self}=true。
     * 回寫：任務頁。
     */
    @當("{string} 在 App 為 {string} 選擇延後原因 {string} 並確認")
    public void chooseDeferReason(String staffName, String task, String reason) {
        throw new PendingException();
    }

    /**
     * 怎麼做：在任務頁點「中斷」開啟中斷面板，點該預設原因，再點確認。權威狀態落地：等同
     * 後端 {@code tasksInterrupt} 語意，{@code hold_reason}＝該原因文字。回寫：任務頁。
     */
    @當("{string} 在 App 為 {string} 選擇中斷原因 {string} 並確認")
    public void chooseInterruptReason(String staffName, String task, String reason) {
        throw new PendingException();
    }

    /**
     * 怎麼做：在延後面板點「其他原因」，輸入該說明文字，點確認。權威狀態落地：等同後端
     * {@code tasksDefer}，{@code hold_reason}＝該說明文字。回寫：任務頁。「其他原因」是
     * feature 文字裡的固定字面（corner bracket「」，不是 Cucumber string 參數）。
     */
    @當("{string} 在 App 為 {string} 選擇延後原因「其他原因」，填入 {string} 並確認")
    public void chooseOtherDeferReason(String staffName, String task, String note) {
        throw new PendingException();
    }

    /**
     * 怎麼做：在延後面板點「其他原因」，輸入指定字數的說明文字，不點確認。回寫：延後面板
     * 目前的送出鈕狀態與字數提示。
     */
    @當("{string} 在 App 為 {string} 的延後面板選「其他原因」並輸入 {int} 字的說明")
    public void typeOtherDeferReasonChars(String staffName, String task, int charCount) {
        throw new PendingException();
    }
}
