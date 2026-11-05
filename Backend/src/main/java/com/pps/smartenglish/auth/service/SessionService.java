package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.auth.domain.RefreshToken;
import com.pps.smartenglish.auth.domain.User;
import com.pps.smartenglish.auth.dto.AuthResponse;
import com.pps.smartenglish.auth.dto.SessionResponse;
import com.pps.smartenglish.auth.repository.RefreshTokenRepository;
import com.pps.smartenglish.auth.repository.UserRepository;
import com.pps.smartenglish.profile.service.ProfileService;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Phiên đăng nhập chung cho mọi cách đăng nhập: access token (JWT) + refresh token (xoay vòng). */
@Service
public class SessionService {

    static final long ACCESS_TTL_SECONDS = 900;
    public static final Duration REFRESH_TTL = Duration.ofDays(30);
    /** Ân hạn: hai tab refresh cùng lúc sẽ gửi cùng một token, tab chậm hơn không phải là bị đánh cắp. */
    private static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final JwtEncoder jwt;
    private final ProfileService profiles;
    private final TransactionTemplate tx;
    private final Clock clock;

    SessionService(RefreshTokenRepository tokens, UserRepository users, JwtEncoder jwt, ProfileService profiles,
                   TransactionTemplate tx, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.jwt = jwt;
        this.profiles = profiles;
        this.tx = tx;
        this.clock = clock;
    }

    public LoginResult open(User user, String deviceLabel) {
        String raw = TokenUtil.newToken();
        tokens.save(new RefreshToken(user.getId(), TokenUtil.hash(raw), clock.instant().plus(REFRESH_TTL), deviceLabel));
        return new LoginResult(new AuthResponse(accessToken(user), ACCESS_TTL_SECONDS,
                new AuthResponse.UserSummary(user.getId(), profiles.displayName(user.getId()), user.getRole().name())), raw);
    }

    /**
     * Không đánh dấu @Transactional cả hàm: việc thu hồi hàng loạt khi phát hiện dùng lại token
     * phải commit dù sau đó ném 401.
     */
    public LoginResult refresh(String raw, String deviceLabel) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.unauthenticated();
        }
        Instant now = clock.instant();
        RefreshToken row = tokens.findByTokenHash(TokenUtil.hash(raw)).orElseThrow(ApiException::unauthenticated);
        if (!row.getExpiresAt().isAfter(now)) {
            throw ApiException.unauthenticated();
        }
        LoginResult result = tx.execute(status -> {
            if (tokens.rotate(row.getId(), now) == 0) {
                return null;
            }
            User user = users.findById(row.getUserId()).orElseThrow(ApiException::unauthenticated);
            if (user.getStatus() == User.Status.LOCKED) {
                throw new ApiException(HttpStatus.FORBIDDEN, "account_locked", "Tài khoản đã bị khóa");
            }
            return open(user, deviceLabel);
        });
        if (result == null) {
            onRotationLost(row.getId(), row.getUserId(), now);
            throw ApiException.unauthenticated();
        }
        return result;
    }

    /** Đọc lại row vì bản đọc trước có thể cũ (request song song vừa xoay vòng). */
    private void onRotationLost(long tokenId, long userId, Instant now) {
        Instant replacedAt = tokens.findById(tokenId).map(RefreshToken::getReplacedAt).orElse(null);
        if (replacedAt == null || !now.isAfter(replacedAt.plus(REUSE_GRACE))) {
            return; // hủy chủ động, hoặc vừa xoay vòng trong ân hạn: chỉ từ chối
        }
        tokens.revokeAll(userId, now); // dùng lại token cũ sau ân hạn: nghi bị đánh cắp
    }

    public void logout(String raw) {
        if (raw != null && !raw.isBlank()) {
            tokens.findByTokenHash(TokenUtil.hash(raw)).ifPresent(t -> tokens.revokeIfActive(t.getId(), clock.instant()));
        }
    }

    public List<SessionResponse> list(long userId) {
        return tokens.findActive(userId, clock.instant()).stream()
                .map(t -> new SessionResponse(t.getId(), t.getDeviceLabel(), t.getCreatedAt(), t.getExpiresAt()))
                .toList();
    }

    public void revoke(long userId, long sessionId) {
        if (tokens.revokeOwned(sessionId, userId, clock.instant()) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Không tìm thấy phiên đăng nhập");
        }
    }

    public void revokeAll(long userId) {
        tokens.revokeAll(userId, clock.instant());
    }

    private String accessToken(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ACCESS_TTL_SECONDS))
                .claim("role", user.getRole().name())
                .build();
        return jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
