/*
 * Decompiled with CFR 0.152.
 */
package com.athena.tms.common;

public final class TenantContext {
    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId \u4e0d\u5f97\u70ba null\uff1b\u8981\u6e05\u9664\u8acb\u7528 clear()");
        }
        CURRENT_TENANT.set(tenantId);
    }

    public static Long get() {
        return CURRENT_TENANT.get();
    }

    public static Long require() {
        Long tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("TenantContext \u672a\u5efa\u7acb\u2014\u2014\u6b64\u8def\u5f91\u4e0d\u8a72\u5728\u8a8d\u8b49\u4e4b\u524d\u78b0\u8cc7\u6599\u5eab");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
