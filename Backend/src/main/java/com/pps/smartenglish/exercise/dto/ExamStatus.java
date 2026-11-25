package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;

/** Trạng thái bài thi khóa của một học viên. reason null khi unlocked. */
public record ExamStatus(Long bankId, int progressPercent, int requiredPercent, boolean unlocked, String reason,
                         long attemptsUsed, int attemptsAllowed, Long inProgressAttemptId, LastResult lastResult) {

    /** score vắng khi còn câu AI chấm dở hoặc lượt Nghe ép (D-147, D-142). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LastResult(BigDecimal score, Integer maxScore, Instant submittedAt) {}
}
