/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  jakarta.servlet.http.HttpServletRequest
 *  org.springframework.stereotype.Component
 */
package com.athena.tms.service;

import com.athena.tms.service.IdempotencyTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RequestAttributeTenantResolver
implements IdempotencyTenantResolver {
    @Override
    public Optional<Long> resolveTenantId(HttpServletRequest request) {
        Object attribute = request.getAttribute("tms.tenant_id");
        if (attribute instanceof Number number) {
            return Optional.of(number.longValue());
        }
        if (attribute instanceof String text && !text.isBlank()) {
            return RequestAttributeTenantResolver.parse(text);
        }
        return Optional.empty();
    }

    private static Optional<Long> parse(String value) {
        try {
            return Optional.of(Long.parseLong(value.trim()));
        }
        catch (NumberFormatException _) {
            return Optional.empty();
        }
    }
}
