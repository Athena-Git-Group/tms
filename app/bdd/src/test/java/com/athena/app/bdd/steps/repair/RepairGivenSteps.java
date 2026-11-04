package com.athena.app.bdd.steps.repair;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.假設;

/** 工務單模組的 Given。 */
public class RepairGivenSteps {

    private final AppiumWorld world;

    public RepairGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /** 怎麼做：把裝置與相機的拍攝時間設為該時刻（{@code MM/DD HH:mm}）。 */
    @假設("裝置時間是 {string}")
    public void deviceTimeIs(String monthDayHourMinute) {
        throw new PendingException();
    }

    /**
     * 怎麼做：從任務頁進入報修畫面，選類別並拍指定張數的照片，停在報修畫面還沒送出。
     * 權威狀態落地：後端還沒有工務單。
     */
    @假設("{string} 在 {string} 的報修畫面選了類別 {string}，附了 {int} 張照片")
    public void repairScreenHasPhotosAttached(String staffName, String task, String category, int photoCount) {
        throw new PendingException();
    }

    /** 怎麼做：攔截網路，讓該張照片的上傳請求失敗，其他照片正常上傳。 */
    @假設("第 {int} 張照片的上傳會失敗")
    public void nthPhotoUploadFails(int photoIndex) {
        throw new PendingException();
    }

    /**
     * 怎麼做：在離線狀態下填好並送出指定張數的冷氣工務單，讓它留在手機的待送出佇列。
     * 權威狀態落地：後端還沒有這些單。房號為 feature 中的裸字（無引號），用 regex 形式的
     * step definition 擷取。
     */
    @假設("^\"([^\"]+)\" 的手機有 (\\d+) 張待送出的 (\\S+) 冷氣工務單$")
    public void phoneHasPendingAcTickets(String staffName, int count, String roomNo) {
        throw new PendingException();
    }

    /**
     * 怎麼做：攔截網路，讓前幾次開單請求在後端已處理後才逾時，迫使 App 用同一個
     * {@code Idempotency-Key} 重送。
     */
    @假設("恢復連線後的前 {int} 次送出會因網路不穩而重送")
    public void firstRetriesAfterReconnectAreFlaky(int retryCount) {
        throw new PendingException();
    }

    /** 怎麼做：透過後端測試資料建立該工務單。權威狀態落地：後端有該單。 */
    @假設("後端已有 {string} 報的 {string} 冷氣工務單 {string}，狀態為 {string}")
    public void backendHasExistingAcTicket(String reporter, String roomNo, String ticketNo, String status) {
        throw new PendingException();
    }

    /** 怎麼做：透過後端測試資料寫入該房今日入住的 VIP 訂單。 */
    @假設("{string} 今天是 VIP 房，住客 {string} 入住")
    public void isVipRoomWithCheckin(String roomNo, String checkinTime) {
        throw new PendingException();
    }

    /** 怎麼做：透過後端測試資料建立這些工務單。權威狀態落地：後端有這些工務單。 */
    @假設("已有以下工務單：")
    public void hasExistingTickets(DataTable table) {
        throw new PendingException();
    }

    /**
     * 怎麼做：透過後端測試資料（或先執行一次等效的降級操作）讓該工務單
     * {@code is_urgent}=false、{@code urgent_downgrade_reason}＝該原因。權威狀態落地：
     * 後端該工務單已是一般件，並記錄降級原因。
     */
    @假設("{string} 已將 {string} 降級為一般件，原因為 {string}")
    public void managerDowngradesTicket(String managerName, String ticketNo, String reason) {
        throw new PendingException();
    }
}
