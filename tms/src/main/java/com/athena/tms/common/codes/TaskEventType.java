package com.athena.tms.common.codes;

import java.util.List;

/**
 * task_events.type 18 值（spec §5.1 列舉）。DB 端刻意不設 CHECK——
 * 「可擴充，加值不改表」，封閉性由本類承載；新增事件種類改這裡即可。
 */
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

    public static final List<String> ALL = List.of(
            CREATED, TRIAGED, ASSIGNED, CLAIMED, STARTED, COMPLETED,
            INSPECTION_PASSED, INSPECTION_FAILED, REASSIGNED, RETURNED_TO_POOL,
            INTERRUPTED, DEFERRED, ANSWERED, RATED, SOP_CHECKED,
            PLAN_ADJUSTED, CHECKOUT_IMPORTED, CANCELLED);

    private TaskEventType() {
    }
}
