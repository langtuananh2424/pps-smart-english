package com.pps.smartenglish.ai.service;

import com.pps.smartenglish.ai.config.AiProperties;
import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Hai rào chắn đứng trước mọi việc tốn tiền AI: rate limit theo user (NFR-SEC-03) và giới hạn hàng đợi. */
@Component
public class AiGuard {

    static final int RATE_LIMIT = 5;
    static final Duration RATE_WINDOW = Duration.ofSeconds(60);
    private static final long RETRY_AFTER_SECONDS = 5;

    private final RateLimiter limiter;
    private final AiJobRepository jobs;
    private final AiProperties props;

    AiGuard(RateLimiter limiter, AiJobRepository jobs, AiProperties props) {
        this.limiter = limiter;
        this.jobs = jobs;
        this.props = props;
    }

    /** 5 request/phút/user trên mọi endpoint AI; vượt thì 429. */
    public void rateLimit(long userId) {
        limiter.requireHit(rateKey(userId), RATE_LIMIT, RATE_WINDOW, "ai:" + userId);
    }

    /** Hàng đợi quá dài thì 503 kèm Retry-After, trước khi trừ lượt nên người dùng không mất gì. */
    public void requireCapacity() {
        if (jobs.countByStatusAndRegradeItemIdIsNull(AiJob.PENDING) > props.maxPending()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "overloaded", "Hệ thống chấm bài đang quá tải, vui lòng thử lại sau",
                    Map.of(), RETRY_AFTER_SECONDS);
        }
    }

    /** Dùng trong test để xóa bộ đếm. */
    public void resetRateLimit(long userId) {
        limiter.reset(rateKey(userId), "ai:" + userId);
    }

    private static String rateKey(long userId) {
        return "rl:ai:" + userId;
    }
}
