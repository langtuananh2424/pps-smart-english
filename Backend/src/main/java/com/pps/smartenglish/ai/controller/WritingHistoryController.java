package com.pps.smartenglish.ai.controller;

import com.pps.smartenglish.ai.dto.WritingDtos.SessionSummary;
import com.pps.smartenglish.ai.dto.WritingDtos.SessionView;
import com.pps.smartenglish.ai.service.WritingHistoryService;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.PathId;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** D-84: xem lại phiên viết (FR-AI-01, FR-AI-02). Chỉ chủ phiên. */
@RestController
class WritingHistoryController {

    private final WritingHistoryService history;

    WritingHistoryController(WritingHistoryService history) {
        this.history = history;
    }

    @GetMapping("/api/writing/sessions/{id}")
    SessionView get(Authentication auth, @PathVariable String id) {
        return history.view(CurrentUser.id(auth),
                PathId.parse(id, "session_not_found", "Không tìm thấy phiên viết"));
    }

    /** Mới nhất trước, lọc theo câu hỏi, phân trang con trỏ. */
    @GetMapping("/api/me/writing")
    CursorPage<SessionSummary> mine(Authentication auth, @RequestParam(required = false) String questionId,
                                    @RequestParam(required = false) String assignmentId, @RequestParam(required = false) String limit, @RequestParam(required = false) String cursor) {
        Long question = null;
        if (questionId != null && !questionId.isBlank()) {
            try {
                question = Long.valueOf(questionId);
            } catch (NumberFormatException e) {
                throw ApiException.validation("questionId", "Phải là số nguyên");
            }
        }
        Long asg = assignmentId == null || assignmentId.isBlank() ? null
                : PathId.parse(assignmentId, "assignment_not_found", "Không tìm thấy bài được giao");
        return history.list(CurrentUser.id(auth), question, asg, limit, cursor);
    }
}
