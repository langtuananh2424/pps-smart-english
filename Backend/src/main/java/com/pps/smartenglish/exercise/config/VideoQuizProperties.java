package com.pps.smartenglish.exercise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.video-quiz.* (D-159, mọi con số cấu hình được): toleranceSec = dung sai khi cộng phần đã xem (chống tua), watchPct = ngưỡng
 * xem đủ mới mở quiz, maxSubmits = số lần nộp quiz mỗi nhóm mỗi lượt, passesRequired = số lượt đạt để bộ hoàn thành,
 * maxDurationSec = thời lượng video tối đa chấp nhận từ trình phát, reportsPerMinute = giới hạn báo tiến độ mỗi học viên.
 */
@ConfigurationProperties(prefix = "app.video-quiz")
public record VideoQuizProperties(@DefaultValue("10") int toleranceSec, @DefaultValue("100") int watchPct,
                                  @DefaultValue("2") int maxSubmits, @DefaultValue("1") int passesRequired,
                                  @DefaultValue("14400") int maxDurationSec, @DefaultValue("60") int reportsPerMinute) {

    public VideoQuizProperties {
        if (toleranceSec < 0 || watchPct < 1 || watchPct > 100 || maxSubmits < 1 || passesRequired < 1 || maxDurationSec < 1
                || reportsPerMinute < 1) {
            throw new IllegalArgumentException("app.video-quiz: toleranceSec >= 0, watchPct 1..100, các số còn lại >= 1");
        }
    }
}
