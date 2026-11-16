package com.pps.smartenglish.exercise.grading;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Điểm một câu trong [0, 1], 4 chữ số thập phân HALF_UP (D-44); BigDecimal để không lệch khi cộng dồn. */
public final class Credit {

    public static final int SCALE = 4;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    public static final BigDecimal ONE = BigDecimal.ONE.setScale(SCALE);

    private Credit() {}

    public static BigDecimal of(boolean correct) {
        return correct ? ONE : ZERO;
    }

    /** total = 0 (cả hai tập rỗng) tính đúng, khớp isCorrect. */
    public static BigDecimal ratio(int hits, int total) {
        return total == 0 ? ONE : BigDecimal.valueOf(hits).divide(BigDecimal.valueOf(total), SCALE, RoundingMode.HALF_UP);
    }

    /** Câu "đúng" khi đạt 100%. */
    public static boolean isFull(BigDecimal credit) {
        return credit != null && credit.compareTo(BigDecimal.ONE) == 0;
    }
}
