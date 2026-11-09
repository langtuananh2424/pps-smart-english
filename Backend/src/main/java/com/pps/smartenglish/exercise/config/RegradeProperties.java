package com.pps.smartenglish.exercise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** app.regrade.*: worker chấm lại bài cũ. maxAiPending là trần số job AI chấm lại đang chạy cùng lúc (cả hệ thống). */
@ConfigurationProperties(prefix = "app.regrade")
public record RegradeProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("2000") long pollMs,
        @DefaultValue("200") int batchSize,
        @DefaultValue("20") int maxAiPending) {}
