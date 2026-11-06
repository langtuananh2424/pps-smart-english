package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Viết phát triển ý (chấm AI qua phiên viết): payload {task, requiredWords > 0}; không có answerKey. */
@Component
class WritingValidator implements QuestionValidator {

    @Override
    public QuestionType type() {
        return QuestionType.WRITING;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("task", "requiredWords"));
        JsonRules.text(payload, "task", "payload");
        if (JsonRules.integer(payload, "requiredWords", "payload") <= 0) {
            throw JsonRules.bad("payload.requiredWords", "Phải lớn hơn 0");
        }
        JsonRules.requireNone(d.answerKey(), "answerKey", "Câu viết không có answerKey");
    }
}
