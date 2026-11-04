package com.athena.app.bdd.steps.repair;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.當;

/** 工務單模組的 When。 */
public class RepairWhenSteps {

    private final AppiumWorld world;

    public RepairWhenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 怎麼做：還沒在報修畫面時，先從任務頁進入；然後用相機拍指定張數。
     * 回寫：報修畫面的照片縮圖與檔案。accessibility id：{@code repair.photo.add-button}。
     */
    @當("{string} 在 {string} 的報修畫面拍 {int} 張照片")
    public void takePhotos(String staffName, String task, int photoCount) {
        throw new PendingException();
    }

    /**
     * 怎麼做：在報修畫面按送出（預設模式：不中斷原任務）。權威狀態落地：只有每張照片都
     * 上傳成功，而且在線上時，才會送出開單請求。
     */
    @當("{string} 送出工務單")
    public void submitTicket(String staffName) {
        throw new PendingException();
    }

    /**
     * 怎麼做：在報修畫面選「中斷 → 需要工務協助」後送出。權威狀態落地：同
     * {@link #submitTicket(String)}。
     */
    @當("{string} 以中斷模式送出工務單")
    public void submitTicketInInterruptMode(String staffName) {
        throw new PendingException();
    }

    /** 怎麼做：從任務頁進入報修畫面，點選類別。App 會呼叫開單前檢查。 */
    @當("{string} 在 {string} 的報修畫面選擇類別 {string}")
    public void chooseRepairCategory(String staffName, String task, String category) {
        throw new PendingException();
    }

    /** 怎麼做：從任務頁、通知或「我的」進入該工務單的詳情頁。回寫：工務單詳情內容。 */
    @當("{string} 在 App 打開 {string} 的工務單詳情")
    public void openTicketDetail(String staffName, String ticketNo) {
        throw new PendingException();
    }

    /** 怎麼做：把裝置切回有網路，等待待送出佇列處理完。 */
    @當("{string} 的手機恢復連線")
    public void phoneBackOnline(String staffName) {
        throw new PendingException();
    }
}
