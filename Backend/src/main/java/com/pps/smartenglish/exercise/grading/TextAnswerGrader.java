package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;

/** Cơ sở cho các dạng điền chữ: key {accepted:[...]}, answer {text}; luật so khớp ở TypedText. */
abstract class TextAnswerGrader implements QuestionGrader {

    private final QuestionType type;
    private final boolean dropPunct;

    TextAnswerGrader(QuestionType type, boolean dropPunct) {
        this.type = type;
        this.dropPunct = dropPunct;
    }

    @Override
    public QuestionType type() {
        return type;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        return TypedText.matches(input.payload(), input.answerKey(), answer.path("text"), dropPunct);
    }
}
