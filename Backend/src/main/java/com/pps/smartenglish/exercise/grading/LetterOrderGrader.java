package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Sắp xếp chữ cái (Figma 10.11): payload {letters:[{id,ch}]}, key {word}, answer {order:[id]}. Phải dùng đủ mọi chữ đúng một lần;
 * ghép ch theo thứ tự đã chọn rồi so với word, không phân biệt hoa thường.
 */
@Component
class LetterOrderGrader implements QuestionGrader {

    private static final int MAX_LETTERS = 100;

    @Override
    public QuestionType type() {
        return QuestionType.LETTER_ORDER;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        JsonNode letters = input.payload() == null ? null : input.payload().path("letters");
        JsonNode order = answer.path("order");
        String want = JsonValues.normalized(input.answerKey().path("word"));
        if (want == null || letters == null || !letters.isArray() || !order.isArray() || order.size() != letters.size()
                || letters.size() > MAX_LETTERS) {
            return false;
        }
        Map<String, String> chOf = new HashMap<>();
        letters.forEach(l -> chOf.put(JsonValues.scalar(l.path("id")), l.path("ch").asText("")));
        Set<String> used = new HashSet<>();
        StringBuilder built = new StringBuilder();
        for (JsonNode id : order) {
            String key = JsonValues.scalar(id);
            if (key == null || !chOf.containsKey(key) || !used.add(key)) {
                return false;
            }
            built.append(chOf.get(key));
        }
        return built.toString().toLowerCase(Locale.ROOT).equals(want);
    }
}
