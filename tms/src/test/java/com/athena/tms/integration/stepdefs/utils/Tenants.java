package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.common.TenantContext;
import java.util.function.Supplier;

public final class Tenants {
    public static <T> T asTenant(long tenantId, Supplier<T> work) {
        try {
            TenantContext.set((Long)tenantId);
            T t = work.get();
            return t;
        }
        finally {
            TenantContext.clear();
        }
    }

    public static void runAsTenant(long tenantId, Runnable work) {
        Tenants.asTenant(tenantId, () -> {
            work.run();
            return null;
        });
    }

    private Tenants() {
    }
}
