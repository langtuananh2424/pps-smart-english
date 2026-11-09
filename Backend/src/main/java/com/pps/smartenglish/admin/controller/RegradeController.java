package com.pps.smartenglish.admin.controller;

import com.pps.smartenglish.exercise.dto.RegradeDtos.Preview;
import com.pps.smartenglish.exercise.dto.RegradeDtos.RunView;
import com.pps.smartenglish.exercise.dto.RegradeDtos.StartRequest;
import com.pps.smartenglish.exercise.dto.RegradeItemView;
import com.pps.smartenglish.exercise.service.RegradeService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chấm lại bài cũ khi sửa đáp án (FR-ADM-02). Chỉ ADMIN: nằm ngoài /api/admin/cms nên rơi vào luật /api/admin/**.
 * Điểm cũ chỉ xuất hiện ở đây, không bao giờ ở response của học viên.
 */
@RestController
@RequestMapping("/api/admin")
class RegradeController {

    private final RegradeService service;

    RegradeController(RegradeService service) {
        this.service = service;
    }

    @GetMapping("/questions/{id}/regrade-preview")
    Preview preview(@PathVariable String id) {
        return service.preview(questionId(id));
    }

    /** 202 khi tạo hoặc chạy lại phần lỗi; 200 khi trả lại run đã có. */
    @PostMapping("/questions/{id}/regrade")
    ResponseEntity<RunView> regrade(Authentication auth, @PathVariable String id, @Valid @RequestBody StartRequest req) {
        var started = service.start(CurrentUser.id(auth), questionId(id), req.answerVersion());
        return ResponseEntity.status(started.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(started.run());
    }

    @GetMapping("/regrades")
    CursorPage<RunView> list(@RequestParam(required = false) String questionId, @RequestParam(required = false) String limit,
                             @RequestParam(required = false) String cursor) {
        Long question = questionId == null || questionId.isBlank() ? null : questionId(questionId);
        return service.list(question, Cursors.size(limit, RegradeService.DEFAULT_LIMIT, RegradeService.MAX_LIMIT), cursor);
    }

    @GetMapping("/regrades/{id}")
    RunView run(@PathVariable String id) {
        return service.get(runId(id));
    }

    @GetMapping("/regrades/{id}/items")
    CursorPage<RegradeItemView> items(@PathVariable String id, @RequestParam(required = false) String limit,
                                      @RequestParam(required = false) String cursor) {
        return service.items(runId(id), Cursors.size(limit, RegradeService.DEFAULT_LIMIT, RegradeService.MAX_LIMIT), cursor);
    }

    private static long questionId(String raw) {
        return PathId.parse(raw, "question_not_found", "Không tìm thấy câu hỏi");
    }

    private static long runId(String raw) {
        return PathId.parse(raw, "regrade_not_found", "Không tìm thấy lần chấm lại");
    }
}
