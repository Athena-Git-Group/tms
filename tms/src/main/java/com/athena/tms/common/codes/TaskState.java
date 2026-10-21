package com.athena.tms.common.codes;

import java.util.List;

/**
 * tasks.state 九值（＝V1 CHECK chk_tasks_state，全寫不縮寫）。
 * 合法轉移全集見 spec §5.1「狀態」；值域由 DB 擋、轉移由任務引擎（唯一入口）擋。
 */
public final class TaskState {

    public static final String TRIAGE = "triage";
    public static final String POOL = "pool";
    public static final String ACCEPTED = "accepted";
    public static final String DOING = "doing";
    public static final String DONE = "done";
    public static final String INSPECTED = "inspected";
    public static final String DEFERRED = "deferred";
    public static final String INTERRUPTED = "interrupted";
    public static final String CANCELLED = "cancelled";

    public static final List<String> ALL = List.of(
            TRIAGE, POOL, ACCEPTED, DOING, DONE, INSPECTED, DEFERRED, INTERRUPTED, CANCELLED);

    private TaskState() {
    }
}
