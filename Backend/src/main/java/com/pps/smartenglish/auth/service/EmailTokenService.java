package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.auth.domain.EmailToken;
import com.pps.smartenglish.auth.domain.EmailToken.Purpose;
import com.pps.smartenglish.auth.repository.EmailTokenRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Token trong link email (xác thực / đặt lại mật khẩu): dùng một lần, có hạn. */
@Service
public class EmailTokenService {

    private final EmailTokenRepository repo;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    EmailTokenService(EmailTokenRepository repo, Clock clock, ApplicationEventPublisher events) {
        this.repo = repo;
        this.clock = clock;
        this.events = events;
    }

    /** Tạo token (DB chỉ giữ hash) rồi phát sự kiện để thư được gửi, sau commit nếu đang trong transaction. */
    public void issueAndSend(long userId, String email, Purpose purpose) {
        String raw = TokenUtil.newToken();
        repo.save(new EmailToken(userId, purpose, TokenUtil.hash(raw), clock.instant().plus(purpose.ttl())));
        events.publishEvent(new EmailTokenIssued(email, purpose, raw));
    }

    /** Đánh dấu đã dùng và trả userId; sai, hết hạn hoặc đã dùng thì 400 invalid_token. */
    public long consume(String raw, Purpose purpose) {
        String hash = TokenUtil.hash(raw);
        if (repo.consume(hash, purpose, clock.instant()) == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_token", "Liên kết không hợp lệ hoặc đã hết hạn");
        }
        return repo.findByTokenHash(hash).orElseThrow().getUserId();
    }
}
