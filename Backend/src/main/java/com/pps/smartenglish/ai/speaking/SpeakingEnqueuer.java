package com.pps.smartenglish.ai.speaking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.domain.AiTask;
import com.pps.smartenglish.ai.service.AiJobService;
import com.pps.smartenglish.ai.service.AiQuotaService;
import com.pps.smartenglish.ai.speaking.SpeakingQuestionSource.SpeakingQuestion;
import com.pps.smartenglish.shared.error.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Một transaction: trừ lượt (trừ bài giao) + tạo job SPEAKING_GRADE + đánh dấu GRADING. Tách bean riêng để I/O kho nằm ngoài transaction. */
@Component
class SpeakingEnqueuer {

    private final AiJobService aiJobs;
    private final AiQuotaService quota;
    private final SpeakingSubmissionRepository submissions;
    private final ObjectMapper mapper;
    private final Clock clock;

    SpeakingEnqueuer(AiJobService aiJobs, AiQuotaService quota, SpeakingSubmissionRepository submissions, ObjectMapper mapper, Clock clock) {
        this.aiJobs = aiJobs;
        this.quota = quota;
        this.submissions = submissions;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Hết lượt thì 402 và không để lại gì; hai request xác nhận đồng thời thì bên thua rollback cả lượt đã trừ. */
    @Transactional
    public long enqueue(SpeakingSubmission s, SpeakingQuestion q, long sizeBytes, double durationSec) {
        ObjectNode in = mapper.createObjectNode();
        in.put("submissionId", s.getId());
        in.put("objectKey", s.getObjectKey());
        in.put("sizeBytes", sizeBytes);
        in.put("durationSec", durationSec);
        in.put("part", q.part());
        in.put("prompt", q.prompt());
        in.put("courseTitle", q.courseTitle());
        var cue = in.putArray("cueCard");
        q.cueCard().forEach(cue::add);
        // D-130: bài giáo viên giao không trừ lượt, vẫn qua giới hạn tần suất và hàng đợi
        long jobId = s.getAssignmentId() != null
                ? aiJobs.enqueueUncharged(s.getUserId(), AiJobKind.SPEAKING_GRADE, in, null)
                : aiJobs.enqueueCharged(s.getUserId(), AiJobKind.SPEAKING_GRADE, in, null, null, AiTask.SPEAKING_PART, q.courseId())
                        .orElseThrow(quota::exhausted);
        BigDecimal duration = BigDecimal.valueOf(durationSec).setScale(2, RoundingMode.HALF_UP);
        int claimed;
        try {
            claimed = submissions.claimUpload(s.getId(), jobId, sizeBytes, duration, Instant.now(clock));
        } catch (DataIntegrityViolationException e) { // uq_speaking_assignment_submitted (PostgreSQL): hai lượt cùng nộp một bài giao
            throw new ApiException(HttpStatus.CONFLICT, "assignment_submitted", "Bạn đã nộp bài được giao này");
        }
        if (claimed == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "submission_busy", "Lượt nói này đang được xác nhận");
        }
        return jobId;
    }
}
