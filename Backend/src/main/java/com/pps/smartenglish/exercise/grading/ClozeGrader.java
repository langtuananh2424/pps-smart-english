package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import org.springframework.stereotype.Component;

/** key {blanks:{id:[đáp án chấp nhận]}}, answer {blanks:{id:giá trị}}; bỏ khoảng trắng đầu cuối, không phân biệt hoa thường. */
@Component
class ClozeGrader implements QuestionGrader {

    @Override
    public QuestionType type() {
        return QuestionType.CONNECTIVE_CLOZE;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        JsonNode expected = input.answerKey().path("blanks");
        JsonNode given = answer.path("blanks");
        if (!expected.isObject() || !given.isObject() || expected.isEmpty()) {
            return false;
        }
        for (var e : expected.properties()) {
            String value = JsonValues.normalized(given.get(e.getKey()));
            if (value == null || !accepts(e.getValue(), value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean accepts(JsonNode accepted, String value) {
        for (JsonNode a : accepted) {
            if (value.equals(JsonValues.normalized(a))) {
                return true;
            }
        }
        return false;
    }
}
