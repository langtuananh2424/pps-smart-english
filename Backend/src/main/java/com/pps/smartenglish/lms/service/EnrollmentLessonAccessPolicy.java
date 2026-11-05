package com.pps.smartenglish.lms.service;

import com.pps.smartenglish.commerce.service.EnrollmentAccess;
import com.pps.smartenglish.lms.dto.PlayableLesson;
import org.springframework.stereotype.Component;

/** Bài trả phí mở khi học viên có enrollment còn hạn của Level chứa bài. */
@Component
class EnrollmentLessonAccessPolicy implements LessonAccessPolicy {

    private final EnrollmentAccess enrollments;

    EnrollmentLessonAccessPolicy(EnrollmentAccess enrollments) {
        this.enrollments = enrollments;
    }

    @Override
    public boolean allows(Long userId, PlayableLesson lesson) {
        return enrollments.hasAccess(userId, lesson.levelId());
    }
}
