package com.pps.smartenglish.exercise.controller;

import com.pps.smartenglish.exercise.dto.VideoQuizDtos.GroupState;
import com.pps.smartenglish.exercise.dto.VideoQuizDtos.ProgressRequest;
import com.pps.smartenglish.exercise.dto.VideoQuizDtos.ProgressResult;
import com.pps.smartenglish.exercise.dto.VideoQuizDtos.QuizRequest;
import com.pps.smartenglish.exercise.dto.VideoQuizDtos.State;
import com.pps.smartenglish.exercise.service.VideoQuizService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** FR-EX-04, D-159: video từ nối (báo tiến độ xem, nộp quiz theo nhóm, trạng thái cho màn làm bài và màn kết quả). */
@RestController
class VideoQuizController {

    private final VideoQuizService video;

    VideoQuizController(VideoQuizService video) {
        this.video = video;
    }

    @PostMapping("/api/attempts/{id}/video-progress")
    ProgressResult progress(@PathVariable String id, Authentication auth, @Valid @RequestBody ProgressRequest req) {
        return video.report(CurrentUser.id(auth), attemptId(id), req);
    }

    @PostMapping("/api/attempts/{id}/groups/{groupId}/video-quiz")
    GroupState submit(@PathVariable String id, @PathVariable String groupId, Authentication auth, @Valid @RequestBody QuizRequest req) {
        return video.submitQuiz(CurrentUser.id(auth), attemptId(id),
                PathId.parse(groupId, "group_not_found", "Không tìm thấy nhóm video trong lượt này"), req.answers());
    }

    @GetMapping("/api/attempts/{id}/video-quiz")
    State state(@PathVariable String id, Authentication auth) {
        return video.state(CurrentUser.id(auth), attemptId(id));
    }

    private static Long attemptId(String raw) {
        return PathId.parse(raw, "attempt_not_found", "Không tìm thấy lượt làm bài");
    }
}
