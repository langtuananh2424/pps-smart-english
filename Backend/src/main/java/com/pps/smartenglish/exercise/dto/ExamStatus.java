package com.pps.smartenglish.exercise.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Trạng thái bài thi khóa của một học viên. reason null khi unlocked. */
public record ExamStatus(Long bankId, int progressPercent, int requiredPercent, boolean unlocked, String reason,
                         long attemptsUsed, int attemptsAllowed, Long inProgressAttemptId, LastResult lastResult) {

    public record LastResult(BigDecimal score, Integer maxScore, Instant submittedAt) {}
}
