package com.athena.tms.common.codes;

import java.util.List;
import java.util.Set;

public final class RolePermissions {

    private static final Set<String> INSPECT_SUBMIT_ROLES = Set.of(StaffRole.MANAGER, "duty_manager");

    private static final List<String> FRONTDESK_WEB_ROLES =
            List.of(StaffRole.FRONTDESK, StaffRole.MANAGER, StaffRole.DUTY_MANAGER);

    private RolePermissions() {
    }

    public static boolean canInspectSubmit(String role) {
        return role != null && INSPECT_SUBMIT_ROLES.contains(role);
    }

    public static boolean canUseFrontdeskWeb(String role) {
        return role != null && FRONTDESK_WEB_ROLES.contains(role);
    }

    public static String[] frontdeskWebRoles() {
        return FRONTDESK_WEB_ROLES.toArray(String[]::new);
    }
}
