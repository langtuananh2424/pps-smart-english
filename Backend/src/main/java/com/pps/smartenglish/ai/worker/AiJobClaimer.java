package com.pps.smartenglish.ai.worker;

import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Lấy job đến hạn bằng SKIP LOCKED rồi đặt RUNNING; nhiều worker/instance không bao giờ lấy trùng một job. */
@Component
public class AiJobClaimer {

    static final Duration LEASE = Duration.ofSeconds(90);

    private final AiJobRepository jobs;
    private final AiJobFinisher finisher;
    private final Clock clock;

    AiJobClaimer(AiJobRepository jobs, AiJobFinisher finisher, Clock clock) {
        this.jobs = jobs;
        this.finisher = finisher;
        this.clock = clock;
    }

    @Transactional
    public List<AiJob> claim(int limit) {
        Instant now = Instant.now(clock);
        List<Long> claimed = new ArrayList<>();
        for (Long id : jobs.lockDue(now, limit)) {
            var job = jobs.findById(id).orElseThrow();
            if (AiJob.RUNNING.equals(job.getStatus()) && job.getAttempts() >= AiJobFinisher.MAX_ATTEMPTS) {
                // lease hết hạn mà đã hết lượt thử: không chạy tiếp, FAILED và hoàn lượt
                finisher.fail(job, false, "Hết số lần thử");
                continue;
            }
            jobs.markRunning(id, now.plus(LEASE), now);
            claimed.add(id);
        }
        return claimed.isEmpty() ? List.of() : jobs.findAllById(claimed);
    }
}
