package com.athena.tms.common;

/**
 * 當前請求的租戶資訊，用於隔離多租戶。
 * 由 auth 的 filter（員工 JWT／住客 session 驗過後）或 AuthService（登入時）寫入，
 * TenantAwareDataSource 在取連線時讀它注入 `SET app.tenant_id`（第二道牆 RLS 的鑰匙）。
 * 其他模組只消費 get()/require()，不自行 set。背景工作（如 outbox relay） 是唯一例外：它沒有 HTTP 請求，須逐租戶自行 set/clear。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId 不得為 null；要清除請用 clear()");
        }
        CURRENT_TENANT.set(tenantId);
    }

    public static Long get() {
        return CURRENT_TENANT.get();
    }

    public static Long require() {
        Long tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("TenantContext 未建立——此路徑不該在認證之前碰資料庫");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
