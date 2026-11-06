package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.commerce.dto.ProductView;
import java.time.Instant;
import java.util.List;

/**
 * Bài chưa nộp: chỉ có id, bankId, startedAt (các trường null bị bỏ), tuyệt đối không có đáp án.
 * Điểm của câu viết lại tính vào maxScore ngay từ lúc nộp; score tăng dần khi AI chấm xong (pendingJobs).
 * aiQuotaRequired + products: có câu cần AI nhưng hết lượt, câu đó giữ isCorrect null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptResponse(Long id, Long bankId, Instant startedAt, Instant submittedAt, Integer score, Integer maxScore,
                              List<QuestionResult> results, List<Long> pendingJobs, Boolean aiQuotaRequired,
                              List<ProductView> products) {

    /** isCorrect null = chưa chấm xong (dạng AI): khi đó không có correctAnswer. */
    public record QuestionResult(Long questionId, Boolean isCorrect,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode correctAnswer,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String explanation,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String feedback) {}
}
