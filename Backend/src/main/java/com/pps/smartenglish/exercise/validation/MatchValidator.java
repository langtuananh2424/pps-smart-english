package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Ghép hai vế: payload {left[], right[]} (phần tử {id,text}); key {pairs:{leftId:rightId}}, mọi vế trái có cặp. */
@Component
class MatchValidator implements QuestionValidator {

    @Override
    public QuestionType type() {
        return QuestionType.CONNECTIVE_MATCH;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("left", "right"));
        Set<String> left = JsonRules.idTextList(payload, "left", "payload", 1);
        Set<String> right = JsonRules.idTextList(payload, "right", "payload", 1);

        JsonNode key = JsonRules.object(d.answerKey(), "answerKey", Set.of("pairs"));
        JsonNode pairs = key.get("pairs");
        if (pairs == null || !pairs.isObject()) {
            throw JsonRules.bad("answerKey.pairs", "Bắt buộc, là object {leftId: rightId}");
        }
        if (!JsonRules.fieldNames(pairs).equals(left)) {
            throw JsonRules.bad("answerKey.pairs", "Phải có đúng một cặp cho mỗi vế trái và không có id lạ");
        }
        pairs.fields().forEachRemaining(e -> {
            if (!e.getValue().isTextual() || !right.contains(e.getValue().asText())) {
                throw JsonRules.bad("answerKey.pairs." + e.getKey(), "Phải là id có trong payload.right");
            }
        });
    }
}
