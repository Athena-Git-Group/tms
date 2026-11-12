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

    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(8);

    private static final Duration QUEUE_DRAIN_TIMEOUT = Duration.ofSeconds(20);

    private static final Map<String, String> SOURCE_KEY_BY_LABEL = Map.of(
            "全部", "all",
            "急件", "urgent",
            "房務報修", "staff",
            "櫃檯", "desk");

    private static final Map<String, String> RESOLUTION_TYPE_BY_LABEL = Map.of(
            "已修好", "fixed",
            "暫時處理", "temporary",
            "無法修復", "cannot_fix",
            "不需處理", "no_action_needed");

    private static final Map<String, String> CATEGORY_KEY_BY_LABEL = Map.of(
            "冷氣", "air_conditioning",
            "水電", "plumbing_electrical",
            "家具／設備", "furniture_equipment",
            "門鎖", "door_lock",
            "其他", "other");

    private static final Map<String, String> WHO_SEGMENT_BY_LABEL = Map.of(
            "我自己處理", "repair.who.me.segment",
            "放進待認領", "repair.who.pool.segment");

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

    @當("{string} 在 App 打開 {string} 的工務單詳情")
    public void openTicketDetail(String staffName, String ticketNo) {
        context.openTicketDetail(world.staff(staffName), ticketNo);
    }

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
        world.waitForTestId("completeSheet.photo.add-button", SCREEN_TIMEOUT);
        for (int i = 0; i < photoCount; i++) {
            context.addPhoto("completeSheet.photo.add-button");
        }
        world.tapTestId("completeSheet.result." + resolutionType, SCREEN_TIMEOUT);
        world.tapTestId("completeSheet.submit-button", SCREEN_TIMEOUT);
    }

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
    }

    @當("{string} 在工務首頁點「開工單」，填寫以下資訊並送出：")
    public void createTicketFromEngineeringHome(String staffName, DataTable table) {
        List<Map<String, String>> rows = table.asMaps();
        if (rows.size() != 1) {
            throw new IllegalArgumentException("「開工單」一次只開一張，data table 應恰有 1 列資料，收到 " + rows.size());
        }
        Map<String, String> row = rows.get(0);
        world.ensureOnEngineeringHome(world.staff(staffName));

        world.tapUntilVisible("engHome.create-ticket-fab", "repair.public-area-toggle", SCREEN_TIMEOUT);

        String location = row.get("房間或公共區域");
        if (location == null || location.isBlank() || "公共區域".equals(location)) {
            world.tapTestId("repair.public-area-toggle", SCREEN_TIMEOUT);
        } else {
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

    @當("{string} 在工務單詳情按通過")
    public void managerTapsPass(String managerName) {
        context.requireTicketDetailOpenAs(managerName);
        world.tapTestId(MANAGER_PASS_BUTTON, SCREEN_TIMEOUT);
    }

    @當("{string} 在工務單詳情按不通過，寫下原因 {string} 並送出")
    public void managerFailsWithReason(String managerName, String reason) {
        if (reason.isBlank()) {
            throw new IllegalArgumentException("這句的原因不能是空白；原因留空的案例請用「打開不通過面板，不寫原因」");
        }
        openFailSheet(managerName);
        world.typeInto(MANAGER_FAIL_REASON_FIELD, SCREEN_TIMEOUT, reason);
        world.tapTestId(MANAGER_FAIL_SUBMIT_BUTTON, SCREEN_TIMEOUT);
    }

    @當("{string} 在工務單詳情打開不通過面板，不寫原因")
    public void managerOpensFailSheetWithoutReason(String managerName) {
        openFailSheet(managerName);
    }

    @當("{string} 從驗收結果卡回到待驗收清單")
    public void managerBackToVerifyQueueFromResultCard(String managerName) {
        if (!world.loggedInStaff().name().equals(managerName)) {
            throw new IllegalStateException("畫面上登入的不是「" + managerName + "」——"
                    + "劇本是否漏了讓該主管送出驗收不通過、停在結果卡的那一句？");
        }
        world.waitForTestId(MANAGER_RESULT_CARD, SCREEN_TIMEOUT);
        world.tapUntilVisible(MANAGER_RESULT_BACK_BUTTON, MANAGER_QUEUE, SCREEN_TIMEOUT);
    }

    private void openFailSheet(String managerName) {
        context.requireTicketDetailOpenAs(managerName);
        world.tapTestId(MANAGER_FAIL_BUTTON, SCREEN_TIMEOUT);
        world.waitForTestId(MANAGER_FAIL_REASON_FIELD, SCREEN_TIMEOUT);
        world.waitForTestId(MANAGER_FAIL_SUBMIT_BUTTON, SCREEN_TIMEOUT);
    }

    private void openInHandTicketDetail(String staffName) {
        String ticketNo = inHandTicketNo(staffName);
        world.ensureLoggedInAs(world.staff(staffName));
        world.tapTestId("engHome.in-hand." + ticketNo + ".card", SCREEN_TIMEOUT);
        world.waitForTestId("ticket.screen", SCREEN_TIMEOUT);
    }

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
