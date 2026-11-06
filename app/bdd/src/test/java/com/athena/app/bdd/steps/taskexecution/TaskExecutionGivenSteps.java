package com.athena.app.bdd.steps.taskexecution;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.java.zh_tw.假設;

import java.util.List;
import java.util.UUID;

/** 任務執行模組的 Given。 */
public class TaskExecutionGivenSteps {

    /** 這一句自己建的任務用什麼內容與類別：SOP 清單只掛在打掃類任務上。 */
    private static final String TASK_TITLE = "退房清潔";

    private static final String CLEANING_CATEGORY = "打掃";

    private final AppiumWorld world;

    public TaskExecutionGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：把這個 SOP 項標記成跳過，讓 App 以該員工身分登入後停在該任務頁。
     * 權威狀態落地：後端 {@code task_sop_checks} 該項為 skipped。
     *
     * <p>這一句沒有搭配的「名下有以下任務」，所以受測任務由這裡自己建：一張該員工
     * 名下、進行中的打掃任務。跳過走後端正式的 SOP 勾記端點，不直接改資料列，勾記
     * 事件與投影出來的勾記狀態才跟員工自己在畫面上跳過一模一樣。
     *
     * <p>場景點名的 SOP 項不一定在租戶的打掃 SOP 項裡，所以先補一筆再跳過，
     * 作法見 {@link BackendChannel#ensureCleaningSopItemId}。
     */
    @假設("{string} 已在 App 跳過 {string} 的 SOP 項 {string}")
    public void alreadySkippedSop(String staffName, String task, String sopItem) {
        world.backend().ensureCleaningSopItemId(sopItem);
        world.seedTasks(staffName, List.of(new AppiumWorld.TaskFixture(
                task, TASK_TITLE, CLEANING_CATEGORY, null, "進行中", 0, null, null)));

        BackendChannel.CreatedStaff staff = world.staff(staffName);
        world.ensureOnTaskDetail(staff, task);
        UUID taskId = (UUID) world.taskRowByRoomNo(task).get("id");
        world.backend().skipSopItem(taskId, staff, sopItem);
    }
}
