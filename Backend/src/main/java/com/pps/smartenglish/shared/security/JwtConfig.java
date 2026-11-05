package com.pps.smartenglish.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** JWT HS256. Khóa cũ (JWT_SECRET_PREVIOUS) chỉ để xác minh khi xoay khóa. */
@Configuration
class JwtConfig {

    private static SecretKey key(String secret, String name) {
        if (secret.contains("change-me")) { // giá trị mẫu trong .env.example: chặn lỡ đem lên production
            throw new IllegalStateException(name + " đang là giá trị mẫu, hãy đặt khóa riêng");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(name + " phải dài tối thiểu 32 byte");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    private static NimbusJwtDecoder decoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${app.jwt.secret}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key(secret, "JWT_SECRET")));
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret,
                          @Value("${app.jwt.secret-previous:}") String previous) {
        NimbusJwtDecoder current = decoder(key(secret, "JWT_SECRET"));
        if (previous.isBlank()) {
            return current;
        }
        NimbusJwtDecoder old = decoder(key(previous, "JWT_SECRET_PREVIOUS"));
        return token -> {
            try {
                return current.decode(token);
            } catch (org.springframework.security.oauth2.jwt.BadJwtException e) {
                return old.decode(token);
            }
        };
    }
}
