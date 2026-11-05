package com.pps.smartenglish.shared.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Xóa khóa quá 24 giờ. Một câu DELETE nên nhiều instance chạy cùng lúc cũng an toàn. */
@Component
class IdempotencyCleanup {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanup.class);
    private static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyRepository keys;
    private final Clock clock;

    IdempotencyCleanup(IdempotencyRepository keys, Clock clock) {
        this.keys = keys;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT1H")
    void purge() {
        try {
            int n = keys.deleteOlderThan(Instant.now(clock).minus(RETENTION));
            if (n > 0) {
                log.info("Đã xóa {} khóa chống lặp quá hạn", n);
            }
        } catch (RuntimeException e) {
            log.warn("Dọn khóa chống lặp lỗi: {}", e.getMessage());
        }
    }
}
