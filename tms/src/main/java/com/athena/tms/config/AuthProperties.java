package com.athena.tms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 設定，對應 application.properties 的
 * tms.auth.jwt.secret／tms.auth.jwt.ttl（=12h，跨整個班次）。
 * secret 正式環境必須以環境變數 TMS_JWT_SECRET 覆寫。
 */
@ConfigurationProperties(prefix = "tms.auth.jwt")
public record AuthProperties(String secret, Duration ttl) {
}
