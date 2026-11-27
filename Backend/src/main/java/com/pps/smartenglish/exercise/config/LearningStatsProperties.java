package com.pps.smartenglish.exercise.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.learning-stats.* (D-138: con số nghiệp vụ là cấu hình có mặc định).
 * maxTimeSpentMs: thời gian một câu hợp lệ tối đa, ngoài 0..max thì bỏ (lưu null).
 * submitGraceSec: ân hạn sau hạn giờ mà lần nộp vẫn được chấm theo đáp án gửi kèm (độ trễ mạng của lần tự nộp).
 * trendWeeks: số tuần của xu hướng; improveMinAnswered/improveLimit: luật "cần cải thiện" (FR-EX-09).
 */
@ConfigurationProperties(prefix = "app.learning-stats")
public record LearningStatsProperties(
        @DefaultValue("3600000") int maxTimeSpentMs,
        @DefaultValue("12") int trendWeeks,
        @DefaultValue("5") int submitGraceSec,
        @DefaultValue("10") int improveMinAnswered,
        @DefaultValue("3") int improveLimit,
        @DefaultValue("Asia/Ho_Chi_Minh") ZoneId zone) {

    /** Sai cấu hình thì backend không khởi động, thay vì lỗi 500 hoặc xu hướng rỗng lúc chạy. */
    public LearningStatsProperties {
        if (maxTimeSpentMs < 0 || trendWeeks < 1 || submitGraceSec < 0 || improveMinAnswered < 1 || improveLimit < 1) {
            throw new IllegalArgumentException(
                    "app.learning-stats: maxTimeSpentMs >= 0; trendWeeks, improveMinAnswered, improveLimit >= 1");
        }
    }
}
