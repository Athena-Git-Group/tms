package com.athena.tms.filter;

import com.athena.tms.common.CurrentActor;
import com.athena.tms.common.TenantContext;
import com.athena.tms.common.error.ApiErrorResponses;
import com.athena.tms.dao.IntegrationApiKeyRepository;
import com.athena.tms.entity.IntegrationApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 整合 API（/integration/**）的 X-API-Key 認證入口：驗證 PMS 這類機器對機器
 * 客戶端的長效金鑰，並由金鑰推導租戶脈絡，API 面不接受任何 client 自帶的租戶參數。
 *
 * <p>驗證順序有一處不能顛倒：必須先從金鑰明文解析出租戶編號並設定
 * TenantContext，之後才查金鑰表。金鑰表本身受資料庫列層級安全（RLS）保護，
 * 沒有租戶脈絡時查詢一律回零列，有效金鑰也會被誤判無效。又因為租戶編號
 * 來自 client 自述的明文，查到列之後必須回頭驗證列上的 tenant_id 與自述一致，
 * 否則拿租戶 1 簽發的金鑰、改寫明文的租戶段，就能把查詢脈絡切到別的租戶。
 *
 * <p>任一步失敗都清掉租戶脈絡、回 401 後不再進授權層；所有失敗模式共用同一則
 * 錯誤訊息，避免攻擊者從回應差異推敲「格式對了」「租戶存在」這類資訊。
 *
 * <p>只要請求帶 X-API-Key 就驗證，不限整合路徑：有效整合金鑰打員工端點時
 * 必須先建立 INTEGRATION 身分，授權矩陣才會回「身分有效、矩陣不放行」的 403；
 * 若只在整合路徑出手，這類請求會被當匿名而誤回 401，把排錯方向誤導成
 * 「憑證壞了」。Authorization header 完全不碰，與既有的 Bearer 認證互不干擾，
 * 員工憑證打整合端點同理由 Bearer 認證建身分、矩陣擋 403。
 *
 * <p>finally 一定清 ThreadLocal：容器執行緒會復用，殘留的租戶脈絡等於
 * 跨租戶污染（與 Bearer 認證 filter 同一條鐵則）。
 */
public class IntegrationApiKeyAuthFilter extends OncePerRequestFilter {

    /** 授權矩陣用的角色名；Spring Security 比對時自動加 ROLE_ 前綴。 */
    public static final String ROLE = "INTEGRATION";

    public static final String API_KEY_HEADER = "X-API-Key";

    /** 明文格式：tmsik_<十進位租戶編號>_<base64url 字元集的隨機值>。 */
    private static final Pattern KEY_PATTERN =
            Pattern.compile("^tmsik_(\\d+)_([A-Za-z0-9_-]+)$");

    private static final String INVALID_KEY_MESSAGE = "整合金鑰無效";

    private final IntegrationApiKeyRepository integrationApiKeyRepository;
    private final ApiErrorResponses apiErrorResponses;

    public IntegrationApiKeyAuthFilter(IntegrationApiKeyRepository integrationApiKeyRepository,
                                       ApiErrorResponses apiErrorResponses) {
        this.integrationApiKeyRepository = integrationApiKeyRepository;
        this.apiErrorResponses = apiErrorResponses;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String apiKey = request.getHeader(API_KEY_HEADER);
            boolean shouldAuthenticate = apiKey != null && !apiKey.isBlank();
            if (shouldAuthenticate && !authenticate(apiKey, response)) {
                return; // 401 已寫出，不再進授權層
            }
            // 缺 X-API-Key 的請求照常放行進授權層：匿名會被授權矩陣擋成 401，
            // 帶員工憑證的則由矩陣判 403，這裡不越俎代庖。
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            CurrentActor.clear();
        }
    }

    private boolean authenticate(String apiKey, HttpServletResponse response) throws IOException {
        Matcher matcher = KEY_PATTERN.matcher(apiKey);
        if (!matcher.matches()) {
            return unauthorized(response);
        }
        long tenantId;
        try {
            tenantId = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException overflow) {
            return unauthorized(response);
        }
        TenantContext.set(tenantId);
        IntegrationApiKey key = integrationApiKeyRepository
                .findByKeyHash(sha256Hex(apiKey))
                .orElse(null);
        if (key == null
                || !key.getTenantId().equals(tenantId)
                || key.getRevokedAt() != null) {
            TenantContext.clear();
            return unauthorized(response);
        }
        establishAuthentication("integration:" + key.getId());
        return true;
    }

    private void establishAuthentication(String principal) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    /** 金鑰是 32 bytes 高熵隨機值，沒有暴力破解面，用快速雜湊即可，不需要 BCrypt 這類慢雜湊。 */
    private static String sha256Hex(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // JVM 規範保證 SHA-256 必定存在，到這裡代表執行環境本身壞了
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private boolean unauthorized(HttpServletResponse response) throws IOException {
        apiErrorResponses.write(response, 401, "unauthorized", INVALID_KEY_MESSAGE);
        return false;
    }
}
