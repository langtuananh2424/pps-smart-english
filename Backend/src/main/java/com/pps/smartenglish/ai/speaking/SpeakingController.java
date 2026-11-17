package com.pps.smartenglish.ai.speaking;

import com.pps.smartenglish.ai.speaking.SpeakingDtos.CompleteResponse;
import com.pps.smartenglish.ai.speaking.SpeakingDtos.CreateRequest;
import com.pps.smartenglish.ai.speaking.SpeakingDtos.QuestionView;
import com.pps.smartenglish.ai.speaking.SpeakingDtos.SubmissionView;
import com.pps.smartenglish.ai.speaking.SpeakingDtos.UploadPermit;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-AI-03: chấm nói. Mọi đường dẫn cần đăng nhập (SecurityConfig: anyRequest().authenticated()). */
@RestController
class SpeakingController {

    private final SpeakingService speaking;

    SpeakingController(SpeakingService speaking) {
        this.speaking = speaking;
    }

    @GetMapping("/api/speaking/questions/{id}")
    QuestionView question(Authentication auth, @PathVariable String id) {
        return speaking.question(CurrentUser.id(auth), PathId.parse(id, "question_not_found", "Không tìm thấy câu hỏi"));
    }

    @PostMapping("/api/speaking/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    UploadPermit create(Authentication auth, @Valid @RequestBody CreateRequest req) {
        return speaking.create(CurrentUser.id(auth), req.questionId());
    }

    @PostMapping("/api/speaking/submissions/{id}/complete")
    @ResponseStatus(HttpStatus.ACCEPTED)
    CompleteResponse complete(Authentication auth, @PathVariable String id) {
        return speaking.complete(CurrentUser.id(auth), submissionId(id));
    }

    @GetMapping("/api/speaking/submissions/{id}")
    SubmissionView view(Authentication auth, @PathVariable String id) {
        return speaking.view(CurrentUser.id(auth), submissionId(id));
    }

    @GetMapping("/api/me/speaking")
    CursorPage<SubmissionView> list(Authentication auth, @RequestParam(required = false) String questionId,
                                    @RequestParam(required = false) String limit, @RequestParam(required = false) String cursor) {
        Long question = null;
        if (questionId != null && !questionId.isBlank()) {
            try {
                question = Long.valueOf(questionId);
            } catch (NumberFormatException e) {
                throw ApiException.validation("questionId", "Phải là số nguyên");
            }
        }
        return speaking.list(CurrentUser.id(auth), question, limit, cursor);
    }

    private static Long submissionId(String raw) {
        return PathId.parse(raw, "submission_not_found", "Không tìm thấy lượt nói");
    }
}
