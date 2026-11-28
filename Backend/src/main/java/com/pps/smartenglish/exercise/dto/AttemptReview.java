package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;

/**
 * D-174: giáo viên xem lượt nộp bài giao bộ câu. attempt như học viên nhận (AttemptResponse); bank là đề đúng dạng GET /api/banks/{id}
 * (không gợi ý); answers là câu trả lời đã lưu. Lượt Nghe ép (COUNT_ONLY) không có isCorrect/credit từng câu (D-142).
 */
public record AttemptReview(AttemptResponse attempt, BankResponse bank, List<AnswerView> answers) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnswerView(Long questionId, JsonNode answer, Boolean isCorrect, BigDecimal credit) {}
}
