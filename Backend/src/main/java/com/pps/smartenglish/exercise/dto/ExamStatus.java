package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pps.smartenglish.exercise.dto.ExamBandDtos.AttemptBands;
import java.math.BigDecimal;
import java.time.Instant;

/** Trạng thái bài thi khóa của một học viên. reason null khi unlocked. */
public record ExamStatus(Long bankId, int progressPercent, int requiredPercent, boolean unlocked, String reason,
                         long attemptsUsed, int attemptsAllowed, Long inProgressAttemptId, LastResult lastResult) {

    /** attemptId: lượt đã nộp để mở "Xem kết quả". score và bands vắng khi còn câu AI chấm dở hoặc lượt Nghe ép (D-147, D-142);
     * bands null khi đề chưa có bảng quy đổi (FR-EXAM-03). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LastResult(Long attemptId, BigDecimal score, Integer maxScore, Instant submittedAt, AttemptBands bands) {}
}
