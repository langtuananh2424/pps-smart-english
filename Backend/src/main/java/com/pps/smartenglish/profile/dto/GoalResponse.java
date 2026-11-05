package com.pps.smartenglish.profile.dto;

import java.time.LocalDate;

public record GoalResponse(Long id, Long courseId, String targetType, String targetValue, LocalDate targetDate) {}
