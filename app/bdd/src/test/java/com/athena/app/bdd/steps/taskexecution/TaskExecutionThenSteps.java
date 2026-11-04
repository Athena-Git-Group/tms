package com.athena.app.bdd.steps.taskexecution;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.那麼;

/** 任務執行模組的 Then。 */
public class TaskExecutionThenSteps {

    private final AppiumWorld world;

    public TaskExecutionThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：任務頁的已做時間顯示該分鐘數；等待一段時間後數字不變。後端累計時間一致。
     * accessibility id：{@code task.hero.paused-elapsed}。
     */
    @那麼("任務頁顯示已做 {int} 分鐘，且計時沒有在走")
    public void showsWorkedMinutesPausedTimer(int minutes) {
        throw new PendingException();
    }

    /**
     * 必查：以 accessibility id {@code task.step-track} 定位，打掃類步驟條
     * （已接→進行中→完成→查房／免查房）目前標示的步驟與後端 {@code tasks.state}／
     * {@code inspection} 對照一致。
     */
    @那麼("任務頁顯示打掃步驟條，目前停在 {string}")
    public void showsCleaningStepTrack(String step) {
        throw new PendingException();
    }

    /**
     * 必查：以 accessibility id {@code task.step-track} 定位，備品類步驟條
     * （已接→進行中→送達）目前標示的步驟與後端 {@code tasks.state} 對照一致。
     */
    @那麼("任務頁顯示備品步驟條，目前停在 {string}")
    public void showsAmenityStepTrack(String step) {
        throw new PendingException();
    }

    /**
     * 必查：以 accessibility id {@code task.primary-button} 定位，畫面上只有一個置底主按鈕，
     * 文字與目前狀態對照表相符；延後／中斷是次要按鈕，不算在此主按鈕判定內。
     */
    @那麼("任務頁只顯示一個主按鈕，文字為 {string}")
    public void showsOnlyOnePrimaryButton(String buttonText) {
        throw new PendingException();
    }

    /**
     * 必查：任務頁 SOP 進度文字（{@code x / y · 跳過不擋流程}）包含該關鍵字；勾選與跳過都
     * 計入 x，y 為 SOP 總項數。accessibility id：TODO（SOP 進度文字節點尚未在 ui-plan.md
     * 逐一命名）。
     */
    @那麼("{string} 的 SOP 進度顯示 {string}")
    public void sopProgressShows(String task, String keyword) {
        throw new PendingException();
    }

    /**
     * 必查：任務頁計時 hero 區顯示該文字；超時分鐘數＝目前已做分鐘數減去預估分鐘，只在超過
     * 時顯示，純提示不觸發推播。accessibility id：TODO（計時 hero 區超時文字節點尚未在
     * ui-plan.md 逐一命名）。
     */
    @那麼("任務頁顯示 {string}")
    public void taskDetailShows(String text) {
        throw new PendingException();
    }

    /**
     * 必查：完成慶祝頁（accessibility id {@code celebrate.title} 所在畫面）顯示的實際與預估
     * 分鐘數，與後端 {@code tasks.worked_sec} 換算分鐘、預估分鐘一致。
     */
    @那麼("完成慶祝頁顯示實際 {int} 分、預估 {int} 分")
    public void celebrationShowsActualAndEstimate(int actualMinutes, int estimatedMinutes) {
        throw new PendingException();
    }

    /**
     * 必查：透過後端測試資料查核，該員工的 {@code outbox} 沒有任何 kind {@code push} 的資料；
     * 用於確認超時提示這種純 App 端顯示沒有連帶觸發推播。
     */
    @那麼("後端沒有對 {string} 送出任何推播")
    public void noPushSentTo(String staffName) {
        throw new PendingException();
    }

    /**
     * 必查：任務頁狀態列顯示的停住原因文字與後端 {@code tasks.hold_reason} 一致。
     * accessibility id：TODO（狀態列停住原因節點尚未在 ui-plan.md 逐一命名）。
     */
    @那麼("任務頁顯示停住原因 {string}")
    public void showsHoldReason(String reason) {
        throw new PendingException();
    }

    /** 必查：延後面板送出鈕的可互動狀態符合；禁用時無法真的送出。accessibility id：TODO。 */
    @那麼("延後面板的送出鈕為 {string}")
    public void deferPanelSubmitButtonState(String enabledOrDisabled) {
        throw new PendingException();
    }

    /** 必查：延後面板「其他原因」欄位下方即時字數提示等於該文字。accessibility id：TODO。 */
    @那麼("延後面板顯示字數 {string}")
    public void deferPanelCharCount(String countText) {
        throw new PendingException();
    }
}
