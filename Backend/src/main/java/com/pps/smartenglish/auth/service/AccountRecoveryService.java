package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.auth.domain.EmailToken.Purpose;
import com.pps.smartenglish.auth.domain.User;
import com.pps.smartenglish.auth.repository.UserRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Xác thực email, gửi lại thư xác thực, quên và đặt lại mật khẩu. */
@Service
public class AccountRecoveryService {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final int MAX_PER_HOUR = 3;

    private final UserRepository users;
    private final EmailTokenService emailTokens;
    private final SessionService sessions;
    private final RateLimiter limiter;
    private final PasswordEncoder encoder;
    private final Clock clock;

    AccountRecoveryService(UserRepository users, EmailTokenService emailTokens, SessionService sessions,
                           RateLimiter limiter, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.emailTokens = emailTokens;
        this.sessions = sessions;
        this.limiter = limiter;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional
    public void verifyEmail(String token) {
        long userId = emailTokens.consume(token, Purpose.VERIFY);
        users.markVerified(userId, clock.instant());
    }

    public void resendVerification(long userId) {
        User user = users.findById(userId).orElseThrow(ApiException::unauthenticated);
        if (user.getEmailVerifiedAt() != null) {
            return;
        }
        limiter.requireHit("rl:resend:" + userId, MAX_PER_HOUR, HOUR, "resend:" + userId);
        emailTokens.issueAndSend(userId, user.getEmail(), Purpose.VERIFY);
    }

    /** Luôn trả bình thường: không để phản hồi lộ email có tồn tại hay đã bị giới hạn. */
    public void forgotPassword(String email, String ip) {
        boolean emailOk = limiter.hit("rl:forgot:email:" + email, MAX_PER_HOUR, HOUR, "forgot:email:" + email);
        boolean ipOk = limiter.hit("rl:forgot:ip:" + ip, MAX_PER_HOUR, HOUR, "forgot:ip:" + ip);
        if (!emailOk || !ipOk) {
            return;
        }
        users.findByEmail(email).ifPresent(u -> emailTokens.issueAndSend(u.getId(), u.getEmail(), Purpose.RESET));
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordRules.check(newPassword, "newPassword");
        long userId = emailTokens.consume(token, Purpose.RESET);
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "invalid_token", "Liên kết không hợp lệ hoặc đã hết hạn"));
        user.setPasswordHash(encoder.encode(newPassword));
        if (user.getEmailVerifiedAt() == null) { // người dùng đã nhận được thư nên email là của họ
            user.setEmailVerifiedAt(clock.instant());
        }
        sessions.revokeAll(userId); // flush thay đổi ở trên trước khi chạy UPDATE
    }
}
