package com.athena.tms.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

public interface IdempotencyTenantResolver {
    public static final String TENANT_ATTRIBUTE = "tms.tenant_id";

    public Optional<Long> resolveTenantId(HttpServletRequest var1);
}
