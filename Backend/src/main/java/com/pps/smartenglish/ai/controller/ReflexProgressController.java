package com.pps.smartenglish.ai.controller;

import com.pps.smartenglish.ai.service.ReflexProgress;
import com.pps.smartenglish.ai.service.ReflexProgress.View;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** D-158: tiến độ bước Viết/Nói của một câu Video phản xạ trong lượt (chỉ chủ lượt). */
@RestController
class ReflexProgressController {

    private final ReflexProgress progress;

    ReflexProgressController(ReflexProgress progress) {
        this.progress = progress;
    }

    @GetMapping("/api/attempts/{id}/video-reflex/{questionId}")
    View view(Authentication auth, @PathVariable String id, @PathVariable String questionId) {
        return progress.view(CurrentUser.id(auth), PathId.parse(id, "attempt_not_found", "Không tìm thấy lượt làm bài"),
                PathId.parse(questionId, "question_not_found", "Không tìm thấy câu hỏi"));
    }
}
