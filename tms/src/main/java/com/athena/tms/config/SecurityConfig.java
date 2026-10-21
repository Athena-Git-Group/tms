package com.athena.tms.config;

import com.athena.tms.common.codes.ActorRole;
import com.athena.tms.common.codes.StaffRole;
import com.athena.tms.dao.GuestSessionRepository;
import com.athena.tms.dao.StaffAccountRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import com.athena.tms.common.error.ApiErrorResponses;
import com.athena.tms.dao.IntegrationApiKeyRepository;
import com.athena.tms.filter.BearerTokenAuthFilter;
import com.athena.tms.filter.IntegrationApiKeyAuthFilter;
import com.athena.tms.service.JwtService;

import java.time.Clock;

/**
 * 角色×動作矩陣的唯一落點：哪個角色能呼叫哪組 URL 前綴，全在這裡宣告，
 * 其他模組只消費 TenantContext／CurrentActor。矩陣集中在這一處而不是
 * 散在各 controller 的 @PreAuthorize：越權規則是安全邊界，散開後漏一個註解
 * 就是靜默放行，集中宣告讓未列入的路徑一律落到 denyAll（fail-closed）。
 *
 * 「housekeeping/engineering 限本部門動作」不在這層：前綴矩陣管「哪個角色
 * 能進哪扇門」，部門歸屬是資源層判斷（例如認領時驗部門（dept 欄位）相符、不符回 409），
 * 由各資源模組用 CurrentActor.dept() 實作。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        // V2 種子雜湊是 $2y$ 版本前綴，Spring 的 BCryptPasswordEncoder 驗 $2a/$2b/$2y 皆可
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                               JwtService jwtService,
                                               StaffAccountRepository staffAccountRepository,
                                               GuestSessionRepository guestSessionRepository,
                                               IntegrationApiKeyRepository integrationApiKeyRepository,
                                               ApiErrorResponses apiErrorResponses,
                                               Clock clock) throws Exception {
        // 在方法內 new 而不是 @Bean：Boot 會把 Filter bean 再註冊成容器級全域
        // filter，同一認證就會跑兩次（一次在 chain 裡、一次在外）
        BearerTokenAuthFilter bearerTokenAuthFilter = new BearerTokenAuthFilter(
                jwtService, staffAccountRepository, guestSessionRepository, apiErrorResponses, clock);
        IntegrationApiKeyAuthFilter integrationApiKeyAuthFilter = new IntegrationApiKeyAuthFilter(
                integrationApiKeyRepository, apiErrorResponses);

        http
                // 無 cookie session 的純 token API，CSRF 的攻擊面不存在；留著只會逼每個 POST 帶 token
                .csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(matrix -> matrix
                        // Boot 的 /error 走 ERROR dispatch 再過一次 chain，不放行會把 404 改寫成 403
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/guest/**").hasRole(ActorRole.GUEST)
                        // /staff 讀寫分開：frontdesk 管 /desk，對 /staff 只有讀
                        .requestMatchers(HttpMethod.GET, "/staff/**").hasAnyRole(
                                StaffRole.HOUSEKEEPING, StaffRole.ENGINEERING,
                                StaffRole.MANAGER, StaffRole.FRONTDESK)
                        .requestMatchers("/staff/**").hasAnyRole(
                                StaffRole.HOUSEKEEPING, StaffRole.ENGINEERING, StaffRole.MANAGER)
                        .requestMatchers("/desk/**").hasAnyRole(StaffRole.FRONTDESK, StaffRole.MANAGER)
                        .requestMatchers("/admin/**").hasRole(StaffRole.MANAGER)
                        // owner 只讀：非 GET 落到 denyAll → 403（MVP 尚無 /owner 端點，矩陣規則先立）
                        .requestMatchers(HttpMethod.GET, "/owner/**").hasRole(StaffRole.OWNER)
                        // 機器對機器整合（PMS 以 X-API-Key 認證），身分只來自整合金鑰 filter
                        .requestMatchers("/integration/**").hasRole(IntegrationApiKeyAuthFilter.ROLE)
                        // 已列前綴之外一律 403；actuator 也包含在內，要不要放行是部署時的運維決策
                        .anyRequest().denyAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(apiErrorResponses)
                        .accessDeniedHandler(apiErrorResponses))
                .addFilterBefore(bearerTokenAuthFilter, AuthorizationFilter.class)
                // 兩個認證 filter 各看各的 header（Authorization vs X-API-Key），互不攔截
                .addFilterBefore(integrationApiKeyAuthFilter, AuthorizationFilter.class);
        return http.build();
    }
}
