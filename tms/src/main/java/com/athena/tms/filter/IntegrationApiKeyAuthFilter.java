package com.athena.tms.filter;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.ApiErrorResponses;
import com.athena.tms.dao.IntegrationApiKeyRepository;
import com.athena.tms.entity.IntegrationApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class IntegrationApiKeyAuthFilter
extends OncePerRequestFilter {
    public static final String ROLE = "INTEGRATION";
    public static final String API_KEY_HEADER = "X-API-Key";
    private static final Pattern KEY_PATTERN = Pattern.compile("^tmsik_(\\d+)_([A-Za-z0-9_-]+)$");
    private static final String INVALID_KEY_MESSAGE = "整合金鑰無效";
    private final IntegrationApiKeyRepository integrationApiKeyRepository;
    private final ApiErrorResponses apiErrorResponses;

    public IntegrationApiKeyAuthFilter(IntegrationApiKeyRepository integrationApiKeyRepository, ApiErrorResponses apiErrorResponses) {
        this.integrationApiKeyRepository = integrationApiKeyRepository;
        this.apiErrorResponses = apiErrorResponses;
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            boolean shouldAuthenticate;
            String apiKey = request.getHeader(API_KEY_HEADER);
            boolean bl = shouldAuthenticate = apiKey != null && !apiKey.isBlank();
            if (shouldAuthenticate && !this.authenticate(apiKey, response)) {
                return;
            }
            filterChain.doFilter((ServletRequest)request, (ServletResponse)response);
        }
        finally {
            TenantContext.clear();
            CurrentActor.clear();
        }
    }

    private boolean authenticate(String apiKey, HttpServletResponse response) throws IOException {
        long tenantId;
        Matcher matcher = KEY_PATTERN.matcher(apiKey);
        if (!matcher.matches()) {
            return this.unauthorized(response);
        }
        try {
            tenantId = Long.parseLong(matcher.group(1));
        }
        catch (NumberFormatException overflow) {
            return this.unauthorized(response);
        }
        TenantContext.set(tenantId);
        IntegrationApiKey key = this.integrationApiKeyRepository.findByKeyHash(IntegrationApiKeyAuthFilter.sha256Hex(apiKey)).orElse(null);
        if (key == null || !key.getTenantId().equals(tenantId) || key.getRevokedAt() != null) {
            TenantContext.clear();
            return this.unauthorized(response);
        }
        this.establishAuthentication("integration:" + String.valueOf(key.getId()));
        return true;
    }

    private void establishAuthentication(String principal) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated((Object)principal, null, List.of(new SimpleGrantedAuthority("ROLE_INTEGRATION")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication((Authentication)authentication);
        SecurityContextHolder.setContext((SecurityContext)context);
    }

    private static String sha256Hex(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private boolean unauthorized(HttpServletResponse response) throws IOException {
        this.apiErrorResponses.write(response, 401, "unauthorized", INVALID_KEY_MESSAGE);
        return false;
    }
}
