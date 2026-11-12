package com.athena.app.bdd.steps.repair;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.當;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class RepairWhenSteps {

    /** 畫面元素出現的等待上限：畫面切換有轉場動畫，用輪詢等待取代固定 sleep。 */
    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(8);

    /** 恢復連線後等待送出佇列清空的上限：每一筆都是一次 HTTP 來回，比畫面轉場久。 */
    private static final Duration QUEUE_DRAIN_TIMEOUT = Duration.ofSeconds(20);

    /**
     * 來源分流磚文字 → {@code engPool.category.{source}.chip} 的 {source} 段
     * （對照 {@code eng_pool_screen.dart} 骨架與工務單契約 {@code PoolItem.source}：
     * staff＝房務報修、desk＝櫃檯）。
     */
    private static final Map<String, String> SOURCE_KEY_BY_LABEL = Map.of(
            "全部", "all",
            "急件", "urgent",
            "房務報修", "staff",
            "櫃檯", "desk");

    /**
     * 完修處理結果文字 → 契約 {@code CompleteTaskRequest.resolutionType}
     * （{@code specs/truth/contracts/components/schemas/任務.yaml}）的 enum 值。不需處理可以
     * 不附照片，送出後直接結案。
     */
    private static final Map<String, String> RESOLUTION_TYPE_BY_LABEL = Map.of(
            "已修好", "fixed",
            "暫時處理", "temporary",
            "無法修復", "cannot_fix",
            "不需處理", "no_action_needed");

    /**
     * 類別文字 → 契約 {@code RepairCategory}
     * （{@code specs/truth/contracts/components/schemas/工務單.yaml}）的 enum 值，
     * 開工單表單的類別 chip id 以它當執行期變數段。
     */
    private static final Map<String, String> CATEGORY_KEY_BY_LABEL = Map.of(
            "冷氣", "air_conditioning",
            "水電", "plumbing_electrical",
            "家具／設備", "furniture_equipment",
            "門鎖", "door_lock",
            "其他", "other");

    /**
     * 劇本寫的處理人只收這兩個值，因為契約
     * {@code CreateRepairTicketRequest.assigneeId} 只分得出「指派給開單的人」與「不指派、
     * 進待認領」兩種結果，開工單面板也就只給這兩個選項，收人名的話劇本寫得出來、面板上
     * 卻點不到。
     */
    private static final Map<String, String> WHO_SEGMENT_BY_LABEL = Map.of(
            "我自己處理", "repair.who.me.segment",
            "放進待認領", "repair.who.pool.segment");

    // ---- 主管視角工務單詳情（managerTicket）驗收用的 accessibility id，對照
    // app/mobile/lib/features/manager/manager_ids.dart。
    private static final String MANAGER_PASS_BUTTON = "managerTicket.pass-button";
    private static final String MANAGER_FAIL_BUTTON = "managerTicket.fail-button";
    private static final String MANAGER_FAIL_REASON_FIELD = "managerTicket.fail-sheet.reason-field";
    private static final String MANAGER_FAIL_SUBMIT_BUTTON = "managerTicket.fail-sheet.submit-button";
    private static final String MANAGER_RESULT_CARD = "managerTicket.result";
    private static final String MANAGER_RESULT_BACK_BUTTON = "managerTicket.result.back-button";
    private static final String MANAGER_QUEUE = "manager.queue";

    private final AppiumWorld world;

    private final RepairContext context;

    public RepairWhenSteps(AppiumWorld world, RepairContext context) {
        this.world = world;
        this.context = context;
    }

    @當("{string} 在 {string} 的報修畫面拍 {int} 張照片")
    public void takePhotos(String staffName, String task, int photoCount) {
        context.openRepairSheet(staffName, task);
        for (int i = 0; i < photoCount; i++) {
            context.addPhoto("repair.photo.add-button");
        }
    }

    @當("{string} 送出工務單")
    public void submitTicket(String staffName) {
        world.staff(staffName);
        world.waitForTestId("repair.sheet", SCREEN_TIMEOUT);
        world.tapTestId("repair.submit-button", SCREEN_TIMEOUT);
    }

    @當("{string} 在 {string} 的報修畫面選擇類別 {string}")
    public void chooseRepairCategory(String staffName, String task, String category) {
        context.openRepairSheet(staffName, task);
        world.tapTestId("repair.category." + categoryKey(category) + ".chip", SCREEN_TIMEOUT);
    }

    private static String categoryKey(String categoryLabel) {
        String key = CATEGORY_KEY_BY_LABEL.get(categoryLabel);
        if (key == null) {
            throw new IllegalArgumentException("認不得的工務單類別：「" + categoryLabel + "」"
                    + "（合法值：" + CATEGORY_KEY_BY_LABEL.keySet() + "）");
        }
        return key;
    }

    /**
     * 怎麼做：從任務頁、通知或「我的」進入該工務單的詳情頁。回寫：工務單詳情內容。
     *
     * <p>三個視角的進法不同（工務走首頁「手上」單卡、報修人走任務頁的同房工務單列、
     * 主管從待驗收清單或通知點該單），分流與進法集中在
     * {@link RepairContext#openTicketDetail}，同名的 Given（Arrange 版）也走同一條路。
     */
    @當("{string} 在 App 打開 {string} 的工務單詳情")
    public void openTicketDetail(String staffName, String ticketNo) {
        context.openTicketDetail(world.staff(staffName), ticketNo);
    }

    /**
     * 怎麼做：把裝置切回有網路（測試後門的 {@code /offline}），等待待送出佇列處理完。
     *
     * <p><b>權威位置：介面根</b> {@code specs/truth/features/ios-app/dsl.md}。這一句同時
     * 服務 {@code 工務單} 與 {@code 任務執行} 兩個模組，後者用在「離線完成的那一筆，恢復
     * 連線後真的送到後端」，所以登記在介面根而不是任一模組的 {@code dsl.md}。stepdef 仍
     * 留在本類別：Cucumber glue 掃全部 steps package，模組歸屬不影響它被兩邊的 feature
     * 找到。
     *
     * <p>「處理完」以 App 本機佇列長度歸零為準，問的是 App 自己而不是畫面
     * （{@link AppiumWorld#waitForPendingSyncDrained}，那一支說明了為什麼畫面頂部的
     * 離線列答不了這個問題）。不等它就往下走，後面的權威讀回會在請求還在路上時數到
     * 比較少的筆數。
     */
    @當("{string} 的手機恢復連線")
    public void phoneBackOnline(String staffName) {
        world.staff(staffName);
        world.goOnline();
        world.waitForPendingSyncDrained(QUEUE_DRAIN_TIMEOUT);
    }

    @當("{string} 在 App 打開工務首頁")
    public void openEngineeringHome(String staffName) {
        world.ensureLoggedInAs(world.staff(staffName));
        world.waitForTestId("engHome.screen", SCREEN_TIMEOUT);
        world.waitForTestId("engHome.progress.in-hand-count", SCREEN_TIMEOUT);
    }

    @當("{string} 在 App 打開工務待認領")
    public void openEngineeringPool(String staffName) {
        world.ensureOnEngineeringPool(world.staff(staffName));
    }

    /**
     * 怎麼做：點該來源分流磚（{@code engPool.category.{source}.chip}，{source} 對照
     * {@link #SOURCE_KEY_BY_LABEL}），等列表更新——更新結果由後續 Then
     * （「工務待認領依序為…」）斷言，這裡不另設等待條件。
     */
    @當("{string} 點選工務待認領分類 {string}")
    public void pickEngineeringPoolCategory(String staffName, String sourceLabel) {
        String sourceKey = SOURCE_KEY_BY_LABEL.get(sourceLabel);
        if (sourceKey == null) {
            throw new IllegalArgumentException("認不得的待認領分類：「" + sourceLabel + "」"
                    + "（合法值：" + SOURCE_KEY_BY_LABEL.keySet() + "）");
        }
        world.tapTestId("engPool.category." + sourceKey + ".chip", SCREEN_TIMEOUT);
    }

    @當("{string} 在 App 認領 {string}")
    public void claimEngineeringTicket(String staffName, String ticketNo) {
        world.staff(staffName);
        world.tapTestId("engPool.item." + ticketNo + ".claim-button", SCREEN_TIMEOUT);
    }

    /**
     * 怎麼做：進入自己手上那張單的工務單詳情，點「放回待認領」
     * （{@code ticket.release-to-pool-button}）。權威狀態落地：等同契約
     * {@code releaseRepairTicketToPool}（{@code specs/truth/contracts/groups/現場作業/工務單.yaml}）：
     * 只允許公共區域的單，accepted／doing 轉回 pool、assignee 清空；客房單回 409
     * {@code room_ticket_not_requeueable}。回寫：工務單詳情或待認領列表。
     *
     * <p>場景沒有先走「打開 {單號} 的工務單詳情」，這裡自己從後端權威狀態查出該工務
     * 手上唯一那張單的單號再進詳情（{@link #openInHandTicketDetail}）。
     */
    @當("{string} 在工務單詳情點「放回待認領」")
    public void releaseTicketToPool(String staffName) {
        openInHandTicketDetail(staffName);
        world.tapTestId("ticket.release-to-pool-button", SCREEN_TIMEOUT);
    }

    @當("{string} 在工務單詳情完修，回報結果 {string}，附 {int} 張照片")
    public void completeRepairWithResolution(String staffName, String resolutionLabel, int photoCount) {
        String resolutionType = RESOLUTION_TYPE_BY_LABEL.get(resolutionLabel);
        if (resolutionType == null) {
            throw new IllegalArgumentException("認不得的完修處理結果：「" + resolutionLabel + "」"
                    + "（合法值：" + RESOLUTION_TYPE_BY_LABEL.keySet() + "）");
        }
        openInHandTicketDetail(staffName);
        world.tapTestId("ticket.complete-button", SCREEN_TIMEOUT);
        // 不需處理可以是 0 張：先等面板打開，接著點的結果 chip 才確定是面板上的。
        world.waitForTestId("completeSheet.photo.add-button", SCREEN_TIMEOUT);
        for (int i = 0; i < photoCount; i++) {
            context.addPhoto("completeSheet.photo.add-button");
        }
        world.tapTestId("completeSheet.result." + resolutionType, SCREEN_TIMEOUT);
        world.tapTestId("completeSheet.submit-button", SCREEN_TIMEOUT);
    }

    /**
     * 怎麼做：在工務單詳情點完修打開完修回報面板，拍指定張數照片（0 張代表不拍），
     * 選處理結果，停在面板上不送出——與 {@link #completeRepairWithResolution} 差在
     * 最後不點 {@code completeSheet.submit-button}，讓後續 Then 斷言送出鈕狀態與
     * 「後端沒有收到這次完修」。權威狀態落地：後端該工務單不變，仍是維修中。
     * 回寫：完修回報面板的照片縮圖、已選結果與送出鈕狀態。
     *
     * <p>面板本身沒有獨立的 accessibility id（{@code ticket_screen.dart} 的
     * {@code _CompleteSheet}），以面板內一定存在的 {@code completeSheet.photo.add-button}
     * 出現為「面板已打開」的判準——0 張時也要先等到它，才能保證接著點的結果 chip
     * 是面板上的，不是詳情頁殘影。
     */
    @當("{string} 在工務單詳情打開完修回報，選結果 {string}，附 {int} 張照片")
    public void openCompletionSheetWithoutSubmitting(String staffName, String resolutionLabel, int photoCount) {
        String resolutionType = RESOLUTION_TYPE_BY_LABEL.get(resolutionLabel);
        if (resolutionType == null) {
            throw new IllegalArgumentException("認不得的完修處理結果：「" + resolutionLabel + "」"
                    + "（合法值：" + RESOLUTION_TYPE_BY_LABEL.keySet() + "）");
        }
        openInHandTicketDetail(staffName);
        world.tapTestId("ticket.complete-button", SCREEN_TIMEOUT);
        world.waitForTestId("completeSheet.photo.add-button", SCREEN_TIMEOUT);
        for (int i = 0; i < photoCount; i++) {
            context.addPhoto("completeSheet.photo.add-button");
        }
        world.tapTestId("completeSheet.result." + resolutionType, SCREEN_TIMEOUT);
        // 刻意停在面板上：不點 completeSheet.submit-button。
    }

    /**
     * 怎麼做：在工務首頁點「開工單」浮動按鈕（{@code engHome.create-ticket-fab}），拍指定
     * 張數照片，填入房間／公共區域、類別、處理人後送出——表單沿用 {@code repair} 報修面板
     * 的 accessibility id 前綴（dsl 本模組說明），按鈕進入時不預先帶入任何原任務。
     * 權威狀態落地：等同後端「不經過任何房務任務開立工務單」語意——request body 對照
     * 契約 {@code CreateRepairTicketRequest}：公共區域走 {@code isPublicArea}=true
     * （{@code repair.public-area-toggle}）、客房走 {@code roomId}（房號填
     * {@code repair.room-field}，App 以 {@code GET /rooms} 換成房間 id）、處理人走
     * {@code assigneeId}（{@code repair.who.me.segment} 指派給自己、
     * {@code repair.who.pool.segment} 不指派，只有公共區域有這一段），不帶
     * {@code sourceTaskId}。回寫：工務單詳情或待認領列表。
     *
     * <p>Data table 表頭照 feature 檔：{@code 房間或公共區域}（缺欄或留白視為預設值
     * 公共區域）、{@code 類別}（必填）、{@code 照片張數}（必填）、{@code 處理人}（選填，
     * 只收「我自己處理」與「放進待認領」兩個值）。
     */
    @當("{string} 在工務首頁點「開工單」，填寫以下資訊並送出：")
    public void createTicketFromEngineeringHome(String staffName, DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        if (rows.size() != 1) {
            throw new IllegalArgumentException("「開工單」一次只開一張，data table 應恰有 1 列資料，收到 " + rows.size());
        }
        Map<String, String> row = rows.get(0);
        world.ensureOnEngineeringHome(world.staff(staffName));

        // 用 tapUntilVisible 而不是單純 tapTestId：前者會等元素位置定格再點，而且首頁的
        // 卡片還在重排時那一下會整個落空（Appium 回報點擊成功、畫面留在首頁），補點一次
        // 就進得去。等的是 repair.public-area-toggle——這顆按鈕開的是開工單面板，不是任務
        // 頁那張 repair.sheet，後者的 id 這個畫面上沒有。
        world.tapUntilVisible("engHome.create-ticket-fab", "repair.public-area-toggle", SCREEN_TIMEOUT);

        String location = row.get("房間或公共區域");
        if (location == null || location.isBlank() || "公共區域".equals(location)) {
            world.tapTestId("repair.public-area-toggle", SCREEN_TIMEOUT);
        } else {
            // 地點類型預設就是客房，但劇本既然指名了房號就點一次，前一次操作留下的狀態
            // 不會讓這一步的結果不一樣。
            world.tapTestId("repair.room-type-toggle", SCREEN_TIMEOUT);
            WebElement roomField = world.waitForTestId("repair.room-field", SCREEN_TIMEOUT);
            roomField.click();
            roomField.sendKeys(location);
        }

        String categoryLabel = row.get("類別");
        String categoryKey = categoryLabel == null ? null : CATEGORY_KEY_BY_LABEL.get(categoryLabel);
        if (categoryKey == null) {
            throw new IllegalArgumentException("認不得的工務單類別：「" + categoryLabel + "」"
                    + "（合法值：" + CATEGORY_KEY_BY_LABEL.keySet() + "）");
        }
        world.tapTestId("repair.category." + categoryKey + ".chip", SCREEN_TIMEOUT);

        int photoCount = Integer.parseInt(row.get("照片張數").trim());
        for (int i = 0; i < photoCount; i++) {
            context.addPhoto("repair.photo.add-button");
        }

        String assignee = row.get("處理人");
        if (assignee != null && !assignee.isBlank()) {
            String segment = WHO_SEGMENT_BY_LABEL.get(assignee.trim());
            if (segment == null) {
                throw new IllegalArgumentException("認不得的處理人：「" + assignee + "」"
                        + "（合法值：" + WHO_SEGMENT_BY_LABEL.keySet() + "）");
            }
            world.tapTestId(segment, SCREEN_TIMEOUT);
        }

        world.tapTestId("repair.submit-button", SCREEN_TIMEOUT);
    }

    /**
     * 怎麼做：在已開著的主管視角工務單詳情點實心「通過」（{@code managerTicket.pass-button}），
     * 不開面板、不填任何東西。權威狀態落地：連線時呼叫 {@code verifyRepairTicket}
     * （{@code VerifyRepairTicketRequest{result: passed}}，帶 {@code Idempotency-Key}），成功則
     * 單轉為已結案、{@code verified_by}＝該主管、{@code verified_at}＝現在；後端回 409 時單
     * 不變；離線時擋在送出之前、不呼叫後端、不進離線佇列。回寫：換頁後的畫面或對話框——
     * 哪一種由 Then 去驗，這裡只按到通過為止。
     */
    @當("{string} 在工務單詳情按通過")
    public void managerTapsPass(String managerName) {
        context.requireTicketDetailOpenAs(managerName);
        world.tapTestId(MANAGER_PASS_BUTTON, SCREEN_TIMEOUT);
    }

    /**
     * 怎麼做：點描邊「不通過」（{@code managerTicket.fail-button}）升起不通過面板，在
     * {@code managerTicket.fail-sheet.reason-field} 輸入原因，點
     * {@code managerTicket.fail-sheet.submit-button}。權威狀態落地：連線時呼叫
     * {@code verifyRepairTicket}（{@code VerifyRepairTicketRequest{result: failed, reason: 原因}}，
     * 帶 {@code Idempotency-Key}），成功則依後端換人判定退回原處理人名下或工務待認領；後端回
     * 409 時單不變、面板收起；離線時擋在送出之前、面板裡的原因保留。回寫：驗收結果卡或
     * 對話框，由 Then 去驗。
     */
    @當("{string} 在工務單詳情按不通過，寫下原因 {string} 並送出")
    public void managerFailsWithReason(String managerName, String reason) {
        if (reason.isBlank()) {
            throw new IllegalArgumentException("這句的原因不能是空白；原因留空的案例請用「打開不通過面板，不寫原因」");
        }
        openFailSheet(managerName);
        world.typeInto(MANAGER_FAIL_REASON_FIELD, SCREEN_TIMEOUT, reason);
        world.tapTestId(MANAGER_FAIL_SUBMIT_BUTTON, SCREEN_TIMEOUT);
    }

    /**
     * 怎麼做：點描邊「不通過」升起不通過面板，原因欄留空，停在面板上。權威狀態落地：不呼叫
     * 後端，單仍為已完修。回寫：面板上的送出鈕狀態（由「不通過面板的送出鈕為」去驗）。
     */
    @當("{string} 在工務單詳情打開不通過面板，不寫原因")
    public void managerOpensFailSheetWithoutReason(String managerName) {
        openFailSheet(managerName);
        // 刻意不輸入、不點送出：留給 Then 驗送出鈕禁用且後端沒收到這次驗收不通過。
    }

    /**
     * 怎麼做：在驗收結果卡點唯一的「回到待驗收」（{@code managerTicket.result.back-button}），
     * 等清單畫面（{@code manager.queue}）出現。權威狀態落地：不改後端。回寫：清單畫面——
     * 清單內容與首頁格數由 Then 去驗。
     */
    @當("{string} 從驗收結果卡回到待驗收清單")
    public void managerBackToVerifyQueueFromResultCard(String managerName) {
        if (!world.loggedInStaff().name().equals(managerName)) {
            throw new IllegalStateException("畫面上登入的不是「" + managerName + "」——"
                    + "劇本是否漏了讓該主管送出驗收不通過、停在結果卡的那一句？");
        }
        world.waitForTestId(MANAGER_RESULT_CARD, SCREEN_TIMEOUT);
        world.tapUntilVisible(MANAGER_RESULT_BACK_BUTTON, MANAGER_QUEUE, SCREEN_TIMEOUT);
    }

    /** 點「不通過」升起不通過面板，等原因欄與送出鈕都在畫面上才算面板開好。 */
    private void openFailSheet(String managerName) {
        context.requireTicketDetailOpenAs(managerName);
        world.tapTestId(MANAGER_FAIL_BUTTON, SCREEN_TIMEOUT);
        world.waitForTestId(MANAGER_FAIL_REASON_FIELD, SCREEN_TIMEOUT);
        world.waitForTestId(MANAGER_FAIL_SUBMIT_BUTTON, SCREEN_TIMEOUT);
    }

    /**
     * 「在工務單詳情點／完修」這類句的場景沒有先導航到詳情，由這裡補：從後端權威狀態
     * 查出該工務名下（accepted／doing）唯一那張工務單的單號，點工務首頁手上列表的該
     * 單卡（{@code engHome.in-hand.{單號}.card}）進詳情，等 {@code ticket.screen} 出現。
     */
    private void openInHandTicketDetail(String staffName) {
        String ticketNo = inHandTicketNo(staffName);
        world.ensureLoggedInAs(world.staff(staffName));
        world.tapTestId("engHome.in-hand." + ticketNo + ".card", SCREEN_TIMEOUT);
        world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
    }

    /** 權威讀回：該工務名下 accepted／doing 的工務單單號；不是恰好一張就是劇本錯誤。 */
    private String inHandTicketNo(String staffName) {
        BackendChannel.CreatedStaff engineer = world.staff(staffName);
        List<Map<String, Object>> rows = world.backend().queryRows(
                """
                SELECT rt.ticket_no FROM repair_tickets rt
                JOIN tasks t ON t.tenant_id = rt.tenant_id AND t.id = rt.task_id
                WHERE rt.tenant_id = ? AND t.assignee_id = ? AND t.state IN ('accepted', 'doing')
                ORDER BY rt.created_at
                """, world.backend().ensureTenant(), engineer.id());
        if (rows.size() != 1) {
            throw new IllegalStateException("「" + staffName + "」名下的進行中工務單應恰有 1 張，"
                    + "實際 " + rows.size() + " 張——句子沒帶單號，無法判斷要進哪張單的詳情");
        }
        return (String) rows.get(0).get("ticket_no");
    }
}
