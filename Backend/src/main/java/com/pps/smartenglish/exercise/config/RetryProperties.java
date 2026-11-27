package com.pps.smartenglish.exercise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.retry.* (D-150): maxRetries = số lần thử lại tối đa mỗi câu (2 nghĩa là tổng 3 lần kiểm tra);
 * hintAfterWrong = số lần sai của một câu để Cam tự mở gợi ý (D-163);
 * checksPerMinute = giới hạn số lần kiểm tra của một học viên mỗi phút.
 */
@ConfigurationProperties(prefix = "app.retry")
public record RetryProperties(@DefaultValue("2") int maxRetries, @DefaultValue("2") int hintAfterWrong,
                              @DefaultValue("60") int checksPerMinute) {

    public RetryProperties {
        if (maxRetries < 0 || hintAfterWrong < 1 || checksPerMinute < 1) {
            throw new IllegalArgumentException("app.retry: maxRetries >= 0, hintAfterWrong >= 1, checksPerMinute >= 1");
        }
        // Lớn hơn maxRetries thì Cam không bao giờ tự mở; maxRetries = 0 (tắt thử lại) thì bỏ qua.
        if (maxRetries > 0 && hintAfterWrong > maxRetries) {
            throw new IllegalArgumentException("app.retry: hintAfterWrong <= maxRetries");
        }
    }
}
