/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  jakarta.servlet.FilterChain
 *  jakarta.servlet.ServletException
 *  jakarta.servlet.ServletRequest
 *  jakarta.servlet.ServletResponse
 *  jakarta.servlet.http.HttpServletRequest
 *  jakarta.servlet.http.HttpServletResponse
 *  org.springframework.core.annotation.Order
 *  org.springframework.http.HttpMethod
 *  org.springframework.stereotype.Component
 *  org.springframework.web.filter.OncePerRequestFilter
 *  org.springframework.web.util.ContentCachingResponseWrapper
 */
package com.athena.tms.filter;

import com.athena.tms.service.IdempotencyService;
import com.athena.tms.service.IdempotencyTenantResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@Order(value=0)
public class IdempotencyFilter
extends OncePerRequestFilter {
    public static final int FILTER_ORDER = 0;
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotency-Replayed";
    private final IdempotencyService idempotencyService;
    private final IdempotencyTenantResolver tenantResolver;

    public IdempotencyFilter(IdempotencyService idempotencyService, IdempotencyTenantResolver tenantResolver) {
        this.idempotencyService = idempotencyService;
        this.tenantResolver = tenantResolver;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            filterChain.doFilter((ServletRequest)request, (ServletResponse)response);
            return;
        }
        String opId = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        Long tenantId = this.tenantResolver.resolveTenantId(request).orElse(null);
        if (opId == null || opId.isBlank() || tenantId == null) {
            filterChain.doFilter((ServletRequest)request, (ServletResponse)response);
            return;
        }
        Optional<IdempotencyService.StoredResult> replay = this.idempotencyService.findResult(tenantId, opId);
        if (replay.isPresent()) {
            this.writeReplay(response, replay.get());
            return;
        }
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter((ServletRequest)request, (ServletResponse)wrapper);
            if (wrapper.getStatus() < 500) {
                String body = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
                try {
                    this.idempotencyService.store(tenantId, opId, wrapper.getStatus(), wrapper.getContentType(), body);
                }
                catch (RuntimeException e) {
                    this.logger.warn((Object)"processed_ops \u843d\u7c3f\u5931\u6557\u2014\u2014\u9019\u628a\u9375\u7684\u91cd\u9001\u6703\u91cd\u7b97\u800c\u4e0d\u662f\u56de\u653e", (Throwable)e);
                }
            }
        }
        finally {
            wrapper.copyBodyToResponse();
        }
    }

    private void writeReplay(HttpServletResponse response, IdempotencyService.StoredResult result) throws IOException {
        response.setStatus(result.status());
        if (result.contentType() != null) {
            response.setContentType(result.contentType());
        }
        response.setHeader(REPLAYED_HEADER, "true");
        if (!result.body().isEmpty()) {
            byte[] bytes = result.body().getBytes(StandardCharsets.UTF_8);
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
        }
    }
}
