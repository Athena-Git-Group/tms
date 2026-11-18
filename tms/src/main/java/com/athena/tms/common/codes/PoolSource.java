package com.athena.tms.common.codes;

import java.util.List;

public final class PoolSource {
    public static final String GUEST_SCAN = "guest_scan";
    public static final String DESK_FORWARD = "desk_forward";
    public static final String MANAGER_REQUEUE = "manager_requeue";
    public static final String LEAVE_RETURN = "leave_return";
    public static final String SHIFT_END_RETURN = "shift_end_return";
    public static final String INSPECTION_RETURN = "inspection_return";
    public static final String STAFF_REPORT = "staff_report";
    public static final String SYSTEM = "system";
    public static final String VERIFICATION_RETURN = "verification_return";
    public static final List<String> ALL = List.of("guest_scan", "desk_forward", "manager_requeue", "leave_return",
            "shift_end_return", "inspection_return", "staff_report", "system", "verification_return");

    private PoolSource() {
    }
}
