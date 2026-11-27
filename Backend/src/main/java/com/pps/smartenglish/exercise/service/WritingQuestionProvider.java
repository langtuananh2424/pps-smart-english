package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.ai.config.VideoReflexProperties;
import com.pps.smartenglish.ai.domain.AiTask;
import com.pps.smartenglish.ai.service.WritingQuestionSource;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cấp câu hỏi viết cho module ai, kiểm quyền vào bộ câu hỏi bằng BankAccessPolicy như mọi bài tập khác. */
@Component
class WritingQuestionProvider implements WritingQuestionSource {

    private final QuestionRepository questions;
    private final QuestionBankRepository banks;
    private final BankGate gate;
    private final VideoReflexProperties reflex;

    WritingQuestionProvider(QuestionRepository questions, QuestionBankRepository banks, BankGate gate, VideoReflexProperties reflex) {
        this.reflex = reflex;
        this.questions = questions;
        this.banks = banks;
        this.gate = gate;
    }

    @Override
    @Transactional(readOnly = true)
    public WritingQuestion find(long questionId, long userId, boolean inAssignment, boolean inAttempt) {
        var q = questions.findById(questionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "question_not_found", "Không tìm thấy câu hỏi"));
        requireWritingLike(q);
        gate.requireFor(q.getBankId(), userId, inAssignment, inAttempt);
        return toQuestion(q);
    }

    @Override
    @Transactional(readOnly = true)
    public WritingQuestion findForNewSession(long questionId, long userId, boolean inAssignment, boolean inAttempt) {
        var q = find(questionId, userId, inAssignment, inAttempt);
        requireNotArchived(questions.findById(questionId).orElseThrow());
        return q;
    }

    static void requireNotArchived(com.pps.smartenglish.exercise.domain.Question q) {
        if (q.getArchivedAt() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "question_archived", "Câu hỏi đã bị ẩn, không còn dùng được");
        }
    }

    /** D-141: giáo viên đọc bài nộp; người gọi đã kiểm bài nộp thuộc đúng bài giao của lớp mình, không xét quyền khóa của học viên. */
    @Override
    @Transactional(readOnly = true)
    public WritingQuestion findForStaff(long questionId) {
        var q = questions.findById(questionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "question_not_found", "Không tìm thấy câu hỏi"));
        requireWritingLike(q);
        return toQuestion(q);
    }

    /** WRITING, hoặc VIDEO_SHORT (bước Viết trước bước Nói, D-158). */
    private static void requireWritingLike(com.pps.smartenglish.exercise.domain.Question q) {
        if (q.getType() != QuestionType.WRITING && q.getType() != QuestionType.VIDEO_SHORT) {
            throw ApiException.validation("questionId", "Không phải câu hỏi viết");
        }
    }

    private WritingQuestion toQuestion(com.pps.smartenglish.exercise.domain.Question q) {
        var payload = q.getPayload();
        boolean video = q.getType() == QuestionType.VIDEO_SHORT;
        String taskField = video ? "prompt" : "task"; // câu video: đề viết chính là câu hỏi ở mốc
        String task = payload != null && payload.path(taskField).isTextual() && !payload.path(taskField).asText().isBlank()
                ? payload.path(taskField).asText() : q.getPrompt();
        Integer required = payload != null && payload.path("requiredWords").isIntegralNumber()
                ? payload.path("requiredWords").asInt() : null;
        var aiTask = payload != null && "TASK1".equals(payload.path("ieltsTask").asText()) ? AiTask.WRITING_TASK1 : AiTask.WRITING_TASK2;
        return new WritingQuestion(q.getId(), q.getPrompt(), task, required,
                banks.findCourseTitle(q.getBankId()).orElse(""), payload, banks.findCourseId(q.getBankId()).orElse(null), aiTask,
                video ? reflex.maxWriteSubmits() : 0);
    }
}
