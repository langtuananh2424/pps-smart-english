package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** key/answer {spans:[{start,end,element}]}, so như tập hợp (không phụ thuộc thứ tự). */
@Component
class IdentifyGrader implements QuestionGrader {

    @Override
    public QuestionType type() {
        return QuestionType.GRAMMAR_IDENTIFY;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        Set<String> expected = spans(input.answerKey().path("spans"));
        return expected != null && expected.equals(spans(answer.path("spans")));
    }

    /** null khi sai định dạng, nên không bao giờ bằng tập đáp án hợp lệ. */
    private static Set<String> spans(JsonNode node) {
        if (!node.isArray()) {
            return null;
        }
        Set<String> out = new HashSet<>();
        for (JsonNode s : node) {
            Long start = JsonValues.integer(s.path("start"));
            Long end = JsonValues.integer(s.path("end"));
            String element = s.path("element").isTextual() ? s.path("element").asText() : null;
            if (start == null || end == null || element == null) {
                return null;
            }
            out.add(start + ":" + end + ":" + element);
        }
        return out;
    }
}
