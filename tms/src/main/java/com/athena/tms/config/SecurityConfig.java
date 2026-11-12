package com.athena.tms.config;

import com.athena.tms.common.error.ApiErrorResponses;
import com.athena.tms.config.AuthProperties;
import com.athena.tms.dao.GuestSessionRepository;
import com.athena.tms.dao.IntegrationApiKeyRepository;
import com.athena.tms.dao.StaffAccountRepository;
import com.athena.tms.filter.BearerTokenAuthFilter;
import com.athena.tms.filter.IntegrationApiKeyAuthFilter;
import com.athena.tms.service.JwtService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(value={AuthProperties.class})
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtService jwtService, StaffAccountRepository staffAccountRepository, GuestSessionRepository guestSessionRepository, IntegrationApiKeyRepository integrationApiKeyRepository, ApiErrorResponses apiErrorResponses, Clock clock) throws Exception {
        BearerTokenAuthFilter bearerTokenAuthFilter = new BearerTokenAuthFilter(jwtService, staffAccountRepository, guestSessionRepository, apiErrorResponses, clock);
        IntegrationApiKeyAuthFilter integrationApiKeyAuthFilter = new IntegrationApiKeyAuthFilter(integrationApiKeyRepository, apiErrorResponses);
        http.csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(matrix -> matrix
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/guest/**").hasRole("guest")
                        .requestMatchers(HttpMethod.GET, "/staff/**")
                        .hasAnyRole("housekeeping", "engineering", "manager", "frontdesk")
                        .requestMatchers("/staff/**").hasAnyRole("housekeeping", "engineering", "manager")
                        .requestMatchers("/desk/**").hasAnyRole("frontdesk", "manager")
                        .requestMatchers("/admin/**").hasRole("manager")
                        .requestMatchers(HttpMethod.GET, "/owner/**").hasRole("owner")
                        .requestMatchers("/integration/**").hasRole("INTEGRATION")
                        .requestMatchers("/api/v1/tenant-configs/**").hasRole("manager")
                        .requestMatchers(HttpMethod.POST, "/api/v1/guest-sessions/scan").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/staff-accounts/login").permitAll()
                        // 撥動後端時鐘的測試端點：只有啟動參數 tms.test-clock.enabled=true 時才有對應的
                        // controller。這裡一律放行，沒開時請求才會落到「找不到路徑」回 404，
                        // 而不是被下面的角色規則擋成 401，測試工具才分得出「後端沒開旗標」。
                        .requestMatchers("/api/v1/test/clock").permitAll()
                        // 本人設定新密碼（contracts changeOwnPassword）：每個角色都要能換自己的密碼，
                        // 所以不套下面「/api/v1 寫入限現場執行部門」的通則，否則櫃檯與老闆一律 403。
                        .requestMatchers(HttpMethod.POST, "/api/v1/staff-accounts/change-password")
                        .hasAnyRole("housekeeping", "engineering", "manager", "frontdesk", "owner")
                        // 推播地址登記與解除（contracts registerPushToken／revokePushToken）：每個會登入 App 的
                        // 角色都要能收推播，所以不套下面「/api/v1 寫入限現場執行部門」的通則。
                        .requestMatchers("/api/v1/me/push-token")
                        .hasAnyRole("housekeeping", "engineering", "manager", "frontdesk", "owner")
                        .requestMatchers("/api/v1/guest-requests/**").hasRole("guest")
                        // 櫃檯備註由櫃檯建立（contracts createDeskNote），不套下面
                        // 「/api/v1 寫入限現場執行部門」的通則，否則櫃檯一律 403。
                        .requestMatchers(HttpMethod.POST, "/api/v1/desk-notes").hasAnyRole("frontdesk", "manager")
                        // 櫃檯交辦工務部門開單（contracts createRepairTicket 的第三條路徑）：櫃檯要能
                        // 先建報修照片、上傳內容，再開工務單，同樣不套下面的通則。
                        .requestMatchers(HttpMethod.POST, "/api/v1/repair-photos", "/api/v1/repair-tickets")
                        .hasAnyRole("housekeeping", "engineering", "manager", "frontdesk")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/repair-photos/*/content")
                        .hasAnyRole("housekeeping", "engineering", "manager", "frontdesk")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**")
                        .hasAnyRole("housekeeping", "engineering", "manager", "frontdesk")
                        .requestMatchers("/api/v1/**").hasAnyRole("housekeeping", "engineering", "manager")
                        .anyRequest().denyAll())
                .exceptionHandling(handling -> handling.authenticationEntryPoint((AuthenticationEntryPoint)apiErrorResponses).accessDeniedHandler((AccessDeniedHandler)apiErrorResponses)).addFilterBefore((Filter)bearerTokenAuthFilter, AuthorizationFilter.class).addFilterBefore((Filter)integrationApiKeyAuthFilter, AuthorizationFilter.class);
        return (SecurityFilterChain)http.build();
    }
}
