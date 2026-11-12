package com.athena.tms.bdd.support;

import java.util.Map;

public final class Terms {

    private Terms() {
    }

    private static final Map<String, String> TASK_STATE = Map.ofEntries(
            Map.entry("待認領", "pool"),
            Map.entry("待開始", "accepted"),
            Map.entry("進行中", "doing"),
            Map.entry("已延後", "deferred"),
            Map.entry("已中斷", "interrupted"),
            Map.entry("待查房", "done"),
            Map.entry("已送達", "done"),
            Map.entry("查房通過", "inspected"),
            Map.entry("完成 · 免查房", "inspected"),
            Map.entry("已取消", "cancelled"));

    private static final Map<String, String> REPAIR_TICKET_STATE = Map.ofEntries(
            Map.entry("已接單", "accepted"),
            Map.entry("維修中", "doing"),
            Map.entry("已完修", "done"),
            Map.entry("已結案", "closed"),
            Map.entry("已撤回", "cancelled"));

    private static final Map<String, String> DEPT = Map.ofEntries(
            Map.entry("房務", "housekeeping"),
            Map.entry("工務", "engineering"),
            Map.entry("櫃檯", "frontdesk"));

    private static final Map<String, String> ROLE = Map.ofEntries(
            Map.entry("主管", "manager"),
            Map.entry("值班經理", "duty_manager"));

    private static final Map<String, String> STAFF_STATE = Map.ofEntries(
            Map.entry("在職", "active"),
            Map.entry("待開通", "invited"),
            Map.entry("停用", "inactive"));

    private static final Map<String, String> INSPECTION = Map.ofEntries(
            Map.entry("通過", "passed"),
            Map.entry("免查", "waived"),
            Map.entry("退回重做", "failed"));

    private static final Map<String, String> POOL_SOURCE = Map.ofEntries(
            Map.entry("住客掃碼", "guest_scan"),
            Map.entry("櫃檯轉單", "desk_forward"),
            Map.entry("主管退回", "manager_requeue"),
            Map.entry("請假退回", "leave_return"),
            Map.entry("班末退池", "shift_end_return"),
            Map.entry("查房退回", "inspection_return"),
            Map.entry("房務報修", "staff_report"),
            Map.entry("自動化規則", "system"));

    private static final Map<String, String> REMINDER_STAGE = Map.ofEntries(
            Map.entry("已提醒同仁", "staff_reminded"),
            Map.entry("已通知主管", "manager_notified"));

    private static final Map<String, String> REPAIR_TICKET_SOURCE = Map.ofEntries(
            Map.entry("房務報修", "staff"),
            Map.entry("櫃檯", "desk"));

    private static final Map<String, String> REPAIR_CATEGORY = Map.ofEntries(
            Map.entry("冷氣", "air_conditioning"),
            Map.entry("水電", "plumbing_electrical"),
            Map.entry("家具／設備", "furniture_equipment"),
            Map.entry("門鎖", "door_lock"),
            Map.entry("其他", "other"));

    private static final Map<String, String> RESOLUTION_TYPE = Map.ofEntries(
            Map.entry("已修好", "fixed"),
            Map.entry("暫時處理", "temporary"),
            Map.entry("無法修復", "cannot_fix"),
            Map.entry("不需處理", "no_action_needed"));

    private static final Map<String, String> POOL_CATEGORY = Map.ofEntries(
            Map.entry("打掃", "cleaning"),
            Map.entry("備品", "amenity"),
            Map.entry("修繕", "repair"),
            Map.entry("其他", "other"));

    public static String taskState(String chinese) {
        return lookup(TASK_STATE, chinese, "任務狀態");
    }

    public static String repairTicketState(String chinese) {
        return lookup(REPAIR_TICKET_STATE, chinese, "工務單狀態");
    }

    public static String repairTicketWithdrawnReason() {
        return "staff_withdrew";
    }

    public static String dept(String chinese) {
        return lookup(DEPT, chinese, "部門");
    }

    public static String role(String chinese) {
        return lookup(ROLE, chinese, "角色");
    }

    public static String staffState(String chinese) {
        return lookup(STAFF_STATE, chinese, "員工狀態");
    }

    public static String inspection(String chinese) {
        return lookup(INSPECTION, chinese, "查房結果");
    }

    public static String poolSource(String chinese) {
        return lookup(POOL_SOURCE, chinese, "待認領來源");
    }

    public static String repairCategory(String chinese) {
        return lookup(REPAIR_CATEGORY, chinese, "工務單類別");
    }

    public static String repairTicketSource(String chinese) {
        return lookup(REPAIR_TICKET_SOURCE, chinese, "工務單來源");
    }

    public static String resolutionType(String chinese) {
        return lookup(RESOLUTION_TYPE, chinese, "完修結果");
    }

    public static String poolCategory(String chinese) {
        return lookup(POOL_CATEGORY, chinese, "待認領類別");
    }

    public static String reminderStage(String chinese) {
        return lookup(REMINDER_STAGE, chinese, "未接提醒階段");
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
