package com.athena.tms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 掃碼入口的 security 例外：`/guest/scan` 與 `/auth/login` 同一種性質——
 * 它就是「取得憑證」的那一步，發生在任何 session 存在之前，不可能先有
 * 住客工作階段才掃碼。SecurityConfig 的角色矩陣把 `/guest/**` 鎖在
 * ROLE_GUEST（fail-closed，正確），所以 scan 需要一個
 * login 式的 permitAll 例外。
 *
 * <p>做法：另立一條只罩 `/guest/scan` 的 filter chain（@Order 在主 chain
 * 之前；主 chain 無 securityMatcher＝兜底，Spring Security 要求它排最後），
 * 不動 SecurityConfig 本體。這與「矩陣唯一落點」的設計意圖有張力，
 * 之後可考慮把這條例外併回 SecurityConfig。
 *
 * <p>permitAll 不等於不設防：掃碼的授權靠 QR token 本身（128-bit 以上隨機、
 * DB 查驗 status）＋IP 失敗計數限流，與 login 靠帳密同構。
 */
@Configuration(proxyBeanMethods = false)
public class GuestScanSecurityConfig {

    @Bean
    @Order(-10)
    SecurityFilterChain guestScanSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/guest/scan")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
