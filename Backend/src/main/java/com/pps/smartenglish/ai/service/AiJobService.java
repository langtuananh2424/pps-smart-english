package com.pps.smartenglish.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.dto.JobView;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tạo và đọc job AI; cổng duy nhất để module khác (exercise) đưa việc vào hàng đợi. */
@Service
public class AiJobService {

    private final AiJobRepository jobs;
    private final AiQuotaService quota;
    private final AiGuard guard;
    private final Clock clock;

    AiJobService(AiJobRepository jobs, AiQuotaService quota, AiGuard guard, Clock clock) {
        this.jobs = jobs;
        this.quota = quota;
        this.guard = guard;
        this.clock = clock;
    }

    /** Job không trừ lượt (câu hỏi đào sâu, bản nháp). Nơi gọi tự gọi guard.requireCapacity() trước. */
    @Transactional
    public AiJob enqueue(long userId, AiJobKind kind, JsonNode input, Long sessionId) {
        return jobs.save(new AiJob(userId, kind, input, sessionId, null, false, Instant.now(clock)));
    }

    /**
     * Job chấm bài: rate limit, kiểm hàng đợi, rồi trừ 1 lượt và tạo job trong CÙNG transaction của nơi gọi.
     * Hết lượt thì không để lại job nào và trả empty. Gọi trong transaction để rollback kéo theo cả lượt đã trừ.
     */
    @Transactional
    public Optional<Long> enqueueCharged(long userId, AiJobKind kind, JsonNode input, Long sessionId, Long attemptId) {
        guard.rateLimit(userId);
        guard.requireCapacity();
        AiJob job = jobs.save(new AiJob(userId, kind, input, sessionId, attemptId, true, Instant.now(clock)));
        if (!quota.tryDebit(userId, job.getId())) {
            jobs.deleteById(job.getId());
            return Optional.empty();
        }
        return Optional.of(job.getId());
    }

    @Transactional(readOnly = true)
    public List<Long> pendingIds(Long attemptId) {
        return jobs.findPendingIdsByAttempt(attemptId);
    }

    @Transactional(readOnly = true)
    public JobView view(long userId, long jobId) {
        return toView(jobs.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found", "Không tìm thấy job")));
    }

    public static JobView toView(AiJob j) {
        boolean failed = AiJob.FAILED.equals(j.getStatus());
        return new JobView(j.getId(), j.getKind().name(), j.getStatus(),
                AiJob.DONE.equals(j.getStatus()) ? j.getOutput() : null, failed ? j.getError() : null, failed && j.isCharged());
    }
}
