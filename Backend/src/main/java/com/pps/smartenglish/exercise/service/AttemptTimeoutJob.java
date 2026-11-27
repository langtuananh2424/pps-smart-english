package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.config.LearningStatsProperties;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Quá hạn giờ cộng ân hạn thì máy chủ tự nộp bằng đáp án đã lưu (FR-EX-06). Tắt bằng app.exercise.timeout-jobs-enabled=false.
 */
@Component
public class AttemptTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(AttemptTimeoutJob.class);

    private final StudentAttemptRepository attempts;
    private final SubmissionService submissions;
    private final Clock clock;
    private final LearningStatsProperties props;
    private final boolean enabled;

    AttemptTimeoutJob(StudentAttemptRepository attempts, SubmissionService submissions, Clock clock, LearningStatsProperties props,
                      @Value("${app.exercise.timeout-jobs-enabled:true}") boolean enabled) {
        this.attempts = attempts;
        this.submissions = submissions;
        this.clock = clock;
        this.props = props;
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
        // Chờ qua ân hạn để lần nộp của học viên (kèm đáp án) tới trước, xem SubmissionService.
        Instant now = Instant.now(clock).minusSeconds(props.submitGraceSec());
        int submitted = 0;
        for (var a : attempts.findRunningTimed()) {
            if (!a.expired(now)) {
                continue;
            }
            try {
                submissions.submit(a.getUserId(), a.getId(), null);
                submitted++;
            } catch (RuntimeException e) {
                log.warn("Tự nộp lượt {} lỗi: {}", a.getId(), e.toString());
            }
        }
        return submitted;
    }
}
