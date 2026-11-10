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

    /** 自填說明要湊到指定字數時重複的字（dsl.md 指定用「住」）。 */
    private static final String FILLER_CHARACTER = "住";

    private static final String OTHER_REASON = "其他原因";

    /** 中斷清單裡唯一不送原因、改開工務單表單的那一列。 */
    private static final String NEEDS_MAINTENANCE_REASON = "需要工務協助";

    private final AppiumWorld world;

    public TaskExecutionWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    @當("{string} 在 App 跳過 {string} 的 SOP 項 {string}")
    public void skipSop(String staffName, String task, String sopItem) {
        // 補 SOP 項要排在打開任務頁之前：任務頁一進去就向後端要 SOP 清單，之後補的
        // 項目不會出現在畫面上那一份。
        UUID sopItemId = world.backend().ensureCleaningSopItemId(sopItem);
        world.ensureOnTaskDetail(world.staff(staffName), task);
        world.tapTestId("task.sop." + sopItem + ".skip-button", SCREEN_TIMEOUT);
        waitUntilSkipped(task, sopItemId);
    }

    /**
     * 等後端真的把這一項記成跳過。
     *
     * <p>點下去只是送出請求，後端回應之前畫面上的進度還是舊的。少了這一步，接著的
     * 進度斷言會趕在 App 收到回應之前讀畫面。
     */
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

    /** 輪詢之間歇一下，不要把資料庫打滿。 */
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

    /**
     * 點一個預設原因。
     *
     * <p>點下去就是送出，面板上沒有第二顆確認鈕：預設原因本身就是要送出去的那段文字，
     * 再問一次「確定嗎」只是讓員工在走廊上多按一下。面板是在後端回應之後才關閉的，
     * 所以它消失代表這次操作整個做完了，接下來的斷言讀到的才是更新後的任務頁。
     */
    private void chooseReason(String reason) {
        world.tapTestId("hold.reason." + reason, SCREEN_TIMEOUT);
        world.waitForTestIdGone("hold.sheet", SCREEN_TIMEOUT);
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

    private void typeOtherDeferReason(String staffName, String task, String note) {
        openHoldSheet(staffName, task, "task.defer-button");
        world.tapTestId("hold.reason." + OTHER_REASON, SCREEN_TIMEOUT);
        world.typeInto("hold.other-reason-field", SCREEN_TIMEOUT, note);
    }

    /**
     * 怎麼做：在任務頁點置底主按鈕「恢復處理」。權威狀態落地：成功時後端 {@code tasksResume}
     * （{@code POST /tasks/{id}/resume}）語意落地——已中斷轉為進行中、{@code worked_sec} 保留、
     * {@code hold_reason} 清空；手上已有另一筆進行中的任務時後端回 409 {@code doing_task_exists}，
     * 畫面出現提示且後端不變；離線時擋在送出之前，不進離線佇列、後端不變。回寫：任務頁內容與
     * 畫面提示。accessibility id：{@code task.primary-button}。
     *
     * <p>這一句要真的按下那顆按鈕，不是只檢查按鈕文字——按鈕文字由
     * {@code 任務頁只顯示一個主按鈕，文字為 "{按鈕文字}"} 另外驗。三種落地（成功、被 409 擋下、
     * 離線擋下）走的都是同一個動作：進任務頁、按主按鈕。差別只在後端與畫面的結果，所以這裡
     * 不分支、也不在這一句斷言成敗，由 Then 句各自驗任務頁狀態、畫面提示與離線佇列。
     */
    @當("{string} 在 App 恢復 {string}")
    public void resumeTask(String staffName, String task) {
        world.ensureOnTaskDetail(world.staff(staffName), task);
        world.tapTestId("task.primary-button", SCREEN_TIMEOUT);
    }

    /**
     * 怎麼做：在完成畫面點底部的文字按鈕「回到首頁」（accessibility id
     * {@code celebrate.back-home-button}）。權威狀態落地：不改任何後端狀態，只換畫面。
     * 回寫：換頁後停在的畫面。
     *
     * <p>刻意不點視覺層級最高的實心主按鈕 {@code celebrate.next-button}——那顆是往
     * 下一步走的「前往下一步」，是另一句。
     */
    @當("{string} 在 App 從完成畫面回到首頁")
    public void backHomeFromCelebration(String staffName) {
        // 查一下員工，場景若漏了建員工就在這裡噴錯，而不是在找不到按鈕時才失敗。
        world.staff(staffName);
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);
        world.tapTestId("celebrate.back-home-button", SCREEN_TIMEOUT);
        // 等覆蓋層收起來才算換頁完成，否則接著的「畫面停在首頁」會趕在轉場途中讀畫面。
        world.waitForTestIdGone("celebrate.screen", SCREEN_TIMEOUT);
    }

    /**
     * 怎麼做：把裝置時間與後端 {@code Clock} 一起往前撥該分鐘數，畫面保持開著、不做任何
     * 操作。權威狀態落地：無——時間流逝本身不產生任何後端事件。回寫：畫面上的計時與
     * 超時提示。
     *
     * <p>這是<b>相對</b>推撥：基準是「目前生效的裝置時間」，不是固定的某一刻。場景前面
     * 若已有句子撥過時鐘（「現在時間是」「已中斷 N 分鐘」），就從那個注入值再往前加；
     * 還沒撥過就從真實當下起算。拿真實當下硬當基準的話，前面撥快的那一段會被整個
     * 倒轉回去。
     *
     * <p>撥完<b>刻意不碰畫面</b>：這一句服務的 Rule 是「超時提示跟著目前已做時間走，
     * 不停在進頁那一刻」，受測的就是畫面開著不動時計時自己走不走。重進任務頁或任何
     * 觸發重抓資料的操作，都會讓畫面靠「重新進頁」跟上新時間，Rule 要抓的「停在進頁
     * 那一刻」的 bug 就測不出來了。
     *
     * <p>裝置半邊：注入測試後門的 {@code POST /clock}（{@code TestHooks.injectedClockIso}），
     * 任務頁的計時從這個注入值讀。後端半邊目前「設不進去」，理由見共通 Given
     * 「現在時間是」：跑著的後端用 {@code ClockConfig} 的系統時鐘，沒有任何可供外部
     * 調整的端點（tms 自己的 BDD 是同 JVM 直接撥 {@code MutableClock}，harness 構不到）。
     * 好在這一句之後不做任何操作、時間流逝也不產生後端事件，後端時鐘沒跟上並不影響
     * 這個場景；等後端開出測試用 Clock 端點時，這裡要改成兩邊一起撥。
     */
    @當("過了 {int} 分鐘")
    public void minutesPass(int minutes) {
        OffsetDateTime injected = world.injectedDeviceClock();
        OffsetDateTime base = injected != null ? injected : OffsetDateTime.now();
        OffsetDateTime moment = base.plusMinutes(minutes);
        world.postBackdoorJson("/clock", "{\"isoTime\":\"" + moment + "\"}");
    }

    /**
     * 怎麼做：在完成畫面點實心主按鈕（accessibility id {@code celebrate.next-button}），
     * 按鈕上寫的是下一間、回到某一間或去待認領，依 {@code tasksComplete} 回應的
     * {@code nextStep.kind} 而定。權威狀態落地：不改任何後端狀態，只換畫面。
     * 回寫：換頁後停在的畫面。
     *
     * <p>刻意不點 {@code celebrate.back-home-button}——那是底部回首頁的文字按鈕、是
     * 「從完成畫面回到首頁」那一句。兩顆都在同一張覆蓋層上，點錯的話這一句會停在首頁，
     * 而要驗的「接力到下一個去向」整段就被跳過了。
     *
     * <p>按鈕文字會隨去向變（「下一間 308」／「去待認領」），所以這裡用固定的
     * accessibility id 定位，不靠文字找按鈕；去向是哪一種由 Then 句各自驗，這裡不分支。
     */
    @當("{string} 在 App 從完成畫面前往下一步")
    public void nextStepFromCelebration(String staffName) {
        // 查一下員工，場景若漏了建員工就在這裡噴錯，而不是在找不到按鈕時才失敗。
        world.staff(staffName);
        world.waitForTestId("celebrate.screen", SCREEN_TIMEOUT);
        world.tapTestId("celebrate.next-button", SCREEN_TIMEOUT);
        // 等覆蓋層收起來才算換頁完成，否則接著的畫面斷言會讀到底下那張剛完成的任務頁。
        world.waitForTestIdGone("celebrate.screen", SCREEN_TIMEOUT);
    }
}
