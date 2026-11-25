package com.pps.smartenglish.exercise.dto;

import com.pps.smartenglish.exercise.dto.ExamBandDtos.AttemptBands;
import java.math.BigDecimal;
import java.time.Instant;

/** Trạng thái bài thi khóa của một học viên. reason null khi unlocked. */
public record ExamStatus(Long bankId, int progressPercent, int requiredPercent, boolean unlocked, String reason,
                         long attemptsUsed, int attemptsAllowed, Long inProgressAttemptId, LastResult lastResult) {

    /** attemptId: lượt đã nộp để mở "Xem kết quả". bands null khi đề chưa có bảng quy đổi hoặc là lượt Nghe ép (FR-EXAM-03). */
    public record LastResult(Long attemptId, BigDecimal score, Integer maxScore, Instant submittedAt, AttemptBands bands) {}
}
