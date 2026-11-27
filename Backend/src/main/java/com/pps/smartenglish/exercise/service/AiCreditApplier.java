package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.ai.service.AttemptCreditSink;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.repository.StudentAnswerRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.event.ExerciseScored;
import java.math.BigDecimal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-145: nhận điểm câu Viết/Nói về sau lúc nộp. Khóa dòng lượt trước, cùng khóa với lúc nộp bài, nên kết quả AI đến
 * đúng lúc đang nộp thì hoặc được nộp đọc thấy, hoặc được ghi vào câu trả lời sau khi nộp xong: không bao giờ mất.
 */
@Component
class AiCreditApplier implements AttemptCreditSink {

    private final StudentAttemptRepository attempts;
    private final StudentAnswerRepository answers;
    private final ApplicationEventPublisher events;
    private final QuestionRepository questions;

    AiCreditApplier(StudentAttemptRepository attempts, StudentAnswerRepository answers, ApplicationEventPublisher events, QuestionRepository questions) {
        this.questions = questions;
        this.attempts = attempts;
        this.answers = answers;
        this.events = events;
    }

    @Override
    public boolean countsWriting(long questionId) {
        return questions.findById(questionId).map(q -> q.getType() != QuestionType.VIDEO_SHORT).orElse(true);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyCredit(long attemptId, long questionId, BigDecimal credit) {
        apply(attemptId, () -> answers.applyAiCredit(attemptId, questionId, credit));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyFailure(long attemptId, long questionId, String reason, boolean refunded) {
        apply(attemptId, () -> answers.applyAiFailure(attemptId, questionId, reason, refunded));
    }

    private void apply(long attemptId, java.util.function.IntSupplier write) {
        var a = attempts.lockById(attemptId).orElse(null);
        if (a == null || write.getAsInt() == 0) {
            return; // lượt chưa nộp (nộp sẽ tự đọc điểm) hoặc câu đã có điểm (kết quả đến trùng)
        }
        attempts.recomputeScore(attemptId);
        // báo điểm mới để retention xét thưởng điểm cao, không tính thêm ngày học
        attempts.findById(attemptId).ifPresent(r -> events.publishEvent(new ExerciseScored(r.getUserId(), r.getBankId(),
                attemptId, r.getScore(), r.getMaxScore(), false, r.isExtra())));
    }
}
