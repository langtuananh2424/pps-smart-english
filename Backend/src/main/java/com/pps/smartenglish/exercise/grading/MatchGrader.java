package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import org.springframework.stereotype.Component;

/** key/answer {pairs:{leftId:rightId}}; phải khớp đủ mọi cặp, không thừa. */
@Component
class MatchGrader implements QuestionGrader {

    @Override
    public QuestionType type() {
        return QuestionType.CONNECTIVE_MATCH;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        JsonNode expected = input.answerKey().path("pairs");
        JsonNode given = answer.path("pairs");
        if (!expected.isObject() || !given.isObject() || expected.isEmpty() || expected.size() != given.size()) {
            return false;
        }
        for (var e : expected.properties()) {
            String want = JsonValues.scalar(e.getValue());
            if (want == null || !want.equals(JsonValues.scalar(given.get(e.getKey())))) {
                return false;
            }
        }
        return true;
    }
}
