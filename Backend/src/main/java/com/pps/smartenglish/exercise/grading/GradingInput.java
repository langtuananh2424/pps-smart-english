package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/**
 * Dữ liệu chấm của một câu: answer_key, id các lựa chọn đúng (trắc nghiệm lấy từ question_choices) và payload
 * (cấu hình chấm của câu như maxWords; null nếu câu không có payload).
 */
public record GradingInput(JsonNode answerKey, Set<Long> correctChoiceIds, JsonNode payload) {

    public GradingInput(JsonNode answerKey, Set<Long> correctChoiceIds) {
        this(answerKey, correctChoiceIds, null);
    }
}
