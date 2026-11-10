package com.athena.tms.auth;

import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.ApiErrorResponses;
import com.athena.tms.dao.IntegrationApiKeyRepository;
import com.athena.tms.entity.IntegrationApiKey;
import com.athena.tms.filter.IntegrationApiKeyAuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

class IntegrationApiKeyAuthFilterTest {
    private static final long TENANT_ID = 1L;
    private static final String VALID_PLAINTEXT = "tmsik_1_" + "A".repeat(43);
    private static final String EVENTS_URI = "/integration/pms/events";
    private IntegrationApiKeyRepository repository;
    private IntegrationApiKeyAuthFilter filter;

    IntegrationApiKeyAuthFilterTest() {
    }

    @BeforeEach
    void setUp() {
        this.repository = (IntegrationApiKeyRepository)Mockito.mock(IntegrationApiKeyRepository.class);
        Mockito.when((Object)this.repository.findByKeyHash(ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        this.filter = new IntegrationApiKeyAuthFilter(this.repository, new ApiErrorResponses(new ObjectMapper()));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName(value="\u6709\u6548\u91d1\u9470\uff1a\u5efa\u7acb ROLE_INTEGRATION \u8eab\u5206\u4e26\u653e\u884c\uff0c\u96e2\u958b filter \u5f8c\u79df\u6236\u8108\u7d61\u4e0d\u6b98\u7559")
    void validKeyAuthenticates() throws Exception {
        this.stubActiveKey(VALID_PLAINTEXT, 1L, null);
        MockFilterChain chain = new MockFilterChain();
        this.filter.doFilter((ServletRequest)this.requestWithKey(VALID_PLAINTEXT), (ServletResponse)new MockHttpServletResponse(), (FilterChain)chain);
        Assertions.assertNotNull((Object)chain.getRequest(), (String)"\u6709\u6548\u91d1\u9470\u61c9\u653e\u884c\u9032\u5f8c\u7e8c chain");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Assertions.assertNotNull((Object)auth, (String)"\u653e\u884c\u6642\u5fc5\u9808\u5df2\u5efa\u7acb\u8eab\u5206");
        Assertions.assertTrue((boolean)auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_INTEGRATION")), (String)"\u8eab\u5206\u5fc5\u9808\u5e36 ROLE_INTEGRATION");
        Assertions.assertNull((Object)TenantContext.get(), (String)"finally \u5fc5\u9808\u6e05\u6389 TenantContext");
    }

    @Test
    @DisplayName(value="\u908a\u754c\uff1a\u7f3a X-API-Key \u6642 filter \u4e0d\u51fa\u624b\uff0c\u653e\u884c\u8b93\u6388\u6b0a\u77e9\u9663\u6c7a\u5b9a 401/403")
    void missingHeaderPassesThroughUnauthenticated() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        this.filter.doFilter((ServletRequest)new MockHttpServletRequest("GET", EVENTS_URI), (ServletResponse)response, (FilterChain)chain);
        Assertions.assertNotNull((Object)chain.getRequest(), (String)"\u7f3a header \u7684\u8acb\u6c42\u61c9\u653e\u884c\u9032\u6388\u6b0a\u5c64");
        Assertions.assertNull((Object)SecurityContextHolder.getContext().getAuthentication(), (String)"filter \u4e0d\u5f97\u6191\u7a7a\u5efa\u7acb\u8eab\u5206");
        Assertions.assertEquals((int)200, (int)response.getStatus(), (String)"401 \u7531\u6388\u6b0a\u5c64\u7684\u932f\u8aa4\u51fa\u53e3\u7522\u751f\uff0cfilter \u4e0d\u5148\u5beb");
    }

    @ParameterizedTest(name="\u683c\u5f0f\u4e0d\u5408\u6cd5\u7684\u91d1\u9470\u300c{0}\u300d\u2192 401")
    @ValueSource(strings={"tmsik_abc_x", "Bearer xxx", "tmsik_1", "tmsik_1_abc$def", "tmsik_99999999999999999999_abc"})
    void malformedKeyRejected(String plaintext) throws Exception {
        this.assertRejectedWith401(plaintext);
    }

    @Test
    @DisplayName(value="\u908a\u754c\uff1a\u683c\u5f0f\u6b63\u78ba\u4f46 SHA-256 \u67e5\u7121\u5217 \u2192 401")
    void unknownKeyRejected() throws Exception {
        this.assertRejectedWith401(VALID_PLAINTEXT);
    }

    @Test
    @DisplayName(value="\u908a\u754c\uff1a\u547d\u4e2d\u5217\u4f46 revoked_at \u5df2\u843d\u5370 \u2192 401")
    void revokedKeyRejected() throws Exception {
        this.stubActiveKey(VALID_PLAINTEXT, 1L, OffsetDateTime.parse("2026-10-20T00:00:00Z"));
        this.assertRejectedWith401(VALID_PLAINTEXT);
    }

    @Test
    @DisplayName(value="\u908a\u754c\uff1a\u660e\u6587\u81ea\u8ff0\u79df\u6236 2\u3001\u5217\u4e0a\u7c3d\u767c\u79df\u6236\u662f 1 \u2192 401")
    void tenantMismatchRejected() throws Exception {
        String tampered = "tmsik_2_" + "A".repeat(43);
        this.stubActiveKey(tampered, 1L, null);
        this.assertRejectedWith401(tampered);
    }

    @Test
    @DisplayName(value="\u975e\u6574\u5408\u8def\u5f91\u5e36\u6709\u6548 X-API-Key\uff1a\u4e00\u6a23\u5efa\u7acb INTEGRATION \u8eab\u5206\u518d\u653e\u884c")
    void nonIntegrationPathStillAuthenticates() throws Exception {
        this.stubActiveKey(VALID_PLAINTEXT, 1L, null);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/staff/tasks");
        request.addHeader("X-API-Key", (Object)VALID_PLAINTEXT);
        this.filter.doFilter((ServletRequest)request, (ServletResponse)new MockHttpServletResponse(), (FilterChain)chain);
        Assertions.assertNotNull((Object)chain.getRequest(), (String)"\u9a57\u8b49\u901a\u904e\u61c9\u653e\u884c\u9032\u6388\u6b0a\u5c64");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Assertions.assertNotNull((Object)auth, (String)"\u5e36\u6709\u6548\u91d1\u9470\u5c31\u5fc5\u9808\u5efa\u7acb\u8eab\u5206\uff0c\u8207\u8acb\u6c42\u8def\u5f91\u7121\u95dc");
        Assertions.assertTrue((boolean)auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_INTEGRATION")), (String)"\u8eab\u5206\u5fc5\u9808\u5e36 ROLE_INTEGRATION");
    }

    private void assertRejectedWith401(String plaintext) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        this.filter.doFilter((ServletRequest)this.requestWithKey(plaintext), (ServletResponse)response, (FilterChain)chain);
        Assertions.assertEquals((int)401, (int)response.getStatus());
        Assertions.assertNull((Object)chain.getRequest(), (String)"\u9a57\u8b49\u5931\u6557\u4e0d\u5f97\u518d\u9032\u6388\u6b0a\u5c64");
        Assertions.assertNull((Object)SecurityContextHolder.getContext().getAuthentication());
        Assertions.assertNull((Object)TenantContext.get(), (String)"\u5931\u6557\u8def\u5f91\u5fc5\u9808\u6e05\u6389\u5df2\u8a2d\u5b9a\u7684\u79df\u6236\u8108\u7d61");
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        Assertions.assertTrue((boolean)body.contains("\"code\":\"unauthorized\""), (String)("\u932f\u8aa4\u683c\u5f0f\u9808\u6cbf\u7528\u65e2\u6709\u7684 ApiError JSON \u5f62\u72c0\uff0c\u5be6\u969b: " + body));
        Assertions.assertTrue((boolean)body.contains("\u6574\u5408\u91d1\u9470\u7121\u6548"), (String)("\u5931\u6557\u8a0a\u606f\u5fc5\u9808\u7d71\u4e00\uff0c\u5be6\u969b: " + body));
    }

    private MockHttpServletRequest requestWithKey(String plaintext) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", EVENTS_URI);
        request.addHeader("X-API-Key", (Object)plaintext);
        return request;
    }

    private void stubActiveKey(String plaintext, long rowTenantId, OffsetDateTime revokedAt) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        IntegrationApiKey key = new IntegrationApiKey();
        key.setId(UUID.randomUUID());
        key.setTenantId(Long.valueOf(rowTenantId));
        key.setName("pms-integration");
        key.setKeyPrefix(plaintext.substring(0, 16));
        key.setKeyHash(hash);
        key.setCreatedAt(OffsetDateTime.now());
        key.setRevokedAt(revokedAt);
        Mockito.when((Object)this.repository.findByKeyHash(hash)).thenReturn(Optional.of(key));
    }
}
