package com.pps.smartenglish.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import com.pps.smartenglish.shared.error.ApiException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D-95: token hợp lệ khi aud khớp một trong các client ID đã cấu hình. */
class GoogleAudienceTest {

    private static Jwt jwtWithAud(String aud) {
        return new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("aud", java.util.List.of(aud)));
    }

    @Test
    void acceptsAnyConfiguredAudienceAndRejectsUnknown() {
        var v = NimbusGoogleTokenVerifier.audienceValidator(Set.of("web-id", "android-id"));
        assertThat(v.validate(jwtWithAud("android-id")).hasErrors()).isFalse();
        assertThat(v.validate(jwtWithAud("web-id")).hasErrors()).isFalse();
        assertThat(v.validate(jwtWithAud("other-id")).hasErrors()).isTrue();
    }

    @Test
    void mergesBothPropertiesAndBlankMeans503() {
        assertThatThrownBy(() -> new NimbusGoogleTokenVerifier(" , ", "").verify("x"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        // có cấu hình (chỉ biến cũ hoặc chỉ biến mới) thì không còn 503: token rác bị từ chối 401
        for (var v : new NimbusGoogleTokenVerifier[] {new NimbusGoogleTokenVerifier("", "old"), new NimbusGoogleTokenVerifier("a, b", "")}) {
            assertThatThrownBy(() -> v.verify("rac")).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }
    }
}
