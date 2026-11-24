package com.pps.smartenglish.exercise.listening;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.listening.* (D-135, D-138): mọi con số nghiệp vụ là cấu hình có mặc định, người tạo đề và giáo viên ghi đè được.
 * playLimit, passPct, hintEveryN, wrongThreshold: mặc định của luật Nghe ép (FR-EX-10). examPlayLimit: số lần nghe chế độ Thi.
 * maxPlayLimit, maxHintEveryN, maxWrongThreshold: cận trên của giá trị người dùng đặt (chống cấu hình vô lý).
 * urlTtl/urlMargin: hạn link nghe = độ dài audio + margin nếu biết, ngược lại urlTtl; không vượt maxUrlTtl.
 * audioMaxBytes/audioMaxDurationMs: giới hạn file audio tải lên.
 */
@ConfigurationProperties(prefix = "app.listening")
public record ListeningProperties(
        @DefaultValue("3") int playLimit,
        @DefaultValue("70") int passPct,
        @DefaultValue("3") int hintEveryN,
        @DefaultValue("3") int wrongThreshold,
        @DefaultValue("1") int examPlayLimit,
        @DefaultValue("10") int maxPlayLimit,
        @DefaultValue("100") int maxHintEveryN,
        @DefaultValue("100") int maxWrongThreshold,
        @DefaultValue("30m") Duration urlTtl,
        @DefaultValue("2m") Duration urlMargin,
        @DefaultValue("2h") Duration maxUrlTtl,
        @DefaultValue("52428800") long audioMaxBytes,
        @DefaultValue("3600000") int audioMaxDurationMs) {

    /** Sai cấu hình thì backend không khởi động, thay vì lỗi 500 lúc chạy. */
    public ListeningProperties {
        boolean ok = between(playLimit, 1, maxPlayLimit) && between(examPlayLimit, 1, maxPlayLimit)
                && between(passPct, 1, 100) && between(hintEveryN, 1, maxHintEveryN)
                && between(wrongThreshold, 1, maxWrongThreshold) && audioMaxBytes > 0 && audioMaxDurationMs > 0
                && !urlTtl.isNegative() && !urlTtl.isZero() && maxUrlTtl.compareTo(urlTtl) >= 0 && !urlMargin.isNegative();
        if (!ok) {
            throw new IllegalArgumentException("app.listening: giá trị ngoài khoảng cho phép (xem ListeningProperties)");
        }
    }

    private static boolean between(int v, int lo, int hi) {
        return v >= lo && v <= hi;
    }
}
