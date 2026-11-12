package com.athena.tms.common.codes;

import java.util.List;
import java.util.stream.Collectors;

public final class TaskState {
    public static final String TRIAGE = "triage";
    public static final String POOL = "pool";
    public static final String ACCEPTED = "accepted";
    public static final String DOING = "doing";
    public static final String DONE = "done";
    public static final String INSPECTED = "inspected";
    /** 已結案，只有工務單會出現：主管驗收通過，或完修結果為不需處理時完成當下直接進入。工務單的 done 只是已完修、等主管驗收。 */
    public static final String CLOSED = "closed";
    public static final String DEFERRED = "deferred";
    public static final String INTERRUPTED = "interrupted";
    public static final String CANCELLED = "cancelled";
    public static final List<String> ALL = List.of("triage", "pool", "accepted", "doing", "done", "inspected", "closed", "deferred", "interrupted", "cancelled");

    /**
     * 已結束的狀態：打掃單的 done 與 inspected，其他任務的 done，工務單的 closed。
     * 工務單的 done 雖然還在等主管驗收，對處理人來說已經做完，今日完成數、首頁排序與重複報修檢查都算它結束。
     * 讀取端一律用這裡判斷，新增狀態值時只改這一處。
     */
    public static final List<String> FINISHED = List.of(DONE, INSPECTED, CLOSED);

    /** {@link #FINISHED} 寫成 SQL 的 IN 清單內容，例如 {@code 'done', 'inspected', 'closed'}。 */
    public static final String FINISHED_SQL_LIST =
            FINISHED.stream().map(state -> "'" + state + "'").collect(Collectors.joining(", "));

    public static boolean isFinished(String state) {
        return FINISHED.contains(state);
    }

    private TaskState() {
    }
}
