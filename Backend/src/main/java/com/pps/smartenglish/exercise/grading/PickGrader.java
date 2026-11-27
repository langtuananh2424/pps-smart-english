package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;

/** Cơ sở cho các dạng chọn một giá trị (id lựa chọn trong layout hoặc giá trị cố định): key và answer cùng một trường, so không phân biệt hoa thường. */
abstract class PickGrader implements QuestionGrader {

    private final QuestionType type;
    private final String field;

    PickGrader(QuestionType type, String field) {
        this.type = type;
        this.field = field;
    }

    @Override
    public QuestionType type() {
        return type;
    }

    @Override
    public boolean isCorrect(GradingInput input, JsonNode answer) {
        String want = JsonValues.normalized(input.answerKey().path(field));
        return want != null && want.equals(JsonValues.normalized(answer.path(field)));
    }
}
