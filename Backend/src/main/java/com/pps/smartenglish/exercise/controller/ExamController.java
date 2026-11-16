package com.pps.smartenglish.exercise.controller;

import com.pps.smartenglish.exercise.dto.ExamStatus;
import com.pps.smartenglish.exercise.service.ExamService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** FR-EXAM-02: giao diện hỏi trạng thái bài thi khóa (khóa/mở, số lượt, kết quả gần nhất). */
@RestController
class ExamController {

    private final ExamService exams;

    ExamController(ExamService exams) {
        this.exams = exams;
    }

    @GetMapping("/api/courses/{courseId}/exam")
    ExamStatus status(@PathVariable String courseId, Authentication auth) {
        return exams.status(CurrentUser.id(auth), PathId.parse(courseId, "course_not_found", "Không tìm thấy khóa học"));
    }
}
