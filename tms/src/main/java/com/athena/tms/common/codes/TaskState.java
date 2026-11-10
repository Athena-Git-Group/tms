package com.athena.tms.common.codes;

import java.util.List;

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
    public static final List<String> ALL = List.of("triage", "pool", "accepted", "doing", "done", "inspected", "deferred", "interrupted", "cancelled");

    private TaskState() {
    }
}
