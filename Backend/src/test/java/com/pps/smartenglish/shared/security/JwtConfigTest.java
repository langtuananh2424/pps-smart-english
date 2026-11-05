package com.pps.smartenglish.shared.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    @Test
    void rejectsTemplateSecretForCurrentAndPreviousKey() {
        String sample = "change-me-at-least-32-bytes-long-secret";
        assertThatThrownBy(() -> config.jwtEncoder(sample)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.jwtDecoder("a-real-secret-a-real-secret-0123456789", sample))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsShortSecretAndAcceptsGoodOne() {
        assertThatThrownBy(() -> config.jwtEncoder("ngan")).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> config.jwtDecoder("a-real-secret-a-real-secret-0123456789", "")).doesNotThrowAnyException();
    }
}
