package com.athena.tms.integration.stepdefs.utils;

import com.athena.tms.common.TenantContext;
import java.util.function.Supplier;

public final class Tenants {

    private Tenants() {
    }

    public static <T> T asTenant(long tenantId, Supplier<T> work) {
        try {
            TenantContext.set(tenantId);
            return work.get();
        } finally {
            TenantContext.clear();
        }
    }

    public static void runAsTenant(long tenantId, Runnable work) {
        asTenant(tenantId, () -> {
            work.run();
            return null;
        });
    }
}
