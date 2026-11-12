package com.athena.app.bdd.steps.repair;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import com.fasterxml.jackson.databind.JsonNode;
import io.appium.java_client.AppiumBy;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.那麼;
import org.openqa.selenium.WebElement;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** 完成畫面那一層覆蓋層的 accessibility id（產品端 {@code celebrate_screen.dart} 的根節點）。 */
    private static final String CELEBRATE_OVERLAY_ID = "celebrate.screen";

    /**
     * 等完修回報送出真的做完的上限。這段要涵蓋帶完修照片的一次網路來回，
     * 所以取和等離線開單送完的 {@link #QUEUE_DRAIN_TIMEOUT} 同一個量級，
     * 不是 {@link #SCREEN_TIMEOUT} 那種單純畫面轉場的量級。模擬器慢一點就逾時的話，
     * 紅的是環境不是產品。
     */
    private static final Duration COMPLETION_SUBMIT_TIMEOUT = Duration.ofSeconds(20);

    /**
     * 完修送出做完之後繼續盯覆蓋層的長度，給畫面時間把覆蓋層畫出來。
     * 這段在確認送出做完的那一刻重新起算，不受等送出用掉多少時間影響，
     * 否則送出慢的場次會把盯的時間無聲砍短，漏看的反而不會失敗。
     */
    private static final Duration CELEBRATE_SETTLE = Duration.ofSeconds(3);

    private final AppiumWorld world;

    private final RepairContext context;

    public RepairThenSteps(AppiumWorld world, RepairContext context) {
        this.world = world;
        this.context = context;
    }

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
     * 必查：完修回報面板送出鈕（{@code completeSheet.submit-button}）的可互動狀態
     * （enabled/disabled）符合；禁用時無法真的觸發送出動作，不是事後才報錯。
     *
     * <p>與報修面板的「送出鈕為」是不同面板的不同句型，各自獨立：這裡定位的是完修回報
     * 面板的送出鈕，「無法觸發」的權威佐證是後端該單仍維修中（{@code tasks.state = 'doing'}），
     * 不是「沒有建新單」。
     */
    @那麼("完修回報的送出鈕為 {string}")
    public void completeSheetSubmitButtonState(String enabledOrDisabled) {
        boolean expectedEnabled = switch (enabledOrDisabled) {
            case "啟用" -> true;
            case "禁用" -> false;
            default -> throw new IllegalArgumentException(
                    "認不得的按鈕狀態「" + enabledOrDisabled + "」（只支援 啟用｜禁用）");
        };
        WebElement submit = world.waitForTestId("completeSheet.submit-button", SCREEN_TIMEOUT);
        if (submit.isEnabled() != expectedEnabled) {
            throw new AssertionError("完修回報的送出鈕應為「" + enabledOrDisabled + "」，實際是「"
                    + (submit.isEnabled() ? "啟用" : "禁用") + "」");
        }
        if (!expectedEnabled) {
            // 只讀 enabled 屬性的話，一顆畫成灰色但照樣送得出去的按鈕也會過關；
            // 所以禁用時要真的按下去，再驗送出沒有發生。
            try {
                submit.click();
            } catch (RuntimeException ignored) {
                // 這句要驗的是「送出沒有發生」；禁用鈕拒絕點擊而拋例外，
                // 正是沒有發生的一種表現，不能當成測試失敗。
            }
            // 面板本身沒有 accessibility id，以送出鈕仍在畫面上代表面板沒有因送出而關掉。
            world.waitForTestId("completeSheet.submit-button", SCREEN_TIMEOUT);
            Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
            if (!"doing".equals(row.get("state"))) {
                throw new AssertionError("禁用的送出鈕不該真的送出完修，但後端該單狀態是 "
                        + row.get("state") + "（應仍為 doing／維修中）");
            }
        }
    }

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

    @那麼("加入照片的按鈕已消失")
    public void addPhotoButtonGone() {
        // 先等報修面板本身在畫面上，否則面板還沒畫出來時的「找不到」會誤判為通過。
        world.waitForTestId("repair.sheet", SCREEN_TIMEOUT);
        if (!world.driver().findElements(world.byTestId("repair.photo.add-button")).isEmpty()) {
            throw new AssertionError("已附滿 3 張時不應再有加入照片的按鈕，但畫面上找得到");
        }
    }

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

    @那麼("工務單與照片保存在手機，畫面標示待送出")
    public void ticketAndPhotosSavedOffline() {
        world.waitForTestId("ticket.summary.offline-queued-label", SCREEN_TIMEOUT);
        // 畫面上的待送出標示與離線列的筆數講的是同一條佇列；只看標示的話，一個
        // 根本沒進佇列的標示也會過關，而那正是這條 Rule 最在意的失敗方式。
        assertLabelContains("system.offline-bar.pending-count", "1", "離線列的待同步筆數");
        backendHasNoTicket();
    }

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
     * 必查：待送出標示消失，App 本機佇列是空的。
     *
     * <p>佇列空了的判準是問 App 自己還剩幾筆（{@link AppiumWorld#waitForPendingSyncDrained}），
     * 不是畫面頂部的離線列不見：看那一列的話這一句什麼都沒驗到，理由見那一支。
     */
    @那麼("畫面不再標示待送出")
    public void noLongerMarkedPendingSync() {
        world.waitForTestIdGone("ticket.summary.offline-queued-label", QUEUE_DRAIN_TIMEOUT);
        world.waitForPendingSyncDrained(QUEUE_DRAIN_TIMEOUT);
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
     * accessibility id：{@code repair.urgent-judgement.card}。
     *
     * <p>判定與理由合成一句讀，不是分兩句讀。理由是判定的一部分，分開驗的話
     * 「急件」與理由之間漏接成「系統判定：急件VIP · 15:00 入住」也會過。
     */
    @那麼("畫面顯示系統判定為急件，理由 {string}")
    public void showsSystemUrgentJudgement(String reason) {
        assertLabelContains("repair.urgent-judgement.card", "系統判定：急件 · " + reason, "急件判定卡");

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

    @那麼("畫面顯示系統判定為一般件")
    public void showsSystemNormalJudgement() {
        assertLabelContains("repair.urgent-judgement.card", "系統判定：一般件", "急件判定卡");

        List<Map<String, Object>> reservations = todayReservationsForRepairRoom();
        if (!reservations.isEmpty()) {
            throw new AssertionError("畫面判成一般件，但後端「" + context.repairRoomNo()
                    + "」今天有訂單（" + urgentRuleOf(reservations.get(0)) + "），該判成急件");
        }
    }

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
     * 必查：工務單詳情的狀態摘要卡或狀態列（{@code ticket.status-summary-card}）顯示該文案，
     * 而且與後端的工務單狀態對照表一致。房務端的文案多半還帶著工務的名字與一句補充，
     * 工務端與主管視角的狀態列在已完修時後面另接完修結果，所以比對的是「文案包含這一段」
     * 而不是整句相等。
     *
     * <p>固定文案查 {@link #STATE_BY_SUMMARY}；帶欄位的四種文案各自多比後端欄位
     * （{@code readRepairTicket} 的回應比 JDBC 多了姓名的 JOIN）：
     * <ul>
     *   <li>「已結案 · 不需處理」：closed、{@code resolution_type}=no_action_needed、沒有驗收人。</li>
     *   <li>「已結案 · {驗收人}驗收」：closed，驗收人就是文案上那一位（{@code verified_by} 與
     *       {@code verifiedBy} 兩邊都比）——不比驗收人的話畫面顯示別人的名字也算過。</li>
     *   <li>「退回重修中 · {處理人} 處理中」（房務端）：退回重修中，處理人是文案上那一位。</li>
     *   <li>「退回重修 · {判定人} 判定不通過」（工務端與主管視角）：退回重修中，最近一次判
     *       不通過的是文案上那一位（{@code last_verify_failed_by} 與 {@code lastVerifyFailedBy}）。</li>
     * </ul>
     * 退回重修中＝accepted 或 doing、{@code last_verify_failed_at} 有值、{@code tasks.done_at} 為空。
     */
    @那麼("工務單詳情顯示狀態摘要卡 {string}")
    public void ticketDetailShowsStatusSummary(String summaryText) {
        assertLabelContains("ticket.status-summary-card", summaryText, "狀態摘要卡");

        Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
        String state = String.valueOf(row.get("state"));

        if (NO_ACTION_CLOSED_SUMMARY.equals(summaryText)) {
            requireState(summaryText, state, "closed");
            if (!"no_action_needed".equals(row.get("resolution_type"))) {
                throw new AssertionError("摘要卡說不需處理結案，但後端 repair_tickets.resolution_type = "
                        + row.get("resolution_type"));
            }
            if (row.get("verified_by") != null) {
                throw new AssertionError("不需處理結案不經主管驗收，但後端 verified_by = " + row.get("verified_by"));
            }
            JsonNode ticket = readTicket(row);
            if (!"no_action_needed".equals(ticket.path("resolutionType").asText(null))
                    || ticket.hasNonNull("verifiedBy")) {
                throw new AssertionError("readRepairTicket 應為 resolutionType=no_action_needed、verifiedBy=null，實際 "
                        + ticket.path("resolutionType").asText(null) + "、" + ticket.path("verifiedBy").asText(null));
            }
            return;
        }
        if (summaryText.startsWith(CLOSED_PREFIX) && summaryText.endsWith("驗收")) {
            requireState(summaryText, state, "closed");
            String verifierName = summaryText.substring(CLOSED_PREFIX.length(),
                    summaryText.length() - "驗收".length()).trim();
            UUID expected = world.staff(verifierName).id();
            if (!expected.toString().equalsIgnoreCase(String.valueOf(row.get("verified_by")))) {
                throw new AssertionError("摘要卡說驗收人是「" + verifierName
                        + "」，後端 verified_by = " + row.get("verified_by"));
            }
            String apiVerifiedBy = readTicket(row).path("verifiedBy").asText(null);
            if (!verifierName.equals(apiVerifiedBy)) {
                throw new AssertionError("摘要卡說驗收人是「" + verifierName
                        + "」，readRepairTicket.verifiedBy = " + apiVerifiedBy);
            }
            return;
        }
        if (summaryText.startsWith(REWORK_REPORTER_PREFIX) && summaryText.endsWith(" 處理中")) {
            requireReworkInProgress(summaryText, row);
            String assigneeName = summaryText.substring(REWORK_REPORTER_PREFIX.length(),
                    summaryText.length() - " 處理中".length()).trim();
            UUID expected = world.staff(assigneeName).id();
            if (!expected.toString().equalsIgnoreCase(String.valueOf(row.get("assignee_id")))) {
                throw new AssertionError("摘要卡說重修的是「" + assigneeName
                        + "」，後端 tasks.assignee_id = " + row.get("assignee_id"));
            }
            return;
        }
        if (summaryText.startsWith(REWORK_PREFIX) && summaryText.endsWith(" 判定不通過")) {
            requireReworkInProgress(summaryText, row);
            String judgeName = summaryText.substring(REWORK_PREFIX.length(),
                    summaryText.length() - " 判定不通過".length()).trim();
            UUID expected = world.staff(judgeName).id();
            if (!expected.toString().equalsIgnoreCase(String.valueOf(row.get("last_verify_failed_by")))) {
                throw new AssertionError("狀態列說判不通過的是「" + judgeName
                        + "」，後端 last_verify_failed_by = " + row.get("last_verify_failed_by"));
            }
            String apiJudge = readTicket(row).path("lastVerifyFailedBy").asText(null);
            if (!judgeName.equals(apiJudge)) {
                throw new AssertionError("狀態列說判不通過的是「" + judgeName
                        + "」，readRepairTicket.lastVerifyFailedBy = " + apiJudge);
            }
            return;
        }
        requireState(summaryText, state, STATE_BY_SUMMARY.get(summaryText));
    }

    /**
     * 退回重修中的權威狀態：accepted 或 doing、最近一次不通過的時間有值、完修時間為空
     * （不通過會清空 {@code tasks.done_at}；再完修後才又有值，那時已不是重修中）。
     */
    private static void requireReworkInProgress(String summaryText, Map<String, Object> row) {
        String state = String.valueOf(row.get("state"));
        if (!"accepted".equals(state) && !"doing".equals(state)) {
            throw new AssertionError("「" + summaryText + "」是退回重修中，後端狀態應為 accepted 或 doing，實際是 " + state);
        }
        if (row.get("last_verify_failed_at") == null) {
            throw new AssertionError("「" + summaryText + "」是退回重修中，後端 last_verify_failed_at 卻是 NULL");
        }
        if (row.get("task_done_at") != null) {
            throw new AssertionError("「" + summaryText + "」是退回重修中，後端 tasks.done_at 應為空，實際 "
                    + row.get("task_done_at"));
        }
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
     * 必查：詳情顯示該降級原因文字；後端
     * {@code repair_tickets.urgent_downgrade_reason} 等於該原因，而且這張單已經不是急件
     * ——只留下原因卻還掛著急件，代表降級只做了一半。
     */
    @那麼("工務單詳情顯示急件降級原因 {string}")
    public void ticketDetailShowsUrgentDowngradeReason(String reason) {
        // 降級原因寫在審核進度時間軸那一步的註腳（`Main.dc.html:4782` 的
        // `'主管改為一般件：' + w.downgrade`），畫面上沒有獨立的降級原因節點。
        assertLabelContains("ticket.timeline", reason, "急件降級原因");

        Map<String, Object> row = singleTicketRow(
                "rt.urgent_downgrade_reason = ?", "降級原因為「" + reason + "」的工務單", reason);
        if (Boolean.TRUE.equals(row.get("is_urgent")) || Boolean.TRUE.equals(row.get("urgent"))) {
            throw new AssertionError("已降級的工務單在後端仍是急件（is_urgent="
                    + row.get("is_urgent") + "、tasks.urgent=" + row.get("urgent") + "）");
        }
    }

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
     * 必查：後端該工務單狀態仍是維修中，沒有新的完修事件、完修照片與完修結果。
     *
     * <p>「完修事件」與「完修結果」在後端是同一筆真相——{@code task_events.type = 'completed'}
     * （結果 {@code resolutionType} 放在它的 payload 裡；{@code repair_tickets} 沒有獨立的
     * 結果欄），所以查無 completed 事件就同時排除了兩者。「完修照片」另查
     * {@code repair_photos.purpose = 'completion'}——照片走獨立的兩段式上傳，就算完修
     * 請求本身沒送出去，先建出 completion 照片列也算走漏，要分開驗。
     */
    @那麼("後端沒有收到這次完修")
    public void backendHasNotReceivedCompletion() {
        Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
        String state = String.valueOf(row.get("state"));
        if (!"doing".equals(state)) {
            throw new AssertionError("後端該工務單應仍是維修中（doing），實際是 " + state);
        }

        Object taskId = row.get("task_id");
        List<Map<String, Object>> completedEvents = world.backend().queryRows("""
                SELECT id FROM task_events
                WHERE tenant_id = ? AND task_id = ? AND type = 'completed'
                """, world.backend().ensureTenant(), taskId);
        if (!completedEvents.isEmpty()) {
            throw new AssertionError("後端不該有這張單的完修事件（task_events.type=completed），實際查到 "
                    + completedEvents.size() + " 筆");
        }

        List<Map<String, Object>> completionPhotos = world.backend().queryRows("""
                SELECT id FROM repair_photos
                WHERE tenant_id = ? AND ticket_task_id = ? AND purpose = 'completion'
                """, world.backend().ensureTenant(), taskId);
        if (!completionPhotos.isEmpty()) {
            throw new AssertionError("後端不該有這張單的完修照片（repair_photos.purpose=completion），實際查到 "
                    + completionPhotos.size() + " 張");
        }
    }

    /**
     * 必查：畫面上沒有完成畫面那一層覆蓋層（accessibility id
     * {@code celebrate.screen} 不存在），員工留在工務單自己的回報流程上。
     * 用於工務單的完修回報——那張卡是房務打掃與備品完成才走的，工務單有
     * 自己的回報與驗收流程。
     *
     * <p>只檢查「有沒有跳走」不夠，要確認那一層根本沒出現，所以這裡不是最後看一眼
     * 停在哪一頁，而是從送出完修那一刻起就每一輪檢查覆蓋層在不在，檢查的方式見
     * {@link #requireNoCelebrateOverlay()}。
     *
     * <p>盯的範圍分成兩段各自計時。第一段用 {@link #COMPLETION_SUBMIT_TIMEOUT} 輪詢等
     * 完修回報面板關閉（{@code completeSheet.submit-button} 不在了）且畫面停在工務單詳情
     * （{@code ticket.screen} 還在），確認送出真的做完；等不到就失敗，否則沒跑完的場次會
     * 偽裝成通過。第二段在確認送出做完的那一刻重新起算整段 {@link #CELEBRATE_SETTLE}，
     * 讓畫面有機會把覆蓋層畫出來。兩段分開計時，是因為合用一份預算時送出愈慢、
     * 盯的時間愈短，而那種場次已經算通過，不會有人發現少盯了。
     */
    @那麼("不出現完成畫面")
    public void noCompletionCelebrationOverlay() {
        long submitDeadline = System.nanoTime() + COMPLETION_SUBMIT_TIMEOUT.toNanos();
        boolean completionSettled = false;
        while (System.nanoTime() < submitDeadline) {
            requireNoCelebrateOverlay();
            if (world.driver().findElements(world.byTestId("completeSheet.submit-button")).isEmpty()
                    && !world.driver().findElements(world.byTestId("ticket.screen")).isEmpty()) {
                completionSettled = true;
                break;
            }
            pause(200);
        }

        if (!completionSettled) {
            throw new AssertionError("等不到完修回報送出完成（面板沒關掉、或畫面不在工務單詳情），"
                    + "逾時 " + COMPLETION_SUBMIT_TIMEOUT.getSeconds()
                    + "s；這樣「沒出現完成畫面」只是因為送出還沒走完");
        }

        long settleDeadline = System.nanoTime() + CELEBRATE_SETTLE.toNanos();
        while (System.nanoTime() < settleDeadline) {
            requireNoCelebrateOverlay();
            pause(200);
        }
        // 迴圈是先查再睡，最後那次睡之後還沒查過，補一次才算盯滿整段。
        requireNoCelebrateOverlay();
    }

    /**
     * 這一輪畫面上不能有完成畫面那一層覆蓋層，查到就當場失敗。以 {@code findElements}
     * 回傳的空清單判定不存在：找不到就是空清單、不必等逾時，所以能密集輪詢。
     * 覆蓋層是疊在任務頁上的一層，可以出現一下再自己收掉，只在最後抓一張畫面
     * 會整個漏掉，所以要每一輪都查。
     */
    private void requireNoCelebrateOverlay() {
        List<WebElement> overlay = world.driver().findElements(world.byTestId(CELEBRATE_OVERLAY_ID));
        if (!overlay.isEmpty()) {
            throw new AssertionError("工務單的完修回報不走房務的完成畫面，"
                    + "但畫面上出現了 " + CELEBRATE_OVERLAY_ID + " 這一層覆蓋層（"
                    + overlay.size() + " 個）");
        }
    }

    /**
     * 必查：工務單詳情頂部步驟條（{@code ticket.stepper.{n}}）固定顯示這四步，目前那一步掛
     * {@code ticket.stepper.{n}--current}，之前的步驟為已完成（{@code ticket.stepper.{n}--done}）、
     * 之後的未到（沒有 {@code --done}）；與後端狀態對照一致：待開始＝等工務接單、已接單或退回
     * 重修中的待開始（pool、accepted），維修中＝doing，驗收＝已完修等主管驗收（done）。
     * 已結案四步全部完成、沒有目前步驟，由「第四步」那一句負責；「完修」沒有對應的停留狀態。
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
        for (int i = 0; i < steps.size(); i++) {
            boolean done = !world.driver().findElements(world.byTestId(stepperDoneId(i + 1))).isEmpty();
            if (i < currentIndex && !done) {
                throw new AssertionError("步驟條第 " + (i + 1) + " 步「" + steps.get(i)
                        + "」在目前步驟之前，應為已完成，畫面上找不到 " + stepperDoneId(i + 1));
            }
            if (i >= currentIndex && done) {
                throw new AssertionError("步驟條第 " + (i + 1) + " 步「" + steps.get(i)
                        + "」還沒到，不該標成已完成，但畫面上找得到 " + stepperDoneId(i + 1));
            }
        }

        Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
        String state = String.valueOf(row.get("state"));
        boolean consistent = switch (currentStep) {
            case "待開始" -> "pool".equals(state) || "accepted".equals(state);
            case "維修中" -> "doing".equals(state);
            case "驗收" -> "done".equals(state);
            default -> throw new IllegalArgumentException("認不得的目前步驟「" + currentStep
                    + "」（只支援 待開始｜維修中｜驗收；已結案改用「第四步」那一句）");
        };
        if (!consistent) {
            throw new AssertionError("目前步驟「" + currentStep + "」與後端不一致：tasks.state = " + state);
        }
    }

    /**
     * 必查：步驟條第四步「驗收」的完成樣式（{@code ticket.stepper.4--done}）符合；已完成只在
     * 後端 {@code tasks.state}=closed（驗收通過或不需處理結案）時成立，已完修等驗收與退回重修中
     * 都是尚未完成。
     */
    @那麼("工務單詳情的步驟條第四步「驗收」為 {string}")
    public void ticketDetailFourthStepIs(String completion) {
        boolean expectedDone = switch (completion) {
            case "已完成" -> true;
            case "尚未完成" -> false;
            default -> throw new IllegalArgumentException(
                    "認不得的完成狀態「" + completion + "」（只支援 已完成｜尚未完成）");
        };
        WebElement step = world.waitForTestId("ticket.stepper.4", SCREEN_TIMEOUT);
        String text = step.getText();
        if (text == null || !text.contains("驗收")) {
            throw new AssertionError("步驟條第四步應為「驗收」，畫面上是「" + text + "」");
        }
        if (expectedDone) {
            world.waitForTestId(stepperDoneId(4), SCREEN_TIMEOUT);
        } else if (!world.driver().findElements(world.byTestId(stepperDoneId(4))).isEmpty()) {
            throw new AssertionError("步驟條第四步「驗收」應為尚未完成，但畫面上找得到 " + stepperDoneId(4));
        }

        Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
        boolean closed = "closed".equals(String.valueOf(row.get("state")));
        if (closed != expectedDone) {
            throw new AssertionError("第四步「驗收」為「" + completion + "」，但後端 tasks.state = " + row.get("state")
                    + "（已完成只在 closed 時成立）");
        }
    }

    /** 步驟條第 [step] 步已完成的標記（產品端 {@code ManagerIds.ticketStepperDone}）。 */
    private static String stepperDoneId(int step) {
        return "ticket.stepper." + step + "--done";
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

    /**
     * 必查：工務單詳情狀態列或摘要卡下方的琥珀提示卡顯示最近一次不通過的原因與判定人姓名
     * （只顯示最近一次），與後端 {@code RepairTicket.lastVerifyFailedReason}、
     * {@code lastVerifyFailedBy}（JDBC 的 {@code last_verify_failed_reason}／{@code _by}）一致。
     * 報修人、處理人與主管三種視角共用。
     *
     * <p>提示卡沒有專屬 accessibility id，以「畫面上有含原因的元素」定位；判定人姓名同時出現在
     * 狀態列上，所以先找同一個元素同時含原因與姓名，找不到才退一步要求姓名也在畫面上。
     */
    @那麼("工務單詳情顯示最近一次不通過的原因 {string} 與判定人 {string}")
    public void ticketDetailShowsLastVerifyFailure(String reason, String managerName) {
        world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
        world.waitForLabelContaining(reason, SCREEN_TIMEOUT);
        boolean sameCard = !world.driver().findElements(AppiumBy.iOSNsPredicateString(
                "label CONTAINS " + nsQuote(reason) + " AND label CONTAINS " + nsQuote(managerName))).isEmpty();
        if (!sameCard) {
            world.waitForLabelContaining(managerName, SCREEN_TIMEOUT);
        }

        Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
        if (!reason.equals(row.get("last_verify_failed_reason"))) {
            throw new AssertionError("畫面的不通過原因「" + reason + "」與後端 last_verify_failed_reason（"
                    + row.get("last_verify_failed_reason") + "）不一致");
        }
        UUID managerId = world.staff(managerName).id();
        if (!managerId.toString().equalsIgnoreCase(String.valueOf(row.get("last_verify_failed_by")))) {
            throw new AssertionError("畫面的判定人「" + managerName + "」與後端 last_verify_failed_by（"
                    + row.get("last_verify_failed_by") + "）不一致");
        }
        JsonNode ticket = readTicket(row);
        if (!reason.equals(ticket.path("lastVerifyFailedReason").asText(null))
                || !managerName.equals(ticket.path("lastVerifyFailedBy").asText(null))) {
            throw new AssertionError("readRepairTicket 的最近一次不通過應為「" + reason + "」／「" + managerName
                    + "」，實際「" + ticket.path("lastVerifyFailedReason").asText(null) + "」／「"
                    + ticket.path("lastVerifyFailedBy").asText(null) + "」");
        }
    }

    /**
     * 必查：工務單詳情上沒有「等主管審核」「主管未核准」字樣，也沒有核准、退回或補資料再送出的
     * 按鈕或面板；後端 {@code RepairTicket.state} 屬於契約現行值集。房務、工務與主管三種視角共用。
     * 先等詳情頁出現再查「沒有」，否則畫面還沒載入時的「找不到」會誤判為通過。
     */
    @那麼("工務單詳情沒有任何主管審核的狀態或動作")
    public void ticketDetailHasNoManagerReview() {
        world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
        List<String> offenders = new ArrayList<>();
        for (String id : LEGACY_REVIEW_IDS) {
            if (!world.driver().findElements(world.byTestId(id)).isEmpty()) {
                offenders.add(id);
            }
        }
        for (String text : List.of("等主管審核", "主管未核准", "補資料再送出")) {
            if (!world.driver().findElements(AppiumBy.iOSNsPredicateString(
                    "label CONTAINS " + nsQuote(text) + " OR name CONTAINS " + nsQuote(text))).isEmpty()) {
                offenders.add("「" + text + "」字樣");
            }
        }
        if (!world.driver().findElements(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeButton' AND (label == '核准' OR label == '退回')")).isEmpty()) {
            offenders.add("核准或退回按鈕");
        }
        if (!offenders.isEmpty()) {
            throw new AssertionError("工務單詳情不該有任何主管審核的狀態或動作，但畫面上找得到：" + offenders);
        }

        Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
        String apiState = readTicket(row).path("state").asText(null);
        for (String state : List.of(String.valueOf(row.get("state")), String.valueOf(apiState))) {
            if (!CONTRACT_TICKET_STATES.contains(state)) {
                throw new AssertionError("工務單狀態「" + state + "」不在契約現行值集 " + CONTRACT_TICKET_STATES + " 裡");
            }
        }
    }

    /**
     * 必查：員工（報修人或處理人）的工務單詳情上沒有 {@code managerTicket.pass-button}、
     * {@code managerTicket.fail-button}，也沒有任何「通過」「不通過」字樣的按鈕；後端以該員工
     * 身分呼叫 {@code verifyRepairTicket} 會回 403。
     *
     * <p>403 以目前登入 App 的那位員工重送一次驗收通過來驗；回 2xx 的話單就真的被驗收了，
     * 但那正是這句要抓的走漏，斷言照樣失敗。
     */
    @那麼("工務單詳情沒有通過或不通過的動作")
    public void ticketDetailHasNoVerifyActions() {
        world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
        List<String> offenders = new ArrayList<>();
        for (String id : List.of(MANAGER_PASS_BUTTON, MANAGER_FAIL_BUTTON)) {
            if (!world.driver().findElements(world.byTestId(id)).isEmpty()) {
                offenders.add(id);
            }
        }
        if (!world.driver().findElements(AppiumBy.iOSNsPredicateString(
                "type == 'XCUIElementTypeButton' AND label CONTAINS '通過'")).isEmpty()) {
            offenders.add("「通過」或「不通過」字樣的按鈕");
        }
        if (!offenders.isEmpty()) {
            throw new AssertionError("員工的工務單詳情不該有通過或不通過的動作，但畫面上找得到：" + offenders);
        }

        Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
        BackendChannel.CreatedStaff viewer = world.loggedInStaff();
        int status = world.backend().verifyRepairTicketStatus((UUID) row.get("task_id"), viewer, "passed", null);
        if (status != 403) {
            throw new AssertionError("以「" + viewer.name() + "」身分呼叫 verifyRepairTicket 應回 403，實際 HTTP " + status);
        }
    }

    /**
     * 必查：以 {@code engPool.item.{單號}.verification-return-label} 定位的琥珀標籤顯示「驗收退回」，
     * 副文等於原因，與後端工務待認領 {@code PoolItem.verificationReturnReason} 一致。
     */
    @那麼("工務待認領中 {string} 標示驗收退回，原因 {string}")
    public void engPoolItemShowsVerificationReturn(String ticketNo, String reason) {
        String id = "engPool.item." + ticketNo + ".verification-return-label";
        world.waitForTestId(id, SCREEN_TIMEOUT);
        String label = world.labelOf(id);
        if (label == null || !label.contains("驗收退回") || !label.contains(reason)) {
            boolean subtitleShown = !world.driver().findElements(AppiumBy.iOSNsPredicateString(
                    "label CONTAINS " + nsQuote(reason))).isEmpty();
            if (label == null || !label.contains("驗收退回") || !subtitleShown) {
                throw new AssertionError("「" + ticketNo + "」的驗收退回標籤應顯示「驗收退回」與原因「" + reason
                        + "」，標籤是「" + label + "」、畫面上" + (subtitleShown ? "" : "找不到") + "含原因的文字");
            }
        }

        Map<String, Object> row = singleTicketRow("rt.ticket_no = ?", "單號 " + ticketNo + " 的工務單", ticketNo);
        String taskId = String.valueOf(row.get("task_id"));
        JsonNode match = null;
        for (JsonNode item : world.backend().getJson("/pool?dept=engineering", world.loggedInStaff()).path("items")) {
            if (taskId.equalsIgnoreCase(item.path("taskId").asText())) {
                match = item;
            }
        }
        if (match == null) {
            throw new AssertionError("後端工務待認領查不到「" + ticketNo + "」（taskId " + taskId + "）");
        }
        String apiReason = match.path("verificationReturnReason").asText(null);
        if (!reason.equals(apiReason)) {
            throw new AssertionError("「" + ticketNo + "」的 PoolItem.verificationReturnReason 應為「" + reason
                    + "」，實際「" + apiReason + "」");
        }
    }

    // ---- 主管視角工務單詳情（managerTicket）的 Then。畫面 id 照 RepairContext 的常數
    //      （來源 app/mobile/lib/features/manager/manager_ids.dart）。 ----

    /** 「驗收時間為現在」的容差：App BDD 沒有可注入的後端 Clock，只能以「幾秒內」收斂。 */
    private static final Duration NOW_TOLERANCE = Duration.ofSeconds(60);

    /** 主管視角的驗收鈕：實心「通過」與描邊「不通過」（產品端 {@code ManagerIds.ticketPassButton}／{@code ticketFailButton}）。 */
    private static final String MANAGER_PASS_BUTTON = "managerTicket.pass-button";
    private static final String MANAGER_FAIL_BUTTON = "managerTicket.fail-button";

    /** 不通過面板的送出鈕（{@code ManagerIds.ticketFailSheetSubmitButton}）。 */
    private static final String MANAGER_FAIL_SUBMIT_BUTTON = "managerTicket.fail-sheet.submit-button";

    /** 不通過送出後覆蓋的驗收結果卡（{@code ManagerIds.ticketResult}）。 */
    private static final String MANAGER_RESULT_CARD = "managerTicket.result";

    /** 已收回的主管審核入口：核准、退回、退回面板、補資料再送出。畫面上一個都不該出現。 */
    private static final List<String> LEGACY_REVIEW_IDS = List.of(
            "managerTicket.approve-button",
            "managerTicket.reject-button",
            "managerTicket.reject-sheet.reason-field",
            "managerTicket.reject-sheet.submit-button",
            "ticket.resubmit-button");

    /** 契約 {@code RepairTicket.state} 的現行值集。 */
    private static final Set<String> CONTRACT_TICKET_STATES =
            Set.of("pool", "accepted", "doing", "done", "closed", "cancelled");

    /** 主管視角完修回報區的完修照片縮圖 id 前綴（{@code ticket.completion-photo.{n}}）。 */
    private static final String COMPLETION_PHOTO_PREFIX = "ticket.completion-photo.";

    /**
     * 必查：主管視角詳情的底部動作列剛好只有列出的按鈕：「通過、不通過」＝
     * {@code managerTicket.pass-button} 與 {@code managerTicket.fail-button} 都存在且可按；
     * 「無」＝兩顆都不存在。與後端 {@code RepairTicket.state} 對照：done 是通過、不通過，
     * 其他一律無（只看狀態，不看驗收欄位）。
     */
    @那麼("工務單詳情的主管動作為 {string}")
    public void managerActionsAre(String actionsText) {
        Set<String> expected = switch (actionsText) {
            case "通過、不通過" -> Set.of(MANAGER_PASS_BUTTON, MANAGER_FAIL_BUTTON);
            case "無" -> Set.of();
            default -> throw new IllegalArgumentException(
                    "認不得的主管動作「" + actionsText + "」（只支援 通過、不通過｜無）");
        };
        world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
        for (String id : expected) {
            WebElement button = world.waitForTestId(id, SCREEN_TIMEOUT);
            if (!button.isEnabled()) {
                throw new AssertionError("主管動作 " + id + " 在畫面上，但不可按");
            }
        }
        Set<String> present = new LinkedHashSet<>();
        for (String id : List.of(MANAGER_PASS_BUTTON, MANAGER_FAIL_BUTTON)) {
            if (!world.driver().findElements(world.byTestId(id)).isEmpty()) {
                present.add(id);
            }
        }
        if (!present.equals(expected)) {
            throw new AssertionError("主管動作應剛好是 " + expected + "，畫面上是 " + present);
        }

        Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
        String state = readTicket(row).path("state").asText(String.valueOf(row.get("state")));
        String expectedByBackend = "done".equals(state) ? "通過、不通過" : "無";
        if (!expectedByBackend.equals(actionsText)) {
            throw new AssertionError("後端 state=" + state + " 對照出的主管動作應為「"
                    + expectedByBackend + "」，劇本期望「" + actionsText + "」");
        }
    }

    /**
     * 必查：主管視角詳情的資訊卡逐項顯示表格中的值（照片張數比對縮圖數
     * {@code ticket.photo.{n}}），與後端 {@code readRepairTicket} 回應的 {@code photoCount}、
     * {@code category}、{@code description}、{@code reporterName} 一致。
     *
     * <p>類別、說明、報修人在詳情頁沒有獨立的 accessibility id（標題區的描述行與資訊卡的
     * 列都是裸 Text），以「畫面上有含該文字的元素」定位；照片張數數縮圖。
     * Data Table 欄：{@code 項目}（照片張數、類別、說明、報修人）、{@code 值}。
     */
    @那麼("工務單詳情顯示報修內容：")
    public void ticketDetailShowsReportedContent(DataTable table) {
        world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
        Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
        JsonNode ticket = readTicket(row);

        for (Map<String, String> line : table.asMaps()) {
            String item = requireCell(line, "項目");
            String value = requireCell(line, "值");
            switch (item) {
                case "照片張數" -> {
                    int expected = Integer.parseInt(value);
                    int onScreen = countIdsMatching("ticket.photo.", "ticket\\.photo\\.[0-9]+");
                    if (onScreen != expected) {
                        throw new AssertionError("報修照片縮圖應有 " + expected + " 張，畫面上 " + onScreen + " 張");
                    }
                    int api = ticket.path("photoCount").asInt(-1);
                    if (api != expected) {
                        throw new AssertionError("readRepairTicket.photoCount 應為 " + expected + "，實際 " + api);
                    }
                }
                case "類別" -> {
                    world.waitForLabelContaining(value, SCREEN_TIMEOUT);
                    String code = CATEGORY_CODE_BY_LABEL.get(value);
                    if (code == null) {
                        throw new IllegalArgumentException("認不得的類別「" + value + "」（合法值："
                                + CATEGORY_CODE_BY_LABEL.keySet() + "）");
                    }
                    String api = ticket.path("category").asText(null);
                    if (!code.equals(api)) {
                        throw new AssertionError("readRepairTicket.category 應為 " + code + "（" + value + "），實際 " + api);
                    }
                }
                case "說明" -> {
                    world.waitForLabelContaining(value, SCREEN_TIMEOUT);
                    String api = ticket.path("description").asText(null);
                    if (!value.equals(api)) {
                        throw new AssertionError("readRepairTicket.description 應為「" + value + "」，實際「" + api + "」");
                    }
                }
                case "報修人" -> {
                    world.waitForLabelContaining(value, SCREEN_TIMEOUT);
                    String api = ticket.path("reporterName").asText(null);
                    if (!value.equals(api)) {
                        throw new AssertionError("readRepairTicket.reporterName 應為「" + value + "」，實際「" + api + "」");
                    }
                }
                default -> throw new IllegalArgumentException(
                        "認不得的項目「" + item + "」（只支援 照片張數｜類別｜說明｜報修人）");
            }
        }
    }

    /**
     * 必查：主管視角詳情的完修回報區顯示該張數的完修照片縮圖（{@code ticket.completion-photo.{n}}）
     * 與該處理說明（{@code ticket.resolution-label} 的「說明：{說明}」），與後端
     * {@code RepairTicket.resolutionNote} 及 purpose=completion 的照片
     * （{@code completionPhotos}、{@code repair_photos.purpose='completion'}）一致。
     */
    @那麼("工務單詳情顯示完修照片 {int} 張與處理說明 {string}")
    public void ticketDetailShowsCompletionReport(int photoCount, String note) {
        world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
        assertLabelContains("ticket.resolution-label", "說明：" + note, "完修回報的處理說明");
        int onScreen = countIdsMatching(COMPLETION_PHOTO_PREFIX, "ticket\\.completion-photo\\.[0-9]+");
        if (onScreen != photoCount) {
            throw new AssertionError("完修照片縮圖應有 " + photoCount + " 張，畫面上 " + onScreen + " 張");
        }

        Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
        JsonNode ticket = readTicket(row);
        String apiNote = ticket.path("resolutionNote").asText(null);
        if (!note.equals(apiNote)) {
            throw new AssertionError("readRepairTicket.resolutionNote 應為「" + note + "」，實際「" + apiNote + "」");
        }
        int apiPhotos = ticket.path("completionPhotos").size();
        if (apiPhotos != photoCount) {
            throw new AssertionError("readRepairTicket.completionPhotos 應有 " + photoCount + " 張，實際 " + apiPhotos + " 張");
        }
        List<Map<String, Object>> completionPhotos = world.backend().queryRows("""
                SELECT id FROM repair_photos
                WHERE tenant_id = ? AND ticket_task_id = ? AND purpose = 'completion'
                """, world.backend().ensureTenant(), row.get("task_id"));
        if (completionPhotos.size() != photoCount) {
            throw new AssertionError("後端 repair_photos（purpose=completion）應有 " + photoCount
                    + " 張，實際 " + completionPhotos.size() + " 張");
        }
    }

    /**
     * 必查：{@code managerTicket.fail-sheet.submit-button} 的可互動狀態符合；禁用時無法真的
     * 送出，後端沒有收到不通過請求（原因去掉前後空白後為空就是禁用）。
     * 與報修面板、完修回報的同名句型各自獨立；禁用時真的按下去再驗「不通過沒有發生」，
     * 只讀 enabled 屬性的話一顆畫成灰色但照樣送得出去的按鈕也會過關。
     */
    @那麼("不通過面板的送出鈕為 {string}")
    public void failSheetSubmitButtonState(String enabledOrDisabled) {
        boolean expectedEnabled = switch (enabledOrDisabled) {
            case "啟用" -> true;
            case "禁用" -> false;
            default -> throw new IllegalArgumentException(
                    "認不得的按鈕狀態「" + enabledOrDisabled + "」（只支援 啟用｜禁用）");
        };
        WebElement submit = world.waitForTestId(MANAGER_FAIL_SUBMIT_BUTTON, SCREEN_TIMEOUT);
        if (submit.isEnabled() != expectedEnabled) {
            throw new AssertionError("不通過面板的送出鈕應為「" + enabledOrDisabled + "」，實際是「"
                    + (submit.isEnabled() ? "啟用" : "禁用") + "」");
        }
        if (!expectedEnabled) {
            try {
                submit.click();
            } catch (RuntimeException ignored) {
                // 禁用鈕拒絕點擊而拋例外，正是「送出沒有發生」的一種表現。
            }
            // 面板沒有因送出而關掉：送出鈕仍在畫面上。
            world.waitForTestId(MANAGER_FAIL_SUBMIT_BUTTON, SCREEN_TIMEOUT);
            backendHasNotReceivedVerificationFailure();
        }
    }

    /**
     * 必查：後端該工務單仍為已完修（done），{@code last_verify_failed_*} 沒有改變，沒有新的
     * {@code repair_verification_failed} 事件，原處理人沒有收到推播。
     *
     * <p>「沒有改變」與「新的」以這一輪完修時間（{@code tasks.done_at}）為界：不通過會清空
     * {@code done_at}，所以單還是 done 且 {@code done_at} 有值，本身就排除了完修之後的不通過；
     * 再要求 {@code last_verify_failed_at} 為空或早於完修、完修之後沒有不通過事件與推播，
     * 前一輪留下的不通過紀錄（重修後再驗的單）不會被誤判。
     */
    @那麼("後端沒有收到這次驗收不通過")
    public void backendHasNotReceivedVerificationFailure() {
        Map<String, Object> row = singleTicketRow("1 = 1", "本情境唯一的工務單");
        String state = String.valueOf(row.get("state"));
        if (!"done".equals(state)) {
            throw new AssertionError("後端該工務單應仍是已完修（done），實際是 " + state);
        }
        Object doneAtRaw = row.get("task_done_at");
        if (doneAtRaw == null) {
            throw new AssertionError("後端該工務單是 done，但 tasks.done_at 被清空了——不通過會清空完修時間");
        }
        Instant doneAt = toInstant(doneAtRaw);
        Object failedAtRaw = row.get("last_verify_failed_at");
        if (failedAtRaw != null && !toInstant(failedAtRaw).isBefore(doneAt)) {
            throw new AssertionError("完修（" + doneAt + "）之後不該有驗收不通過，但 last_verify_failed_at = "
                    + failedAtRaw + "、原因「" + row.get("last_verify_failed_reason") + "」");
        }
        long tenantId = world.backend().ensureTenant();
        Object taskId = row.get("task_id");
        List<Map<String, Object>> events = world.backend().queryRows("""
                SELECT id FROM task_events
                WHERE tenant_id = ? AND task_id = ? AND type = 'repair_verification_failed' AND created_at >= ?
                """, tenantId, taskId, Timestamp.from(doneAt));
        if (!events.isEmpty()) {
            throw new AssertionError("完修之後不該有這張單的驗收不通過事件（task_events.type=repair_verification_failed），實際查到 "
                    + events.size() + " 筆");
        }
        List<Map<String, Object>> pushes = world.backend().queryRows("""
                SELECT id FROM outbox
                WHERE tenant_id = ? AND kind = 'push' AND aggregate_key = ?
                  AND payload::text LIKE '%repair_verification_failed%' AND created_at >= ?
                """, tenantId, "task:" + taskId, Timestamp.from(doneAt));
        if (!pushes.isEmpty()) {
            throw new AssertionError("後端不該對原處理人送出驗收不通過推播（outbox kind=push、event=repair_verification_failed），實際查到 "
                    + pushes.size() + " 筆");
        }
        if (row.get("assignee_id") != null) {
            List<Map<String, Object>> notifications = world.backend().queryRows("""
                    SELECT id FROM notifications
                    WHERE tenant_id = ? AND recipient_staff_id = ? AND event = 'repair_verification_failed'
                      AND created_at >= ?
                    """, tenantId, row.get("assignee_id"), Timestamp.from(doneAt));
            if (!notifications.isEmpty()) {
                throw new AssertionError("原處理人不該收到驗收不通過的通知，實際查到 " + notifications.size() + " 則");
            }
        }
    }

    /**
     * 必查：驗收結果卡（{@code managerTicket.result}）標題含該單號與「退回重修」，副文含
     * 「回到{處理人}名下」與「會收到推播」，下方列出這次寫的原因；後端該單為待開始（accepted）、
     * 負責人不變，最近一筆不通過事件的去處是 assignee、原處理人是這一位
     * （{@code RepairVerificationOutcome.destination}／{@code assigneeName} 的權威來源）。
     */
    @那麼("驗收結果卡說明 {string} 退回重修，回到 {string} 名下")
    public void verificationResultBackToAssignee(String ticketNo, String assigneeName) {
        // 先等結果卡出現再讀後端：送出後到後端落地之間有一段往返，先讀會讀到送出前的單。
        world.waitForTestId(MANAGER_RESULT_CARD, SCREEN_TIMEOUT);
        Map<String, Object> row = singleTicketRow("rt.ticket_no = ?", "單號 " + ticketNo + " 的工務單", ticketNo);
        String reason = requireLastVerifyFailure(ticketNo, row);
        assertResultCardMentions(ticketNo, "退回重修", "回到" + assigneeName + "名下", "會收到推播", reason);

        if (!"accepted".equals(row.get("state"))) {
            throw new AssertionError("退回原處理人名下後「" + ticketNo + "」在後端應為待開始（accepted），實際是 " + row.get("state"));
        }
        UUID assigneeId = world.staff(assigneeName).id();
        if (!assigneeId.toString().equalsIgnoreCase(String.valueOf(row.get("assignee_id")))) {
            throw new AssertionError("「" + ticketNo + "」的負責人應仍是「" + assigneeName + "」，後端 assignee_id = "
                    + row.get("assignee_id"));
        }
        requireLastFailureDestination(ticketNo, row, "assignee", assigneeId);
    }

    /**
     * 必查：驗收結果卡標題含該單號與「退回重修」，副文含「{處理人}已下班」與「回到了工務待認領」；
     * 後端該單為待認領（pool）、沒有負責人，最近一筆不通過事件的去處是 pool、原處理人是這一位。
     * App 不自己判斷班表，只照回應呈現，所以這裡也只比對後端給的去處。
     */
    @那麼("驗收結果卡說明 {string} 退回重修，{string} 已下班，回到工務待認領")
    public void verificationResultBackToPool(String ticketNo, String assigneeName) {
        // 先等結果卡出現再讀後端：送出後到後端落地之間有一段往返，先讀會讀到送出前的單。
        world.waitForTestId(MANAGER_RESULT_CARD, SCREEN_TIMEOUT);
        Map<String, Object> row = singleTicketRow("rt.ticket_no = ?", "單號 " + ticketNo + " 的工務單", ticketNo);
        requireLastVerifyFailure(ticketNo, row);
        assertResultCardMentions(ticketNo, "退回重修", assigneeName + "已下班", "回到了工務待認領");

        if (!"pool".equals(row.get("state"))) {
            throw new AssertionError("退回工務待認領後「" + ticketNo + "」在後端應為待認領（pool），實際是 " + row.get("state"));
        }
        if (row.get("assignee_id") != null) {
            throw new AssertionError("「" + ticketNo + "」回到工務待認領不該有負責人，後端 assignee_id = " + row.get("assignee_id"));
        }
        requireLastFailureDestination(ticketNo, row, "pool", world.staff(assigneeName).id());
    }

    /** 這張單後端有最近一次不通過的原因；回傳原因給結果卡比對。 */
    private static String requireLastVerifyFailure(String ticketNo, Map<String, Object> row) {
        Object reason = row.get("last_verify_failed_reason");
        if (reason == null || row.get("last_verify_failed_at") == null) {
            throw new AssertionError("「" + ticketNo + "」剛被判不通過，後端 last_verify_failed_reason／_at 卻是空的");
        }
        return String.valueOf(reason);
    }

    /**
     * 最近一筆 {@code repair_verification_failed} 事件的 payload：{@code destination} 是去處、
     * {@code assigneeId} 是原處理人。這是驗收回應 {@code RepairVerificationOutcome} 的權威來源，
     * App 收到的那份回應事後讀不到，以事件比對。
     */
    private void requireLastFailureDestination(
            String ticketNo, Map<String, Object> row, String destination, UUID assigneeId) {
        List<Map<String, Object>> events = world.backend().queryRows("""
                SELECT payload ->> 'destination' AS destination, payload ->> 'assigneeId' AS assignee_id
                FROM task_events
                WHERE tenant_id = ? AND task_id = ? AND type = 'repair_verification_failed'
                ORDER BY created_at DESC
                LIMIT 1
                """, world.backend().ensureTenant(), row.get("task_id"));
        if (events.isEmpty()) {
            throw new AssertionError("「" + ticketNo + "」查不到驗收不通過事件（task_events.type=repair_verification_failed）");
        }
        Map<String, Object> event = events.get(0);
        if (!destination.equals(event.get("destination"))) {
            throw new AssertionError("「" + ticketNo + "」驗收不通過的去處應為 " + destination + "，事件記的是 "
                    + event.get("destination"));
        }
        if (!assigneeId.toString().equalsIgnoreCase(String.valueOf(event.get("assignee_id")))) {
            throw new AssertionError("「" + ticketNo + "」驗收不通過事件的原處理人應為 " + assigneeId + "，實際 "
                    + event.get("assignee_id"));
        }
    }

    /**
     * 驗收結果卡（{@code managerTicket.result}）在畫面上，且單號與每一段文字都看得到：先看結果卡
     * 自己的 label，結果卡的子節點各自成一個無障礙元素時，退一步找畫面上含該段文字的元素。
     */
    private void assertResultCardMentions(String ticketNo, String... fragments) {
        world.waitForTestId(MANAGER_RESULT_CARD, SCREEN_TIMEOUT);
        String cardLabel = world.labelOf(MANAGER_RESULT_CARD);
        List<String> missing = new ArrayList<>();
        List<String> all = new ArrayList<>();
        all.add(ticketNo);
        all.addAll(List.of(fragments));
        for (String fragment : all) {
            if (cardLabel != null && cardLabel.contains(fragment)) {
                continue;
            }
            if (world.driver().findElements(AppiumBy.iOSNsPredicateString(
                    "label CONTAINS " + nsQuote(fragment))).isEmpty()) {
                missing.add(fragment);
            }
        }
        if (!missing.isEmpty()) {
            throw new AssertionError("驗收結果卡應說明 " + all + "，找不到 " + missing + "；結果卡 label 是「"
                    + cardLabel + "」");
        }
    }

    /**
     * 必查：透過後端測試資料查核，{@code tasks.state}=closed（已結案），
     * {@code repair_tickets.verified_by} 是該主管、{@code verified_at} 等於現在（App BDD 沒有
     * 可注入的後端 Clock，以 {@link #NOW_TOLERANCE} 內收斂）；{@code readRepairTicket} 的
     * {@code state}、{@code verifiedBy}、{@code verifiedAt} 一致。
     */
    @那麼("{string} 已由 {string} 驗收，驗收時間為現在")
    public void ticketVerifiedByManagerNow(String ticketNo, String managerName) {
        Map<String, Object> row = singleTicketRow("rt.ticket_no = ?", "單號 " + ticketNo + " 的工務單", ticketNo);
        if (!"closed".equals(row.get("state"))) {
            throw new AssertionError("驗收通過後「" + ticketNo + "」在後端應為 closed（已結案），實際是 " + row.get("state"));
        }
        UUID managerId = world.staff(managerName).id();
        if (!managerId.toString().equalsIgnoreCase(String.valueOf(row.get("verified_by")))) {
            throw new AssertionError("「" + ticketNo + "」的 verified_by 應為「" + managerName + "」（" + managerId
                    + "），實際 " + row.get("verified_by"));
        }
        Object verifiedAtRaw = row.get("verified_at");
        if (verifiedAtRaw == null) {
            throw new AssertionError("「" + ticketNo + "」的 verified_at 應為現在，實際 NULL");
        }
        Instant verifiedAt = toInstant(verifiedAtRaw);
        Instant now = Instant.now();
        if (Duration.between(verifiedAt, now).abs().compareTo(NOW_TOLERANCE) > 0) {
            throw new AssertionError("「" + ticketNo + "」的 verified_at 應為現在（" + now + " ± "
                    + NOW_TOLERANCE.getSeconds() + "s），實際 " + verifiedAt);
        }

        JsonNode ticket = readTicket(row);
        String apiState = ticket.path("state").asText(null);
        if (!"closed".equals(apiState)) {
            throw new AssertionError("readRepairTicket.state 應為 closed，實際 " + apiState);
        }
        String apiVerifiedBy = ticket.path("verifiedBy").asText(null);
        if (!managerName.equals(apiVerifiedBy)) {
            throw new AssertionError("readRepairTicket.verifiedBy 應為「" + managerName + "」，實際「" + apiVerifiedBy + "」");
        }
        String apiVerifiedAt = ticket.path("verifiedAt").asText(null);
        if (apiVerifiedAt == null || !OffsetDateTime.parse(apiVerifiedAt).toInstant().truncatedTo(ChronoUnit.MILLIS)
                .equals(verifiedAt.truncatedTo(ChronoUnit.MILLIS))) {
            throw new AssertionError("readRepairTicket.verifiedAt（" + apiVerifiedAt + "）與 repair_tickets.verified_at（"
                    + verifiedAt + "）不一致");
        }
    }

    /** 類別中文 → 契約 {@code RepairCategory}（與 Given／When 的對照表同一套權威值）。 */
    private static final Map<String, String> CATEGORY_CODE_BY_LABEL = Map.of(
            "冷氣", "air_conditioning",
            "水電", "plumbing_electrical",
            "家具／設備", "furniture_equipment",
            "門鎖", "door_lock",
            "其他", "other");

    /**
     * 權威讀回：{@code readRepairTicket}（{@code GET /repair-tickets/{taskId}}）的回應，以目前
     * 登入 App 的人（主管／工務／房務都看得到自己該看的單）的身分讀。比 JDBC 多了姓名的
     * JOIN（reporterName、verifiedBy）與事件推出來的時間（reviewRequestedAt）。
     */
    private JsonNode readTicket(Map<String, Object> row) {
        return world.backend().getJson("/repair-tickets/" + row.get("task_id"), world.loggedInStaff());
    }

    /** 數畫面上 id 以某前綴開頭且整個 id 符合 regex 的元素（排除同前綴的子節點，例如 add-button）。 */
    private int countIdsMatching(String prefix, String regex) {
        return (int) world.findTestIdsWithPrefix(prefix).stream().filter(id -> id.matches(regex)).count();
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return OffsetDateTime.parse(String.valueOf(value)).toInstant();
    }

    /** 把文字包成 iOS NSPredicate 的字串常值（跳脫反斜線與單引號）。 */
    private static String nsQuote(String text) {
        return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static String requireCell(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺必填欄「" + column + "」：" + row);
        }
        return value.trim();
    }

    /**
     * 固定的狀態摘要卡文案 → 權威 {@code tasks.state}（對照 dsl 的狀態用語表）。房務端的
     * 「已修好 · 等主管驗收」與工務端、主管視角的「已完修 · 等主管驗收」在後端是同一個 done。
     * 帶姓名的已結案、退回重修與不需處理結案由 {@link #ticketDetailShowsStatusSummary}
     * 另外比對欄位，不放進這張表。
     */
    private static final Map<String, String> STATE_BY_SUMMARY = Map.of(
            "等工務接單", "pool",
            "已接單", "accepted",
            "維修中", "doing",
            "已修好 · 等主管驗收", "done",
            "已完修 · 等主管驗收", "done");

    /** 「已結案 · 不需處理」：完修結果選不需處理，直接結案、不經主管驗收。 */
    private static final String NO_ACTION_CLOSED_SUMMARY = "已結案 · 不需處理";

    /** 「已結案 · {驗收人}驗收」的前段。 */
    private static final String CLOSED_PREFIX = "已結案 · ";

    /** 房務端「退回重修中 · {處理人} 處理中」的前段。 */
    private static final String REWORK_REPORTER_PREFIX = "退回重修中 · ";

    /** 工務端與主管視角「退回重修 · {判定人} 判定不通過」的前段。 */
    private static final String REWORK_PREFIX = "退回重修 · ";

    /** 輪詢畫面時的間隔；被中斷就不再裝作沒事，否則盯不到覆蓋層會被誤讀成通過。 */
    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("盯畫面有沒有出現完成畫面時被中斷", e);
        }
    }

    private void assertLabelContains(String testId, String expected, String what) {
        String actual = world.waitForLabelOf(testId, expected, SCREEN_TIMEOUT);
        if (actual == null || !actual.contains(expected)) {
            throw new AssertionError(what + "應包含「" + expected + "」，畫面上是「" + actual + "」");
        }
    }

    private static void requireState(String summaryText, String actualState, String expectedState) {
        if (expectedState == null) {
            throw new IllegalArgumentException("認不得的狀態摘要卡文案「" + summaryText
                    + "」（合法值：" + STATE_BY_SUMMARY.keySet() + "，另加 已結案 · 不需處理、已結案 · {驗收人}驗收、"
                    + "退回重修中 · {處理人} 處理中、退回重修 · {判定人} 判定不通過）");
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

    private static String urgentRuleOf(Map<String, Object> reservation) {
        Object checkinAt = reservation.get("checkin_at");
        OffsetDateTime moment = checkinAt instanceof Timestamp timestamp
                ? timestamp.toLocalDateTime().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime()
                : (OffsetDateTime) checkinAt;
        return (Boolean.TRUE.equals(reservation.get("vip")) ? "VIP · " : "")
                + moment.format(CHECKIN_TIME) + " 入住";
    }

    private void assertScreenNumber(String testId, int expected) {
        WebElement element = world.waitForTestId(testId, SCREEN_TIMEOUT);
        String text = element.getText();
        if (text == null || !text.trim().equals(String.valueOf(expected))) {
            throw new AssertionError(testId + " 應顯示 " + expected + "，畫面上是「" + text + "」");
        }
    }

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
                SELECT rt.*, t.state, t.source, t.urgent, t.assignee_id, t.done_at AS task_done_at
                FROM repair_tickets rt
                JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                WHERE rt.tenant_id = ?
                  AND""" + " " + condition, all);
    }

    private Map<String, Object> singleTicketRow(String condition, String what, Object... params) {
        List<Map<String, Object>> rows = ticketRows(condition, params);
        if (rows.size() != 1) {
            throw new AssertionError("預期剛好一張（" + what + "），後端查到 " + rows.size() + " 張");
        }
        return rows.get(0);
    }
}
