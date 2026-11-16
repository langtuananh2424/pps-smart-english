package com.pps.smartenglish.exercise.dto;

import java.math.BigDecimal;

/** Một dòng lịch sử chấm lại (chỉ ADMIN): điểm cũ/mới của câu trả lời và của cả lượt làm bài. */
public record RegradeItemView(Long id, Long attemptId, Long userId, Long answerId, Boolean oldIsCorrect, Boolean newIsCorrect,
                              BigDecimal oldScore, BigDecimal newScore, String status) {}
