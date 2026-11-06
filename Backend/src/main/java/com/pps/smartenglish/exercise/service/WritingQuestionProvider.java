package com.pps.smartenglish.exercise.service;

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
    public WritingQuestion find(long questionId, long userId) {
        var q = questions.findById(questionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "question_not_found", "Không tìm thấy câu hỏi"));
        if (q.getType() != QuestionType.WRITING) {
            throw ApiException.validation("questionId", "Không phải câu hỏi viết");
        }
        gate.require(q.getBankId(), userId);
        var payload = q.getPayload();
        String task = payload != null && payload.path("task").isTextual() && !payload.path("task").asText().isBlank()
                ? payload.path("task").asText() : q.getPrompt();
        Integer required = payload != null && payload.path("requiredWords").isIntegralNumber()
                ? payload.path("requiredWords").asInt() : null;
        return new WritingQuestion(q.getId(), q.getPrompt(), task, required,
                banks.findCourseTitle(q.getBankId()).orElse(""), payload);
    }
}
