package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.commerce.dto.ProductView;
import com.pps.smartenglish.exercise.dto.LearningDtos.Breakdown;
import com.pps.smartenglish.exercise.listening.ListeningDtos.ForcedResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Bài chưa nộp: chỉ có id, bankId, startedAt (các trường null bị bỏ), tuyệt đối không có đáp án.
 * Điểm của câu viết lại tính vào maxScore ngay từ lúc nộp; score tăng dần khi AI chấm xong (pendingJobs).
 * score là số thập phân (tổng credit, D-44); maxScore là số câu (nguyên).
 * aiQuotaRequired + products: có câu cần AI nhưng hết lượt, câu đó giữ isCorrect null.
 * breakdown (FR-EX-08): chỉ có ở GET lượt đã nộp, không có ở phản hồi nộp bài.
 * passed, passPct (D-145): chỉ bài thường đã nộp; passed vắng khi còn câu AI chưa chấm, passPct vẫn trả. Nghe ép, đề đầu vào: không có.
 * Còn câu AI chưa chốt (D-147): chỉ có id, bankId, startedAt, submittedAt, maxScore, pendingJobs (và aiQuotaRequired, products
 * nếu hết lượt, passPct); không có score, results, breakdown, passed. QuestionResult.aiFailure: lý do câu Viết/Nói tính 0.
 * forced (FR-EX-10): thay results và breakdown ở lượt Nghe ép (COUNT_ONLY): chỉ số câu đúng, không có đáp án từng câu.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptResponse(Long id, Long bankId, Instant startedAt, Instant submittedAt, BigDecimal score, Integer maxScore,
                              List<QuestionResult> results, List<Long> pendingJobs, Boolean aiQuotaRequired,
                              List<ProductView> products, Breakdown breakdown, ForcedResult forced,
                              Boolean passed, Integer passPct) {

    /** isCorrect null = chưa chấm xong (dạng AI): khi đó không có correctAnswer. */
    public record QuestionResult(Long questionId, Boolean isCorrect,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode correctAnswer,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String explanation,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String feedback,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String aiFailure,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) Boolean aiRefunded) {}
}
