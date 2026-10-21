package com.athena.tms.common.codes;

import java.util.List;

/** staff_accounts.role（＝V1 CHECK chk_staff_accounts_role）。 */
public final class StaffRole {

    public static final String FRONTDESK = "frontdesk";
    public static final String HOUSEKEEPING = "housekeeping";
    public static final String ENGINEERING = "engineering";
    public static final String MANAGER = "manager";
    public static final String OWNER = "owner";

    public static final List<String> ALL = List.of(FRONTDESK, HOUSEKEEPING, ENGINEERING, MANAGER, OWNER);

    private StaffRole() {
    }
}
