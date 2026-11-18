package com.athena.tms.common.codes;

import java.util.List;
import java.util.Set;

public final class RolePermissions {

    private static final Set<String> INSPECT_SUBMIT_ROLES = Set.of(StaffRole.MANAGER, "duty_manager");

    // 能用櫃檯畫面的角色：櫃檯、主管、值班主管。登入的角色檢查與櫃檯端點的權限規則都照這份名單。
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

    // 給 SecurityConfig 的 hasAnyRole 用，角色名稱不帶 ROLE_ 前綴。每次回新的陣列，呼叫端改了也不影響名單。
    public static String[] frontdeskWebRoles() {
        return FRONTDESK_WEB_ROLES.toArray(String[]::new);
    }
}
