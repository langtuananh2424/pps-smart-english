package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Gõ chữ: payload tùy chọn {maxWords >= 1}; key {accepted:[đáp án chấp nhận]}, mỗi đáp án không vượt maxWords từ. */
@Component
class FillTextValidator implements QuestionValidator {

    @Override
    public QuestionType type() {
        return QuestionType.FILL_TEXT;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        int maxWords = Integer.MAX_VALUE;
        if (d.payload() != null) {
            JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("maxWords"));
            maxWords = JsonRules.integer(payload, "maxWords", "payload");
            if (maxWords < 1) {
                throw JsonRules.bad("payload.maxWords", "Phải từ 1 trở lên");
            }
        }
        JsonNode key = JsonRules.object(d.answerKey(), "answerKey", Set.of("accepted"));
        for (String a : JsonRules.texts(JsonRules.boundedArray(key, "accepted", "answerKey", 1), "answerKey.accepted", false)) {
            if (a.replaceAll("(?U)^\\s+|\\s+$", "").split("(?U)\\s+").length > maxWords) {
                throw JsonRules.bad("answerKey.accepted", "Đáp án \"" + a + "\" vượt giới hạn " + maxWords + " từ");
            }
        }
    }
}
