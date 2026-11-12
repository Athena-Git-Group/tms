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
                        .requestMatchers("/api/v1/test/clock").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/staff-accounts/change-password")
                        .hasAnyRole("housekeeping", "engineering", "manager", "frontdesk", "owner")
                        .requestMatchers("/api/v1/me/push-token")
                        .hasAnyRole("housekeeping", "engineering", "manager", "frontdesk", "owner")
                        .requestMatchers("/api/v1/guest-requests/**").hasRole("guest")
                        .requestMatchers(HttpMethod.POST, "/api/v1/desk-notes").hasAnyRole("frontdesk", "manager")
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
