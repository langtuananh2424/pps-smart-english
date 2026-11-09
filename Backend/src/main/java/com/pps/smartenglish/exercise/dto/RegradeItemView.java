package com.pps.smartenglish.exercise.dto;

/** Một dòng lịch sử chấm lại (chỉ ADMIN): điểm cũ/mới của câu trả lời và của cả lượt làm bài. */
public record RegradeItemView(Long id, Long attemptId, Long userId, Long answerId, Boolean oldIsCorrect, Boolean newIsCorrect,
                              Integer oldScore, Integer newScore, String status) {}
