package com.pps.smartenglish.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.domain.AiTask;
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
    /** Học viên chỉ thấy lời chung; lời báo gốc ở cột error dành cho admin. */
    static final String FAILED_MESSAGE = "Cam chưa chấm được bài này, thử lại sau nhé.";


    private final AiJobRepository jobs;
    private final AiQuotaService quota;
    private final AiGuard guard;
    private final AttemptResultGate gate;
    private final Clock clock;

    AiJobService(AiJobRepository jobs, AiQuotaService quota, AiGuard guard, AttemptResultGate gate, Clock clock) {
        this.gate = gate;
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
     * Job chấm bài: rate limit, kiểm hàng đợi, rồi trừ lượt theo trọng số của task (định kỳ của courseId, thiếu thì pool)
     * và tạo job trong CÙNG transaction của nơi gọi. Hết lượt thì không để lại job nào và trả empty.
     * Gọi trong transaction để rollback kéo theo cả lượt đã trừ.
     */
    @Transactional
    public Optional<Long> enqueueCharged(long userId, AiJobKind kind, JsonNode input, Long sessionId, Long attemptId,
                                         AiTask task, Long courseId) {
        guard.rateLimit(userId);
        guard.requireCapacity();
        AiJob job = jobs.save(new AiJob(userId, kind, input, sessionId, attemptId, true, Instant.now(clock)));
        var charge = quota.charge(userId, job.getId(), courseId, task);
        if (charge.isEmpty()) {
            jobs.deleteById(job.getId());
            return Optional.empty();
        }
        var c = charge.get();
        jobs.recordCharge(job.getId(), c.courseId(), c.periodStart(), c.periodic(), c.pool());
        return Optional.of(job.getId());
    }

    /** Job chấm bài không trừ lượt (bài giáo viên giao, D-130): vẫn giới hạn tần suất và kiểm hàng đợi như job thường. */
    @Transactional
    public long enqueueUncharged(long userId, AiJobKind kind, JsonNode input, Long attemptId) {
        return enqueueUncharged(userId, kind, input, null, attemptId);
    }

    /** Như trên, kèm phiên viết (WRITING_GRADE của bài giao cần sessionId để ghi kết quả). */
    @Transactional
    public long enqueueUncharged(long userId, AiJobKind kind, JsonNode input, Long sessionId, Long attemptId) {
        guard.rateLimit(userId);
        guard.requireCapacity();
        return jobs.save(new AiJob(userId, kind, input, sessionId, attemptId, false, Instant.now(clock))).getId();
    }

    /**
     * Job chấm lại bài cũ (Admin xác nhận): không rate limit, không requireCapacity, không trừ hay hoàn lượt của học viên.
     * Giới hạn số job đang chạy do nơi gọi giữ (app.regrade.max-ai-pending). adminId làm chủ job nên học viên không thấy.
     */
    @Transactional
    public long enqueueRegrade(long adminId, JsonNode input, long attemptId, long regradeItemId) {
        return jobs.save(AiJob.regrade(adminId, input, attemptId, regradeItemId, Instant.now(clock))).getId();
    }

    @Transactional(readOnly = true)
    public long regradeInFlight() {
        return jobs.countRegradeInFlight();
    }

    /** Trạng thái các job (id -> status); id không còn thì vắng mặt. */
    @Transactional(readOnly = true)
    public java.util.Map<Long, String> statuses(java.util.Collection<Long> ids) {
        var out = new java.util.HashMap<Long, String>();
        if (!ids.isEmpty()) {
            jobs.findStatuses(ids).forEach(r -> out.put(((Number) r[0]).longValue(), (String) r[1]));
        }
        return out;
    }

    /** Job chấm lần đầu của lượt làm bài (không tính job chấm lại). */
    @Transactional(readOnly = true)
    public List<Long> pendingIds(Long attemptId) {
        return jobs.findPendingIdsByAttempt(attemptId);
    }

    @Transactional(readOnly = true)
    public JobView view(long userId, long jobId) {
        return toView(jobs.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found", "Không tìm thấy job")));
    }

    /** D-147: job của phiên gắn lượt chưa chốt điểm thì không trả kết quả (điểm, bản sửa), cả đường poll lẫn SSE. */
    public JobView toView(AiJob j) {
        boolean failed = AiJob.FAILED.equals(j.getStatus());
        boolean show = AiJob.DONE.equals(j.getStatus()) && !gate.sealed(j);
        return new JobView(j.getId(), j.getKind().name(), j.getStatus(),
                show ? j.getOutput() : null, failed ? FAILED_MESSAGE : null, failed && j.isCharged());
    }
}
