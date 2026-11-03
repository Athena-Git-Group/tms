/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.nimbusds.jose.JOSEException
 *  com.nimbusds.jose.JWSAlgorithm
 *  com.nimbusds.jose.JWSHeader
 *  com.nimbusds.jose.JWSSigner
 *  com.nimbusds.jose.JWSVerifier
 *  com.nimbusds.jose.crypto.MACSigner
 *  com.nimbusds.jose.crypto.MACVerifier
 *  com.nimbusds.jwt.JWTClaimsSet
 *  com.nimbusds.jwt.JWTClaimsSet$Builder
 *  com.nimbusds.jwt.SignedJWT
 *  org.springframework.stereotype.Service
 */
package com.athena.tms.service;

import com.athena.tms.config.AuthProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final MACSigner signer;
    private final MACVerifier verifier;
    private final Duration ttl;

    public JwtService(AuthProperties properties) {
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        try {
            this.signer = new MACSigner(secret);
            this.verifier = new MACVerifier(secret);
        }
        catch (JOSEException e) {
            throw new IllegalStateException("tms.auth.jwt.secret \u9577\u5ea6\u4e0d\u8db3\uff08HS256 \u9700\u81f3\u5c11 32 bytes\uff09", e);
        }
        this.ttl = properties.ttl();
    }

    public IssuedToken issue(long tenantId, UUID staffId, String role, String dept) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(this.ttl);
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject(staffId.toString()).claim("tenant_id", (Object)tenantId).claim("staff_id", (Object)staffId.toString()).claim("role", (Object)role).issueTime(Date.from(now)).expirationTime(Date.from(expiresAt));
        if (dept != null) {
            claims.claim("dept", (Object)dept);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        try {
            jwt.sign((JWSSigner)this.signer);
        }
        catch (JOSEException e) {
            throw new IllegalStateException("JWT \u7c3d\u7ae0\u5931\u6557", e);
        }
        return new IssuedToken(jwt.serialize(), expiresAt.atOffset(ZoneOffset.UTC));
    }

    public StaffClaims verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse((String)token);
            if (!jwt.verify((JWSVerifier)this.verifier)) {
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
            return new StaffClaims(tenantId, UUID.fromString(staffId), claims.getStringClaim("role"), claims.getStringClaim("dept"));
        }
        catch (JOSEException | IllegalArgumentException | ParseException e) {
            return null;
        }
    }

    public record IssuedToken(String token, OffsetDateTime expiresAt) {
    }

    public record StaffClaims(long tenantId, UUID staffId, String role, String dept) {
    }
}
