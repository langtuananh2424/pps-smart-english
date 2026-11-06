package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Xếp khối thành câu: payload {blocks:[{id,text}]} (cho học viên thấy); key {order:[id]} là hoán vị đúng của mọi id. */
@Component
class BuildValidator implements QuestionValidator {

    @Override
    public QuestionType type() {
        return QuestionType.GRAMMAR_BUILD;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("blocks"));
        Set<String> ids = JsonRules.idTextList(payload, "blocks", "payload", 2);
        JsonNode key = JsonRules.object(d.answerKey(), "answerKey", Set.of("order"));
        Set<String> order = JsonRules.texts(JsonRules.array(key, "order", "answerKey", 1), "answerKey.order", true);
        if (!order.equals(ids)) {
            throw JsonRules.bad("answerKey.order", "Phải là hoán vị đầy đủ của các id trong payload.blocks");
        }
    }
}
