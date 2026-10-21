package com.athena.tms.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import com.athena.tms.config.AuthProperties;

/**
 * 員工 JWT 簽發與驗證（HS256、TTL 12 小時）。claim 名照 spec 字面用
 * snake_case（tenant_id/staff_id/role/dept），不隨 Java 慣例改 camelCase——
 * 它是跨前後端的 wire format，spec 是唯一基準。
 *
 * 這裡刻意不驗 staff_accounts.active：JWT 是無狀態憑證，簽出去就收不回來，
 * 停用帳號若只靠 TTL 到期，會有長達 12 小時的時間裡 token 已經該失效卻仍被
 * 當作有效；因此 active 每個請求都要驗，那一刀由 BearerTokenAuthFilter 打
 * DB 完成，本類維持純密碼學判定。
 */
@Service
public class JwtService {

    private final MACSigner signer;
    private final MACVerifier verifier;
    private final java.time.Duration ttl;

    public JwtService(AuthProperties properties) {
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        try {
            this.signer = new MACSigner(secret);
            this.verifier = new MACVerifier(secret);
        } catch (JOSEException e) {
            throw new IllegalStateException("tms.auth.jwt.secret 長度不足（HS256 需至少 32 bytes）", e);
        }
        this.ttl = properties.ttl();
    }

    /** expiresAt 與 exp claim 同值，另給一份是讓前端不必解 JWT 才知道何時要重新登入。 */
    public record IssuedToken(String token, OffsetDateTime expiresAt) {
    }

    public record StaffClaims(long tenantId, UUID staffId, String role, String dept) {
    }

    public IssuedToken issue(long tenantId, UUID staffId, String role, String dept) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(staffId.toString())
                .claim("tenant_id", tenantId)
                .claim("staff_id", staffId.toString())
                .claim("role", role)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt));
        // dept 為 null（主管/老闆）時整個 claim 不放，避免序列化出 "dept":null 這種兩義值
        if (dept != null) {
            claims.claim("dept", dept);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 簽章失敗", e);
        }
        return new IssuedToken(jwt.serialize(), expiresAt.atOffset(ZoneOffset.UTC));
    }

    /**
     * 任何不合法（格式、簽章、過期、缺必要 claim）一律回 null 而不是細分例外——
     * 401 的訊息不該幫攻擊者區分「token 偽造」與「token 過期」。
     */
    public StaffClaims verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(verifier)) {
                return null;
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.toInstant().isAfter(Instant.now())) {
                return null;
            }
            Long tenantId = claims.getLongClaim("tenant_id");
            String staffId = claims.getStringClaim("staff_id");
            if (tenantId == null || staffId == null) {
                return null;
            }
            return new StaffClaims(tenantId, UUID.fromString(staffId),
                    claims.getStringClaim("role"), claims.getStringClaim("dept"));
        } catch (ParseException | JOSEException | IllegalArgumentException e) {
            return null;
        }
    }
}
