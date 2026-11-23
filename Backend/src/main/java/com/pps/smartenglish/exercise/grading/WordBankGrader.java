package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * key/answer {blanks:{id:từ}}; phải điền đủ mọi chỗ trống, đúng từ (trim, không phân biệt hoa thường), không thừa.
 * payload.scoring: PARTIAL (mặc định, số chỗ đúng ÷ tổng chỗ) hoặc ALL. Cờ allowReuse chỉ ràng buộc lúc soạn (key không lặp từ khi false), nên chấm chỉ cần so với key.
 */
@Component
class WordBankGrader implements QuestionGrader {

    @Override
    public QuestionType type() {
        return QuestionType.WORD_BANK;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        JsonNode expected = input.answerKey().path("blanks");
        JsonNode given = answer.path("blanks");
        if (!expected.isObject() || !given.isObject() || expected.isEmpty() || expected.size() != given.size()) {
            return false;
        }
        for (var e : expected.properties()) {
            String want = JsonValues.normalized(e.getValue());
            if (want == null || !want.equals(JsonValues.normalized(given.get(e.getKey())))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public BigDecimal credit(GradingInput input, JsonNode answer) {
        return PartialScoring.credit(input.payload(), input.answerKey().path("blanks"), answer.path("blanks"),
                JsonValues::normalized, isCorrect(input, answer));
    }
}
