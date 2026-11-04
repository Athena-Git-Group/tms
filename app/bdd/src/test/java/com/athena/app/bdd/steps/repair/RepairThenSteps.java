package com.athena.app.bdd.steps.repair;

import com.athena.app.bdd.AppiumWorld;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.那麼;

/** 工務單模組的 Then。 */
public class RepairThenSteps {

    private final AppiumWorld world;

    public RepairThenSteps(AppiumWorld world) {
        this.world = world;
    }

    /**
     * 必查：照片檔案上有該浮水印文字（以 OCR 或 App 揭露的中繼資料比對）。上傳到後端的照片
     * 也帶有同樣的浮水印。accessibility id：{@code repair.photo.{index}.watermark-label}。
     */
    @那麼("這張照片的浮水印為 {string}")
    public void photoWatermarkIs(String watermark) {
        throw new PendingException();
    }

    /** 必查：後端沒有新的修繕任務，也沒有對應的開單請求紀錄。 */
    @那麼("後端沒有收到這張工務單")
    public void backendHasNoTicket() {
        throw new PendingException();
    }

    /**
     * 必查：以 accessibility id {@code repair.photo.add-button}／送出鈕定位，其可互動狀態
     * （enabled/disabled）符合；禁用時無法真的觸發送出動作，不是事後才報錯。
     */
    @那麼("送出鈕為 {string}")
    public void submitButtonState(String enabledOrDisabled) {
        throw new PendingException();
    }

    /**
     * 必查：以 accessibility id {@code repair.photo.count-label} 定位的欄位標題文字包含該
     * 提示；不要求事後 Alert 或 Toast 出現同樣文字。
     */
    @那麼("照片欄位提示 {string}")
    public void photoFieldHint(String hint) {
        throw new PendingException();
    }

    /** 必查：報修畫面的照片縮圖數等於張數。 */
    @那麼("報修畫面仍只有 {int} 張照片")
    public void repairScreenStillHasPhotos(int photoCount) {
        throw new PendingException();
    }

    /**
     * 必查：以 accessibility id {@code repair.photo.add-button} 定位，元件不存在或不可見；
     * 不要求出現「最多 3 張」的事後提示文字。
     */
    @那麼("加入照片的按鈕已消失")
    public void addPhotoButtonGone() {
        throw new PendingException();
    }

    /**
     * 必查：該張照片有失敗標示與單張重試操作；其他照片顯示已上傳。
     * accessibility id：{@code repair.photo.{index}.upload-status}、
     * {@code repair.photo.{index}.retry-button}。
     */
    @那麼("報修畫面標示第 {int} 張照片上傳失敗，並提供只重試這一張")
    public void nthPhotoFailedWithRetryOption(int photoIndex) {
        throw new PendingException();
    }

    /**
     * 必查：App 本機佇列有這張單與它的照片；畫面上有待送出標示。重新開啟 App 後仍在。
     * accessibility id：{@code ticket.summary.offline-queued-label}。
     */
    @那麼("工務單與照片保存在手機，畫面標示待送出")
    public void ticketAndPhotosSavedOffline() {
        throw new PendingException();
    }

    /**
     * 必查：後端該房未結案的冷氣修繕任務筆數等於張數。房號為 feature 中的裸字（無引號），
     * 用 regex 形式的 step definition 擷取。
     */
    @那麼("^後端只有 (\\d+) 張 (\\S+) 冷氣工務單$")
    public void backendHasOnlyNAcTickets(int count, String roomNo) {
        throw new PendingException();
    }

    /** 必查：待送出標示消失，App 本機佇列是空的。 */
    @那麼("畫面不再標示待送出")
    public void noLongerMarkedPendingSync() {
        throw new PendingException();
    }

    /** 必查：重複提示上有補照片到該單的操作，點下去會進入補照片流程。accessibility id：TODO。 */
    @那麼("畫面提供補充照片到 {string} 的選項")
    public void offersAddPhotoOptionToTicket(String ticketNo) {
        throw new PendingException();
    }

    /**
     * 必查：報修畫面的急件判定卡顯示「系統判定：急件 · {理由}」，與後端 {@code urgentSuggestion}
     * 的判定結果與 {@code rule} 一致；沒有輸入可以更改這個判定。
     * accessibility id：{@code repair.urgent-judgement.card}、{@code repair.urgent-judgement.reason}。
     */
    @那麼("畫面顯示系統判定為急件，理由 {string}")
    public void showsSystemUrgentJudgement(String reason) {
        throw new PendingException();
    }

    /**
     * 必查：報修畫面的急件判定卡顯示「系統判定：一般件」，與後端
     * {@code urgentSuggestion.suggested}=false 一致；沒有輸入可以更改這個判定。
     * accessibility id：{@code repair.urgent-judgement.card}。
     */
    @那麼("畫面顯示系統判定為一般件")
    public void showsSystemNormalJudgement() {
        throw new PendingException();
    }

    /**
     * 必查：急件判定卡上有這段固定說明文字，且判定卡上沒有任何可點的
     * 「設為急件／取消急件」切換元件。accessibility id：{@code repair.urgent-judgement.card}。
     */
    @那麼("畫面顯示急件判定由系統依飯店設定規則決定，房務不能更改")
    public void showsUrgentJudgementNotEditableByStaff() {
        throw new PendingException();
    }

    /**
     * 必查：工務單詳情的狀態摘要卡顯示該文案，與後端工務單狀態對照表一致。
     * accessibility id：TODO（狀態摘要卡節點尚未在 ui-plan.md 逐一命名）。
     */
    @那麼("工務單詳情顯示狀態摘要卡 {string}")
    public void ticketDetailShowsStatusSummary(String summaryText) {
        throw new PendingException();
    }

    /**
     * 必查：工務單詳情沒有可點的撤回操作入口；{@code already_claimed} 語意直接表現為按鈕
     * 不存在，不是先顯示再點擊才報 409。accessibility id：TODO。
     */
    @那麼("工務單詳情沒有撤回按鈕")
    public void ticketDetailHasNoWithdrawButton() {
        throw new PendingException();
    }

    /**
     * 必查：工務單詳情顯示該降級原因文字，與後端 {@code repair_tickets.urgent_downgrade_reason}
     * 一致。accessibility id：TODO。
     */
    @那麼("工務單詳情顯示急件降級原因 {string}")
    public void ticketDetailShowsUrgentDowngradeReason(String reason) {
        throw new PendingException();
    }
}
