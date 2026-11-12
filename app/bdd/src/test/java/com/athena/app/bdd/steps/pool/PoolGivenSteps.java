package com.athena.app.bdd.steps.pool;

import com.athena.app.bdd.AppiumWorld;
import com.athena.app.bdd.BackendChannel;
import com.athena.app.bdd.steps.common.CommonGivenSteps;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.zh_tw.假設;

import java.time.Duration;
import java.util.Map;

public class PoolGivenSteps {

    private final AppiumWorld world;

    public PoolGivenSteps(AppiumWorld world) {
        this.world = world;
    }

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

    private static final String RULE_HIT_PREFIX = "規則引擎命中：";

    @假設("{string} 已停用")
    public void staffDeactivated(String staffName) {
        world.backend().deactivateStaff(world.staff(staffName).id());
    }

    @假設("{string} 已在 App 打開待認領")
    public void alreadyOpenedPool(String staffName) {
        PoolDept.ensureOnPool(world, world.staff(staffName));
    }

    /**
     * 怎麼做：清除 App 本機的待認領部門選擇，讓這台手機沒有任何部門紀錄；以該主管身分
     * 登入後停在首頁、還沒切到待認領。回寫：無。
     *
     * <p>部門選擇只存在手機上、後端沒有，所以直接刪掉那一把本機偏好設定
     * （{@link CommonGivenSteps#POOL_DEPT_PREFERENCE_KEY}）；偏好設定只在 App 啟動時讀，
     * 刪完要整個 process 重開再登入。
     */
    @假設("{string} 第一次在這台手機打開待認領")
    public void firstTimeOpeningPoolOnThisDevice(String managerName) {
        BackendChannel.CreatedStaff manager = world.staff(managerName);
        if (!PoolDept.isManager(manager)) {
            throw new IllegalArgumentException("只有主管的待認領會記部門，「" + managerName + "」的角色是 " + manager.role());
        }
        world.deleteLocalPreference(CommonGivenSteps.POOL_DEPT_PREFERENCE_KEY);
        world.restartApp();
        world.ensureLoggedInAs(manager);
        world.waitForTestId("manager.home", Duration.ofSeconds(10));
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
