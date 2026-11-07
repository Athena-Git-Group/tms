package com.athena.app.bdd.steps.repair;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.當;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 工務單模組的 When。 */
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
     * （{@code specs/truth/contracts/components/schemas/任務.yaml}）的 enum 值。
     */
    private static final Map<String, String> RESOLUTION_TYPE_BY_LABEL = Map.of(
            "已修好", "fixed",
            "暫時處理", "temporary",
            "無法修復", "cannot_fix");

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

    private final AppiumWorld world;

    private final RepairContext context;

    public RepairWhenSteps(AppiumWorld world, RepairContext context) {
        this.world = world;
        this.context = context;
    }

    /**
     * 怎麼做：還沒在報修畫面時，先從任務頁進入；然後用相機拍指定張數。
     * 回寫：報修畫面的照片縮圖與檔案。accessibility id：{@code repair.photo.add-button}。
     */
    @當("{string} 在 {string} 的報修畫面拍 {int} 張照片")
    public void takePhotos(String staffName, String task, int photoCount) {
        context.openRepairSheet(staffName, task);
        for (int i = 0; i < photoCount; i++) {
            world.tapTestId("repair.photo.add-button", SCREEN_TIMEOUT);
        }
    }

    /**
     * 怎麼做：在報修畫面按送出（預設模式：不中斷原任務）。權威狀態落地：只有每張照片都
     * 上傳成功，而且在線上時，才會送出開單請求。
     */
    @當("{string} 送出工務單")
    public void submitTicket(String staffName) {
        world.staff(staffName);
        world.waitForTestId("repair.sheet", SCREEN_TIMEOUT);
        world.tapTestId("repair.submit-button", SCREEN_TIMEOUT);
    }

    /**
     * 怎麼做：在報修畫面選「中斷 → 需要工務協助」後送出。權威狀態落地：同
     * {@link #submitTicket(String)}，另外原任務會被中斷。
     *
     * <p>中斷開關的 accessibility id 取 {@code repair.interrupt-source-task-toggle}：
     * 報修面板的開關一律以契約 {@code CreateRepairTicketRequest} 的欄位名命名再加
     * {@code -toggle}（既有的 {@code repair.public-area-toggle} 對 {@code isPublicArea}
     * 就是這樣），讀的人從 id 就知道它送的是哪一個欄位。
     */
    @當("{string} 以中斷模式送出工務單")
    public void submitTicketInInterruptMode(String staffName) {
        world.staff(staffName);
        world.waitForTestId("repair.sheet", SCREEN_TIMEOUT);
        world.tapTestId("repair.interrupt-source-task-toggle", SCREEN_TIMEOUT);
        world.tapTestId("repair.submit-button", SCREEN_TIMEOUT);
    }

    /** 怎麼做：從任務頁進入報修畫面，點選類別。App 會呼叫開單前檢查。 */
    @當("{string} 在 {string} 的報修畫面選擇類別 {string}")
    public void chooseRepairCategory(String staffName, String task, String category) {
        context.openRepairSheet(staffName, task);
        world.tapTestId("repair.category." + categoryKey(category) + ".chip", SCREEN_TIMEOUT);
    }

    /** 類別中文 → 契約 enum；查不到就噴錯帶原詞，不猜。 */
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
     * <p>兩個視角的進法不同，依登入者部門分流：
     * <ul>
     *   <li>工務：工務首頁「手上」列表的單卡（{@code engHome.in-hand.{單號}.card}）
     *       ——工務的單一定掛在自己名下。</li>
     *   <li>房務（報修人）：dsl 的三條路（任務頁／通知／「我的」）裡只有任務頁的
     *       同房工務單區有契約可依（{@code listRepairTickets?roomId=}），所以走
     *       首頁該房任務列 → 任務頁 → 該單的工務單列
     *       （{@code task.repair-ticket.{單號}.card}）。</li>
     * </ul>
     */
    @當("{string} 在 App 打開 {string} 的工務單詳情")
    public void openTicketDetail(String staffName, String ticketNo) {
        BackendChannel.CreatedStaff staff = world.staff(staffName);
        world.ensureLoggedInAs(staff);
        if ("engineering".equals(staff.dept())) {
            world.tapTestId("engHome.in-hand." + ticketNo + ".card", SCREEN_TIMEOUT);
        } else {
            world.tapTestId("home.task." + roomNoOf(ticketNo) + ".card", SCREEN_TIMEOUT);
            world.waitForTestId("task.screen", SCREEN_TIMEOUT);
            world.tapTestId("task.repair-ticket." + ticketNo + ".card", SCREEN_TIMEOUT);
        }
        world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
    }

    /** 權威讀回：該工務單所在的房號（房務端從同房任務頁進詳情要用）。 */
    private String roomNoOf(String ticketNo) {
        List<Map<String, Object>> rows = world.backend().queryRows(
                """
                SELECT r.room_no FROM repair_tickets rt
                JOIN rooms r ON r.tenant_id = rt.tenant_id AND r.id = rt.room_id
                WHERE rt.tenant_id = ? AND rt.ticket_no = ?
                """, world.backend().ensureTenant(), ticketNo);
        if (rows.size() != 1) {
            throw new IllegalStateException("單號「" + ticketNo + "」在後端應恰有 1 張工務單，實際 "
                    + rows.size() + " 張");
        }
        return (String) rows.get(0).get("room_no");
    }

    /**
     * 怎麼做：把裝置切回有網路（測試後門的 {@code /offline}），等待待送出佇列處理完。
     *
     * <p>「處理完」以畫面頂部的離線列（{@code system.offline-bar}）消失為準：那一列顯示的
     * 就是佇列長度本身（{@code ConnectionStatus.pendingSyncCount}），它不見代表佇列已經
     * 空了。不等它就往下走，後面的權威讀回會在請求還在路上時數到比較少的筆數。
     */
    @當("{string} 的手機恢復連線")
    public void phoneBackOnline(String staffName) {
        world.staff(staffName);
        world.postBackdoorJson("/offline", "{\"offline\":false}");
        world.waitForTestIdGone("system.offline-bar", QUEUE_DRAIN_TIMEOUT);
    }

    // ──── 工務視角（T312–T314 落點） ────

    /**
     * 怎麼做：以該工務身分登入後進入首頁（{@code engHome}），等今日進度載入完成。
     *
     * <p>TEST_BACKDOOR 建置下 App 跳過登入殼（{@code app/mobile/lib/main.dart}），
     * 工務的部門分流（登入後落在 {@code engHome} 而不是房務首頁）要等 Phase 3V 的
     * [BDD-GREEN] 接上；這裡以 {@code engHome.screen}＋三個進度數字節點
     * （{@code engHome.progress.in-hand-count}，T305 骨架已掛）出現為「載入完成」的
     * 機械判準——分流還沒接上時的失敗形式是定位不到元素，不是 undefined step。
     */
    @當("{string} 在 App 打開工務首頁")
    public void openEngineeringHome(String staffName) {
        // 場景引用沒建過的員工是劇本錯誤，world.staff 會先在這裡爆出來。
        world.ensureLoggedInAs(world.staff(staffName));
        world.waitForTestId("engHome.screen", SCREEN_TIMEOUT);
        world.waitForTestId("engHome.progress.in-hand-count", SCREEN_TIMEOUT);
    }

    /**
     * 怎麼做：以該工務身分登入後切到待認領分頁（{@code engPool}），等列表載入完成。
     * 判準與入口接線的取捨同 {@link #openEngineeringHome}（以及 RepairGivenSteps
     * 的同義 Given「已在 App 打開工務待認領」）：以 {@code engPool.screen} 出現為準。
     */
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

    /**
     * 怎麼做：在工務待認領點該單的「認領這單」（{@code engPool.item.{單號}.claim-button}，
     * 單卡 id 段與 dsl 的 {@code engPool.item.{單號}.source-label} 同一套）。
     * 權威狀態落地：等同後端 {@code tasksClaim} 語意；失敗（已被他人認領）時單仍留給
     * 原認領人——撞單的畫面與權威狀態斷言在 Then「工務認領撞單…」。
     */
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

    /**
     * 怎麼做：在工務單詳情點完修（{@code ticket.complete-button}），拍指定張數照片，
     * 選處理結果後送出（完修面板沿用雛形 {@code completeSheet} 簡稱：
     * {@code completeSheet.photo.add-button}、{@code completeSheet.result.{resolutionType}}、
     * {@code completeSheet.submit-button}）。權威狀態落地：等同後端對應的完修 When——
     * {@code resolutionType} 對照 {@link #RESOLUTION_TYPE_BY_LABEL}（fixed／temporary／
     * cannot_fix）。回寫：工務單詳情。
     */
    @當("{string} 在工務單詳情完修，回報結果 {string}，附 {int} 張照片")
    public void completeRepairWithResolution(String staffName, String resolutionLabel, int photoCount) {
        String resolutionType = RESOLUTION_TYPE_BY_LABEL.get(resolutionLabel);
        if (resolutionType == null) {
            throw new IllegalArgumentException("認不得的完修處理結果：「" + resolutionLabel + "」"
                    + "（合法值：" + RESOLUTION_TYPE_BY_LABEL.keySet() + "）");
        }
        openInHandTicketDetail(staffName);
        world.tapTestId("ticket.complete-button", SCREEN_TIMEOUT);
        for (int i = 0; i < photoCount; i++) {
            world.tapTestId("completeSheet.photo.add-button", SCREEN_TIMEOUT);
        }
        world.tapTestId("completeSheet.result." + resolutionType, SCREEN_TIMEOUT);
        world.tapTestId("completeSheet.submit-button", SCREEN_TIMEOUT);
    }

    /**
     * 怎麼做：在待認領點「開工單」FAB（{@code engPool.create-ticket-fab}），拍指定張數
     * 照片，填入房間／公共區域、類別、處理人後送出——表單沿用 {@code repair} 報修面板
     * 的 accessibility id 前綴（dsl 本模組說明），FAB 進入時不預先帶入任何原任務。
     * 權威狀態落地：等同後端「不經過任何房務任務開立工務單」語意——request body 對照
     * 契約 {@code CreateRepairTicketRequest}：公共區域走 {@code isPublicArea}=true
     * （{@code repair.public-area-toggle}）、客房走 {@code roomId}（房號填
     * {@code repair.room-field}）、處理人走 {@code assigneeId}（{@code repair.assignee-field}
     * 點開後選姓名），不帶 {@code sourceTaskId}。回寫：工務單詳情或待認領列表。
     *
     * <p>Data table 表頭照 feature 檔：{@code 房間或公共區域}（缺欄或留白視為預設值
     * 公共區域）、{@code 類別}（必填）、{@code 照片張數}（必填）、{@code 處理人}（選填）。
     */
    @當("{string} 在待認領點「開工單」，填寫以下資訊並送出：")
    public void createTicketFromPool(String staffName, DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        if (rows.size() != 1) {
            throw new IllegalArgumentException("「開工單」一次只開一張，data table 應恰有 1 列資料，收到 " + rows.size());
        }
        Map<String, String> row = rows.get(0);
        world.ensureOnEngineeringPool(world.staff(staffName));

        // 用 tapUntilVisible 而不是單純 tapTestId：前者會等元素位置定格再點，而且列表還在
        // 重排時那一下會整個落空（Appium 回報點擊成功、畫面留在待認領），補點一次就進得去。
        // 等的是 repair.public-area-toggle——FAB 開的是獨立的開工單頁
        // （repair_ticket_form_screen），不是任務頁那張 repair.sheet，後者的 id 這裡沒有。
        world.tapUntilVisible("engPool.create-ticket-fab", "repair.public-area-toggle", SCREEN_TIMEOUT);

        String location = row.get("房間或公共區域");
        if (location == null || location.isBlank() || "公共區域".equals(location)) {
            world.tapTestId("repair.public-area-toggle", SCREEN_TIMEOUT);
        } else {
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
            world.tapTestId("repair.photo.add-button", SCREEN_TIMEOUT);
        }

        String assigneeName = row.get("處理人");
        if (assigneeName != null && !assigneeName.isBlank()) {
            world.waitForTestId("repair.assignee-field", SCREEN_TIMEOUT).click();
            world.waitForLabelContaining(assigneeName, SCREEN_TIMEOUT).click();
        }

        world.tapTestId("repair.submit-button", SCREEN_TIMEOUT);
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

    // ──── 房務端生命週期（T312–T314 落點） ────
}
