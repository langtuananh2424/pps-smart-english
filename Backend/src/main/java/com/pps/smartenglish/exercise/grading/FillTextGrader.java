package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import org.springframework.stereotype.Component;

/**
 * key {accepted:[đáp án]}, answer {text}; so khớp không phân biệt hoa thường, bỏ khoảng trắng thừa.
 * payload.maxWords (tùy chọn): câu trả lời dài hơn là sai dù chứa đáp án ("NO MORE THAN TWO WORDS").
 */
@Component
class FillTextGrader implements QuestionGrader {

    @Override
    public QuestionType type() {
        return QuestionType.FILL_TEXT;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        String text = JsonValues.squashed(answer.path("text"));
        if (text == null || text.isEmpty() || tooLong(input.payload(), text)) {
            return false;
        }
        for (JsonNode a : input.answerKey().path("accepted")) {
            if (text.equals(JsonValues.squashed(a))) {
                return true;
            }
        }
        return false;
    }

    private static boolean tooLong(JsonNode payload, String text) {
        Long max = payload == null ? null : JsonValues.integer(payload.path("maxWords"));
        return max != null && text.split(" ").length > max;
    }
}
