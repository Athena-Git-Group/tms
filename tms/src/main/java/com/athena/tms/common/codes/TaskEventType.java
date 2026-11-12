package com.athena.tms.common.codes;

import java.util.List;
import java.util.Set;

public final class TaskEventType {
    public static final String CREATED = "created";
    public static final String TRIAGED = "triaged";
    public static final String ASSIGNED = "assigned";
    public static final String CLAIMED = "claimed";
    public static final String STARTED = "started";
    public static final String COMPLETED = "completed";
    public static final String INSPECTION_PASSED = "inspection_passed";
    public static final String INSPECTION_FAILED = "inspection_failed";
    public static final String REASSIGNED = "reassigned";
    public static final String RETURNED_TO_POOL = "returned_to_pool";
    public static final String INTERRUPTED = "interrupted";
    public static final String DEFERRED = "deferred";
    public static final String ANSWERED = "answered";
    public static final String RATED = "rated";
    public static final String SOP_CHECKED = "sop_checked";
    public static final String PLAN_ADJUSTED = "plan_adjusted";
    public static final String CHECKOUT_IMPORTED = "checkout_imported";
    public static final String CANCELLED = "cancelled";
    /** 主管驗收工務單不通過。推播事件與通知類別也用同一個值，三處逐字相同。 */
    public static final String REPAIR_VERIFICATION_FAILED = "repair_verification_failed";
    public static final List<String> ALL = List.of("created", "triaged", "assigned", "claimed", "started", "completed", "inspection_passed", "inspection_failed", "reassigned", "returned_to_pool", "interrupted", "deferred", "answered", "rated", "sop_checked", "plan_adjusted", "checkout_imported", "cancelled");

    /**
     * 只記在事件流水帳、不送上即時事件流（Kafka task-events）的事件類型。
     * SOP 勾記一張單就有十幾筆，沒有任何畫面需要即時收到，送上去只會讓每個 instance 的
     * SSE 消費者白白多處理。客戶端看到的事件 seq 因此會跳過這些號碼，seq 只用來去重與排序，不要求連號。
     */
    public static final Set<String> NOT_STREAMED = Set.of(SOP_CHECKED);

    public static boolean isStreamed(String type) {
        return !NOT_STREAMED.contains(type);
    }

    private TaskEventType() {
    }
}
