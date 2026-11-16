package com.pps.smartenglish.home.dto;

import java.time.Instant;

/** Khóa PUBLISHED mà người dùng có enrollment; status là cột enrollment, hết hạn thật tính theo expiresAt. */
public record CourseRow(Long courseId, String slug, String title, String targetType, String status, Instant expiresAt) {

    /** Khóa chưa mua nhưng đã học thử (D-61). */
    public CourseRow(Long courseId, String slug, String title, String targetType) {
        this(courseId, slug, title, targetType, "TRIAL", null);
    }
}
