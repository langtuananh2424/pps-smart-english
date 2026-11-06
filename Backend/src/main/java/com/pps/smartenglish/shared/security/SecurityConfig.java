package com.pps.smartenglish.shared.security;

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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
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
    SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder decoder, SecurityErrorHandlers errors) throws Exception {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);

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
                        .requestMatchers(HttpMethod.GET, "/api/products").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/activation-links/*").permitAll() // xem trước gói trước khi đăng nhập
                        // luật cụ thể phải đứng trước /api/admin/**: soạn nội dung mở cho EDITOR, phần còn lại chỉ ADMIN
                        .requestMatchers("/api/admin/cms/**").hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // webhook xác thực bằng chữ ký của cổng thanh toán, không bằng token
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook/*").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }
}
