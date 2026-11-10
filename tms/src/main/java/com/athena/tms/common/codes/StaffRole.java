package com.athena.tms.common.codes;

import java.util.List;

public final class StaffRole {
    public static final String FRONTDESK = "frontdesk";
    public static final String HOUSEKEEPING = "housekeeping";
    public static final String ENGINEERING = "engineering";
    public static final String MANAGER = "manager";
    public static final String OWNER = "owner";
    public static final List<String> ALL = List.of("frontdesk", "housekeeping", "engineering", "manager", "owner");

    private StaffRole() {
    }
}
