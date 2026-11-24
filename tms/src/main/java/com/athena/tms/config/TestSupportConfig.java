package com.athena.tms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 只給驗收測試與手動測試環境用的後門：可撥的業務時鐘，以及 {@code /api/v1/test/**} 底下的端點。
 * 只有啟動時帶 {@code test-support} profile 才會註冊；正式環境不帶，這些端點與放行規則都不存在。
 * 端點不驗身分，所以絕不能讓這個 profile 出現在對外的部署。
 */
@Configuration(proxyBeanMethods = false)
@Profile(TestSupportConfig.PROFILE)
public class TestSupportConfig {

    public static final String PROFILE = "test-support";

    @Bean
    public AdjustableClock adjustableClock() {
        return new AdjustableClock();
    }

    @Bean
    @Order(-20)
    SecurityFilterChain testSupportSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/test/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
