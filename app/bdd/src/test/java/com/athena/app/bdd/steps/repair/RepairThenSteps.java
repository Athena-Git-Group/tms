package com.athena.app.bdd.steps.repair;

import com.athena.app.bdd.AppiumWorld;
import io.appium.java_client.AppiumBy;
import io.cucumber.java.PendingException;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 工務單模組的 Then。依共通實作約定，每句除了查畫面（accessibility id 經
 * {@link AppiumWorld#byTestId}）還要讀回後端權威狀態（{@code repair_tickets} 連
 * {@code tasks}），兩邊一致才算過。
 */
public class RepairThenSteps {

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(6);

    /** 來源標示文字 → 權威 {@code tasks.source}（對照工務單契約 PoolItem.source 的對照說明）。 */
    private static final Map<String, String> SOURCE_BY_LABEL = Map.of(
            "房務報修", "staff",
            "櫃檯", "desk");

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

    // ──── 工務視角 ────

    /**
     * 必查：以 {@code engHome.progress.in-hand-count}／{@code engHome.progress.repaired-count}／
     * {@code engHome.progress.urgent-count} 定位的三格數字等於期望值，並與後端工務視角
     * 今日進度一致。依 {@code specs/truth/contracts/components/schemas/員工工作台.yaml} 的
     * {@code engineeringSummary}：手上（openCount）＝本人 accepted／doing 的工務單筆數、
     * 已完修（completedToday）＝今天 done 的筆數、急件（urgentCount）＝手上單
     * （accepted／doing）中 isUrgent=true 的筆數。
     */
    @那麼("工務首頁顯示手上 {int} 張、今天已完修 {int} 張、急件 {int} 張")
    public void engHomeProgressShows(int inHandCount, int repairedCount, int urgentCount) {
        assertScreenNumber("engHome.progress.in-hand-count", inHandCount);
        assertScreenNumber("engHome.progress.repaired-count", repairedCount);
        assertScreenNumber("engHome.progress.urgent-count", urgentCount);

        assertBackendCount("手上（accepted／doing）", inHandCount, "t.state IN ('accepted', 'doing')");
        assertBackendCount("今天已完修", repairedCount, "t.state = 'done'");
        assertBackendCount("急件（手上單中 urgent）", urgentCount,
                "t.state IN ('accepted', 'doing') AND t.urgent = true");
    }

    /**
     * 必查：以 {@code engPool.item.{單號}.source-label} 定位的單卡來源標示文字等於該值，
     * 並與後端 {@code tasks.source} 對照一致（staff＝房務報修、desk＝櫃檯）。
     */
    @那麼("工務待認領中 {string} 的來源標示為 {string}")
    public void engPoolItemSourceLabelIs(String ticketNo, String sourceLabel) {
        WebElement label = world.waitForTestId("engPool.item." + ticketNo + ".source-label", SCREEN_TIMEOUT);
        String onScreen = label.getText();
        if (onScreen == null || !onScreen.contains(sourceLabel)) {
            throw new AssertionError("「" + ticketNo + "」的來源標示應為「" + sourceLabel
                    + "」，畫面上是「" + onScreen + "」");
        }

        String expectedSource = SOURCE_BY_LABEL.get(sourceLabel);
        if (expectedSource == null) {
            throw new IllegalArgumentException("認不得的來源標示「" + sourceLabel + "」（只支援 房務報修｜櫃檯）");
        }
        Map<String, Object> row = singleTicketRow(
                "rt.ticket_no = ?", "單號 " + ticketNo + " 的工務單", ticketNo);
        if (!expectedSource.equals(row.get("source"))) {
            throw new AssertionError("後端 tasks.source 應為 " + expectedSource
                    + "（" + sourceLabel + "），實際是 " + row.get("source"));
        }
    }

    /**
     * 必查：列表中的單卡順序剛好等於清單，沒有多也沒有少；清單裡的每張單在後端都是
     * 待認領（{@code tasks.state = 'pool'}）。單號清單以「、」分隔、非 quoted string，
     * 故用 regex 形式的 step definition 擷取（比照「待認領列表依序為」）。
     * 順序以各單卡（{@code engPool.item.{單號}.card}）在畫面上的縱向位置判定。
     */
    @那麼("^工務待認領依序為 (.+)$")
    public void engPoolOrderIs(String ticketListText) {
        List<String> expected = List.of(ticketListText.split("、"));

        int previousY = Integer.MIN_VALUE;
        String previousNo = null;
        for (String ticketNo : expected) {
            WebElement card = world.waitForTestId("engPool.item." + ticketNo + ".card", SCREEN_TIMEOUT);
            int y = card.getRect().getY();
            if (y <= previousY) {
                throw new AssertionError("工務待認領順序不對：「" + ticketNo + "」應排在「"
                        + previousNo + "」之後，但畫面上位置相反");
            }
            previousY = y;
            previousNo = ticketNo;
        }

        List<WebElement> allCards = world.driver().findElements(AppiumBy.iOSNsPredicateString(
                "name BEGINSWITH 'engPool.item.' AND name ENDSWITH '.card'"));
        if (allCards.size() != expected.size()) {
            throw new AssertionError("工務待認領應剛好 " + expected.size() + " 張單卡，畫面上有 "
                    + allCards.size() + " 張");
        }

        for (String ticketNo : expected) {
            Map<String, Object> row = singleTicketRow(
                    "rt.ticket_no = ?", "單號 " + ticketNo + " 的工務單", ticketNo);
            if (!"pool".equals(row.get("state"))) {
                throw new AssertionError("「" + ticketNo + "」在後端應為 pool（待認領），實際是 " + row.get("state"));
            }
        }
    }

    /**
     * 必查：以 {@code engPool.claim-conflict-alert} 定位的 Alert 顯示含該員工姓名的撞單提示；
     * 後端該單仍在對方名下（有工務單的任務指派給這位員工），畫面上的操作者沒有拿到這張單；
     * 該單已從待認領列表移除（dsl「已被對方接走」——{@code engPool.item.{單號}.card} 不存在）。
     */
    @那麼("工務認領撞單，畫面顯示「已被 {string} 認領」")
    public void engPoolClaimConflictShows(String claimerName) {
        WebElement alert = world.waitForTestId("engPool.claim-conflict-alert", SCREEN_TIMEOUT);
        String text = alert.getText();
        if (text == null || !text.contains(claimerName)) {
            throw new AssertionError("撞單提示應包含「" + claimerName + "」，畫面上是「" + text + "」");
        }

        UUID claimerId = world.staff(claimerName).id();
        List<Map<String, Object>> rows = ticketRows(
                "t.assignee_id = ? AND t.state IN ('accepted', 'doing')", claimerId);
        if (rows.isEmpty()) {
            throw new AssertionError("後端沒有任何工務單指派給「" + claimerName + "」——撞單的單應仍在對方名下");
        }
        for (Map<String, Object> row : rows) {
            String ticketNo = (String) row.get("ticket_no");
            if (!world.driver().findElements(world.byTestId("engPool.item." + ticketNo + ".card")).isEmpty()) {
                throw new AssertionError("「" + ticketNo + "」已被「" + claimerName
                        + "」接走，應從待認領列表移除，但畫面上還找得到它的單卡");
            }
        }
    }

    /**
     * 必查：工務單詳情沒有可點的「放回待認領」操作入口（{@code ticket.release-to-pool-button}
     * 不存在）；客房工務單只能用「暫停」處理。先等到詳情頁（{@code ticket.screen}）出現
     * 再查按鈕不存在，否則畫面還沒載入時的「找不到」會誤判為通過。
     */
    @那麼("工務單詳情沒有「放回待認領」按鈕")
    public void ticketDetailHasNoReleaseToPoolButton() {
        world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
        if (!world.driver().findElements(world.byTestId("ticket.release-to-pool-button")).isEmpty()) {
            throw new AssertionError("客房工務單的詳情不應出現「放回待認領」按鈕，但畫面上找得到");
        }
    }

    /**
     * 必查：後端該單已回到待認領（{@code tasks.state = 'pool'}）且不再指派給任何人
     * （{@code tasks.assignee_id} 為 null）；畫面上狀態摘要卡（{@code ticket.status-summary-card}）
     * 顯示「待認領」。
     */
    @那麼("{string} 回到工務待認領，不再指派給任何人")
    public void ticketBackToPoolUnassigned(String ticketNo) {
        WebElement summary = world.waitForTestId("ticket.status-summary-card", SCREEN_TIMEOUT);
        String text = summary.getText();
        if (text == null || !text.contains("待認領")) {
            throw new AssertionError("狀態摘要卡應顯示「待認領」，畫面上是「" + text + "」");
        }

        Map<String, Object> row = singleTicketRow(
                "rt.ticket_no = ?", "單號 " + ticketNo + " 的工務單", ticketNo);
        if (!"pool".equals(row.get("state"))) {
            throw new AssertionError("「" + ticketNo + "」在後端應為 pool（待認領），實際是 " + row.get("state"));
        }
        if (row.get("assignee_id") != null) {
            throw new AssertionError("「" + ticketNo + "」不應指派給任何人，後端 assignee_id = " + row.get("assignee_id"));
        }
    }

    /**
     * 必查：狀態摘要卡（{@code ticket.status-summary-card}）顯示「已完修」、處理結果
     * （{@code ticket.resolution-label}）顯示該結果文字；後端該單已完修
     * （{@code tasks.state = 'done'}），且各結果的連動也已落地——暫時處理有一張
     * {@code origin_ticket_task_id} 指回本單的追蹤單、無法修復時該房
     * {@code rooms.hk_state} 已轉為 out_of_order。
     */
    @那麼("工務單詳情顯示已完修，結果為 {string}")
    public void ticketDetailShowsResolved(String resolutionLabel) {
        WebElement summary = world.waitForTestId("ticket.status-summary-card", SCREEN_TIMEOUT);
        String summaryText = summary.getText();
        if (summaryText == null || !summaryText.contains("已完修")) {
            throw new AssertionError("狀態摘要卡應顯示「已完修」，畫面上是「" + summaryText + "」");
        }
        WebElement resolution = world.waitForTestId("ticket.resolution-label", SCREEN_TIMEOUT);
        String resolutionText = resolution.getText();
        if (resolutionText == null || !resolutionText.contains(resolutionLabel)) {
            throw new AssertionError("處理結果應顯示「" + resolutionLabel + "」，畫面上是「" + resolutionText + "」");
        }

        Map<String, Object> done = singleTicketRow("t.state = 'done'", "已完修的工務單");
        switch (resolutionLabel) {
            case "已修好" -> { /* 權威狀態只需 done，沒有額外連動 */ }
            case "暫時處理" -> {
                List<Map<String, Object>> followUps = ticketRows(
                        "rt.origin_ticket_task_id = ?", done.get("task_id"));
                if (followUps.isEmpty()) {
                    throw new AssertionError("暫時處理應自動建立指回原單的追蹤單，後端查不到");
                }
            }
            case "無法修復" -> {
                List<Map<String, Object>> rooms = world.backend().queryRows(
                        "SELECT hk_state FROM rooms WHERE tenant_id = ? AND id = ?",
                        world.backend().ensureTenant(), done.get("room_id"));
                if (rooms.size() != 1 || !"out_of_order".equals(rooms.get(0).get("hk_state"))) {
                    throw new AssertionError("無法修復後該房 rooms.hk_state 應為 out_of_order，實際查到 " + rooms);
                }
            }
            default -> throw new IllegalArgumentException(
                    "認不得的處理結果「" + resolutionLabel + "」（只支援 已修好｜暫時處理｜無法修復）");
        }
    }

    /**
     * 必查：詳情頂部步驟條固定依序顯示四步（第 i 步以 {@code ticket.stepper.{i}} 定位、
     * 文字等於步驟名，i 從 1 起算），目前步驟另掛 {@code ticket.stepper.{i}--current}；
     * 後端狀態與目前步驟對照一致（待開始＝accepted、維修中＝doing、完修＝done 且尚未
     * 驗收、驗收＝done 且 verified_at 已有值）。
     */
    @那麼("工務單詳情的步驟條依序為 {string}，目前在 {string}")
    public void ticketDetailStepperIs(String stepListText, String currentStep) {
        List<String> steps = List.of(stepListText.split("、"));
        int currentIndex = steps.indexOf(currentStep);
        if (currentIndex < 0) {
            throw new IllegalArgumentException("目前步驟「" + currentStep + "」不在步驟清單 " + steps + " 裡");
        }

        for (int i = 0; i < steps.size(); i++) {
            WebElement step = world.waitForTestId("ticket.stepper." + (i + 1), SCREEN_TIMEOUT);
            String text = step.getText();
            if (text == null || !text.contains(steps.get(i))) {
                throw new AssertionError("步驟條第 " + (i + 1) + " 步應為「" + steps.get(i)
                        + "」，畫面上是「" + text + "」");
            }
        }
        world.waitForTestId("ticket.stepper." + (currentIndex + 1) + "--current", SCREEN_TIMEOUT);

        Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
        String state = (String) row.get("state");
        boolean verified = row.get("verified_at") != null;
        boolean consistent = switch (currentStep) {
            case "待開始" -> "accepted".equals(state);
            case "維修中" -> "doing".equals(state);
            case "完修" -> "done".equals(state) && !verified;
            case "驗收" -> "done".equals(state) && verified;
            default -> throw new IllegalArgumentException("認不得的目前步驟「" + currentStep + "」");
        };
        if (!consistent) {
            throw new AssertionError("目前步驟「" + currentStep + "」與後端不一致：tasks.state = "
                    + state + "、verified_at " + (verified ? "已有值" : "為 null"));
        }
    }

    /**
     * 必查：送出成功後導向剛建立那張工務單的詳情頁（{@code ticket.screen}），後端確實
     * 已建立對應工務單（本情境唯一的一張，單號已配發）。表單內容與後端欄位的逐項比對
     * 屬於開工單 When 句的權威語意，這裡驗「單真的建立且畫面停在它的詳情」。
     */
    @那麼("App 停在新建工務單的詳情")
    public void appStaysOnNewTicketDetail() {
        world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);

        Map<String, Object> row = singleTicketRow("1 = 1", "剛建立的工務單");
        Object ticketNo = row.get("ticket_no");
        if (ticketNo == null) {
            throw new AssertionError("後端的新工務單沒有配發單號（repair_tickets.ticket_no 為 null）");
        }
    }

    // ──── 房務端生命週期 ────

    /**
     * 必查：詳情顯示退回原因文字（{@code ticket.reject-reason-label}）；後端
     * {@code repair_tickets.reject_reason} 等於該原因、狀態為 rejected（主管未核准）。
     */
    @那麼("工務單詳情顯示退回原因 {string}")
    public void ticketDetailShowsRejectReason(String reason) {
        WebElement label = world.waitForTestId("ticket.reject-reason-label", SCREEN_TIMEOUT);
        String text = label.getText();
        if (text == null || !text.contains(reason)) {
            throw new AssertionError("退回原因應顯示「" + reason + "」，畫面上是「" + text + "」");
        }

        Map<String, Object> row = singleTicketRow(
                "rt.reject_reason = ?", "退回原因為「" + reason + "」的工務單", reason);
        if (!"rejected".equals(row.get("state"))) {
            throw new AssertionError("被退回的工務單在後端應為 rejected（主管未核准），實際是 " + row.get("state"));
        }
    }

    /**
     * 必查：工務單詳情有可點的撤回操作入口（{@code ticket.withdraw-button}），
     * 點下去會進入撤回確認流程。
     */
    @那麼("工務單詳情顯示撤回按鈕")
    public void ticketDetailShowsWithdrawButton() {
        WebElement button = world.waitForTestId("ticket.withdraw-button", SCREEN_TIMEOUT);
        if (!button.isEnabled()) {
            throw new AssertionError("撤回按鈕在畫面上，但不可點");
        }
    }

    /**
     * 必查：工務單詳情有可點的「補資料再送出」操作入口（{@code ticket.resubmit-button}）；
     * 這顆按鈕只在主管未核准狀態顯示，所以後端該單必須是 rejected。
     */
    @那麼("工務單詳情顯示「補資料再送出」按鈕")
    public void ticketDetailShowsResubmitButton() {
        WebElement button = world.waitForTestId("ticket.resubmit-button", SCREEN_TIMEOUT);
        if (!button.isEnabled()) {
            throw new AssertionError("「補資料再送出」按鈕在畫面上，但不可點");
        }

        Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
        if (!"rejected".equals(row.get("state"))) {
            throw new AssertionError("「補資料再送出」只在主管未核准（rejected）時顯示，後端狀態是 " + row.get("state"));
        }
    }

    // ──── 共用 helper ────

    /** 畫面上某個數字格（count 類 accessibility id）的文字必須剛好是這個數字。 */
    private void assertScreenNumber(String testId, int expected) {
        WebElement element = world.waitForTestId(testId, SCREEN_TIMEOUT);
        String text = element.getText();
        if (text == null || !text.trim().equals(String.valueOf(expected))) {
            throw new AssertionError(testId + " 應顯示 " + expected + "，畫面上是「" + text + "」");
        }
    }

    /** 後端符合條件的工務單筆數必須等於期望值；{@code what} 用在錯誤訊息裡說明在數什麼。 */
    private void assertBackendCount(String what, int expected, String condition) {
        List<Map<String, Object>> rows = ticketRows(condition);
        if (rows.size() != expected) {
            throw new AssertionError("後端「" + what + "」的工務單應為 " + expected + " 張，實際 " + rows.size() + " 張");
        }
    }

    /**
     * 讀回符合條件的工務單（repair_tickets 連 tasks），給權威狀態斷言用。
     * {@code condition} 內可用別名 rt（repair_tickets）與 t（tasks）。
     */
    private List<Map<String, Object>> ticketRows(String condition, Object... params) {
        Object[] all = new Object[params.length + 1];
        all[0] = world.backend().ensureTenant();
        System.arraycopy(params, 0, all, 1, params.length);
        return world.backend().queryRows("""
                SELECT rt.*, t.state, t.source, t.urgent, t.assignee_id
                FROM repair_tickets rt
                JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                WHERE rt.tenant_id = ?
                  AND""" + " " + condition, all);
    }

    /** 同 {@link #ticketRows}，但條件必須剛好命中一張單，否則斷言失敗。 */
    private Map<String, Object> singleTicketRow(String condition, String what, Object... params) {
        List<Map<String, Object>> rows = ticketRows(condition, params);
        if (rows.size() != 1) {
            throw new AssertionError("預期剛好一張（" + what + "），後端查到 " + rows.size() + " 張");
        }
        return rows.get(0);
    }
}
