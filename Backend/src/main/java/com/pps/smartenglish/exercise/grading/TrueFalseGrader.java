package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import org.springframework.stereotype.Component;

/** key {answer}, answer {value}; giá trị phải thuộc payload.options và trùng đáp án (không phân biệt hoa thường). */
@Component
class TrueFalseGrader implements QuestionGrader {

    @Override
    public QuestionType type() {
        return QuestionType.TRUE_FALSE;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        String want = JsonValues.normalized(input.answerKey().path("answer"));
        String given = JsonValues.normalized(answer.path("value"));
        return want != null && want.equals(given) && inOptions(input.payload(), given);
    }

    private static boolean inOptions(JsonNode payload, String value) {
        if (payload == null) {
            return false;
        }
        for (JsonNode o : payload.path("options")) {
            if (value.equals(JsonValues.normalized(o))) {
                return true;
            }
        }
        return false;
    }
}
