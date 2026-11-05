package com.athena.app.bdd.steps.pool;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;

import java.util.Map;

/** 待認領模組的 Given。 */
public class PoolGivenSteps {

    private final AppiumWorld world;

    public PoolGivenSteps(AppiumWorld world) {
        this.world = world;
    }

    /** 怎麼做：透過後端測試資料建立待認領任務。權威狀態落地：後端有這些 pool 的任務。 */
    @假設("房務待認領有以下任務：")
    public void housekeepingPoolHasTasks(DataTable table) {
        for (Map<String, String> row : table.asMaps()) {
            seedPoolTask(world, row);
        }
    }

    /**
     * 一列待認領任務。欄位語意照 backend dsl.md 同名句型：房號選填（留白＝公共區域）、
     * 樓層選填（預設取房號第一位數字）、類別與來源選填（預設其他／住客掃碼）、
     * 標記只有「優先通知」、提醒狀態直接標記未接提醒已到達的階段。
     *
     * <p>「交代或原因」一欄承載兩種意思，以全形分號分段：第一段是退池原因
     * （{@code return_reason}），第二段是接手進度交代（{@code requeue_note}）——
     * 單卡的「原因」與「接手進度」兩列分別讀這兩個欄位。來源是自動化規則時整欄
     * 是「規則引擎命中：{規則名}」，規則名同時落地到 {@code rule_hit}。
     */
    static void seedPoolTask(AppiumWorld world, Map<String, String> row) {
        String roomNo = blankToNull(row.get("房號"));
        String floorText = blankToNull(row.get("樓層"));
        String category = blankToNull(row.get("類別"));
        String source = blankToNull(row.get("來源"));
        String mark = blankToNull(row.get("標記"));
        String reminder = blankToNull(row.get("提醒狀態"));
        String note = blankToNull(row.get("交代或原因"));

        String poolSourceCode = BackendChannel.poolSource(source != null ? source : "住客掃碼");
        String ruleHit = null;
        String returnReason = null;
        String handoverNote = null;
        if (note != null) {
            if ("system".equals(poolSourceCode)) {
                ruleHit = note.startsWith(RULE_HIT_PREFIX) ? note.substring(RULE_HIT_PREFIX.length()) : note;
                returnReason = note;
            } else {
                String[] parts = note.split("；", 2);
                returnReason = parts[0];
                handoverNote = parts.length > 1 ? parts[1] : null;
            }
        }

        world.backend().createHousekeepingPoolTask(
                roomNo,
                floorText == null ? null : Integer.valueOf(floorText),
                requireCell(row, "內容"),
                BackendChannel.taskType(category != null ? category : "其他"),
                poolSourceCode,
                "優先通知".equals(mark),
                returnReason,
                handoverNote,
                ruleHit,
                reminder == null ? null : BackendChannel.reminderStage(reminder));
    }

    /** 自動化規則單的「交代或原因」前綴，後面接規則名（backend dsl.md 同名句型）。 */
    private static final String RULE_HIT_PREFIX = "規則引擎命中：";

    /** 怎麼做：透過後端測試資料把該員工停用。權威狀態落地：後端 staff 為停用，名下已接任務不變。 */
    @假設("{string} 已停用")
    public void staffDeactivated(String staffName) {
        world.backend().deactivateStaff(world.staff(staffName).id());
    }

    /** 怎麼做：以該員工登入後切到待認領分頁，等列表載入完成。 */
    @假設("{string} 已在 App 打開待認領")
    public void alreadyOpenedPool(String staffName) {
        world.ensureOnPool(world.staff(staffName));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String requireCell(Map<String, String> row, String column) {
        String value = blankToNull(row.get(column));
        if (value == null) {
            throw new IllegalArgumentException("Data Table 缺少必填欄位「" + column + "」：" + row);
        }
        return value;
    }
}
