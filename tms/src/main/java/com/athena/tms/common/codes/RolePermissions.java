package com.athena.tms.common.codes;

import java.util.Set;

/**
 * 角色×動作矩陣。契約（員工帳號與登入）明寫「角色×動作矩陣在程式/設定，不在表裡」，
 * 所以這裡用程式碼承載：查某個角色是否具備某個動作權限，查不到的角色一律視為未勾選。
 */
public final class RolePermissions {

    /** 查房判定（inspect_submit）：只有主管與值班經理勾選；現場部門角色一律未勾選。 */
    private static final Set<String> INSPECT_SUBMIT_ROLES = Set.of(StaffRole.MANAGER, "duty_manager");

    private RolePermissions() {
    }

    /** 這個角色是否具備查房判定（inspect_submit）權限。 */
    public static boolean canInspectSubmit(String role) {
        return role != null && INSPECT_SUBMIT_ROLES.contains(role);
    }
}
