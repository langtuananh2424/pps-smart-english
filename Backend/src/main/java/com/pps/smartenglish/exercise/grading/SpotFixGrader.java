package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import org.springframework.stereotype.Component;

/** key {position,corrections:[...]}, answer {position,correction}; bản sửa so như cloze (trim, không phân biệt hoa thường). */
@Component
class SpotFixGrader implements QuestionGrader {

    @Override
    public QuestionType type() {
        return QuestionType.GRAMMAR_SPOT_FIX;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        Long position = JsonValues.integer(input.answerKey().path("position"));
        String correction = JsonValues.normalized(answer.path("correction"));
        if (position == null || correction == null || !position.equals(JsonValues.integer(answer.path("position")))) {
            return false;
        }
        for (JsonNode c : input.answerKey().path("corrections")) {
            if (correction.equals(JsonValues.normalized(c))) {
                return true;
            }
        }
        return false;
    }
}
