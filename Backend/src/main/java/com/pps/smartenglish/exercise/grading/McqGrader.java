package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.pps.smartenglish.exercise.domain.QuestionType;
import org.springframework.stereotype.Component;

/** answer {choiceId}; đúng khi lựa chọn đó có question_choices.is_correct. */
@Component
class McqGrader implements QuestionGrader {

    @Override
    public QuestionType type() {
        return QuestionType.CONNECTIVE_MCQ;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        Long id = JsonValues.integer(answer.path("choiceId"));
        return id != null && input.correctChoiceIds().contains(id);
    }

    @Override
    public JsonNode correctAnswer(GradingInput input) {
        return input.correctChoiceIds().stream().sorted().findFirst()
                .<JsonNode>map(id -> JsonNodeFactory.instance.objectNode().put("choiceId", id)).orElse(null);
    }
}
