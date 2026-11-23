package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Chọn nhiều đáp án: đáp án ở choices (is_correct), không có answerKey. payload tùy chọn {scoring: PARTIAL|ALL}, thiếu = PARTIAL.
 * Cần ít nhất một lựa chọn đúng và ít nhất một lựa chọn sai.
 */
@Component
class MultiChoiceValidator implements QuestionValidator {

    @Override
    public QuestionType type() {
        return QuestionType.CHOICE_MULTI;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        if (d.payload() != null) {
            JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("scoring"));
            if (!payload.has("scoring") || !Set.of("PARTIAL", "ALL").contains(payload.get("scoring").asText())) {
                throw JsonRules.bad("payload.scoring", "Chỉ nhận PARTIAL hoặc ALL");
            }
        }
        JsonRules.requireNone(d.answerKey(), "answerKey", "Đáp án nằm ở choices, không có answerKey");
        long correct = McqValidator.countCorrect(d.choices());
        if (correct < 1 || correct >= d.choices().size()) {
            throw JsonRules.bad("choices", "Cần ít nhất 1 lựa chọn đúng và 1 lựa chọn sai");
        }
    }
}
