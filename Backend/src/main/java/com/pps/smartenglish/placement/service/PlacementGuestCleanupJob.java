package com.pps.smartenglish.placement.service;

import com.pps.smartenglish.placement.repository.PlacementAttemptRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D-50: xóa lượt kiểm tra đầu vào của khách bỏ dở (chưa nộp, chưa gắn tài khoản) sau guest-abandoned-ttl.
 * Lượt đã nộp hoặc đã gắn user không bao giờ bị xóa. Tắt chung cờ với PlacementTimeoutJob.
 */
@Component
public class PlacementGuestCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PlacementGuestCleanupJob.class);
    private static final int BATCH = 200;

    private final PlacementAttemptRepository attempts;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration ttl;
    private final boolean enabled;

    PlacementGuestCleanupJob(PlacementAttemptRepository attempts, TransactionTemplate tx, Clock clock,
                             @Value("${app.placement.guest-abandoned-ttl:3d}") Duration ttl,
                             @Value("${app.exercise.timeout-jobs-enabled:true}") boolean enabled) {
        this.attempts = attempts;
        this.tx = tx;
        this.clock = clock;
        this.ttl = ttl;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.placement.cleanup-poll-ms:3600000}")
    void run() {
        if (enabled) {
            tick();
        }
    }

    /** Trả số lượt đã xóa. Mỗi lô một transaction; lỗi một lô dừng lần chạy này, lần sau thử lại. */
    public int tick() {
        Instant cutoff = Instant.now(clock).minus(ttl);
        int deleted = 0;
        try {
            while (true) {
                int n = tx.execute(s -> {
                    var ids = attempts.findGuestAbandonedIds(cutoff, PageRequest.of(0, BATCH));
                    return ids.isEmpty() ? 0 : attempts.deleteGuestAbandoned(ids);
                });
                if (n == 0) {
                    break;
                }
                deleted += n;
            }
        } catch (RuntimeException e) {
            log.warn("Dọn lượt kiểm tra đầu vào của khách lỗi: {}", e.toString());
        }
        if (deleted > 0) {
            log.info("Đã xóa {} lượt kiểm tra đầu vào bỏ dở của khách", deleted);
        }
        return deleted;
    }
}
