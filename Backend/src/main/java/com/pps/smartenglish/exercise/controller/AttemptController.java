package com.pps.smartenglish.exercise.controller;

import com.pps.smartenglish.exercise.dto.AttemptResponse;
import com.pps.smartenglish.exercise.dto.AttemptSummary;
import com.pps.smartenglish.exercise.dto.SubmitRequest;
import com.pps.smartenglish.exercise.service.AttemptService;
import com.pps.smartenglish.exercise.service.SubmissionService;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AttemptController {

    private final AttemptService attempts;
    private final SubmissionService submissions;

    AttemptController(AttemptService attempts, SubmissionService submissions) {
        this.attempts = attempts;
        this.submissions = submissions;
    }

    @PostMapping("/api/attempts/{id}/submit")
    AttemptResponse submit(@PathVariable String id, Authentication auth, @Valid @RequestBody SubmitRequest req) {
        return submissions.submit(CurrentUser.id(auth), attemptId(id), req);
    }

    @GetMapping("/api/attempts/{id}")
    AttemptResponse get(@PathVariable String id, Authentication auth) {
        return attempts.get(CurrentUser.id(auth), attemptId(id));
    }

    @GetMapping("/api/me/attempts")
    CursorPage<AttemptSummary> mine(Authentication auth, @RequestParam(required = false) String limit,
                                    @RequestParam(required = false) String cursor) {
        return attempts.mine(CurrentUser.id(auth), parseLimit(limit), cursor);
    }

    private static Long attemptId(String raw) {
        return PathId.parse(raw, "attempt_not_found", "Không tìm thấy lượt làm bài");
    }

    private static Integer parseLimit(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            throw ApiException.validation("limit", "Phải là số nguyên");
        }
    }
}
