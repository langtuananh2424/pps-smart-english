package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.shared.error.ApiException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
class NimbusGoogleTokenVerifier implements IdentityTokenVerifier {

    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> ISSUERS = List.of("accounts.google.com", "https://accounts.google.com");

    private final NimbusJwtDecoder decoder; // null khi chưa cấu hình client ID Google

    /** clientIds: danh sách phẩy (web, Android, iOS); clientId: biến cũ một giá trị, gộp chung (D-95). */
    NimbusGoogleTokenVerifier(@Value("${app.google.client-ids:}") String clientIds,
                              @Value("${app.google.client-id:}") String clientId) {
        Set<String> audiences = Stream.concat(Arrays.stream(clientIds.split(",")), Stream.of(clientId))
                .map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toSet());
        if (audiences.isEmpty()) {
            this.decoder = null;
            return;
        }
        OAuth2TokenValidator<Jwt> issuer = jwt -> ISSUERS.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Sai issuer", null));
        OAuth2TokenValidator<Jwt> audience = audienceValidator(audiences);
        this.decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
        // createDefault() kiểm exp/nbf
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), issuer, audience));
    }

    static OAuth2TokenValidator<Jwt> audienceValidator(Set<String> allowed) {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(allowed::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Sai audience", null));
    }

    @Override
    public String provider() {
        return "GOOGLE";
    }

    @Override
    public ExternalIdentity verify(String idToken) {
        if (decoder == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "google_login_unavailable",
                    "Đăng nhập Google hiện chưa khả dụng");
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            throw ApiException.unauthenticated();
        }
        String email = jwt.getClaimAsString("email");
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")) || email == null || jwt.getSubject() == null) {
            throw ApiException.unauthenticated();
        }
        return new ExternalIdentity(jwt.getSubject(), email, jwt.getClaimAsString("name"));
    }
}
