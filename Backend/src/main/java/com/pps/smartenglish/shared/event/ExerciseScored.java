package com.pps.smartenglish.shared.event;

import java.math.BigDecimal;

/**
 * Phát khi nộp bài tập (initial = true) và lại sau khi AI chấm xong câu viết lại với điểm mới (initial = false).
 * extra (D-152): lượt Luyện thêm, XP tính theo lượt thay vì theo bộ.
 * Chỉ lần initial mới tính hoạt động trong ngày: AI chấm qua nửa đêm không được tạo ngày học mới.
 */
public record ExerciseScored(long userId, long bankId, long attemptId, BigDecimal score, int maxScore, boolean initial, boolean extra) {

    public ExerciseScored(long userId, long bankId, long attemptId, BigDecimal score, int maxScore, boolean initial) {
        this(userId, bankId, attemptId, score, maxScore, initial, false);
    }
}
