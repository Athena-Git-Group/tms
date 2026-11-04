package com.athena.app.bdd.steps.taskexecution;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.假設;

/** 任務執行模組的 Given。 */
public class TaskExecutionGivenSteps {

    private final AppiumWorld world;

    public TaskExecutionGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：透過後端測試資料（或先執行一次等效的跳過操作）把該 SOP 項標記為 skipped，
     * App 以該員工身分登入後停在該任務頁。權威狀態落地：後端 {@code task_sop_checks} 該項為
     * skipped。
     */
    @假設("{string} 已在 App 跳過 {string} 的 SOP 項 {string}")
    public void alreadySkippedSop(String staffName, String task, String sopItem) {
        throw new PendingException();
    }
}
