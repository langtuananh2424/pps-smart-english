package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import org.springframework.stereotype.Component;

/** D-159: answer {choiceIds:[id]}; chấm cả câu (chọn đủ và đúng mọi đáp án), cùng phép so với CHOICE_MULTI. */
@Component
class VideoConnectiveGrader implements QuestionGrader {

    private final MultiChoiceGrader sets = new MultiChoiceGrader();

    @Override
    public QuestionType type() {
        return QuestionType.VIDEO_CONNECTIVE;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        return sets.isCorrect(input, answer);
    }

    @Override
    public JsonNode correctAnswer(GradingInput input) {
        return sets.correctAnswer(input);
    }
}
