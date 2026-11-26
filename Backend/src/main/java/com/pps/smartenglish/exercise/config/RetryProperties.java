package com.pps.smartenglish.exercise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.retry.* (D-150): maxRetries = số lần thử lại tối đa mỗi câu (1 nghĩa là tổng 2 lần kiểm tra);
 * checksPerMinute = giới hạn số lần kiểm tra của một học viên mỗi phút.
 */
@ConfigurationProperties(prefix = "app.retry")
public record RetryProperties(@DefaultValue("1") int maxRetries, @DefaultValue("60") int checksPerMinute) {

    public RetryProperties {
        if (maxRetries < 0 || checksPerMinute < 1) {
            throw new IllegalArgumentException("app.retry: maxRetries >= 0, checksPerMinute >= 1");
        }
    }
}
