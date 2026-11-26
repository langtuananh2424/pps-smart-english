package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.dto.AttemptProgress;
import com.pps.smartenglish.exercise.dto.SubmitRequest;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạm dừng, tiếp tục và lưu nháp đáp án (FR-EX-06, D-11). Chỉ bộ luyện tập (PRACTICE) được tạm dừng; hết giờ thì lượt
 * do AttemptTimeoutJob hoặc lần nộp nộp bằng đáp án đã lưu.
 */
@Service
public class AttemptTimerService {

    private final StudentAttemptRepository attempts;
    private final QuestionBankRepository banks;
    private final QuestionLoader loader;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final AttemptCheckService retry;

    AttemptTimerService(StudentAttemptRepository attempts, QuestionBankRepository banks, QuestionLoader loader,
                        ObjectMapper mapper, Clock clock, AttemptCheckService retry) {
        this.retry = retry;
        this.attempts = attempts;
        this.banks = banks;
        this.loader = loader;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public AttemptProgress pause(long userId, long attemptId) {
        var a = openAttempt(userId, attemptId);
        if (a.getTimeLimitSec() == null || !QuestionBank.PRACTICE.equals(bankMode(a))) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_not_pausable", "Bài thi này không được tạm dừng");
        }
        Instant now = Instant.now(clock);
        if (a.expired(now)) {
            throw timeUp();
        }
        if (!a.paused()) {
            a.pause(now);
            attempts.save(a);
        }
        return progress(a);
    }

    @Transactional
    public AttemptProgress resume(long userId, long attemptId) {
        var a = openAttempt(userId, attemptId);
        if (a.paused()) {
            a.resume(Instant.now(clock));
            attempts.save(a);
        }
        return progress(a);
    }

    /** Lưu nháp: thay toàn bộ đáp án đã lưu. Câu hỏi phải thuộc bộ; chấm chỉ xảy ra khi nộp. */
    @Transactional
    public AttemptProgress saveDraft(long userId, long attemptId, SubmitRequest req) {
        var a = openAttempt(userId, attemptId);
        if (a.expired(Instant.now(clock))) {
            throw timeUp();
        }
        var questions = loader.load(a.getBankId()).questions();
        SubmissionService.indexAnswers(req.answers(), questions);
        a.saveDraft(mapper.valueToTree(req.answers()));
        attempts.save(a);
        return progress(a);
    }

    @Transactional(readOnly = true)
    public AttemptProgress progress(long userId, long attemptId) {
        return progress(findOwned(userId, attemptId));
    }

    AttemptProgress progress(StudentAttempt a) {
        return new AttemptProgress(a.getId(), a.getTimeLimitSec(), a.remainingSec(Instant.now(clock)), a.paused(),
                a.getDraftAnswers(), retry.stateOf(a));
    }

    private StudentAttempt openAttempt(long userId, long attemptId) {
        var a = findOwned(userId, attemptId);
        if (a.getSubmittedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_already_submitted", "Lượt làm bài này đã được nộp");
        }
        return a;
    }

    private StudentAttempt findOwned(long userId, long attemptId) {
        return attempts.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài"));
    }

    private String bankMode(StudentAttempt a) {
        return banks.findById(a.getBankId()).map(QuestionBank::getMode).orElse(QuestionBank.EXAM);
    }

    private static ApiException timeUp() {
        return new ApiException(HttpStatus.CONFLICT, "attempt_time_up", "Đã hết giờ làm bài");
    }
}
