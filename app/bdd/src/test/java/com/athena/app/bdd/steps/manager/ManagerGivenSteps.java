package com.athena.app.bdd.steps.manager;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.appium.java_client.AppiumBy;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 主管工作台模組的 Given（主管工作台 dsl.md Given 句型區）：主管已在首頁或某一類清單、
 * 查房項目設定、查房畫面停在某個判定狀態、另一位主管先查完、結果畫面停著。
 * 只鋪準備狀態，不含任何斷言——「必查」在 {@link ManagerThenSteps}。
 *
 * <p>畫面 id 照 {@code app/mobile/lib/features/manager/manager_ids.dart}：主管首頁
 * {@code manager.home}、格子磚 {@code manager.home.tile.{queue}}、清單 {@code manager.queue}、
 * 清單列 {@code manager.queue.row.{房號}}、查房畫面 {@code inspect}、項目列
 * {@code inspect.item.{item_key}} 與其 {@code .pass-button}／{@code .fail-button}、
 * 送出鈕 {@code inspect.submit-button}、結果畫面 {@code inspect.result}。
 */
public class ManagerGivenSteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String HOME = "manager.home";
    private static final String QUEUE = "manager.queue";
    private static final String INSPECT = "inspect";
    private static final String INSPECT_SUBMIT_BUTTON = "inspect.submit-button";
    private static final String INSPECT_RESULT = "inspect.result";

    /** 清單中文 → 契約 ManagerQueues 的鍵（同 {@code manager.home.tile.{key}} 的 key）。 */
    private static final Map<String, String> QUEUE_KEYS = Map.of(
            "待查房清單", "inspection",
            "待驗收清單", "verify");

    private final AppiumWorld world;
    private final BackendChannel backend;
    private final ManagerContext context;

    public ManagerGivenSteps(AppiumWorld world, BackendChannel backend, ManagerContext context) {
        this.world = world;
        this.backend = backend;
        this.context = context;
    }

    /**
     * 怎麼做：以該主管身分登入，停在主管首頁，等格子數字與名下的單載入完成。這是 Arrange；
     * 開首頁本身是受測動作時用介面根的「在 App 打開首頁」。
     */
    @假設("{string} 已在 App 打開主管首頁")
    public void managerIsOnHome(String managerName) {
        ensureOnManagerHome(world.staff(managerName));
    }

    /** 怎麼做：以該主管身分登入，在主管首頁點對應的格，等清單載入完成，停在清單上。 */
    @假設("{string} 已在 App 打開 {string}")
    public void managerIsOnQueue(String managerName, String queueChinese) {
        openQueue(world.staff(managerName), queueKey(queueChinese));
    }

    /**
     * 怎麼做：透過後端測試資料把租戶啟用的查房項目整批換成表格列出的這些，依表格順序。
     * 權威狀態落地：後端現行啟用的查房項目集合剛好等於表格（同後端查房模組的同名句型）。
     */
    @假設("飯店目前設定的查房項目如下：")
    public void hotelHasInspectionItems(DataTable table) {
        List<String> labels = table.asMaps().stream()
                .map(row -> requireCell(row, "查房項目"))
                .toList();
        backend.replaceInspectionItems(labels);
    }

    /** 怎麼做：透過後端測試資料把租戶的查房項目全部停用。權威狀態落地：後端現行啟用集合是空的。 */
    @假設("飯店目前沒有任何啟用的查房項目")
    public void hotelHasNoActiveInspectionItems() {
        backend.disableAllInspectionItems();
    }

    /**
     * 怎麼做：以該主管身分登入，從待查房清單點該房進查房畫面，等項目載入完成，不動任何切換。
     * 回寫：畫面停在查房畫面。
     */
    @假設("{string} 已從待查房清單打開 {string}，還沒判任何一項")
    public void managerOpenedInspectionUntouched(String managerName, String roomNo) {
        openInspection(world.staff(managerName), roomNo);
    }

    /**
     * 怎麼做：以該主管身分登入，從待查房清單點該房進查房畫面，依表格逐項點「通過」或「不通過」
     * 的切換，寫「未判」的不動；停在查房畫面、不送出。權威狀態落地：後端該單仍為待查房，
     * 沒有任何查房紀錄（這裡不碰後端，自然成立）。回寫：畫面停在查房畫面，判定狀態如表。
     */
    @假設("{string} 已從待查房清單打開 {string}，判定如下：")
    public void managerOpenedInspectionWithJudgements(String managerName, String roomNo, DataTable table) {
        openInspection(world.staff(managerName), roomNo);
        for (Map<String, String> row : table.asMaps()) {
            String label = requireCell(row, "查房項目");
            String verdict = requireCell(row, "判定");
            switch (verdict) {
                case "通過" -> judge(label, "pass-button", verdict);
                case "不通過" -> judge(label, "fail-button", verdict);
                case "未判" -> { }
                default -> throw new IllegalArgumentException(
                        "判定欄只接受「通過」「不通過」「未判」，拿到：" + verdict);
            }
        }
    }

    /**
     * 怎麼做：在該主管的查房畫面已開著之後，透過後端測試資料在現行集合最後新增一個啟用的
     * 查房項目；不觸發畫面刷新。權威狀態落地：後端現行集合多了這一項；畫面上還是舊的集合。
     */
    @假設("飯店在 {string} 送出前新增了查房項目 {string}")
    public void hotelAppendedInspectionItemBeforeSubmit(String managerName, String label) {
        requireInspectionOpen(managerName);
        backend.appendInspectionItem(label);
    }

    /**
     * 怎麼做：不經 App，以另一位主管的身分呼叫 createInspections 全部通過；不觸發畫面刷新。
     * 權威狀態落地：後端該單已是查房通過；畫面開著的那位仍停在查房畫面。
     */
    @假設("{string} 剛被 {string} 查完，{string} 的畫面還沒更新")
    public void anotherManagerJustPassedInspection(String roomNo, String otherManagerName, String managerName) {
        requireInspectionOpen(managerName);
        UUID taskId = (UUID) world.taskRowByRoomNo(roomNo).get("id");
        backend.passInspectionAs(taskId, world.staff(otherManagerName));
    }

    /**
     * 怎麼做：以該主管身分從待查房清單打開該房、把所有項目判通過並送出，停在查房結果畫面
     * 不再操作。權威狀態落地：後端該單已是查房通過。回寫：畫面停在結果畫面。
     */
    @假設("{string} 剛送出 {string} 的查房，結果畫面停在畫面上")
    public void managerJustSubmittedInspection(String managerName, String roomNo) {
        openInspection(world.staff(managerName), roomNo);
        for (String key : activeInspectionItemKeys()) {
            world.tapTestId("inspect.item." + key + ".pass-button", TIMEOUT);
        }
        world.tapTestId(INSPECT_SUBMIT_BUTTON, TIMEOUT);
        world.waitForTestId(INSPECT_RESULT, TIMEOUT);
    }

    /**
     * 確保 App 已以這位主管登入並停在主管首頁，該主管種類的格子與「我名下的單」都載好。登入後就落在
     * 這一頁；被前面的句子推進去的畫面（清單、查房、結果）照返回鈕一路退回來，退不回去
     * 就點底部分頁的「首頁」。
     */
    private void ensureOnManagerHome(BackendChannel.CreatedStaff manager) {
        world.ensureLoggedInAs(manager);
        if (!isVisible(HOME) && !hasBackButton()) {
            world.waitForLabelContaining("首頁", TIMEOUT).click();
        }
        world.ensureOnScreenRoot(HOME, TIMEOUT);
        ManagerHomeWait.await(world, manager.name(), TIMEOUT);
    }

    /** 從主管首頁點該格進清單，等清單畫面出現，並把選擇留給同情境的 When／Then。 */
    private void openQueue(BackendChannel.CreatedStaff manager, String queueKey) {
        ensureOnManagerHome(manager);
        rememberQueueCount(world, context, queueKey);
        world.tapUntilVisible("manager.home.tile." + queueKey, QUEUE, TIMEOUT);
        context.openQueue(queueKey);
    }

    /**
     * 從待查房清單點該房進查房畫面，等項目載入完成：後端現行啟用的每一項都要在畫面上出現
     * （沒有任何啟用項目時只等送出鈕）。打開時清掉上一次的判定紀錄。
     */
    private void openInspection(BackendChannel.CreatedStaff manager, String roomNo) {
        openQueue(manager, "inspection");
        world.tapUntilVisible("manager.queue.row." + roomNo, INSPECT, TIMEOUT);
        world.waitForTestId(INSPECT_SUBMIT_BUTTON, TIMEOUT);
        for (String key : activeInspectionItemKeys()) {
            world.waitForTestId("inspect.item." + key, TIMEOUT);
        }
        context.openInspection(roomNo);
        context.clearJudgements();
    }

    /** 點該項目那一列的「通過」或「不通過」切換，並把判定留在情境狀態裡。 */
    private void judge(String label, String buttonSuffix, String verdict) {
        world.tapTestId("inspect.item." + inspectionItemKey(label) + "." + buttonSuffix, TIMEOUT);
        context.recordJudgement(label, verdict);
    }

    /** 這句的前提是該主管的查房畫面已開著；沒開就是劇本少了打開查房畫面那一句。 */
    private void requireInspectionOpen(String managerName) {
        if (context.inspectingRoomNo() == null
                || !world.loggedInStaff().name().equals(managerName) || !isVisible(INSPECT)) {
            throw new IllegalStateException("「" + managerName + "」的查房畫面還沒開著——"
                    + "劇本是否漏了「已從待查房清單打開 … 」那一句？");
        }
    }

    /** 租戶現行啟用的查房項目鍵，依 sort_order——就是查房畫面會列出的那一串。 */
    private List<String> activeInspectionItemKeys() {
        return backend.queryRows(
                        "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection'"
                                + " AND active = true ORDER BY sort_order", backend.ensureTenant())
                .stream()
                .map(row -> String.valueOf(row.get("item_key")))
                .toList();
    }

    /** 顯示名稱 → {@code sop_items.item_key}；畫面上的列 id 用鍵不用名。 */
    private String inspectionItemKey(String label) {
        List<Map<String, Object>> rows = backend.queryRows(
                "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection'"
                        + " AND active = true AND label = ?", backend.ensureTenant(), label);
        if (rows.size() != 1) {
            throw new IllegalStateException("查房項目「" + label + "」在後端現行啟用集合裡有 "
                    + rows.size() + " 筆，無法對到畫面上的列；背景「飯店目前設定的查房項目如下」是否列了它？");
        }
        return String.valueOf(rows.get(0).get("item_key"));
    }

    private static String queueKey(String chinese) {
        String key = QUEUE_KEYS.get(chinese);
        if (key == null) {
            throw new IllegalArgumentException(
                    "清單只接受 待查房清單／待驗收清單，拿到：" + chinese);
        }
        return key;
    }

    private boolean isVisible(String testId) {
        return !world.driver().findElements(world.byTestId(testId)).isEmpty();
    }

    /**
     * 點格子進清單之前，把主管首頁那一格的數字記進 {@link ManagerContext}：介面根的
     * 「畫面回到清單，某單不在清單上」要拿它比「比操作前少 1」。磚上數字還沒載好
     * （不是整數）就不記，那一句會略過少 1 的比對。Given 的打開清單與 When 的點格子共用。
     */
    static void rememberQueueCount(AppiumWorld world, ManagerContext context, String queueKey) {
        String shown = world.labelOf("manager.home.tile." + queueKey + ".count");
        if (shown == null) {
            return;
        }
        try {
            context.recordQueueCountBefore(queueKey, Integer.parseInt(shown.trim()));
        } catch (NumberFormatException e) {
            // 磚上還不是數字（載入中或佔位），交給那一句略過。
        }
    }

    private boolean hasBackButton() {
        return !world.driver().findElements(world.byTestId("nav.back-button")).isEmpty()
                || !world.driver().findElements(AppiumBy.accessibilityId("Back")).isEmpty();
    }

    private static String requireCell(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return value;
    }
}
