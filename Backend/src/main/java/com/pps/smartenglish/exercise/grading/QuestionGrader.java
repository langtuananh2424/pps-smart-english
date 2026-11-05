package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;

/** Strategy chấm một dạng câu hỏi, thuần trong bộ nhớ (FR-EX-03, < 100 ms). */
public interface QuestionGrader {

    QuestionType type();

    /** answer luôn khác null (bỏ trống là MissingNode); sai định dạng phải trả false chứ không ném lỗi. */
    boolean isCorrect(GradingInput input, JsonNode answer);

    /** Đáp án đúng để hiển thị sau khi nộp. */
    default JsonNode correctAnswer(GradingInput input) {
        return input.answerKey();
    }
}
