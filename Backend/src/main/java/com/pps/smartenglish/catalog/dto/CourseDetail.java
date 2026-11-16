package com.pps.smartenglish.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

public record CourseDetail(String slug, String title, String description, String targetType, BigDecimal bandFrom,
                           BigDecimal bandTo, int trialLessonCount, Long priceFromVnd, int levelCount, int lessonCount,
                           List<ProductOption> products, List<LevelNode> levels) {

    public record LevelNode(Long id, String code, String title, List<TopicNode> topics) {}

    public record TopicNode(Long id, String title, boolean isFreeTrial, List<LessonNode> lessons) {}

    public record LessonNode(Long id, String title, String type, Integer durationSec) {}
}
