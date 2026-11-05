package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import org.springframework.stereotype.Component;

/** key/answer {order:[blockId]}; đúng thứ tự từng khối. */
@Component
class BuildGrader implements QuestionGrader {

    @Override
    public QuestionType type() {
        return QuestionType.GRAMMAR_BUILD;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        JsonNode expected = input.answerKey().path("order");
        JsonNode given = answer.path("order");
        if (!expected.isArray() || !given.isArray() || expected.isEmpty() || expected.size() != given.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            String want = JsonValues.scalar(expected.get(i));
            if (want == null || !want.equals(JsonValues.scalar(given.get(i)))) {
                return false;
            }
        }
        return true;
    }
}
