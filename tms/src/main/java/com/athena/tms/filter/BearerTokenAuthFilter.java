package com.athena.tms.filter;

import com.athena.tms.common.CredentialFingerprint;
import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.ApiErrorResponses;
import com.athena.tms.dao.GuestSessionRepository;
import com.athena.tms.dao.StaffAccountRepository;
import com.athena.tms.dto.GuestSessionTokens;
import com.athena.tms.entity.GuestSession;
import com.athena.tms.entity.StaffAccount;
import com.athena.tms.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerTokenAuthFilter
extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtService jwtService;
    private final StaffAccountRepository staffAccountRepository;
    private final GuestSessionRepository guestSessionRepository;
    private final ApiErrorResponses apiErrorResponses;
    private final Clock clock;

    public BearerTokenAuthFilter(JwtService jwtService, StaffAccountRepository staffAccountRepository, GuestSessionRepository guestSessionRepository, ApiErrorResponses apiErrorResponses, Clock clock) {
        this.jwtService = jwtService;
        this.staffAccountRepository = staffAccountRepository;
        this.guestSessionRepository = guestSessionRepository;
        this.apiErrorResponses = apiErrorResponses;
        this.clock = clock;
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            String guestSessionHeader = request.getHeader("X-Guest-Session");
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                boolean authenticated;
                String token = header.substring(BEARER_PREFIX.length()).trim();
                Long guestTenantId = GuestSessionTokens.parseTenantId(token);
                boolean bl = authenticated = guestTenantId != null ? this.authenticateGuest(token, guestTenantId, response) : this.authenticateStaff(token, response, this.isChangeOwnPassword(request));
                if (!authenticated) {
                    return;
                }
                request.setAttribute("tms.tenant_id", (Object)TenantContext.require());
            } else if (guestSessionHeader != null && !guestSessionHeader.isBlank()) {
                // 契約的住客憑證走 X-Guest-Session header（parameters.yaml GuestSession）：
                // token 本身不含租戶，由 session 資料列反查租戶。
                if (!this.authenticateGuestBySessionHeader(guestSessionHeader.trim(), response)) {
                    return;
                }
                request.setAttribute("tms.tenant_id", (Object)TenantContext.require());
            }
            filterChain.doFilter((ServletRequest)request, (ServletResponse)response);
        }
        finally {
            TenantContext.clear();
            CurrentActor.clear();
        }
    }

    /**
     * 本人設定新密碼的端點（contracts changeOwnPassword）。被強制改密的員工只有這條路可以走，
     * 下面的 must_change_password 檢查要對它放行，否則員工在改密畫面出不來。
     */
    private static final String CHANGE_OWN_PASSWORD_PATH = "/api/v1/staff-accounts/change-password";

    private boolean isChangeOwnPassword(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && CHANGE_OWN_PASSWORD_PATH.equals(request.getRequestURI());
    }

    private boolean authenticateStaff(String token, HttpServletResponse response, boolean changeOwnPassword) throws IOException {
        JwtService.StaffClaims claims = this.jwtService.verify(token);
        if (claims == null) {
            return this.unauthorized(response, "憑證無效或已過期");
        }
        TenantContext.set(claims.tenantId());
        StaffAccount account = this.staffAccountRepository.findByTenantIdAndId(claims.tenantId(), claims.staffId()).orElse(null);
        if (account == null || !account.isActive()) {
            TenantContext.clear();
            return this.unauthorized(response, "帳號不存在或已停用");
        }
        // contracts changeOwnPassword「改密成功後舊 token 立即失效」：權杖簽發時帶的指紋對不上
        // 帳號現在的密碼雜湊，代表密碼已經換過，這張是改密前簽出去的，不放行。
        // 沒帶指紋的權杖（例如測試直接用 JwtService 簽的）不受這條約束。
        String fingerprint = claims.credentialFingerprint();
        if (fingerprint != null && !fingerprint.equals(CredentialFingerprint.of(account.getPasswordHash()))) {
            TenantContext.clear();
            return this.unauthorized(response, "密碼已變更，請重新登入");
        }
        // dsl.md 帳號與登入「必須先設定新密碼才能繼續」：臨時密碼登入成功後憑證仍會發出，
        // 但完成改密前這組憑證呼叫其他需要身分的 API 一律被拒；改密端點本身是完成改密的
        // 唯一合法路徑，必須放行。
        if (account.isMustChangePassword() && !changeOwnPassword) {
            TenantContext.clear();
            return this.forbidden(response, "必須先設定新密碼才能繼續使用");
        }
        CurrentActor.set(CurrentActor.staff(claims.tenantId(), account.getId(), account.getRole(), account.getDept()));
        this.establishAuthentication("staff:" + String.valueOf(account.getId()), account.getRole());
        return true;
    }

    private boolean authenticateGuestBySessionHeader(String token, HttpServletResponse response) throws IOException {
        GuestSession session = this.guestSessionRepository.findBySessionToken(token).orElse(null);
        if (session == null || session.getRevokedAt() != null || session.getExpiresAt() == null || !session.getExpiresAt().isAfter(OffsetDateTime.now(this.clock))) {
            return this.unauthorized(response, "session 無效、過期或已撤銷");
        }
        TenantContext.set(session.getTenantId());
        CurrentActor.set(CurrentActor.guest(session.getTenantId(), session.getId(), session.getRoomId(), session.getReservationId()));
        this.establishAuthentication("guest:" + String.valueOf(session.getId()), "guest");
        return true;
    }

    private boolean authenticateGuest(String token, Long tenantId, HttpServletResponse response) throws IOException {
        TenantContext.set(tenantId);
        GuestSession session = this.guestSessionRepository.findBySessionToken(token).orElse(null);
        if (session == null || !tenantId.equals(session.getTenantId()) || session.getRevokedAt() != null || session.getExpiresAt() == null || !session.getExpiresAt().isAfter(OffsetDateTime.now(this.clock))) {
            TenantContext.clear();
            return this.unauthorized(response, "session 無效、過期或已撤銷");
        }
        CurrentActor.set(CurrentActor.guest(tenantId, session.getId(), session.getRoomId(), session.getReservationId()));
        this.establishAuthentication("guest:" + String.valueOf(session.getId()), "guest");
        return true;
    }

    private void establishAuthentication(String principal, String role) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated((Object)principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication((Authentication)authentication);
        SecurityContextHolder.setContext((SecurityContext)context);
    }

    private boolean unauthorized(HttpServletResponse response, String message) throws IOException {
        this.apiErrorResponses.write(response, 401, "unauthorized", message);
        return false;
    }

    private boolean forbidden(HttpServletResponse response, String message) throws IOException {
        this.apiErrorResponses.write(response, 403, "must_change_password", message);
        return false;
    }
}
