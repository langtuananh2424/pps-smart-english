package com.pps.smartenglish.retention.config;

import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Cấu hình app.retention.* (Q-18, Q-19, Q-20). Con số XP và danh hiệu là cấu hình, không phải hằng số trong code. */
@ConfigurationProperties(prefix = "app.retention")
public record RetentionProperties(
        @DefaultValue("Asia/Ho_Chi_Minh") ZoneId zone,
        @DefaultValue("true") boolean jobsEnabled,
        @DefaultValue Xp xp,
        List<Title> titles,
        @DefaultValue Push push) {

    public record Xp(
            @DefaultValue("10") int lesson,
            @DefaultValue("10") int exercise,
            @DefaultValue("10") int highScore,
            @DefaultValue("80") int highScorePercent) {}

    /** Danh hiệu theo ngưỡng XP; sắp tăng dần theo minXp. */
    public record Title(String name, long minXp) {}

    /**
     * allowedHosts: host nhận push hợp lệ, server sẽ POST vào endpoint nên phải chặn SSRF; mục bắt đầu bằng "."
     * là hậu tố. vapid*: khóa VAPID, thiếu khóa thì không gửi push (NoopPushSender).
     */
    public record Push(
            List<String> allowedHosts,
            @DefaultValue("10") int concurrency,
            @DefaultValue("") String vapidPublicKey,
            @DefaultValue("") String vapidPrivateKey,
            @DefaultValue("mailto:no-reply@localhost") String vapidSubject) {

        public boolean vapidConfigured() {
            return !vapidPublicKey.isBlank() && !vapidPrivateKey.isBlank();
        }
    }
}
