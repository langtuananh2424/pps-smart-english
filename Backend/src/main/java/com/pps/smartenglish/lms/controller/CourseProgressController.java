package com.pps.smartenglish.lms.controller;

import com.pps.smartenglish.lms.dto.CourseProgress;
import com.pps.smartenglish.lms.service.CourseProgressService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** FR-EXAM-02, D-46: tiến độ học của học viên trong một khóa. */
@RestController
class CourseProgressController {

    private final CourseProgressService progress;

    CourseProgressController(CourseProgressService progress) {
        this.progress = progress;
    }

    @GetMapping("/api/me/courses/{courseId}/progress")
    CourseProgress get(@PathVariable String courseId, Authentication auth) {
        return progress.of(CurrentUser.id(auth), PathId.parse(courseId, "course_not_found", "Không tìm thấy khóa học"));
    }
}
