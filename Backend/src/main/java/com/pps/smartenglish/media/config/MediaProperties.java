package com.pps.smartenglish.media.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** app.media.* (D-134, D-138): mọi con số là cấu hình, chỉnh bằng biến môi trường, không sửa code. */
@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(@DefaultValue Image image,
                              @DefaultValue("20") int maxPerQuestion,
                              @DefaultValue("120m") Duration urlTtl,
                              @DefaultValue("15m") Duration uploadTtl,
                              @DefaultValue("24h") Duration expireAfter,
                              @DefaultValue("true") boolean cleanupEnabled) {

    /** targetBytes: vượt thì hạ chất lượng xuống fallbackQuality. maxPixels chống ảnh bom nén. */
    public record Image(@DefaultValue("10485760") long maxUploadBytes,
                        @DefaultValue("1600") int maxEdgePx,
                        @DefaultValue("80") int quality,
                        @DefaultValue("70") int fallbackQuality,
                        @DefaultValue("307200") long targetBytes,
                        @DefaultValue("40000000") long maxPixels) {}
}
