package com.pps.smartenglish.suggestion.config;

import com.pps.smartenglish.profile.domain.SelfLevel;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** app.suggestion.*: band quy đổi từ trình độ tự đánh giá (D-56) và giới hạn gọi công khai theo IP mỗi giờ. */
@ConfigurationProperties(prefix = "app.suggestion")
public record SuggestionProperties(Map<SelfLevel, BigDecimal> selfLevelBand, @DefaultValue("30") int guestIpLimit) {

    public SuggestionProperties {
        for (SelfLevel level : SelfLevel.values()) {
            if (selfLevelBand == null || selfLevelBand.get(level) == null) {
                throw new IllegalStateException("Thiếu app.suggestion.self-level-band." + level);
            }
        }
    }

    public BigDecimal bandOf(SelfLevel level) {
        return selfLevelBand.get(level);
    }
}
