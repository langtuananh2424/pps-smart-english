package com.pps.smartenglish.ai.controller;

import com.pps.smartenglish.ai.dto.WritingDtos.AnswerAccepted;
import com.pps.smartenglish.ai.dto.WritingDtos.AnswerRequest;
import com.pps.smartenglish.ai.dto.WritingDtos.CreateSessionRequest;
import com.pps.smartenglish.ai.dto.WritingDtos.DraftAccepted;
import com.pps.smartenglish.ai.dto.WritingDtos.SessionCreated;
import com.pps.smartenglish.ai.dto.WritingDtos.SubmitAccepted;
import com.pps.smartenglish.ai.dto.WritingDtos.SubmitRequest;
import com.pps.smartenglish.ai.service.WritingService;
import com.pps.smartenglish.shared.idempotency.IdempotencyService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-AI-01: viết phát triển ý và nộp chấm. */
@RestController
@RequestMapping("/api/writing/sessions")
class WritingController {

    private final WritingService writing;

    WritingController(WritingService writing) {
        this.writing = writing;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SessionCreated create(Authentication auth, @Valid @RequestBody CreateSessionRequest req) {
        return writing.create(CurrentUser.id(auth), req.questionId());
    }

    /** 202 kèm jobId; 200 khi đủ câu hỏi đào sâu và session đã READY (không có job). */
    @PostMapping("/{id}/answer")
    ResponseEntity<AnswerAccepted> answer(Authentication auth, @PathVariable String id, @Valid @RequestBody AnswerRequest req) {
        var r = writing.answer(CurrentUser.id(auth), sessionId(id), req.text());
        return ResponseEntity.status(r.jobId() == null ? HttpStatus.OK : HttpStatus.ACCEPTED).body(r);
    }

    @PostMapping("/{id}/draft")
    @ResponseStatus(HttpStatus.ACCEPTED)
    DraftAccepted draft(Authentication auth, @PathVariable String id) {
        return writing.draft(CurrentUser.id(auth), sessionId(id));
    }

    @PostMapping("/{id}/submit")
    ResponseEntity<SubmitAccepted> submit(Authentication auth, @PathVariable String id,
                                          @RequestHeader(value = IdempotencyService.HEADER, required = false) String key,
                                          @Valid @RequestBody SubmitRequest req) {
        var outcome = writing.submit(CurrentUser.id(auth), sessionId(id), req.finalText(), key);
        return ResponseEntity.status(outcome.status()).body(outcome.body());
    }

    private static Long sessionId(String raw) {
        return PathId.parse(raw, "session_not_found", "Không tìm thấy phiên viết");
    }
}
