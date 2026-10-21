package com.athena.tms.filter;

import com.athena.tms.common.codes.ActorRole;
import com.athena.tms.entity.GuestSession;
import com.athena.tms.entity.StaffAccount;
import com.athena.tms.dao.GuestSessionRepository;
import com.athena.tms.dao.StaffAccountRepository;
import com.athena.tms.service.IdempotencyTenantResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.ApiErrorResponses;
import com.athena.tms.dto.GuestSessionTokens;
import com.athena.tms.service.JwtService;

/**
 * 統一的 Bearer 認證入口：員工 JWT 與住客 session token 走同一個 header，
 * 靠格式分流（JWT 兩個點、住客 token「租戶.隨機」一個點）。不按路徑分流是
 * 刻意的——拿員工 JWT 打 /guest 應該是「身分有效但矩陣不放行」的 403，
 * 按路徑解讀會把它誤報成 401，排錯時方向就反了。
 *
 * 認證通過才建 TenantContext／CurrentActor／SecurityContext；授權（角色×前綴
 * 矩陣）交給 SecurityConfig 的 authorizeHttpRequests，這裡不重複判。
 * finally 一定清 ThreadLocal：容器執行緒會復用，殘留的租戶脈絡=跨租戶污染。
 */
public class BearerTokenAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final StaffAccountRepository staffAccountRepository;
    private final GuestSessionRepository guestSessionRepository;
    private final ApiErrorResponses apiErrorResponses;
    private final Clock clock;

    public BearerTokenAuthFilter(JwtService jwtService,
                                 StaffAccountRepository staffAccountRepository,
                                 GuestSessionRepository guestSessionRepository,
                                 ApiErrorResponses apiErrorResponses,
                                 Clock clock) {
        this.jwtService = jwtService;
        this.staffAccountRepository = staffAccountRepository;
        this.guestSessionRepository = guestSessionRepository;
        this.apiErrorResponses = apiErrorResponses;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                String token = header.substring(BEARER_PREFIX.length()).trim();
                Long guestTenantId = GuestSessionTokens.parseTenantId(token);
                boolean authenticated = (guestTenantId != null)
                        ? authenticateGuest(token, guestTenantId, response)
                        : authenticateStaff(token, response);
                if (!authenticated) {
                    return; // 401 已寫出，不再進授權層
                }
                // 冪等接頭：IdempotencyFilter 排在 security chain 之後，
                // 靠這個 attribute 取得「已驗證身分」的租戶——這是它唯一可信的
                // 租戶來源（header 版退路已移除，見 RequestAttributeTenantResolver）
                request.setAttribute(IdempotencyTenantResolver.TENANT_ATTRIBUTE, TenantContext.require());
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            CurrentActor.clear();
        }
    }

    /**
     * 簽章與效期由 JwtService 判，active 每請求打 DB——
     * role/dept 也以 DB 當下值為準而非 claim，改職/停用立即生效、不等 TTL。
     */
    private boolean authenticateStaff(String token, HttpServletResponse response) throws IOException {
        JwtService.StaffClaims claims = jwtService.verify(token);
        if (claims == null) {
            return unauthorized(response, "憑證無效或已過期");
        }
        // 先立租戶脈絡再查 DB：tms_app 角色受 RLS 管，沒有 app.tenant_id 只會查到零列
        TenantContext.set(claims.tenantId());
        StaffAccount account = staffAccountRepository
                .findByTenantIdAndId(claims.tenantId(), claims.staffId())
                .orElse(null);
        if (account == null || !account.isActive()) {
            TenantContext.clear();
            return unauthorized(response, "帳號不存在或已停用");
        }
        CurrentActor.set(CurrentActor.staff(claims.tenantId(), account.getId(),
                account.getRole(), account.getDept()));
        establishAuthentication("staff:" + account.getId(), account.getRole());
        return true;
    }

    /** 住客工作階段驗證：expires_at > now() 且 revoked_at IS NULL，缺一即 401；驗過綁房＋訂單。 */
    private boolean authenticateGuest(String token, Long tenantId, HttpServletResponse response)
            throws IOException {
        TenantContext.set(tenantId);
        GuestSession session = guestSessionRepository.findBySessionToken(token).orElse(null);
        if (session == null
                || !tenantId.equals(session.getTenantId())
                || session.getRevokedAt() != null
                || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(OffsetDateTime.now(clock))) {
            TenantContext.clear();
            return unauthorized(response, "session 無效、過期或已撤銷");
        }
        CurrentActor.set(CurrentActor.guest(tenantId, session.getId(),
                session.getRoomId(), session.getReservationId()));
        establishAuthentication("guest:" + session.getId(), ActorRole.GUEST);
        return true;
    }

    private void establishAuthentication(String principal, String role) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private boolean unauthorized(HttpServletResponse response, String message) throws IOException {
        apiErrorResponses.write(response, 401, "unauthorized", message);
        return false;
    }
}
