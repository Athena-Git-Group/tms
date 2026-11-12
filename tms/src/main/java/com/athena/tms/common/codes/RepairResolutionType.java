package com.athena.tms.common.codes;

import java.util.List;

/** 工務單的完修結果（repair_tickets.resolution_type）。 */
public final class RepairResolutionType {
    public static final String FIXED = "fixed";
    public static final String TEMPORARY = "temporary";
    public static final String CANNOT_FIX = "cannot_fix";
    /** 到場判斷不需處理：不要求完修照片、不建追蹤單、不改房況，完成當下直接結案，不進待驗收。 */
    public static final String NO_ACTION_NEEDED = "no_action_needed";
    public static final List<String> ALL = List.of("fixed", "temporary", "cannot_fix", "no_action_needed");

    private RepairResolutionType() {
    }
}
