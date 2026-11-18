package com.athena.tms.common.codes;

import java.util.List;

public final class RepairResolutionType {
    public static final String FIXED = "fixed";
    public static final String TEMPORARY = "temporary";
    public static final String CANNOT_FIX = "cannot_fix";
    public static final String NO_ACTION_NEEDED = "no_action_needed";
    public static final List<String> ALL = List.of("fixed", "temporary", "cannot_fix", "no_action_needed");

    private RepairResolutionType() {
    }
}
