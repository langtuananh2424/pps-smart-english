package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * answer {choiceIds:[id]}; đáp án đúng là các lựa chọn is_correct. payload.scoring: PARTIAL (mặc định) hoặc ALL.
 * PARTIAL theo cùng công thức D-44: số lựa chọn đúng ÷ max(số đáp án đúng, số lựa chọn đã chọn), nên chọn thừa bị trừ gián tiếp.
 */
@Component
class MultiChoiceGrader implements QuestionGrader {

    @Override
    public QuestionType type() {
        return QuestionType.CHOICE_MULTI;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        Set<Long> given = chosen(answer);
        return given != null && !input.correctChoiceIds().isEmpty() && input.correctChoiceIds().equals(given);
    }

    @Override
    public BigDecimal credit(GradingInput input, JsonNode answer) {
        Set<Long> given = chosen(answer);
        Set<Long> correct = input.correctChoiceIds();
        if (given == null || correct.isEmpty()) {
            return Credit.ZERO;
        }
        if (isAllOrNothing(input.payload())) {
            return Credit.of(correct.equals(given));
        }
        int hits = (int) given.stream().filter(correct::contains).count();
        return Credit.ratio(hits, Math.max(correct.size(), given.size()));
    }

    @Override
    public JsonNode correctAnswer(GradingInput input) {
        var ids = JsonNodeFactory.instance.arrayNode();
        input.correctChoiceIds().stream().sorted().forEach(ids::add);
        return JsonNodeFactory.instance.objectNode().set("choiceIds", ids);
    }

    private static boolean isAllOrNothing(JsonNode payload) {
        return payload != null && "ALL".equals(payload.path("scoring").asText());
    }

    /** Tập id đã chọn (trùng lặp chỉ tính một lần); null khi sai định dạng. */
    private static Set<Long> chosen(JsonNode answer) {
        JsonNode ids = answer.path("choiceIds");
        if (!ids.isArray()) {
            return null;
        }
        Set<Long> out = new HashSet<>();
        for (JsonNode n : ids) {
            Long id = JsonValues.integer(n);
            if (id == null) {
                return null;
            }
            out.add(id);
        }
        return out;
    }
}
