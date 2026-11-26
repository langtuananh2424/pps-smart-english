package com.pps.smartenglish.exercise.controller;

import com.pps.smartenglish.exercise.dto.AttemptStarted;
import com.pps.smartenglish.exercise.dto.BankResponse;
import com.pps.smartenglish.exercise.dto.ExtraDtos.Overview;
import com.pps.smartenglish.exercise.dto.ExtraDtos.StartRequest;
import com.pps.smartenglish.exercise.service.ExtraAttemptViewService;
import com.pps.smartenglish.exercise.service.ExtraOverviewService;
import com.pps.smartenglish.exercise.service.ExtraStartService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-EX-07, D-152: Luyện thêm theo Topic; chỉ trả dữ liệu của người đăng nhập. */
@RestController
@RequestMapping("/api/me")
class ExtraPracticeController {

    private final ExtraOverviewService overview;
    private final ExtraStartService starter;
    private final ExtraAttemptViewService view;

    ExtraPracticeController(ExtraOverviewService overview, ExtraStartService starter, ExtraAttemptViewService view) {
        this.overview = overview;
        this.starter = starter;
        this.view = view;
    }

    @GetMapping("/courses/{courseId}/extra-practice")
    Overview overview(@PathVariable String courseId, Authentication auth) {
        return overview.of(CurrentUser.id(auth), course(courseId));
    }

    @PostMapping("/courses/{courseId}/extra-practice/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    AttemptStarted start(@PathVariable String courseId, @RequestBody(required = false) StartRequest req, Authentication auth) {
        return starter.start(CurrentUser.id(auth), course(courseId), req);
    }

    /** Đề của lượt Luyện thêm theo dạng "bộ" (không đáp án). */
    @GetMapping("/extra-practice/attempts/{id}")
    BankResponse attempt(@PathVariable String id, Authentication auth) {
        return view.get(CurrentUser.id(auth), PathId.parse(id, "attempt_not_found", "Không tìm thấy lượt làm bài"));
    }

    private static long course(String raw) {
        return PathId.parse(raw, "course_not_found", "Không tìm thấy khóa học");
    }
}
