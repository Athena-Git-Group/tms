package com.athena.tms.dto;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 住客 session token 的格式擁有者：`<tenant_id>.<base64url 隨機 32 bytes>`。
 *
 * 為什麼要把租戶編進 token：guest_sessions 有 RLS（第二道牆），tms_app 角色
 * 未注入 app.tenant_id 時查 session_token 一律零列——住客請求又天生沒有租戶
 * 脈絡，所以租戶線索只能由 token 自己攜帶：先解前綴立 TenantContext，再進 DB
 * 驗 session。token 隨機段 256-bit，猜中不可行；前綴只是路由線索，
 * 真正的授權仍由 DB 裡那一列（expires_at/revoked_at）決定。
 *
 * 簽發方（/guest/scan 掃碼端點）一律呼叫 issue()，不得自創格式。
 */
public final class GuestSessionTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private GuestSessionTokens() {
    }

    public static String issue(long tenantId) {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        return tenantId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * 解出 token 內嵌的租戶 id；非本格式（含 JWT——它有兩個點）回 null。
     * 回 null 不代表 401 的全部條件，只代表「這不是住客 token」。
     */
    public static Long parseTenantId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        int separator = token.indexOf('.');
        if (separator <= 0 || token.indexOf('.', separator + 1) >= 0 || separator == token.length() - 1) {
            return null;
        }
        try {
            return Long.parseLong(token.substring(0, separator));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
