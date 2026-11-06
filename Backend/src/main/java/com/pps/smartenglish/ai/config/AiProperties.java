package com.pps.smartenglish.ai.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Cấu hình app.ai.*. price: USD cho 1 triệu token. */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        @DefaultValue("anthropic") String provider,
        @DefaultValue("claude-haiku-5-5") String model,
        @DefaultValue("") String apiKey,
        @DefaultValue("200") int maxPending,
        @DefaultValue("true") boolean workerEnabled,
        @DefaultValue("10") int concurrency,
        @DefaultValue("true") boolean redisNotify,
        @DefaultValue Price price) {

    public record Price(
            @DefaultValue("0.10") BigDecimal input,
            @DefaultValue("0.50") BigDecimal output,
            @DefaultValue("0.01") BigDecimal cacheRead,
            @DefaultValue("0.125") BigDecimal cacheWrite) {}
}
