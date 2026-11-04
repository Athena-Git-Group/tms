package com.athena.app.bdd.steps.system;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.那麼;

/** 系統層模組的 Then。 */
public class SystemThenSteps {

    private final AppiumWorld world;

    public SystemThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：App 本機佇列有這筆完成動作；任務卡／任務頁有待同步標示；後端該任務狀態還未
     * 改變。恢復連線後自動送出，以同一個 {@code Idempotency-Key} 呼叫 {@code tasksComplete}。
     */
    @那麼("{string} 的完成已保存在手機，畫面標示待同步")
    public void completionSavedOnPhonePendingSync(String task) {
        throw new PendingException();
    }

    /**
     * 必查：畫面頂部離線列顯示該文案與待同步筆數；筆數與 App 本機待送出佇列筆數一致
     * （涵蓋待送出的工務單與離線完成的任務）。accessibility id：TODO（離線列節點尚未命名）。
     */
    @那麼("離線列顯示 {string} 與待同步筆數 {int}")
    public void offlineBarShowsTextAndPendingCount(String text, int pendingCount) {
        throw new PendingException();
    }

    /**
     * 必查：畫面頂部連線異常列顯示該文案；期間畫面資料每 5 秒重新輪詢一次，不彈離線
     * Alert、不封鎖操作（與離線模式不同）。accessibility id：TODO（連線異常列節點尚未命名）。
     */
    @那麼("連線異常列顯示 {string}")
    public void connectionIssueBarShowsText(String text) {
        throw new PendingException();
    }
}
