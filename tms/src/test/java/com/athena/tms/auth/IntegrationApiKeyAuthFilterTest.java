package com.athena.tms.auth;

import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.ApiErrorResponses;
import com.athena.tms.dao.IntegrationApiKeyRepository;
import com.athena.tms.entity.IntegrationApiKey;
import com.athena.tms.filter.IntegrationApiKeyAuthFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * 整合金鑰認證 filter 的單獨驗證：不起 Spring context，repository 用替身，
 * 聚焦「哪些金鑰被拒、哪些放行、租戶脈絡是否殘留」這三件授權矩陣看不到的事。
 *
 * <p>缺 header 的 401、整合金鑰打員工端點的 403 與員工憑證打整合端點的 403
 * 不在這裡驗：那些結果由授權矩陣與其錯誤出口產生，這裡只驗到 filter 建好
 * 身分（或放行）為止。
 */
class IntegrationApiKeyAuthFilterTest {

    private static final long TENANT_ID = 1L;
    private static final String VALID_PLAINTEXT =
            "tmsik_1_" + "A".repeat(43);
    private static final String EVENTS_URI = "/integration/pms/events";

    private IntegrationApiKeyRepository repository;
    private IntegrationApiKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(IntegrationApiKeyRepository.class);
        // 預設任何 hash 都查無列；個別測試再覆寫成有列
        Mockito.when(repository.findByKeyHash(anyString())).thenReturn(Optional.empty());
        filter = new IntegrationApiKeyAuthFilter(repository,
                new ApiErrorResponses(new tools.jackson.databind.ObjectMapper()));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("有效金鑰：建立 ROLE_INTEGRATION 身分並放行，離開 filter 後租戶脈絡不殘留")
    void validKeyAuthenticates() throws Exception {
        stubActiveKey(VALID_PLAINTEXT, TENANT_ID, null);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithKey(VALID_PLAINTEXT), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), "有效金鑰應放行進後續 chain");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "放行時必須已建立身分");
        assertTrue(auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_INTEGRATION")),
                "身分必須帶 ROLE_INTEGRATION");
        // 容器執行緒會復用，脈絡殘留等於下一個請求繼承到別人的租戶
        assertNull(TenantContext.get(), "finally 必須清掉 TenantContext");
    }

    @Test
    @DisplayName("邊界：缺 X-API-Key 時 filter 不出手，放行讓授權矩陣決定 401/403")
    void missingHeaderPassesThroughUnauthenticated() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", EVENTS_URI), response, chain);

        assertNotNull(chain.getRequest(), "缺 header 的請求應放行進授權層");
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "filter 不得憑空建立身分");
        assertEquals(200, response.getStatus(), "401 由授權層的錯誤出口產生，filter 不先寫");
    }

    @ParameterizedTest(name = "格式不合法的金鑰「{0}」→ 401")
    @ValueSource(strings = {
            "tmsik_abc_x",          // 租戶段不是數字
            "Bearer xxx",           // 整串不是本系統的金鑰格式
            "tmsik_1",              // 缺隨機段
            "tmsik_1_abc$def",      // 隨機段含 base64url 之外的字元
            "tmsik_99999999999999999999_abc" // 租戶段超出 long 範圍
    })
    void malformedKeyRejected(String plaintext) throws Exception {
        assertRejectedWith401(plaintext);
    }

    @Test
    @DisplayName("邊界：格式正確但 SHA-256 查無列 → 401")
    void unknownKeyRejected() throws Exception {
        // repository 替身預設查無列，直接送格式正確的明文即可
        assertRejectedWith401(VALID_PLAINTEXT);
    }

    @Test
    @DisplayName("邊界：命中列但 revoked_at 已落印 → 401")
    void revokedKeyRejected() throws Exception {
        stubActiveKey(VALID_PLAINTEXT, TENANT_ID,
                OffsetDateTime.parse("2026-10-20T00:00:00Z"));
        assertRejectedWith401(VALID_PLAINTEXT);
    }

    @Test
    @DisplayName("邊界：明文自述租戶 2、列上簽發租戶是 1 → 401")
    void tenantMismatchRejected() throws Exception {
        // 攻擊情境：拿租戶 1 的有效金鑰，把明文的租戶段改寫成 2
        String tampered = "tmsik_2_" + "A".repeat(43);
        stubActiveKey(tampered, TENANT_ID, null);
        assertRejectedWith401(tampered);
    }

    @Test
    @DisplayName("非整合路徑帶有效 X-API-Key：一樣建立 INTEGRATION 身分再放行")
    void nonIntegrationPathStillAuthenticates() throws Exception {
        // 身分建好之後，員工端點的拒絕是授權矩陣的 403（身分有效、矩陣不放行），
        // 不是匿名的 401；403 本身由整合測試驗，這裡只驗 filter 有建身分。
        stubActiveKey(VALID_PLAINTEXT, TENANT_ID, null);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/staff/tasks");
        request.addHeader(IntegrationApiKeyAuthFilter.API_KEY_HEADER, VALID_PLAINTEXT);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), "驗證通過應放行進授權層");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "帶有效金鑰就必須建立身分，與請求路徑無關");
        assertTrue(auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_INTEGRATION")),
                "身分必須帶 ROLE_INTEGRATION");
    }

    /** 共用的拒絕斷言：401、統一錯誤格式、不進 chain、不留身分與租戶脈絡。 */
    private void assertRejectedWith401(String plaintext) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithKey(plaintext), response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest(), "驗證失敗不得再進授權層");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(TenantContext.get(), "失敗路徑必須清掉已設定的租戶脈絡");
        // 所有失敗模式共用同一則訊息，避免從回應差異推敲失敗原因
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"code\":\"unauthorized\""),
                "錯誤格式須沿用既有的 ApiError JSON 形狀，實際: " + body);
        assertTrue(body.contains("整合金鑰無效"), "失敗訊息必須統一，實際: " + body);
    }

    private MockHttpServletRequest requestWithKey(String plaintext) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", EVENTS_URI);
        request.addHeader(IntegrationApiKeyAuthFilter.API_KEY_HEADER, plaintext);
        return request;
    }

    /** 讓 repository 替身對指定明文的 SHA-256 hex 回一列（revokedAt 可為 null＝有效）。 */
    private void stubActiveKey(String plaintext, long rowTenantId, OffsetDateTime revokedAt)
            throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        IntegrationApiKey key = new IntegrationApiKey();
        key.setId(UUID.randomUUID());
        key.setTenantId(rowTenantId);
        key.setName("pms-integration");
        key.setKeyPrefix(plaintext.substring(0, 16));
        key.setKeyHash(hash);
        key.setCreatedAt(OffsetDateTime.now());
        key.setRevokedAt(revokedAt);
        Mockito.when(repository.findByKeyHash(hash)).thenReturn(Optional.of(key));
    }
}
