package com.pps.smartenglish.suggestion.dto;

import java.math.BigDecimal;
import java.util.List;

public final class SuggestionDtos {

    private SuggestionDtos() {}

    /** Nguồn của band hiện tại: kiểm tra đầu vào, trình độ tự đánh giá, hoặc chưa có gì. */
    public enum Basis { PLACEMENT, SELF_LEVEL, NONE }

    public record CourseCard(Long courseId, String slug, String title, BigDecimal bandFrom, BigDecimal bandTo,
                             int trialLessonCount, Long priceFromVnd) {}

    /** primary null khi chưa biết band hoặc không khóa nào có dải band; path là các khóa kế tiếp tới band mục tiêu. */
    public record CourseSuggestion(Basis basis, BigDecimal currentBand, BigDecimal targetBand, CourseCard primary,
                                   List<CourseCard> path) {}
}
