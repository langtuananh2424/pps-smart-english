package com.pps.smartenglish.ai.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import com.pps.smartenglish.ai.service.AiAutoRetry;
import com.pps.smartenglish.ai.service.AiQuotaService;
import com.pps.smartenglish.ai.service.AttemptAiScores;
import com.pps.smartenglish.ai.service.AttemptLinks;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chuyển trạng thái job có điều kiện (WHERE status = 'RUNNING' AND attempts = ?). 0 dòng nghĩa là lease đã bị lấy lại
 * hoặc job đã xong bởi lượt khác, nên kết quả muộn bị bỏ qua và không hoàn/ghi hai lần.
 */
@Component
public class AiJobFinisher {

    static final int MAX_ATTEMPTS = 3;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(2);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private final AiJobRepository jobs;
    private final AiQuotaService quota;
    private final AttemptLinks attempts;
    private final AiAutoRetry autoRetry;
    private final AttemptAiScores scores;
    private final Clock clock;

    AiJobFinisher(AiJobRepository jobs, AiQuotaService quota, AttemptLinks attempts, AiAutoRetry autoRetry, AttemptAiScores scores, Clock clock) {
        this.attempts = attempts;
        this.autoRetry = autoRetry;
        this.scores = scores;
        this.jobs = jobs;
        this.quota = quota;
        this.clock = clock;
    }

    /** Ghi output + tác dụng phụ (session, điểm lượt làm bài) cùng một transaction. false = bị bỏ qua. */
    @Transactional
    public boolean complete(AiJob job, JsonNode output, Consumer<AiJob> effects) {
        if (jobs.markDone(job.getId(), job.getAttempts(), output, Instant.now(clock)) == 0) {
            return false;
        }
        effects.accept(job);
        return true;
    }

    /**
     * Lỗi tạm thời còn lượt thử thì về PENDING với run_after tăng dần có ngẫu nhiên; ngược lại FAILED và hoàn lượt
     * trong cùng transaction nếu đã trừ. Trả trạng thái mới, hoặc null nếu bị bỏ qua.
     */
    @Transactional
    public String fail(AiJob job, boolean retryable, String error) {
        Instant now = Instant.now(clock);
        if (retryable && job.getAttempts() < MAX_ATTEMPTS) {
            return jobs.markRetry(job.getId(), job.getAttempts(), now.plus(backoff(job.getAttempts())), error, now) == 1
                    ? AiJob.PENDING : null;
        }
        attempts.lockAttempt(job.getAttemptId()); // lượt trước, quota sau (cùng thứ tự với nộp chấm và nộp lượt)
        if (autoRetry.applies(job)) { // D-147: lượt đã nộp (đọc sau khi khóa), chấm lại đúng một lần trước khi bỏ cuộc
            return jobs.markAutoRetry(job.getId(), job.getAttempts(), error, now) == 1 ? AiJob.PENDING : null;
        }
        if (jobs.markFailed(job.getId(), job.getAttempts(), error, now) == 0) {
            return null;
        }
        if (job.isCharged()) {
            quota.refund(job);
        }
        scores.onGradeFailed(job); // câu Viết/Nói của lượt đã nộp tính 0 kèm lý do, cùng transaction với hoàn lượt
        return AiJob.FAILED;
    }

    private static Duration backoff(int attempts) {
        long millis = Math.min(MAX_BACKOFF.toMillis(), BASE_BACKOFF.toMillis() << (attempts - 1));
        return Duration.ofMillis((long) (millis * (0.5 + ThreadLocalRandom.current().nextDouble())));
    }
}
