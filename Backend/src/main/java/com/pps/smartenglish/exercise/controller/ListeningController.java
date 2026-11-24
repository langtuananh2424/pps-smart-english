package com.pps.smartenglish.exercise.controller;

import com.pps.smartenglish.exercise.listening.AudioPlayService;
import com.pps.smartenglish.exercise.listening.ListeningDtos.PlayGrant;
import com.pps.smartenglish.exercise.listening.ListeningDtos.PlayState;
import com.pps.smartenglish.exercise.listening.ListeningDtos.ScriptView;
import com.pps.smartenglish.exercise.listening.ScriptService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-EX-10, D-135, D-136: nghe audio qua máy chủ (đếm lượt) và xem script câu sai nhiều. Chỉ chủ lượt làm bài. */
@RestController
class ListeningController {

    private final AudioPlayService plays;
    private final ScriptService scripts;

    ListeningController(AudioPlayService plays, ScriptService scripts) {
        this.plays = plays;
        this.scripts = scripts;
    }

    @PostMapping("/api/attempts/{id}/groups/{groupId}/play")
    PlayGrant play(@PathVariable String id, @PathVariable String groupId, Authentication auth) {
        return plays.play(CurrentUser.id(auth), attempt(id), group(groupId));
    }

    @PostMapping("/api/attempts/{id}/groups/{groupId}/play/complete")
    PlayState complete(@PathVariable String id, @PathVariable String groupId, Authentication auth) {
        return plays.complete(CurrentUser.id(auth), attempt(id), group(groupId));
    }

    @GetMapping("/api/attempts/{id}/scripts/{questionId}")
    ScriptView script(@PathVariable String id, @PathVariable String questionId, Authentication auth) {
        return scripts.view(CurrentUser.id(auth), attempt(id),
                PathId.parse(questionId, "question_not_found", "Không tìm thấy câu hỏi"));
    }

    private static Long attempt(String raw) {
        return PathId.parse(raw, "attempt_not_found", "Không tìm thấy lượt làm bài");
    }

    private static Long group(String raw) {
        return PathId.parse(raw, "group_not_found", "Không tìm thấy part");
    }
}
