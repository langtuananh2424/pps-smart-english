package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.StudentAnswer;
import com.pps.smartenglish.exercise.dto.AttemptReview;
import com.pps.smartenglish.exercise.dto.AttemptReview.AnswerView;
import com.pps.smartenglish.exercise.repository.StudentAnswerRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** D-174: đề và câu trả lời của một lượt đã nộp cho nhân sự lớp. Người gọi đã kiểm quyền lớp và lượt thuộc bài giao; owner là chủ lượt. */
@Service
public class AttemptReviewService {

    private final AttemptService attempts;
    private final BankService banks;
    private final StudentAttemptRepository attemptRepo;
    private final StudentAnswerRepository answers;

    AttemptReviewService(AttemptService attempts, BankService banks, StudentAttemptRepository attemptRepo, StudentAnswerRepository answers) {
        this.attempts = attempts;
        this.banks = banks;
        this.attemptRepo = attemptRepo;
        this.answers = answers;
    }

    @Transactional(readOnly = true)
    public AttemptReview review(long owner, long attemptId) {
        var a = attemptRepo.findByIdAndUserId(attemptId, owner)
                .filter(x -> x.getSubmittedAt() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt đã nộp của bài này"));
        var stored = answers.findByAttemptId(attemptId);
        boolean hide = a.isCountOnly();
        var views = stored.stream().map(s -> new AnswerView(s.getQuestionId(), s.getAnswer(), hide ? null : s.getCorrect(), hide ? null : s.getCredit())).toList();
        return new AttemptReview(attempts.get(owner, attemptId), banks.forReview(a, stored.stream().map(StudentAnswer::getQuestionId).toList()), views);
    }
}
