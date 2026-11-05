package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.auth.domain.EmailToken.Purpose;
import com.pps.smartenglish.auth.domain.User;
import com.pps.smartenglish.auth.dto.LoginRequest;
import com.pps.smartenglish.auth.dto.RegisterRequest;
import com.pps.smartenglish.auth.repository.UserRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.ratelimit.RateLimiter;
import java.time.Duration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Đăng ký và đăng nhập bằng email + mật khẩu (FR-AUTH-01, FR-AUTH-02). */
@Service
public class AuthService {

    private static final int MAX_FAILED_LOGINS = 5;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final int LOGIN_PER_IP_PER_MINUTE = 25;
    private static final int REGISTER_PER_IP_PER_HOUR = 15;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AccountFactory accounts;
    private final EmailTokenService emailTokens;
    private final SessionService sessions;
    private final RateLimiter limiter;
    private final TransactionTemplate tx;
    /** So khớp với hash giả khi email không tồn tại để thời gian phản hồi không lộ ra. */
    private final String dummyHash;

    AuthService(UserRepository users, PasswordEncoder encoder, AccountFactory accounts,
                EmailTokenService emailTokens, SessionService sessions, RateLimiter limiter,
                TransactionTemplate tx) {
        this.users = users;
        this.encoder = encoder;
        this.accounts = accounts;
        this.emailTokens = emailTokens;
        this.sessions = sessions;
        this.limiter = limiter;
        this.tx = tx;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    public LoginResult register(RegisterRequest req, String ip, String deviceLabel) {
        limiter.requireHit("rl:register:ip:" + ip, REGISTER_PER_IP_PER_HOUR, Duration.ofHours(1), "register:ip:" + ip);
        PasswordRules.check(req.password(), "password");
        String hash = encoder.encode(req.password());
        User user;
        try {
            user = tx.execute(status -> {
                if (users.existsByEmail(req.email())) {
                    throw emailTaken();
                }
                User u = accounts.createStudent(req.email(), hash, null, req.displayName());
                emailTokens.issueAndSend(u.getId(), u.getEmail(), Purpose.VERIFY); // thư gửi sau commit
                return u;
            });
        } catch (DataIntegrityViolationException e) { // hai request cùng email đua nhau
            throw emailTaken();
        }
        return sessions.open(user, deviceLabel);
    }

    public LoginResult login(LoginRequest req, String ip, String deviceLabel) {
        limiter.requireHit("rl:login:ip:" + ip, LOGIN_PER_IP_PER_MINUTE, Duration.ofMinutes(1), "login:ipcap:" + ip);
        String counter = "rl:login:" + req.identifier() + ":" + ip;
        String fallback = "login:ip:" + ip;
        // Đếm trước BCrypt (mỗi lần thử tính một lần) để request song song không vượt được giới hạn; đăng nhập đúng thì xóa.
        limiter.requireHit(counter, MAX_FAILED_LOGINS, LOGIN_WINDOW, fallback);

        User user = users.findByEmail(req.identifier()).orElse(null);
        boolean hasPassword = user != null && user.getPasswordHash() != null;
        // Mật khẩu quá 72 byte chắc chắn sai (đăng ký đã chặn); so với chuỗi rỗng để khỏi ném lỗi của BCrypt.
        String attempt = PasswordRules.tooLong(req.password()) ? "" : req.password();
        boolean matches = encoder.matches(attempt, hasPassword ? user.getPasswordHash() : dummyHash);
        if (!hasPassword || !matches) {
            throw ApiException.unauthenticated();
        }
        limiter.reset(counter, fallback);
        if (user.getStatus() == User.Status.LOCKED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "account_locked", "Tài khoản đã bị khóa");
        }
        return sessions.open(user, deviceLabel);
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "conflict", "Email đã được sử dụng");
    }
}
