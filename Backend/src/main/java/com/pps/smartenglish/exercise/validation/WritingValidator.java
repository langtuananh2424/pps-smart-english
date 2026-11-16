package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Viết phát triển ý (chấm AI qua phiên viết): payload {task, requiredWords > 0, ieltsTask? TASK1|TASK2 (thiếu = TASK2, quyết định trọng số lượt D-32)}; không có answerKey. */
@Component
class WritingValidator implements QuestionValidator {

    @Override
    public QuestionType type() {
        return QuestionType.WRITING;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("task", "requiredWords", "ieltsTask"));
        JsonRules.text(payload, "task", "payload");
        if (JsonRules.integer(payload, "requiredWords", "payload") <= 0) {
            throw JsonRules.bad("payload.requiredWords", "Phải lớn hơn 0");
        }
        if (payload.has("ieltsTask") && !Set.of("TASK1", "TASK2").contains(payload.get("ieltsTask").asText())) {
            throw JsonRules.bad("payload.ieltsTask", "Chỉ nhận TASK1 hoặc TASK2");
        }
        JsonRules.requireNone(d.answerKey(), "answerKey", "Câu viết không có answerKey");
    }
}
