package com.athena.tms.common.codes;

import java.util.Set;

public final class RolePermissions {

    private static final Set<String> INSPECT_SUBMIT_ROLES = Set.of(StaffRole.MANAGER, "duty_manager");

    private RolePermissions() {
    }

    public static boolean canInspectSubmit(String role) {
        return role != null && INSPECT_SUBMIT_ROLES.contains(role);
    }
}
