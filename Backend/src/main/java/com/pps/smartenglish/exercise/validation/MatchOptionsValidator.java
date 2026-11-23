package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Chọn mục từ danh sách chung: payload {items[], options[], scoring?: PARTIAL|ALL} (phần tử {id,text}); key {matches:{itemId:optionId}}, mọi mục có đáp án. */
@Component
class MatchOptionsValidator implements QuestionValidator {

    @Override
    public QuestionType type() {
        return QuestionType.MATCH_OPTIONS;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("items", "options", "scoring"));
        JsonRules.scoring(payload);
        Set<String> items = JsonRules.idTextList(payload, "items", "payload", 1);
        Set<String> options = JsonRules.idTextList(payload, "options", "payload", 2);

        JsonNode key = JsonRules.object(d.answerKey(), "answerKey", Set.of("matches"));
        JsonNode matches = key.get("matches");
        if (matches == null || !matches.isObject() || !JsonRules.fieldNames(matches).equals(items)) {
            throw JsonRules.bad("answerKey.matches", "Phải có đúng một đáp án cho mỗi mục trong payload.items");
        }
        matches.fields().forEachRemaining(e -> {
            if (!e.getValue().isTextual() || !options.contains(e.getValue().asText())) {
                throw JsonRules.bad("answerKey.matches." + e.getKey(), "Phải là id có trong payload.options");
            }
        });
    }
}
