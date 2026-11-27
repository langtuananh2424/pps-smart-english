package com.pps.smartenglish.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình app.video-reflex.* (D-158, FR-AI-03): số lượt ghi/viết, ngưỡng đạt bước Viết và thời lượng ghi mặc định.
 * Mặc định theo mẫu Figma 10.28–10.31; chỉnh bằng cấu hình, không sửa code.
 */
@ConfigurationProperties(prefix = "app.video-reflex")
public record VideoReflexProperties(
        @DefaultValue("3") int maxRecordings,
        @DefaultValue("3") int maxWriteSubmits,
        @DefaultValue("70") int writePassPct,
        @DefaultValue("30") int shortSeconds,
        @DefaultValue("60") int otherSeconds) {

    public VideoReflexProperties {
        if (maxRecordings < 1 || maxWriteSubmits < 1 || writePassPct < 1 || writePassPct > 100 || shortSeconds < 10 || otherSeconds < 10) {
            throw new IllegalArgumentException("app.video-reflex.* không hợp lệ");
        }
    }
}
