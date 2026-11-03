package com.athena.tms.bdd.support;

import java.util.Map;

/**
 * Gherkin 場景裡的中文狀態詞和資料庫／contracts 的英文權威值是兩套獨立的詞彙。
 * 如果每個 stepdef 各自寫一份中翻英對照，兩邊改名時很容易漏改其中幾處，
 * 場景斷言就會悄悄比對到舊值而不自知。這裡集中成唯一一份，權威值改名只要改這裡。
 *
 * <p>每個對照表只收錄 dsl.md 或 contracts 已經寫明的詞。查不到的詞一律拋
 * {@link IllegalArgumentException} 帶原詞，不自行猜測權威值——猜錯的對照會讓
 * 場景「斷言通過」但其實驗的是錯的狀態，比起直接噴錯更難發現。
 */
public final class Terms {

    private Terms() {
    }

    // 來源：specs/truth/features/backend/dsl.md 「狀態用語對照」表前半（任務狀態，非工務單）。
    private static final Map<String, String> TASK_STATE = Map.ofEntries(
            Map.entry("待接", "pool"),
            Map.entry("待開始", "accepted"),
            Map.entry("進行中", "doing"),
            Map.entry("已延後", "deferred"),
            Map.entry("已中斷", "interrupted"),
            Map.entry("待查房", "done"),
            Map.entry("已送達", "done"),
            Map.entry("查房通過", "inspected"),
            Map.entry("完成 · 免查房", "inspected"),
            Map.entry("已取消", "cancelled"));

    // 來源：specs/truth/features/backend/dsl.md 「狀態用語對照」表後段（工務單狀態）。
    private static final Map<String, String> REPAIR_TICKET_STATE = Map.ofEntries(
            Map.entry("已認領", "accepted"),
            Map.entry("處理中", "doing"),
            Map.entry("已完修", "done"),
            Map.entry("已撤回", "cancelled"));

    // 來源：specs/truth/features/backend/dsl.md 「部門用語」表（dept 列）。
    private static final Map<String, String> DEPT = Map.ofEntries(
            Map.entry("房務", "housekeeping"),
            Map.entry("工務", "engineering"),
            Map.entry("櫃檯", "frontdesk"));

    // 來源：specs/truth/features/backend/dsl.md 「部門用語」表（role 列）。
    private static final Map<String, String> ROLE = Map.ofEntries(
            Map.entry("主管", "manager"),
            Map.entry("值班經理", "duty_manager"));

    // 來源：specs/truth/features/backend/dsl.md 「員工狀態用語」表。
    private static final Map<String, String> STAFF_STATE = Map.ofEntries(
            Map.entry("在職", "active"),
            Map.entry("待開通", "invited"),
            Map.entry("停用", "inactive"));

    // 來源：specs/truth/features/backend/查房/dsl.md 「查房結果用語」表。
    private static final Map<String, String> INSPECTION = Map.ofEntries(
            Map.entry("通過", "passed"),
            Map.entry("免查", "waived"),
            Map.entry("退回重做", "failed"));

    // 來源：specs/truth/features/backend/dsl.md 「房務待接池有以下任務：」列的「來源」Data Table
    // 參數說明，權威值對齊 specs/truth/data/data-model.dbml 的 tasks.pool_source CHECK
    // 與 specs/truth/contracts/components/schemas/員工工作台.yaml 的 PoolSource enum。
    private static final Map<String, String> POOL_SOURCE = Map.ofEntries(
            Map.entry("住客掃碼", "guest_scan"),
            Map.entry("櫃檯轉單", "desk_forward"),
            Map.entry("主管退回", "manager_requeue"),
            Map.entry("請假退回", "leave_return"),
            Map.entry("班末退池", "shift_end_return"),
            Map.entry("查房退回", "inspection_return"),
            Map.entry("房務報修", "staff_report"),
            Map.entry("自動化規則", "system"));

    // 來源：specs/truth/features/backend/工務單/dsl.md 的工務單類別，權威代碼以
    // specs/truth/contracts/components/schemas/工務單.yaml 的 RepairCategory enum 為準。
    private static final Map<String, String> REPAIR_CATEGORY = Map.ofEntries(
            Map.entry("冷氣", "air_conditioning"),
            Map.entry("水電", "plumbing_electrical"),
            Map.entry("家具／設備", "furniture_equipment"),
            Map.entry("門鎖", "door_lock"),
            Map.entry("其他", "other"));

    // 來源：specs/truth/contracts/components/schemas/員工工作台.yaml 的 PoolCategory enum。
    private static final Map<String, String> POOL_CATEGORY = Map.ofEntries(
            Map.entry("打掃", "cleaning"),
            Map.entry("備品", "amenity"),
            Map.entry("修繕", "repair"),
            Map.entry("其他", "other"));

    // 來源：specs/truth/features/backend/dsl.md「飯店的逾時升級設定如下：」列的「項目」Data
    // Table 參數說明，權威鍵對齊 specs/truth/data/data-model.dbml 的 tenant_config.key CHECK（V6 增補）。
    private static final Map<String, String> TENANT_CONFIG_SLA_ITEM = Map.ofEntries(
            Map.entry("打掃類 SLA 時限", "sla_minutes_cleaning"),
            Map.entry("備品類 SLA 時限", "sla_minutes_amenity"),
            Map.entry("其他類 SLA 時限", "sla_minutes_other"),
            Map.entry("升級層距", "escalation_step_minutes"));

    /**
     * 任務（非工務單）狀態中文 → 權威 {@code tasks.state}。「待查房」與「已送達」都對應
     * {@code done}（差別只在是否打掃類，不屬於狀態本身），「查房通過」與「完成 · 免查房」
     * 都對應 {@code inspected}（差別在 {@code tasks.inspection}，見 {@link #inspection}）。
     */
    public static String taskState(String chinese) {
        return lookup(TASK_STATE, chinese, "任務狀態");
    }

    /**
     * 工務單狀態中文 → 權威 {@code tasks.state}。「已撤回」除了狀態還帶取消原因，
     * 用 {@link #repairTicketWithdrawnReason()} 另外取得。
     */
    public static String repairTicketState(String chinese) {
        return lookup(REPAIR_TICKET_STATE, chinese, "工務單狀態");
    }

    /** 工務單「已撤回」狀態對應的 {@code tasks.cancel_reason} 權威值（固定為 staff_withdrew）。 */
    public static String repairTicketWithdrawnReason() {
        return "staff_withdrew";
    }

    /** 部門中文 → 權威 {@code dept}（housekeeping|engineering|frontdesk）。 */
    public static String dept(String chinese) {
        return lookup(DEPT, chinese, "部門");
    }

    /** 角色中文 → 權威 {@code role}（manager|duty_manager，僅收錄 dsl.md 點名的這兩種）。 */
    public static String role(String chinese) {
        return lookup(ROLE, chinese, "角色");
    }

    /** 員工狀態中文 → 權威 {@code staff_accounts} 狀態（active|invited|inactive）。 */
    public static String staffState(String chinese) {
        return lookup(STAFF_STATE, chinese, "員工狀態");
    }

    /** 查房結果中文 → 權威 {@code tasks.inspection}（passed|waived|failed）。 */
    public static String inspection(String chinese) {
        return lookup(INSPECTION, chinese, "查房結果");
    }

    /** 待接池來源中文 → 權威 {@code tasks.pool_source}（與 PoolSource enum 對齊）。 */
    public static String poolSource(String chinese) {
        return lookup(POOL_SOURCE, chinese, "待接池來源");
    }

    /** 工務單類別中文 → 權威 {@code repair_tickets.category}（RepairCategory enum）。 */
    public static String repairCategory(String chinese) {
        return lookup(REPAIR_CATEGORY, chinese, "工務單類別");
    }

    /** 待接池類別中文 → 權威 {@code PoolCategory} enum。 */
    public static String poolCategory(String chinese) {
        return lookup(POOL_CATEGORY, chinese, "待接池類別");
    }

    /** 逾時升級設定「項目」中文 → 權威 {@code tenant_config.key}（sla_minutes_*／escalation_step_minutes）。 */
    public static String tenantConfigSlaItem(String chinese) {
        return lookup(TENANT_CONFIG_SLA_ITEM, chinese, "逾時升級設定項目");
    }

    private static String lookup(Map<String, String> table, String chinese, String tableName) {
        String value = table.get(chinese);
        if (value == null) {
            throw new IllegalArgumentException(
                    "找不到「" + chinese + "」在" + tableName + "對照表中的權威值（原詞：" + chinese + "）");
        }
        return value;
    }
}
