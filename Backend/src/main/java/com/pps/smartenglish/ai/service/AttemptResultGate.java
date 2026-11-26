package com.pps.smartenglish.ai.service;

import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.repository.AiJobRepository;
import com.pps.smartenglish.ai.repository.WritingSessionRepository;
import com.pps.smartenglish.ai.speaking.SpeakingSubmissionRepository;
import org.springframework.stereotype.Component;

/**
 * D-147: điểm và bản sửa của phiên Viết/Nói gắn lượt chỉ lộ khi lượt đã nộp và mọi job chấm của lượt đã xong. Trước đó
 * học viên đọc bản sửa rồi mở phiên mới dán vào để nâng điểm.
 */
@Component
public class AttemptResultGate {

    private final AttemptLinks attempts;
    private final AiJobRepository jobs;
    private final WritingSessionRepository writing;
    private final SpeakingSubmissionRepository speaking;

    AttemptResultGate(AttemptLinks attempts, AiJobRepository jobs, WritingSessionRepository writing, SpeakingSubmissionRepository speaking) {
        this.attempts = attempts;
        this.jobs = jobs;
        this.writing = writing;
        this.speaking = speaking;
    }

    /** true = phải giấu điểm. attemptId null (phiên luyện tập) thì không giấu. */
    public boolean sealed(Long attemptId) {
        return attemptId != null && (attempts.isOpen(attemptId) || !jobs.findPendingIdsByAttempt(attemptId).isEmpty());
    }

    /**
     * Phiên của học viên userId trên câu questionId. Gắn lượt thì theo lượt; luyện riêng (không gắn) mà câu đang nằm trong một
     * lượt chưa nộp của chính học viên thì cũng giấu (phiên tạo trước khi mở lượt).
     */
    public boolean sealedSession(long userId, long questionId, Long attemptId) {
        // D-152: câu có thể nằm đồng thời trong lượt Luyện thêm và lượt của bộ; còn lượt nào mở thì kết quả của lượt kia vẫn giấu
        return (attemptId != null && sealed(attemptId)) || attempts.openAttemptForQuestion(userId, questionId) != null;
    }

    /** Job chấm Viết/Nói: theo lượt của job, hoặc theo câu của phiên khi job không gắn lượt. Job chấm lại của Admin không giấu. */
    public boolean sealed(AiJob job) {
        if (job.getAttemptId() != null && sealed(job.getAttemptId())) {
            return true;
        }
        Long question = switch (job.getKind()) {
            case WRITING_GRADE -> job.getSessionId() == null ? null : writing.findById(job.getSessionId()).map(s -> s.getQuestionId()).orElse(null);
            case SPEAKING_GRADE -> speaking.findByJobId(job.getId()).map(s -> s.getQuestionId()).orElse(null);
            default -> null;
        };
        return question != null && attempts.openAttemptForQuestion(job.getUserId(), question) != null;
    }
}
