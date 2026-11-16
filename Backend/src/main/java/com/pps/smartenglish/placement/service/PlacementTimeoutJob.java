package com.pps.smartenglish.placement.service;

import com.pps.smartenglish.placement.repository.PlacementAttemptRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hết giờ (đã qua ân hạn) thì máy chủ tự nộp bằng nháp (FR-TEST-01). Tắt chung cờ với AttemptTimeoutJob. */
@Component
public class PlacementTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(PlacementTimeoutJob.class);
    private static final int BATCH = 200;

    private final PlacementAttemptRepository attempts;
    private final PlacementSubmitService submits;
    private final Clock clock;
    private final boolean enabled;

    PlacementTimeoutJob(PlacementAttemptRepository attempts, PlacementSubmitService submits, Clock clock,
                        @Value("${app.exercise.timeout-jobs-enabled:true}") boolean enabled) {
        this.attempts = attempts;
        this.submits = submits;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.exercise.timeout-poll-ms:30000}")
    void run() {
        if (enabled) {
            tick();
        }
    }

    /** Trả số lượt đã tự nộp. Lỗi của một lượt không chặn các lượt khác. */
    public int tick() {
        Instant cutoff = Instant.now(clock).minus(PlacementSubmitService.GRACE);
        int submitted = 0;
        for (Long id : attempts.findOverdueIds(cutoff, PageRequest.of(0, BATCH))) {
            try {
                submits.autoSubmit(id);
                submitted++;
            } catch (RuntimeException e) {
                log.warn("Tự nộp lượt kiểm tra đầu vào {} lỗi: {}", id, e.toString());
            }
        }
        return submitted;
    }
}
