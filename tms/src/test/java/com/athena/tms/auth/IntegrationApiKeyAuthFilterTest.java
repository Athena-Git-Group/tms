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
    @DisplayName(value="有效金鑰：建立 ROLE_INTEGRATION 身分並放行，離開 filter 後租戶脈絡不殘留")
    void validKeyAuthenticates() throws Exception {
        this.stubActiveKey(VALID_PLAINTEXT, 1L, null);
        MockFilterChain chain = new MockFilterChain();
        this.filter.doFilter((ServletRequest)this.requestWithKey(VALID_PLAINTEXT), (ServletResponse)new MockHttpServletResponse(), (FilterChain)chain);
        Assertions.assertNotNull((Object)chain.getRequest(), (String)"有效金鑰應放行進後續 chain");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Assertions.assertNotNull((Object)auth, (String)"放行時必須已建立身分");
        Assertions.assertTrue((boolean)auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_INTEGRATION")), (String)"身分必須帶 ROLE_INTEGRATION");
        Assertions.assertNull((Object)TenantContext.get(), (String)"finally 必須清掉 TenantContext");
    }

    @Test
    @DisplayName(value="邊界：缺 X-API-Key 時 filter 不出手，放行讓授權矩陣決定 401/403")
    void missingHeaderPassesThroughUnauthenticated() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        this.filter.doFilter((ServletRequest)new MockHttpServletRequest("GET", EVENTS_URI), (ServletResponse)response, (FilterChain)chain);
        Assertions.assertNotNull((Object)chain.getRequest(), (String)"缺 header 的請求應放行進授權層");
        Assertions.assertNull((Object)SecurityContextHolder.getContext().getAuthentication(), (String)"filter 不得憑空建立身分");
        Assertions.assertEquals((int)200, (int)response.getStatus(), (String)"401 由授權層的錯誤出口產生，filter 不先寫");
    }

    @ParameterizedTest(name="格式不合法的金鑰「{0}」→ 401")
    @ValueSource(strings={"tmsik_abc_x", "Bearer xxx", "tmsik_1", "tmsik_1_abc$def", "tmsik_99999999999999999999_abc"})
    void malformedKeyRejected(String plaintext) throws Exception {
        this.assertRejectedWith401(plaintext);
    }

    @Test
    @DisplayName(value="邊界：格式正確但 SHA-256 查無列 → 401")
    void unknownKeyRejected() throws Exception {
        this.assertRejectedWith401(VALID_PLAINTEXT);
    }

    @Test
    @DisplayName(value="邊界：命中列但 revoked_at 已落印 → 401")
    void revokedKeyRejected() throws Exception {
        this.stubActiveKey(VALID_PLAINTEXT, 1L, OffsetDateTime.parse("2026-10-20T00:00:00Z"));
        this.assertRejectedWith401(VALID_PLAINTEXT);
    }

    @Test
    @DisplayName(value="邊界：明文自述租戶 2、列上簽發租戶是 1 → 401")
    void tenantMismatchRejected() throws Exception {
        String tampered = "tmsik_2_" + "A".repeat(43);
        this.stubActiveKey(tampered, 1L, null);
        this.assertRejectedWith401(tampered);
    }

    @Test
    @DisplayName(value="非整合路徑帶有效 X-API-Key：一樣建立 INTEGRATION 身分再放行")
    void nonIntegrationPathStillAuthenticates() throws Exception {
        this.stubActiveKey(VALID_PLAINTEXT, 1L, null);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/staff/tasks");
        request.addHeader("X-API-Key", (Object)VALID_PLAINTEXT);
        this.filter.doFilter((ServletRequest)request, (ServletResponse)new MockHttpServletResponse(), (FilterChain)chain);
        Assertions.assertNotNull((Object)chain.getRequest(), (String)"驗證通過應放行進授權層");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Assertions.assertNotNull((Object)auth, (String)"帶有效金鑰就必須建立身分，與請求路徑無關");
        Assertions.assertTrue((boolean)auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_INTEGRATION")), (String)"身分必須帶 ROLE_INTEGRATION");
    }

    private void assertRejectedWith401(String plaintext) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        this.filter.doFilter((ServletRequest)this.requestWithKey(plaintext), (ServletResponse)response, (FilterChain)chain);
        Assertions.assertEquals((int)401, (int)response.getStatus());
        Assertions.assertNull((Object)chain.getRequest(), (String)"驗證失敗不得再進授權層");
        Assertions.assertNull((Object)SecurityContextHolder.getContext().getAuthentication());
        Assertions.assertNull((Object)TenantContext.get(), (String)"失敗路徑必須清掉已設定的租戶脈絡");
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        Assertions.assertTrue((boolean)body.contains("\"code\":\"unauthorized\""), (String)("錯誤格式須沿用既有的 ApiError JSON 形狀，實際: " + body));
        Assertions.assertTrue((boolean)body.contains("整合金鑰無效"), (String)("失敗訊息必須統一，實際: " + body));
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
