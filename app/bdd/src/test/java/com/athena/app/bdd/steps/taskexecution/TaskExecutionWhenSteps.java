package com.athena.app.bdd.steps.taskexecution;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;

/** 任務執行模組的 When。 */
public class TaskExecutionWhenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    /** 自填說明要湊到指定字數時重複的字（dsl.md 指定用「住」）。 */
    private static final String FILLER_CHARACTER = "住";

    private static final String OTHER_REASON = "其他原因";

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
        openHoldSheet(staffName, task, "task.defer-button");
        chooseReasonAndConfirm(reason);
    }

    /**
     * 怎麼做：在任務頁點「中斷」開啟中斷面板，點該預設原因，再點確認。權威狀態落地：等同
     * 後端 {@code tasksInterrupt} 語意，{@code hold_reason}＝該原因文字。回寫：任務頁。
     */
    @當("{string} 在 App 為 {string} 選擇中斷原因 {string} 並確認")
    public void chooseInterruptReason(String staffName, String task, String reason) {
        openHoldSheet(staffName, task, "task.interrupt-button");
        chooseReasonAndConfirm(reason);
    }

    /**
     * 怎麼做：在延後面板點「其他原因」，輸入該說明文字，點確認。權威狀態落地：等同後端
     * {@code tasksDefer}，{@code hold_reason}＝該說明文字。回寫：任務頁。「其他原因」是
     * feature 文字裡的固定字面（corner bracket「」，不是 Cucumber string 參數）。
     */
    @當("{string} 在 App 為 {string} 選擇延後原因「其他原因」，填入 {string} 並確認")
    public void chooseOtherDeferReason(String staffName, String task, String note) {
        typeOtherDeferReason(staffName, task, note);
        confirm();
    }

    /**
     * 怎麼做：在延後面板點「其他原因」，輸入指定字數的說明文字，不點確認。回寫：延後面板
     * 目前的送出鈕狀態與字數提示。
     */
    @當("{string} 在 App 為 {string} 的延後面板選「其他原因」並輸入 {int} 字的說明")
    public void typeOtherDeferReasonChars(String staffName, String task, int charCount) {
        typeOtherDeferReason(staffName, task, FILLER_CHARACTER.repeat(charCount));
    }

    /** 進該房的任務頁，點延後或中斷的入口，等面板出現。 */
    private void openHoldSheet(String staffName, String task, String entryTestId) {
        world.ensureOnTaskDetail(world.staff(staffName), task);
        world.tapTestId(entryTestId, SCREEN_TIMEOUT);
        world.waitForTestId("hold.sheet", SCREEN_TIMEOUT);
    }

    /** 選一個預設原因後送出。 */
    private void chooseReasonAndConfirm(String reason) {
        world.tapTestId("hold.reason." + reason, SCREEN_TIMEOUT);
        confirm();
    }

    /**
     * 送出並等到面板收起來。
     *
     * <p>面板是在後端回應之後才關閉的，所以它消失代表這次操作整個做完了，接下來的
     * 斷言讀到的才是更新後的任務頁。少了這一步，斷言會趕在 App 收到回應之前讀畫面。
     */
    private void confirm() {
        world.tapTestId("hold.confirm-button", SCREEN_TIMEOUT);
        world.waitForTestIdGone("hold.sheet", SCREEN_TIMEOUT);
    }

    /** 開延後面板、選「其他原因」、把說明打進去；送不送出由呼叫端決定。 */
    private void typeOtherDeferReason(String staffName, String task, String note) {
        openHoldSheet(staffName, task, "task.defer-button");
        world.tapTestId("hold.reason." + OTHER_REASON, SCREEN_TIMEOUT);
        world.typeInto("hold.other-reason-field", SCREEN_TIMEOUT, note);
    }
}
