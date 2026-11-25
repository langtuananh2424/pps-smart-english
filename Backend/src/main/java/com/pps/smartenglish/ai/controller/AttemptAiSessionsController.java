package com.pps.smartenglish.ai.controller;

import com.pps.smartenglish.ai.service.AttemptAiSessions;
import com.pps.smartenglish.ai.service.AttemptAiSessions.Item;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** D-145: trạng thái phiên Viết/Nói mới nhất của từng câu trong một lượt (chỉ chủ lượt). */
@RestController
class AttemptAiSessionsController {

    private final AttemptAiSessions sessions;

    AttemptAiSessionsController(AttemptAiSessions sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/api/attempts/{id}/ai-sessions")
    Map<String, List<Item>> list(Authentication auth, @PathVariable String id) {
        return Map.of("data", sessions.list(CurrentUser.id(auth), PathId.parse(id, "attempt_not_found", "Không tìm thấy lượt làm bài")));
    }
}
