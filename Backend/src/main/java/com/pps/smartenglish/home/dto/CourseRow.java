package com.pps.smartenglish.home.dto;

import java.time.Instant;

/** Khóa PUBLISHED mà người dùng có enrollment; status là cột enrollment, hết hạn thật tính theo expiresAt. */
public record CourseRow(Long courseId, String slug, String title, String targetType, String status, Instant expiresAt) {}
