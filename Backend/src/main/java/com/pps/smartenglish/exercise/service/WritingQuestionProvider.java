package com.pps.smartenglish.exercise.service;

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

    WritingQuestionProvider(QuestionRepository questions, QuestionBankRepository banks, BankGate gate) {
        this.questions = questions;
        this.banks = banks;
        this.gate = gate;
    }

    @Override
    @Transactional(readOnly = true)
    public WritingQuestion find(long questionId, long userId, boolean inAssignment) {
        var q = questions.findById(questionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "question_not_found", "Không tìm thấy câu hỏi"));
        if (q.getType() != QuestionType.WRITING) {
            throw ApiException.validation("questionId", "Không phải câu hỏi viết");
        }
        gate.requirePracticable(q.getBankId(), userId, inAssignment);
        return toQuestion(q);
    }

    /** D-141: giáo viên đọc bài nộp; người gọi đã kiểm bài nộp thuộc đúng bài giao của lớp mình, không xét quyền khóa của học viên. */
    @Override
    @Transactional(readOnly = true)
    public WritingQuestion findForStaff(long questionId) {
        var q = questions.findById(questionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "question_not_found", "Không tìm thấy câu hỏi"));
        if (q.getType() != QuestionType.WRITING) {
            throw ApiException.validation("questionId", "Không phải câu hỏi viết");
        }
        return toQuestion(q);
    }

    private WritingQuestion toQuestion(com.pps.smartenglish.exercise.domain.Question q) {
        var payload = q.getPayload();
        String task = payload != null && payload.path("task").isTextual() && !payload.path("task").asText().isBlank()
                ? payload.path("task").asText() : q.getPrompt();
        Integer required = payload != null && payload.path("requiredWords").isIntegralNumber()
                ? payload.path("requiredWords").asInt() : null;
        var aiTask = payload != null && "TASK1".equals(payload.path("ieltsTask").asText()) ? AiTask.WRITING_TASK1 : AiTask.WRITING_TASK2;
        return new WritingQuestion(q.getId(), q.getPrompt(), task, required,
                banks.findCourseTitle(q.getBankId()).orElse(""), payload, banks.findCourseId(q.getBankId()).orElse(null), aiTask);
    }
}
