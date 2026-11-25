package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.ai.service.AttemptLinks;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** D-145: kiểm lượt trước khi gắn phiên Viết/Nói vào. */
@Component
class AttemptLinkChecker implements AttemptLinks {

    private final StudentAttemptRepository attempts;
    private final QuestionRepository questions;

    AttemptLinkChecker(StudentAttemptRepository attempts, QuestionRepository questions) {
        this.attempts = attempts;
        this.questions = questions;
    }

    @Override
    public void requireOwned(long userId, long attemptId) {
        if (attempts.findByIdAndUserId(attemptId, userId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài");
        }
    }

    @Override
    public boolean isOpen(Long attemptId) {
        return attemptId != null && attempts.findById(attemptId).map(a -> a.getSubmittedAt() == null).orElse(false);
    }

    @Override
    public void requireAttemptOpen(Long attemptId) {
        if (attemptId != null && !isOpen(attemptId)) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_already_submitted", "Lượt làm bài này đã được nộp");
        }
    }

    @Override
    public Long openAttemptForQuestion(long userId, long questionId) {
        return questions.findById(questionId)
                .flatMap(q -> attempts.findFirstByUserIdAndBankIdAndSubmittedAtIsNullOrderByIdDesc(userId, q.getBankId()))
                .map(a -> a.getId()).orElse(null);
    }

    @Override
    public void requireNotInOpenAttempt(long userId, long questionId) {
        Long open = openAttemptForQuestion(userId, questionId);
        if (open != null) {
            Long bankId = questions.findById(questionId).map(q -> q.getBankId()).orElse(null);
            throw new ApiException(HttpStatus.CONFLICT, "question_in_open_attempt",
                    "Câu này đang nằm trong bài bạn chưa nộp, hãy làm tiếp trong bài đó", java.util.Map.of("attemptId", open, "bankId", bankId), null);
        }
    }

    @Override
    public boolean isAssigned(Long attemptId) {
        return attemptId != null && attempts.findById(attemptId).map(a -> a.getAssignmentId() != null).orElse(false);
    }

    @Override
    public void lockAttempt(Long attemptId) {
        if (attemptId != null) {
            attempts.lockRow(attemptId);
        }
    }

    @Override
    public void requireOpenLocked(Long attemptId) {
        if (attemptId != null && attempts.lockIfOpen(attemptId).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_already_submitted", "Lượt làm bài này đã được nộp");
        }
    }

    @Override
    public void requireOpen(long userId, long attemptId, long questionId) {
        var a = attempts.findByIdAndUserId(attemptId, userId) // lượt người khác coi như không có
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài"));
        if (a.getSubmittedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_already_submitted", "Lượt làm bài này đã được nộp");
        }
        if (!questions.findById(questionId).map(q -> q.getBankId().equals(a.getBankId())).orElse(false)) {
            throw ApiException.validation("attemptId", "Câu hỏi không thuộc bộ của lượt làm bài này");
        }
    }
}
