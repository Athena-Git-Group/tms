package com.athena.app.bdd.steps.taskexecution;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TaskExecutionWhenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    private static final String FILLER_CHARACTER = "住";

    private static final String OTHER_REASON = "其他原因";

    private static final String NEEDS_MAINTENANCE_REASON = "需要工務協助";

    private final AppiumWorld world;

    public TaskExecutionWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    @當("{string} 在 App 跳過 {string} 的 SOP 項 {string}")
    public void skipSop(String staffName, String task, String sopItem) {
        UUID sopItemId = world.backend().ensureCleaningSopItemId(sopItem);
        world.ensureOnTaskDetail(world.staff(staffName), task);
        world.tapTestId("task.sop." + sopItem + ".skip-button", SCREEN_TIMEOUT);
        waitUntilSkipped(task, sopItemId);
    }

    private void waitUntilSkipped(String task, UUID sopItemId) {
        UUID taskId = (UUID) world.taskRowByRoomNo(task).get("id");
        long deadline = System.nanoTime() + SCREEN_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            List<Map<String, Object>> rows = world.backend().queryRows(
                    """
                    SELECT c.skipped FROM task_sop_checks c
                      JOIN sop_items s ON s.tenant_id = c.tenant_id AND s.item_key = c.item_key
                     WHERE c.tenant_id = ? AND c.task_id = ? AND s.id = ?
                    """, world.backend().ensureTenant(), taskId, sopItemId);
            if (!rows.isEmpty() && Boolean.TRUE.equals(rows.get(0).get("skipped"))) {
                return;
            }
            sleepBriefly();
        }
        throw new AssertionError("後端沒有把這一項 SOP 記成跳過：task=" + taskId + "，sopItem=" + sopItemId);
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待後端落地時被中斷", e);
        }
    }

    @當("{string} 在 App 為 {string} 選擇延後原因 {string} 並確認")
    public void chooseDeferReason(String staffName, String task, String reason) {
        openHoldSheet(staffName, task, "task.defer-button");
        chooseReason(reason);
    }

    @當("{string} 在 App 為 {string} 選擇中斷原因 {string} 並確認")
    public void chooseInterruptReason(String staffName, String task, String reason) {
        openHoldSheet(staffName, task, "task.interrupt-button");
        chooseReason(reason);
    }

    @當("{string} 在 App 為 {string} 選擇延後原因「其他原因」，填入 {string} 並確認")
    public void chooseOtherDeferReason(String staffName, String task, String note) {
        typeOtherDeferReason(staffName, task, note);
        confirm();
    }

    @當("{string} 在 App 為 {string} 的延後面板選「其他原因」並輸入 {int} 字的說明")
    public void typeOtherDeferReasonChars(String staffName, String task, int charCount) {
        typeOtherDeferReason(staffName, task, FILLER_CHARACTER.repeat(charCount));
    }

    @當("{string} 在 App 為 {string} 的中斷面板選「需要工務協助」")
    public void chooseNeedsMaintenance(String staffName, String task) {
        openHoldSheet(staffName, task, "task.interrupt-button");
        world.tapTestId("hold.reason." + NEEDS_MAINTENANCE_REASON, SCREEN_TIMEOUT);
    }

    private void openHoldSheet(String staffName, String task, String entryTestId) {
        world.ensureOnTaskDetail(world.staff(staffName), task);
        world.tapTestId(entryTestId, SCREEN_TIMEOUT);
        world.waitForTestId("hold.sheet", SCREEN_TIMEOUT);
    }

    private void chooseReason(String reason) {
        world.tapTestId("hold.reason." + reason, SCREEN_TIMEOUT);
        world.waitForTestIdGone("hold.sheet", SCREEN_TIMEOUT);
    }

    private void confirm() {
        world.tapTestId("hold.confirm-button", SCREEN_TIMEOUT);
        world.waitForTestIdGone("hold.sheet", SCREEN_TIMEOUT);
    }

    private void typeOtherDeferReason(String staffName, String task, String note) {
        openHoldSheet(staffName, task, "task.defer-button");
        world.tapTestId("hold.reason." + OTHER_REASON, SCREEN_TIMEOUT);
        world.typeInto("hold.other-reason-field", SCREEN_TIMEOUT, note);
    }

    @當("{string} 在 App 恢復 {string}")
    public void resumeTask(String staffName, String task) {
        world.ensureOnTaskDetail(world.staff(staffName), task);
        world.tapTestId("task.primary-button", SCREEN_TIMEOUT);
    }

    @當("{string} 在 App 從完成畫面回到首頁")
    public void backHomeFromCelebration(String staffName) {
        world.staff(staffName);
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);
        world.tapTestId("celebrate.back-home-button", SCREEN_TIMEOUT);
        world.waitForTestIdGone("celebrate.screen", SCREEN_TIMEOUT);
    }

    @當("過了 {int} 分鐘")
    public void minutesPass(int minutes) {
        OffsetDateTime injected = world.injectedDeviceClock();
        OffsetDateTime base = injected != null ? injected : OffsetDateTime.now();
        OffsetDateTime moment = base.plusMinutes(minutes);
        world.postBackdoorJson("/clock", "{\"isoTime\":\"" + moment + "\"}");
    }

    @當("{string} 在 App 從完成畫面前往下一步")
    public void nextStepFromCelebration(String staffName) {
        world.staff(staffName);
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);
        world.tapTestId("celebrate.next-button", SCREEN_TIMEOUT);
        world.waitForTestIdGone("celebrate.screen", SCREEN_TIMEOUT);
    }
}
