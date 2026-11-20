package com.pps.smartenglish.shared.security;

import com.pps.smartenglish.auth.service.PermissionGuard;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_POST = {
        "/api/auth/register", "/api/auth/login", "/api/auth/oauth/google", "/api/auth/refresh",
        "/api/auth/logout", "/api/auth/verify-email", "/api/auth/forgot-password", "/api/auth/reset-password"
    };

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // NFR-SEC-01
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder decoder, SecurityErrorHandlers errors, PermissionGuard guard)
            throws Exception {
        // CSRF tắt: xác thực bằng Bearer; cookie refresh là SameSite=Strict và chỉ gửi tới /api/auth.
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        // SSE: request gốc đã xác thực; lần dispatch ASYNC chỉ để ghi nốt phản hồi, không có JWT để kiểm lại
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/catalog/courses", "/api/catalog/courses/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/lessons/*").permitAll() // FR-LMS-02: bài học thử cho khách
                        // FR-TEST-01: khách làm kiểm tra đầu vào; quyền theo token của lượt được kiểm trong PlacementAccess
                        .requestMatchers(HttpMethod.POST, "/api/placement/attempts", "/api/placement/attempts/*/submit").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/placement/attempts/*/draft").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/catalog/course-suggestions").permitAll() // khách làm Q&A
                        .requestMatchers(HttpMethod.GET, "/api/products").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/activation-links/*").permitAll() // xem trước gói trước khi đăng nhập
                        // FR-ADM-04: quyền theo role và override, đọc từ DB mỗi lần gọi. Route không khai báo thì từ chối.
                        .requestMatchers("/api/admin/cms/**").access(guard.require("cms.content"))
                        .requestMatchers("/api/admin/reports/**").access(guard.require("report.view"))
                        .requestMatchers("/api/admin/activation-links/**").access(guard.require("activation.manage"))
                        .requestMatchers("/api/admin/regrades/**", "/api/admin/questions/**").access(guard.require("regrade.manage"))
                        .requestMatchers("/api/admin/audit-logs/**").access(guard.require("audit.view"))
                        .requestMatchers("/api/admin/orders/**").access(guard.require("order.view"))
                        .requestMatchers(HttpMethod.PUT, "/api/admin/support/settings").access(guard.require("support.configure"))
                        .requestMatchers("/api/admin/support/tickets", "/api/admin/support/tickets/**", "/api/admin/support/settings")
                        .access(guard.require("ticket.manage"))
                        .requestMatchers(HttpMethod.GET, "/api/admin/support/**").access(guard.require("student.view"))
                        .requestMatchers(HttpMethod.POST, "/api/admin/support/**").access(guard.require("student.support"))
                        .requestMatchers("/api/admin/classes", "/api/admin/classes/**").access(guard.require("class.manage"))
                        .requestMatchers("/api/admin/ai-weights", "/api/admin/products/**", "/api/admin/products")
                        .access(guard.require("product.manage"))
                        .requestMatchers("/api/admin/roles/**", "/api/admin/permissions", "/api/admin/users/**")
                        .access(guard.require("rbac.manage"))
                        .requestMatchers("/api/admin/**").denyAll()
                        // webhook xác thực bằng chữ ký của cổng thanh toán, không bằng token
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook/*").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(decoder))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }
}
