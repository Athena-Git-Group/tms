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
    public static final String CLOSED = "closed";
    public static final String DEFERRED = "deferred";
    public static final String INTERRUPTED = "interrupted";
    public static final String CANCELLED = "cancelled";
    public static final List<String> ALL = List.of("triage", "pool", "accepted", "doing", "done", "inspected", "closed", "deferred", "interrupted", "cancelled");

    public static final List<String> FINISHED = List.of(DONE, INSPECTED, CLOSED);

    public static final String FINISHED_SQL_LIST =
            FINISHED.stream().map(state -> "'" + state + "'").collect(Collectors.joining(", "));

    public static boolean isFinished(String state) {
        return FINISHED.contains(state);
    }

    private TaskState() {
    }
}
