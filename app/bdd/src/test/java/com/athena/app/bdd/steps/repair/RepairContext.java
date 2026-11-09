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

    /** 動作選單的進場動畫約 0.26 秒，等它站定再點。 */
    private static final Duration MENU_TIMEOUT = Duration.ofSeconds(3);

    private static final String PHOTO_MENU_CAMERA = "photoMenu.camera-button";

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
     * 加一張照片到目前這張表單上。
     *
     * <p>點「加入照片」會先跳出 iOS 動作選單問要拍照還是從相簿選，所以每一個
     * 要照片的劇本都得點兩下。兩下分散在各個 step 裡各寫一次，選單日後改了
     * 就得在六個地方各改一次，所以集中在這裡。
     *
     * @param addButtonId 該畫面「加入照片」的 accessibility id；報修面板與完修
     *                    回報面板的前綴不同。
     */
    public void addPhoto(String addButtonId) {
        world.tapTestId(addButtonId, SCREEN_TIMEOUT);
        long deadline = System.nanoTime() + MENU_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (!world.driver().findElements(world.byTestId(PHOTO_MENU_CAMERA)).isEmpty()) {
                world.tapTestId(PHOTO_MENU_CAMERA, SCREEN_TIMEOUT);
                return;
            }
            if (world.driver().findElements(world.byTestId(addButtonId)).isEmpty()) {
                return;
            }
        }
        throw new AssertionError("點了 " + addButtonId + " 之後沒有跳出拍照／從相簿選擇的選單");
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
