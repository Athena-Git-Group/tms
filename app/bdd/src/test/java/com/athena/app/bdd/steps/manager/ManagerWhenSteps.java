package com.athena.app.bdd.steps.manager;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.當;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 主管工作台模組的 When：點格子、點清單列、逐項判定、送出查房、從結果畫面回清單、點「我名下的單」。
 *
 * <p>七句都只在「已開著」的畫面上操作（主管首頁、待查房清單、查房畫面、結果畫面由
 * Given 準備好），這裡不重新登入、不重新導航；accessibility id 以
 * {@code app/mobile/lib/features/manager/manager_ids.dart} 為準。
 */
public class ManagerWhenSteps {

    /** 畫面元素出現的等待上限：畫面切換有轉場動畫，用輪詢等待取代固定 sleep。 */
    private static final Duration SCREEN_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 送出查房後等結果畫面／對話框的上限：是一次 HTTP 來回（{@code createInspections}），
     * 比畫面轉場久。
     */
    private static final Duration SUBMIT_TIMEOUT = Duration.ofSeconds(20);

    /**
     * 格子文字 → 契約 {@code ManagerQueues} 的鍵，也是 {@code manager.home.tile.{queue}}
     * 的 {queue} 段。工務主管的待驗收寬磚同樣是 {@code manager.home.tile.verify}。
     */
    private static final Map<String, String> QUEUE_KEY_BY_LABEL = Map.of(
            "待查房", "inspection",
            "待驗收", "verify");

    /** 判定文字 → 查房項目列上那顆切換的 id 尾段（{@code inspect.item.{key}.pass-button}／{@code .fail-button}）。 */
    private static final Map<String, String> VERDICT_BUTTON_SUFFIX_BY_LABEL = Map.of(
            "通過", "pass-button",
            "不通過", "fail-button");

    private final AppiumWorld world;
    private final BackendChannel backend;
    private final ManagerContext context;

    public ManagerWhenSteps(AppiumWorld world, BackendChannel backend, ManagerContext context) {
        this.world = world;
        this.backend = backend;
        this.context = context;
    }

    /**
     * 怎麼做：在已開著的主管首頁點該格的磚（{@code manager.home.tile.{inspection|verify}}），
     * 等清單畫面載入完成。權威狀態落地：不改後端。回寫：清單畫面；把點的是哪一類、點之前那一格的數字留在
     * {@link ManagerContext}，清單斷言的「依序為」與「比操作前少 1」靠它分辨。
     */
    @當("{string} 在主管首頁點 {string}")
    public void tapHomeTile(String managerName, String tileLabel) {
        world.staff(managerName);
        String queueKey = queueKey(tileLabel);
        world.waitForTestId("manager.home", SCREEN_TIMEOUT);
        ManagerGivenSteps.rememberQueueCount(world, context, queueKey);
        world.tapTestId("manager.home.tile." + queueKey, SCREEN_TIMEOUT);
        world.waitForTestId("manager.queue", SCREEN_TIMEOUT);
        context.openQueue(queueKey);
    }

    /**
     * 怎麼做：在已開著的待查房清單點該房那一列（{@code manager.queue.row.{房號}}），等查房畫面
     * 載入項目（App 以 {@code listSopItems} 篩 applies_to=inspection 且啟用）。權威狀態落地：
     * 不改後端。回寫：查房畫面。
     *
     * <p>「項目載入完成」以底部「送出查房結果」鈕出現為準：項目數可能是 0（飯店沒有任何啟用的
     * 查房項目那條情境），等不到任何一列不代表還在載入。
     */
    @當("{string} 從待查房清單打開 {string}")
    public void openInspectionFromQueue(String managerName, String roomNo) {
        world.staff(managerName);
        world.waitForTestId("manager.queue", SCREEN_TIMEOUT);
        world.tapTestId("manager.queue.row." + roomNo, SCREEN_TIMEOUT);
        world.waitForTestId("inspect", SCREEN_TIMEOUT);
        world.waitForTestId("inspect.submit-button", SCREEN_TIMEOUT);
        // 後端現行啟用的每一項都要在畫面上出現才算載入完成（沒有任何啟用項目時只等送出鈕）。
        for (Map<String, Object> row : backend.queryRows(
                "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection'"
                        + " AND active = true ORDER BY sort_order", backend.ensureTenant())) {
            world.waitForTestId("inspect.item." + row.get("item_key"), SCREEN_TIMEOUT);
        }
        context.openInspection(roomNo);
        context.clearJudgements();
    }

    /**
     * 怎麼做：在已開著的查房畫面點該項目那一列的對應切換（{@code inspect.item.{key}.pass-button}
     * 或 {@code .fail-button}）。權威狀態落地：不改後端。回寫：該列的判定狀態與送出鈕狀態。
     */
    @當("{string} 把 {string} 判 {string}")
    public void judgeOneItem(String managerName, String itemLabel, String verdict) {
        world.staff(managerName);
        world.waitForTestId("inspect", SCREEN_TIMEOUT);
        judgeItem(itemLabel, verdict);
    }

    /**
     * 怎麼做：在已開著的查房畫面依表格逐項切換，然後點底部「送出查房結果」
     * （{@code inspect.submit-button}）。輸入資料並按一次送出收斂成一句 When。
     * 權威狀態落地：連線時 App 呼叫 {@code createInspections}（帶 {@code Idempotency-Key}），
     * 成功則依後端規則結案或退回；後端回 409／422 時單不變；離線時擋在送出之前。
     * 回寫：結果畫面、對話框或重新載入後的查房畫面——哪一種由 Then 去驗，這裡只按到送出為止。
     *
     * <p>Data Table 欄：{@code 查房項目}（顯示名稱）、{@code 判定}（通過、不通過）。
     */
    @當("{string} 把以下項目判定後送出：")
    public void judgeItemsAndSubmit(String managerName, DataTable table) {
        world.staff(managerName);
        world.waitForTestId("inspect", SCREEN_TIMEOUT);
        for (Map<String, String> row : table.asMaps()) {
            judgeItem(required(row, "查房項目"), required(row, "判定"));
        }
        tapSubmit();
    }

    /**
     * 怎麼做：在已開著的查房畫面點底部「送出查房結果」，不改任何判定。用在判定已由 Given
     * 準備好、或沒有項目可判的案例。權威狀態落地與回寫同「把以下項目判定後送出」。
     */
    @當("{string} 送出查房")
    public void submitInspection(String managerName) {
        world.staff(managerName);
        world.waitForTestId("inspect", SCREEN_TIMEOUT);
        tapSubmit();
    }

    /**
     * 怎麼做：在查房結果畫面點實心「回到待查房」（{@code inspect.result.back-button}）。
     * 權威狀態落地：不改後端。回寫：清單畫面（待查房那一類）。
     */
    @當("{string} 從結果畫面回到待查房清單")
    public void backToInspectionQueue(String managerName) {
        world.staff(managerName);
        world.waitForTestId("inspect.result", SCREEN_TIMEOUT);
        world.tapTestId("inspect.result.back-button", SCREEN_TIMEOUT);
        world.waitForTestId("manager.queue", SCREEN_TIMEOUT);
        context.openQueue("inspection");
    }

    /**
     * 怎麼做：在已開著的主管首頁「我名下的單」區點該工務單卡（工務首頁手上的單同一個元件，
     * {@code engHome.in-hand.{單號}.card}），等工務單詳情（{@code ticket.screen}）載入完成。
     * 權威狀態落地：不改後端。回寫：工務單詳情。
     */
    @當("{string} 在「我名下的單」點 {string}")
    public void tapMineTicket(String managerName, String ticketNo) {
        world.staff(managerName);
        world.waitForTestId("manager.home.mine", SCREEN_TIMEOUT);
        world.tapUntilVisible("engHome.in-hand." + ticketNo + ".card", "ticket.screen", SCREEN_TIMEOUT);
    }

    /**
     * 點該項目那一列的「通過」或「不通過」切換，並把判定留在 {@link ManagerContext}。
     * 項目在畫面上的 id 用 {@code sop_items.item_key}，劇本寫的是顯示名稱，所以先回後端
     * 把 label 換成 key——權威來源是 {@code sop_items}，不在測試裡另維護一份對照表。
     * 包成 package-private 讓同模組的 Given（「判定如下」）共用同一個點法。
     */
    void judgeItem(String itemLabel, String verdict) {
        String suffix = VERDICT_BUTTON_SUFFIX_BY_LABEL.get(verdict);
        if (suffix == null) {
            throw new IllegalArgumentException("認不得的判定：「" + verdict + "」"
                    + "（合法值：" + VERDICT_BUTTON_SUFFIX_BY_LABEL.keySet() + "）");
        }
        world.tapTestId("inspect.item." + inspectionItemKey(itemLabel) + "." + suffix, SCREEN_TIMEOUT);
        context.recordJudgement(itemLabel, verdict);
    }

    private void tapSubmit() {
        world.tapTestId("inspect.submit-button", SCREEN_TIMEOUT);
        // 送出是一次 HTTP 來回；至少等送出鈕的 loading 態結束（結果畫面、對話框或重載的查房畫面
        // 三者之一出現）再把控制權交給 Then。三種去處的 id 不同，這裡只等「畫面有變化」最保守的
        // 共同點：送出鈕不再可見，或結果畫面出現。
        try {
            world.waitForTestId("inspect.result", SUBMIT_TIMEOUT);
        } catch (RuntimeException | AssertionError notTheResultScreen) {
            // waitForTestId 逾時丟的是 AssertionError（附元素樹與截圖），只接 RuntimeException
            // 會讓 409／422 這兩種本來就不到結果畫面的去向在 When 就紅掉。
            // 退回、409／422 對話框或離線擋下時不會有結果畫面；由 Then 去驗那一種。
        }
    }

    private String inspectionItemKey(String label) {
        List<Map<String, Object>> rows = backend.queryRows(
                "SELECT item_key FROM sop_items WHERE tenant_id = ? AND applies_to = 'inspection'"
                        + " AND active = true AND label = ?",
                backend.ensureTenant(), label);
        if (rows.size() != 1) {
            throw new IllegalStateException("查房項目「" + label + "」在後端現行啟用的 sop_items（applies_to=inspection）"
                    + "應恰有 1 筆，實際 " + rows.size() + " 筆——場景是否漏了「飯店的查房項目」那句 Given？");
        }
        return String.valueOf(rows.get(0).get("item_key"));
    }

    private static String queueKey(String tileLabel) {
        String key = QUEUE_KEY_BY_LABEL.get(tileLabel);
        if (key == null) {
            throw new IllegalArgumentException("認不得的主管首頁格：「" + tileLabel + "」"
                    + "（合法值：" + QUEUE_KEY_BY_LABEL.keySet() + "）");
        }
        return key;
    }

    private static String required(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Data Table 缺必填欄「" + column + "」：" + row);
        }
        return value.trim();
    }
}
