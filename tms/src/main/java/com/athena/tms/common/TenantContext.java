package com.athena.tms.common;

public final class TenantContext {
    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal();

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
