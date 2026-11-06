package com.athena.app.bdd.steps.repair;

import com.athena.app.bdd.AppiumWorld;

import java.time.Duration;

/**
 * 工務單模組的情境內共用狀態與進法。給同一情境的 Given／When／Then 共用兩件事：
 *
 * <ul>
 *   <li><b>報修面板的進法</b>：Given（「選了類別 X，附了 N 張照片」）與 When（「拍 N 張
 *       照片」「選擇類別 X」）都要先站在報修面板上，三處各寫一次就會各自漂移。</li>
 *   <li><b>報修面板是從哪一間房進來的</b>：急件判定的 Then 句型不帶房號，但「必查」
 *       要回頭讀該房今天的訂房快取才驗得到判定理由，只能由進面板的那一步留下來。</li>
 * </ul>
 *
 * <p>Cucumber 以 constructor injection 讓同情境的 step 類別共用同一個實例。
 */
public class RepairContext {

    /** 畫面切換有轉場動畫，用輪詢等待取代固定 sleep。 */
    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(8);

    private final AppiumWorld world;

    private String repairRoomNo;

    public RepairContext(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 確保 App 停在該房的報修面板上（{@code repair.sheet}）。房務報修的進法只有一條：
     * 登入 → 首頁該房的任務卡 → 任務頁 → 報修圖示（{@code task.repair-button}）。
     * 已經停在報修面板時不重複進入——Given 鋪好面板、When 再對同一個面板操作是常見的接法。
     */
    public void openRepairSheet(String staffName, String roomNo) {
        repairRoomNo = roomNo;
        if (!world.driver().findElements(world.byTestId("repair.sheet")).isEmpty()) {
            return;
        }
        world.ensureOnTaskDetail(world.staff(staffName), roomNo);
        world.tapTestId("task.repair-button", SCREEN_TIMEOUT);
        world.waitForTestId("repair.sheet", SCREEN_TIMEOUT);
    }

    /**
     * 本情境的報修面板是從哪一間房進來的。還沒進過報修面板就問，代表劇本漏了帶人進
     * 面板的那一句，直接噴錯而不是回 null 讓後面的查詢查出一個空結果。
     */
    public String repairRoomNo() {
        if (repairRoomNo == null) {
            throw new IllegalStateException(
                    "本情境還沒有進過報修面板——場景是否漏了「在 X 的報修畫面…」這類句子？");
        }
        return repairRoomNo;
    }
}
