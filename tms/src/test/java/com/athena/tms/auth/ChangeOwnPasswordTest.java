package com.athena.tms.auth;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.ApiErrorResponses;
import com.athena.tms.common.error.ApiException;
import com.athena.tms.config.AuthProperties;
import com.athena.tms.dao.GuestSessionRepository;
import com.athena.tms.dao.StaffAccountRepository;
import com.athena.tms.dto.StaffChangePasswordRequest;
import com.athena.tms.dto.StaffLoginResponse;
import com.athena.tms.entity.StaffAccount;
import com.athena.tms.filter.BearerTokenAuthFilter;
import com.athena.tms.service.JwtService;
import com.athena.tms.service.StaffAccountLoginService;
import com.athena.tms.service.TenantScopedExecutor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

/**
 * contracts changeOwnPassword（{@code POST /staff-accounts/change-password}）的分支驗證：
 * 強制改密與自願變更兩種情境、三個錯誤碼、改密後舊權杖失效，以及
 * {@link BearerTokenAuthFilter} 對這條路徑的放行。
 */
class ChangeOwnPasswordTest {

    private static final long TENANT_ID = 1L;
    private static final String CHANGE_PASSWORD_URI = "/api/v1/staff-accounts/change-password";
    private static final String CURRENT_PASSWORD = "Passw0rd!";

    private StaffAccountRepository staffAccountRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private StaffAccountLoginService loginService;
    private BearerTokenAuthFilter filter;

    @BeforeEach
    void setUp() {
        staffAccountRepository = Mockito.mock(StaffAccountRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService(new AuthProperties("x".repeat(48), Duration.ofHours(12)));
        loginService = new StaffAccountLoginService(
                staffAccountRepository, Mockito.mock(TenantScopedExecutor.class), passwordEncoder, jwtService);
        filter = new BearerTokenAuthFilter(
                jwtService,
                staffAccountRepository,
                Mockito.mock(GuestSessionRepository.class),
                new ApiErrorResponses(new ObjectMapper()),
                Clock.systemUTC());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        CurrentActor.clear();
    }

    @Test
    @DisplayName("強制改密：不帶目前密碼也能換，旗標清掉、換發新權杖、舊密碼不再成立")
    void forcedChangeWithoutCurrentPassword() {
        StaffAccount account = stubAccount(true);
        StaffLoginResponse response =
                loginService.changePassword(TENANT_ID, account.getId(), new StaffChangePasswordRequest(null, "NewPassw0rd!"));

        Assertions.assertFalse(response.mustChangePassword(), "回應的 mustChangePassword 必為 false");
        Assertions.assertNotNull(response.token(), "必須換發新權杖");
        Assertions.assertFalse(account.isMustChangePassword(), "強制改密旗標要清掉");
        Assertions.assertTrue(passwordEncoder.matches("NewPassw0rd!", account.getPasswordHash()), "新密碼要落地");
        Assertions.assertFalse(passwordEncoder.matches(CURRENT_PASSWORD, account.getPasswordHash()), "舊密碼不再成立");
    }

    @Test
    @DisplayName("自願變更：沒帶目前密碼 → 422 current_password_required")
    void voluntaryChangeRequiresCurrentPassword() {
        StaffAccount account = stubAccount(false);
        ApiException thrown = Assertions.assertThrows(
                ApiException.class,
                () -> loginService.changePassword(
                        TENANT_ID, account.getId(), new StaffChangePasswordRequest("  ", "NewPassw0rd!")));
        Assertions.assertEquals(422, thrown.getStatus());
        Assertions.assertEquals("current_password_required", thrown.getCode());
    }

    @Test
    @DisplayName("自願變更：目前密碼比對不符 → 401 current_password_invalid")
    void voluntaryChangeRejectsWrongCurrentPassword() {
        StaffAccount account = stubAccount(false);
        ApiException thrown = Assertions.assertThrows(
                ApiException.class,
                () -> loginService.changePassword(
                        TENANT_ID, account.getId(), new StaffChangePasswordRequest("WrongPass", "NewPassw0rd!")));
        Assertions.assertEquals(401, thrown.getStatus());
        Assertions.assertEquals("current_password_invalid", thrown.getCode());
    }

    @Test
    @DisplayName("新密碼與目前密碼相同 → 422 new_password_invalid")
    void sameAsCurrentPasswordRejected() {
        StaffAccount account = stubAccount(false);
        ApiException thrown = Assertions.assertThrows(
                ApiException.class,
                () -> loginService.changePassword(
                        TENANT_ID, account.getId(), new StaffChangePasswordRequest(CURRENT_PASSWORD, CURRENT_PASSWORD)));
        Assertions.assertEquals(422, thrown.getStatus());
        Assertions.assertEquals("new_password_invalid", thrown.getCode());
    }

    @Test
    @DisplayName("密碼強度門檻 truth 還沒定義：短密碼照收，不在後端自己擋長度")
    void shortNewPasswordAccepted() {
        StaffAccount account = stubAccount(true);
        StaffLoginResponse response =
                loginService.changePassword(TENANT_ID, account.getId(), new StaffChangePasswordRequest(null, "ab"));
        Assertions.assertNotNull(response.token());
    }

    @Test
    @DisplayName("放行：強制改密中的員工打改密端點，filter 讓它進去並建立身分")
    void filterLetsChangePasswordThroughWhileMustChangePassword() throws Exception {
        StaffAccount account = stubAccount(true);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                (ServletRequest) requestWithToken("POST", CHANGE_PASSWORD_URI, tokenFor(account)),
                (ServletResponse) response,
                (FilterChain) chain);

        Assertions.assertNotNull(chain.getRequest(), "改密端點必須放行，否則員工卡死在改密畫面");
        Assertions.assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("其他業務端點在強制改密期間維持 403 must_change_password")
    void filterStillBlocksOtherEndpointsWhileMustChangePassword() throws Exception {
        StaffAccount account = stubAccount(true);
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                (ServletRequest) requestWithToken("GET", "/api/v1/my-today", tokenFor(account)),
                (ServletResponse) response,
                (FilterChain) chain);

        Assertions.assertNull(chain.getRequest(), "強制改密期間其他端點不得放行");
        Assertions.assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("改密成功後舊權杖立即失效 → 401")
    void oldTokenRejectedAfterPasswordChange() throws Exception {
        StaffAccount account = stubAccount(true);
        String oldToken = tokenFor(account);
        loginService.changePassword(TENANT_ID, account.getId(), new StaffChangePasswordRequest(null, "NewPassw0rd!"));

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(
                (ServletRequest) requestWithToken("GET", "/api/v1/my-today", oldToken),
                (ServletResponse) response,
                (FilterChain) chain);

        Assertions.assertNull(chain.getRequest(), "改密前簽出去的權杖不得再放行");
        Assertions.assertEquals(401, response.getStatus());
    }

    /** filter 與 service 共用同一個 account 物件，service 改掉雜湊後 filter 查到的就是改後的狀態。 */
    private StaffAccount stubAccount(boolean mustChangePassword) {
        StaffAccount account = new StaffAccount();
        account.setId(UUID.randomUUID());
        account.setTenantId(TENANT_ID);
        account.setName("阿美");
        account.setLoginId("H-0532");
        account.setPasswordHash(passwordEncoder.encode(CURRENT_PASSWORD));
        account.setRole("housekeeping");
        account.setDept("housekeeping");
        account.setActive(true);
        account.setMustChangePassword(mustChangePassword);
        account.setCreatedAt(OffsetDateTime.now());
        account.setUpdatedAt(OffsetDateTime.now());
        Mockito.when(staffAccountRepository.findByTenantIdAndId(TENANT_ID, account.getId()))
                .thenReturn(Optional.of(account));
        Mockito.when(staffAccountRepository.save(ArgumentMatchers.any(StaffAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return account;
    }

    /** 比照登入換發的權杖：帶密碼雜湊指紋，改密之後就對不上。 */
    private String tokenFor(StaffAccount account) {
        return jwtService
                .issue(
                        TENANT_ID,
                        account.getId(),
                        account.getRole(),
                        account.getDept(),
                        com.athena.tms.common.CredentialFingerprint.of(account.getPasswordHash()))
                .token();
    }

    private MockHttpServletRequest requestWithToken(String method, String uri, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
