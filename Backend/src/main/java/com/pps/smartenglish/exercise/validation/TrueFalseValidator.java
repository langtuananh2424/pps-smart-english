package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Đúng/Sai: payload {options} là đúng một trong ba tập TF, TFNG, YNNG (theo thứ tự hiển thị); key {answer} thuộc options. */
@Component
class TrueFalseValidator implements QuestionValidator {

    private static final Set<List<String>> SETS = Set.of(
            List.of("TRUE", "FALSE"),
            List.of("TRUE", "FALSE", "NOT_GIVEN"),
            List.of("YES", "NO", "NOT_GIVEN"));

    @Override
    public QuestionType type() {
        return QuestionType.TRUE_FALSE;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("options"));
        List<String> options = new ArrayList<>(JsonRules.texts(JsonRules.boundedArray(payload, "options", "payload", 2), "payload.options", false));
        if (!SETS.contains(options)) {
            throw JsonRules.bad("payload.options", "Phải là [TRUE, FALSE], [TRUE, FALSE, NOT_GIVEN] hoặc [YES, NO, NOT_GIVEN]");
        }
        JsonNode key = JsonRules.object(d.answerKey(), "answerKey", Set.of("answer"));
        if (!options.contains(JsonRules.text(key, "answer", "answerKey"))) {
            throw JsonRules.bad("answerKey.answer", "Phải là một giá trị trong payload.options");
        }
    }
}
