package com.pps.smartenglish.catalog.dto;

import java.math.BigDecimal;

/** Thẻ khóa học công khai (D-54). priceFromVnd null khi chưa có gói bán; trialLessonCount 0 khi không có topic học thử. */
public record CourseSummary(String slug, String title, String description, String targetType, BigDecimal bandFrom,
                            BigDecimal bandTo, int trialLessonCount, Long priceFromVnd, int levelCount, int lessonCount) {}
