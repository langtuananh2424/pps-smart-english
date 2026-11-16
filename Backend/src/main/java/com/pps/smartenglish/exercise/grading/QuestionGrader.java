package com.pps.smartenglish.exercise.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.math.BigDecimal;

/** Strategy chấm một dạng câu hỏi, thuần trong bộ nhớ (FR-EX-03, < 100 ms). */
public interface QuestionGrader {

    QuestionType type();

    /** answer luôn khác null (bỏ trống là MissingNode); sai định dạng phải trả false chứ không ném lỗi. */
    boolean isCorrect(GradingInput input, JsonNode answer);

    /** Điểm câu 0..1; mặc định đúng/sai. Dạng chấm từng phần ghi đè và phải nhất quán: credit = 1 khi và chỉ khi isCorrect. */
    default BigDecimal credit(GradingInput input, JsonNode answer) {
        return Credit.of(isCorrect(input, answer));
    }

    /** Đáp án đúng để hiển thị sau khi nộp. */
    default JsonNode correctAnswer(GradingInput input) {
        return input.answerKey();
    }
}
