package com.pps.smartenglish.shared.event;

/**
 * Phát khi nộp bài tập (initial = true) và lại sau khi AI chấm xong câu viết lại với điểm mới (initial = false).
 * Chỉ lần initial mới tính hoạt động trong ngày: AI chấm qua nửa đêm không được tạo ngày học mới.
 */
public record ExerciseScored(long userId, long bankId, long attemptId, int score, int maxScore, boolean initial) {}
