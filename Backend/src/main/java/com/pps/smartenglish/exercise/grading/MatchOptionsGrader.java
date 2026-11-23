package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** key/answer {matches:{itemId:optionId}}; mỗi mục chọn một lựa chọn trong danh sách chung (được chọn lặp); đủ mọi mục, không thừa. payload.scoring: PARTIAL (mặc định, số mục đúng ÷ tổng mục) hoặc ALL. */
@Component
class MatchOptionsGrader implements QuestionGrader {

    @Override
    public QuestionType type() {
        return QuestionType.MATCH_OPTIONS;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        JsonNode expected = input.answerKey().path("matches");
        JsonNode given = answer.path("matches");
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

    @Override
    public BigDecimal credit(GradingInput input, JsonNode answer) {
        return PartialScoring.credit(input.payload(), input.answerKey().path("matches"), answer.path("matches"),
                JsonValues::scalar, isCorrect(input, answer));
    }
}
