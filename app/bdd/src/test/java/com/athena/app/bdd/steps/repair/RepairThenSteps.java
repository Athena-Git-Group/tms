package com.athena.app.bdd.steps.repair;

import com.athena.app.bdd.AppiumWorld;
import io.appium.java_client.AppiumBy;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.WebElement;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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

    /** 急件判定理由裡的入住時刻格式，與後端組理由時用的格式同一套。 */
    private static final DateTimeFormatter CHECKIN_TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** 等後端收到離線期間保存的開單的上限：每一筆都是一次 HTTP 來回，比畫面轉場久。 */
    private static final Duration QUEUE_DRAIN_TIMEOUT = Duration.ofSeconds(20);

    private final AppiumWorld world;

    private final RepairContext context;

    public RepairThenSteps(AppiumWorld world, RepairContext context) {
        this.world = world;
        this.context = context;
    }

    /**
     * 必查：照片檔案上有該浮水印文字（以 OCR 或 App 揭露的中繼資料比對）。上傳到後端的照片
     * 也帶有同樣的浮水印。accessibility id：{@code repair.photo.{index}.watermark-label}。
     */
    @那麼("這張照片的浮水印為 {string}")
    public void photoWatermarkIs(String watermark) {
        assertLabelContains("repair.photo.1.watermark-label", watermark, "照片浮水印");
    }

    /**
     * 必查：後端沒有新的修繕任務，也沒有對應的開單請求紀錄。
     *
     * <p>用到這句的情境都沒有事先鋪任何工務單，所以「沒收到」等於整個租戶一張都沒有。
     * 以總筆數驗而不是只驗某一張，連「建成了另一張單」這種走偏也會被抓到。
     */
    @那麼("後端沒有收到這張工務單")
    public void backendHasNoTicket() {
        List<Map<String, Object>> rows = ticketRows("1 = 1");
        if (!rows.isEmpty()) {
            throw new AssertionError("後端不該收到工務單，實際查到 " + rows.size() + " 張");
        }
    }

    /**
     * 必查：以 accessibility id {@code repair.photo.add-button}／送出鈕定位，其可互動狀態
     * （enabled/disabled）符合；禁用時無法真的觸發送出動作，不是事後才報錯。
     */
    @那麼("送出鈕為 {string}")
    public void submitButtonState(String enabledOrDisabled) {
        boolean expectedEnabled = switch (enabledOrDisabled) {
            case "啟用" -> true;
            case "禁用" -> false;
            default -> throw new IllegalArgumentException(
                    "認不得的按鈕狀態「" + enabledOrDisabled + "」（只支援 啟用｜禁用）");
        };
        WebElement submit = world.waitForTestId("repair.submit-button", SCREEN_TIMEOUT);
        if (submit.isEnabled() != expectedEnabled) {
            throw new AssertionError("送出鈕應為「" + enabledOrDisabled + "」，實際是「"
                    + (submit.isEnabled() ? "啟用" : "禁用") + "」");
        }
        if (!expectedEnabled) {
            // dsl 的「禁用時無法真的觸發送出動作，不是事後才報錯」：按下去要什麼都不發生。
            // 只讀 enabled 屬性的話，一顆畫成灰色但照樣送得出去的按鈕也會過關。
            try {
                submit.click();
            } catch (RuntimeException ignored) {
                // 點不動本來就是這句要的結果。
            }
            world.waitForTestId("repair.sheet", SCREEN_TIMEOUT);
            backendHasNoTicket();
        }
    }

    /**
     * 必查：以 accessibility id {@code repair.photo.count-label} 定位的欄位標題文字包含該
     * 提示；不要求事後 Alert 或 Toast 出現同樣文字。
     */
    @那麼("照片欄位提示 {string}")
    public void photoFieldHint(String hint) {
        assertLabelContains("repair.photo.count-label", hint, "照片欄位標題");
    }

    /**
     * 必查：報修畫面的照片縮圖數等於張數。縮圖以它自己的浮水印標籤
     * （{@code repair.photo.{index}.watermark-label}，每張照片各一個）計數——報修面板上
     * 只有照片格帶這個 id，數它就是數縮圖。
     */
    @那麼("報修畫面仍只有 {int} 張照片")
    public void repairScreenStillHasPhotos(int photoCount) {
        List<String> thumbnails = world.findTestIdsWithPrefix("repair.photo.").stream()
                .filter(id -> id.endsWith(".watermark-label"))
                .toList();
        if (thumbnails.size() != photoCount) {
            throw new AssertionError("報修畫面應有 " + photoCount + " 張照片，實際 "
                    + thumbnails.size() + " 張：" + thumbnails);
        }
    }

    /**
     * 必查：以 accessibility id {@code repair.photo.add-button} 定位，元件不存在或不可見；
     * 不要求出現「最多 3 張」的事後提示文字。
     */
    @那麼("加入照片的按鈕已消失")
    public void addPhotoButtonGone() {
        // 先等報修面板本身在畫面上，否則面板還沒畫出來時的「找不到」會誤判為通過。
        world.waitForTestId("repair.sheet", SCREEN_TIMEOUT);
        if (!world.driver().findElements(world.byTestId("repair.photo.add-button")).isEmpty()) {
            throw new AssertionError("已附滿 3 張時不應再有加入照片的按鈕，但畫面上找得到");
        }
    }

    /**
     * 必查：該張照片有失敗標示與單張重試操作；其他照片顯示已上傳。
     * accessibility id：{@code repair.photo.{index}.upload-status}、
     * {@code repair.photo.{index}.retry-button}。
     */
    @那麼("報修畫面標示第 {int} 張照片上傳失敗，並提供只重試這一張")
    public void nthPhotoFailedWithRetryOption(int photoIndex) {
        assertLabelContains("repair.photo." + photoIndex + ".upload-status", "失敗", "照片上傳狀態");
        WebElement retry = world.waitForTestId(
                "repair.photo." + photoIndex + ".retry-button", SCREEN_TIMEOUT);
        if (!retry.isEnabled()) {
            throw new AssertionError("第 " + photoIndex + " 張照片的重試按鈕在畫面上，但不可點");
        }

        // 「只重試這一張」的另一半：其他照片不能跟著被標成失敗，否則員工會整批重傳。
        for (String id : world.findTestIdsWithPrefix("repair.photo.")) {
            if (!id.endsWith(".upload-status") || id.contains("." + photoIndex + ".")) {
                continue;
            }
            String status = world.labelOf(id);
            if (status != null && status.contains("失敗")) {
                throw new AssertionError("只有第 " + photoIndex + " 張該失敗，但「" + id
                        + "」也標示失敗：「" + status + "」");
            }
        }
    }

    /**
     * 必查：App 本機佇列有這張單與它的照片；畫面上有待送出標示。重新開啟 App 後仍在。
     * accessibility id：{@code ticket.summary.offline-queued-label}。
     */
    @那麼("工務單與照片保存在手機，畫面標示待送出")
    public void ticketAndPhotosSavedOffline() {
        world.waitForTestId("ticket.summary.offline-queued-label", SCREEN_TIMEOUT);
        // 畫面上的待送出標示與離線列的筆數講的是同一條佇列；只看標示的話，一個
        // 根本沒進佇列的標示也會過關，而那正是這條 Rule 最在意的失敗方式。
        assertLabelContains("system.offline-bar.pending-count", "1", "離線列的待同步筆數");
        backendHasNoTicket();
    }

    /**
     * 必查：後端該房未結案的冷氣修繕任務筆數等於張數。房號為 feature 中的裸字（無引號），
     * 用 regex 形式的 step definition 擷取。
     */
    @那麼("^後端只有 (\\d+) 張 (\\S+) 冷氣工務單$")
    public void backendHasOnlyNAcTickets(int count, String roomNo) {
        List<Map<String, Object>> rows = world.backend().queryRows("""
                SELECT rt.ticket_no FROM repair_tickets rt
                JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                JOIN rooms r ON r.tenant_id = rt.tenant_id AND r.id = rt.room_id
                WHERE rt.tenant_id = ? AND r.room_no = ? AND rt.category = 'air_conditioning'
                  AND t.state NOT IN ('done', 'inspected', 'cancelled')
                """, world.backend().ensureTenant(), roomNo);
        if (rows.size() != count) {
            throw new AssertionError("「" + roomNo + "」未結案的冷氣工務單應為 " + count
                    + " 張，實際 " + rows.size() + " 張——重送用的是同一把 Idempotency-Key 嗎？");
        }
    }

    /**
     * 必查：待送出標示消失，App 本機佇列是空的。佇列空了的判準是離線列整列不見——
     * 那一列顯示的就是佇列長度本身（{@code ConnectionStatus.pendingSyncCount}）。
     */
    @那麼("畫面不再標示待送出")
    public void noLongerMarkedPendingSync() {
        world.waitForTestIdGone("ticket.summary.offline-queued-label", QUEUE_DRAIN_TIMEOUT);
        world.waitForTestIdGone("system.offline-bar", QUEUE_DRAIN_TIMEOUT);
    }

    /**
     * 必查：重複提示（{@code repair.duplicate-ticket.banner}）指的就是那張單，上面有可點的
     * 補照片入口（{@code repair.duplicate-ticket.add-photo-button}）；後端那張單確實還沒
     * 結案，才會被開單前檢查當成重複。
     */
    @那麼("畫面提供補充照片到 {string} 的選項")
    public void offersAddPhotoOptionToTicket(String ticketNo) {
        assertLabelContains("repair.duplicate-ticket.banner", ticketNo, "重複提示");
        WebElement addPhoto = world.waitForTestId(
                "repair.duplicate-ticket.add-photo-button", SCREEN_TIMEOUT);
        if (!addPhoto.isEnabled()) {
            throw new AssertionError("補照片到「" + ticketNo + "」的按鈕在畫面上，但不可點");
        }

        Map<String, Object> row = singleTicketRow(
                "rt.ticket_no = ?", "單號 " + ticketNo + " 的工務單", ticketNo);
        if (List.of("done", "inspected", "cancelled").contains(String.valueOf(row.get("state")))) {
            throw new AssertionError("「" + ticketNo + "」在後端已結案（" + row.get("state")
                    + "），不該被當成可以補照片的重複單");
        }
    }

    /**
     * 必查：報修畫面的急件判定卡顯示「系統判定：急件 · {理由}」，與後端 {@code urgentSuggestion}
     * 的判定結果與 {@code rule} 一致；沒有輸入可以更改這個判定。
     * accessibility id：{@code repair.urgent-judgement.card}、{@code repair.urgent-judgement.reason}。
     */
    @那麼("畫面顯示系統判定為急件，理由 {string}")
    public void showsSystemUrgentJudgement(String reason) {
        assertLabelContains("repair.urgent-judgement.card", "系統判定：急件", "急件判定卡");
        assertLabelContains("repair.urgent-judgement.reason", reason, "急件判定理由");

        List<Map<String, Object>> reservations = todayReservationsForRepairRoom();
        if (reservations.isEmpty()) {
            throw new AssertionError("畫面判成急件，但後端「" + context.repairRoomNo()
                    + "」今天沒有未作廢的訂單，判定沒有真相可依");
        }
        String rule = urgentRuleOf(reservations.get(0));
        if (!rule.equals(reason)) {
            throw new AssertionError("急件理由與後端判定不一致：後端依訂房算出「" + rule
                    + "」，畫面上是「" + reason + "」");
        }
    }

    /**
     * 必查：報修畫面的急件判定卡顯示「系統判定：一般件」，與後端
     * {@code urgentSuggestion.suggested}=false 一致；沒有輸入可以更改這個判定。
     * accessibility id：{@code repair.urgent-judgement.card}。
     */
    @那麼("畫面顯示系統判定為一般件")
    public void showsSystemNormalJudgement() {
        assertLabelContains("repair.urgent-judgement.card", "系統判定：一般件", "急件判定卡");

        List<Map<String, Object>> reservations = todayReservationsForRepairRoom();
        if (!reservations.isEmpty()) {
            throw new AssertionError("畫面判成一般件，但後端「" + context.repairRoomNo()
                    + "」今天有訂單（" + urgentRuleOf(reservations.get(0)) + "），該判成急件");
        }
    }

    /**
     * 必查：急件判定卡上有這段固定說明文字，且判定卡上沒有任何可點的
     * 「設為急件／取消急件」切換元件。accessibility id：{@code repair.urgent-judgement.card}。
     */
    @那麼("畫面顯示急件判定由系統依飯店設定規則決定，房務不能更改")
    public void showsUrgentJudgementNotEditableByStaff() {
        assertLabelContains("repair.urgent-judgement.card", "依飯店設定的規則判定", "急件判定卡");
        assertLabelContains("repair.urgent-judgement.card", "房務不能更改", "急件判定卡");

        // 說明文字只是寫給人看的；真正保證房務改不了的是判定卡上沒有任何可點的元件。
        List<String> controls = world.findTestIdsWithPrefix("repair.urgent-judgement.").stream()
                .filter(id -> id.endsWith("-button") || id.endsWith("-toggle") || id.endsWith(".chip"))
                .toList();
        if (!controls.isEmpty()) {
            throw new AssertionError("急件判定卡上不該有可以改判定的元件，但找得到：" + controls);
        }
    }

    /**
     * 必查：狀態摘要卡（{@code ticket.status-summary-card}）顯示該文案，而且與後端的
     * 工務單狀態一致。對照表（dsl 的狀態用語）：等主管審核＝pending_review、
     * 主管未核准＝rejected、待認領＝pool、已接單＝accepted、維修中＝doing、
     * 已完修＝done。
     *
     * <p>工務端多看得到兩種文案，它們在後端是同一個狀態（done）、差別在驗收欄位：
     * 「已完修 · 等主管驗收」要求 {@code verified_at} 還是 null，「已驗收 · {主管}」
     * 要求驗收人就是文案上那一位——把驗收人一起比對，畫面才不會顯示別人的名字也算過。
     */
    @那麼("工務單詳情顯示狀態摘要卡 {string}")
    public void ticketDetailShowsStatusSummary(String summaryText) {
        assertLabelContains("ticket.status-summary-card", summaryText, "狀態摘要卡");

        Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
        String state = String.valueOf(row.get("state"));
        boolean verified = row.get("verified_at") != null;

        if ("已完修 · 等主管驗收".equals(summaryText)) {
            requireState(summaryText, state, "done");
            if (verified) {
                throw new AssertionError("摘要卡說還在等主管驗收，但後端已經有驗收時間");
            }
            return;
        }
        if (summaryText.startsWith("已驗收 · ")) {
            requireState(summaryText, state, "done");
            String verifierName = summaryText.substring("已驗收 · ".length()).trim();
            UUID expected = world.staff(verifierName).id();
            if (!expected.toString().equalsIgnoreCase(String.valueOf(row.get("verified_by")))) {
                throw new AssertionError("摘要卡說驗收人是「" + verifierName
                        + "」，後端 verified_by = " + row.get("verified_by"));
            }
            return;
        }
        requireState(summaryText, state, STATE_BY_SUMMARY.get(summaryText));
    }

    /**
     * 必查：工務單詳情沒有可點的撤回操作入口（{@code ticket.withdraw-button} 不存在）；
     * {@code already_claimed} 語意直接表現為按鈕不存在，不是先顯示再點擊才報 409。
     * 先等詳情頁出現再查按鈕不存在，否則畫面還沒載入時的「找不到」會誤判為通過。
     */
    @那麼("工務單詳情沒有撤回按鈕")
    public void ticketDetailHasNoWithdrawButton() {
        world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
        if (!world.driver().findElements(world.byTestId("ticket.withdraw-button")).isEmpty()) {
            throw new AssertionError("已被工務認領的單不該出現撤回按鈕，但畫面上找得到");
        }
    }

    /**
     * 必查：詳情顯示該降級原因文字（{@code ticket.urgent-downgrade-reason-label}，命名沿用
     * 同一頁的 {@code ticket.reject-reason-label}）；後端
     * {@code repair_tickets.urgent_downgrade_reason} 等於該原因，而且這張單已經不是急件
     * ——只留下原因卻還掛著急件，代表降級只做了一半。
     */
    @那麼("工務單詳情顯示急件降級原因 {string}")
    public void ticketDetailShowsUrgentDowngradeReason(String reason) {
        assertLabelContains("ticket.urgent-downgrade-reason-label", reason, "急件降級原因");

        Map<String, Object> row = singleTicketRow(
                "rt.urgent_downgrade_reason = ?", "降級原因為「" + reason + "」的工務單", reason);
        if (Boolean.TRUE.equals(row.get("is_urgent")) || Boolean.TRUE.equals(row.get("urgent"))) {
            throw new AssertionError("已降級的工務單在後端仍是急件（is_urgent="
                    + row.get("is_urgent") + "、tasks.urgent=" + row.get("urgent") + "）");
        }
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

    /**
     * 狀態摘要卡文案 → 權威 {@code tasks.state}（對照 dsl 的狀態用語表）。工務端另外兩種
     * 文案（已完修 · 等主管驗收、已驗收 · {主管}）在後端是同一個 done，由
     * {@link #ticketDetailShowsStatusSummary} 另外比對驗收欄位，不放進這張表。
     */
    private static final Map<String, String> STATE_BY_SUMMARY = Map.of(
            "等主管審核", "pending_review",
            "主管未核准", "rejected",
            "待認領", "pool",
            "已接單", "accepted",
            "維修中", "doing",
            "已完修", "done");

    /** 畫面上某個節點的文字必須包含期望值；{@code what} 用在錯誤訊息裡說明在看哪一段。 */
    private void assertLabelContains(String testId, String expected, String what) {
        String actual = world.waitForLabelOf(testId, expected, SCREEN_TIMEOUT);
        if (actual == null || !actual.contains(expected)) {
            throw new AssertionError(what + "應包含「" + expected + "」，畫面上是「" + actual + "」");
        }
    }

    /** 畫面文案與後端狀態的對照；對不上就連文案一起報出來，不只報狀態碼。 */
    private static void requireState(String summaryText, String actualState, String expectedState) {
        if (expectedState == null) {
            throw new IllegalArgumentException("認不得的狀態摘要卡文案「" + summaryText
                    + "」（合法值：" + STATE_BY_SUMMARY.keySet() + "，另加 已完修 · 等主管驗收、已驗收 · {主管}）");
        }
        if (!expectedState.equals(actualState)) {
            throw new AssertionError("摘要卡顯示「" + summaryText + "」，後端狀態應為 "
                    + expectedState + "，實際是 " + actualState);
        }
    }

    /**
     * 本情境報修面板那一間房今天還有效的訂房列。急件判定的真相只有這張表
     * （後端 {@code RepairService.judgeUrgent} 查的就是它），畫面判定對不對一律跟它比。
     */
    private List<Map<String, Object>> todayReservationsForRepairRoom() {
        return world.backend().queryRows("""
                SELECT rc.checkin_at, (rc.attrs ->> 'vip') = 'true' AS vip
                  FROM reservations_cache rc
                  JOIN rooms r ON r.tenant_id = rc.tenant_id AND r.id = rc.room_id
                 WHERE rc.tenant_id = ? AND r.room_no = ? AND rc.superseded_at IS NULL
                   AND rc.checkin_at >= CURRENT_DATE AND rc.checkin_at < CURRENT_DATE + 1
                 ORDER BY rc.checkin_at
                 LIMIT 1
                """, world.backend().ensureTenant(), context.repairRoomNo());
    }

    /** 依訂房列組出後端會給的急件理由：{@code (VIP · )HH:mm 入住}。 */
    private static String urgentRuleOf(Map<String, Object> reservation) {
        Object checkinAt = reservation.get("checkin_at");
        OffsetDateTime moment = checkinAt instanceof Timestamp timestamp
                ? timestamp.toLocalDateTime().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime()
                : (OffsetDateTime) checkinAt;
        return (Boolean.TRUE.equals(reservation.get("vip")) ? "VIP · " : "")
                + moment.format(CHECKIN_TIME) + " 入住";
    }

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
