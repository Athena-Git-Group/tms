package com.athena.tms.common.codes;

import java.util.Set;

/**
 * 角色×動作矩陣。契約（員工帳號與登入）明寫「角色×動作矩陣在程式/設定，不在表裡」，
 * 所以這裡用程式碼承載：查某個角色是否具備某個動作權限，查不到的角色一律視為未勾選。
 */
public final class RolePermissions {

    private static final Set<String> INSPECT_SUBMIT_ROLES = Set.of(StaffRole.MANAGER, "duty_manager");

    private RolePermissions() {
    }

    public static boolean canInspectSubmit(String role) {
        return role != null && INSPECT_SUBMIT_ROLES.contains(role);
    }
}
