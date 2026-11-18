package com.athena.tms.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="tms.auth.jwt")
public record AuthProperties(String secret, Duration ttl) {
}
