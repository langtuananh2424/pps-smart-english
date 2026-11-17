package com.pps.smartenglish.home.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** app.exercise-stats.*: số câu đã nộp tối thiểu của một dạng để được gợi ý "nên luyện" (D-62). */
@ConfigurationProperties(prefix = "app.exercise-stats")
public record ExerciseStatsProperties(@DefaultValue("10") int weakMinAnswered) {}
