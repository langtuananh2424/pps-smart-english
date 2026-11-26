package com.pps.smartenglish.exercise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.extra-practice.* (D-152): reviewSize = số câu "Ôn lại" theo dạng; challengeSize = số câu Thử thách; challengeMaxRatio = trần số
 * câu theo tỉ lệ tổng số câu hợp lệ (dùng chung hai chế độ); poolLimit = số câu ứng viên tối đa nạp mỗi lần chọn.
 */
@ConfigurationProperties(prefix = "app.extra-practice")
public record ExtraProperties(@DefaultValue("10") int reviewSize, @DefaultValue("12") int challengeSize,
                              @DefaultValue("0.15") double challengeMaxRatio, @DefaultValue("2000") int poolLimit) {

    public ExtraProperties {
        if (reviewSize < 1 || challengeSize < 1 || challengeMaxRatio <= 0 || challengeMaxRatio > 1 || poolLimit < 50) {
            throw new IllegalArgumentException("app.extra-practice: sizes >= 1, 0 < ratio <= 1, poolLimit >= 50");
        }
    }

    /** min(n, ceil(ratio * total)), tối thiểu 1. */
    public int capped(int n, int total) {
        return Math.max(1, Math.min(n, (int) Math.ceil(challengeMaxRatio * total - 1e-9)));
    }
}
