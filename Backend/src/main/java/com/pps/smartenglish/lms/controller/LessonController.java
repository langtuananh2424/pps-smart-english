package com.pps.smartenglish.lms.controller;

import com.pps.smartenglish.lms.dto.LessonResponse;
import com.pps.smartenglish.lms.dto.ProgressRequest;
import com.pps.smartenglish.lms.dto.ProgressResponse;
import com.pps.smartenglish.lms.service.LessonProgressService;
import com.pps.smartenglish.lms.service.LessonService;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-LMS-02 (xem, công khai có hoặc không token) và FR-LMS-03 (tiến độ, cần đăng nhập). */
@RestController
@RequestMapping("/api/lessons")
class LessonController {

    private final LessonService lessons;
    private final LessonProgressService progress;

    LessonController(LessonService lessons, LessonProgressService progress) {
        this.lessons = lessons;
        this.progress = progress;
    }

    @GetMapping("/{id}")
    LessonResponse get(@PathVariable String id, Authentication auth) {
        return lessons.get(parse(id), CurrentUser.idOrNull(auth));
    }

    @PostMapping("/{id}/progress")
    ProgressResponse progress(@PathVariable String id, Authentication auth, @Valid @RequestBody ProgressRequest req) {
        return progress.record(CurrentUser.id(auth), parse(id), req);
    }

    /** Id không phải số cũng là "không tìm thấy", không để lọt thành 500. */
    private static Long parse(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "lesson_not_found", "Không tìm thấy bài học");
        }
    }
}
