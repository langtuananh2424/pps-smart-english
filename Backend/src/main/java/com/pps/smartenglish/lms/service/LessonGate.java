package com.pps.smartenglish.lms.service;

import com.pps.smartenglish.lms.dto.PlayableLesson;
import com.pps.smartenglish.lms.repository.LessonMediaRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Cổng chung: 404 nếu bài không công khai, 403 nếu không policy nào cho xem hoặc unit chưa mở (học tuần tự). */
@Component
class LessonGate {

    private final LessonMediaRepository lessons;
    private final List<LessonAccessPolicy> policies;
    private final UnitUnlockService unitUnlock;

    LessonGate(LessonMediaRepository lessons, List<LessonAccessPolicy> policies, UnitUnlockService unitUnlock) {
        this.lessons = lessons;
        this.policies = policies;
        this.unitUnlock = unitUnlock;
    }

    PlayableLesson require(Long lessonId, Long userId) {
        var lesson = lessons.findPlayable(lessonId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "lesson_not_found", "Không tìm thấy bài học"));
        if (policies.stream().noneMatch(p -> p.allows(userId, lesson))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "lesson_locked", "Bạn chưa có quyền xem bài học này");
        }
        unitUnlock.requireUnlocked(userId, lesson.topicId()); // học tuần tự (FR-LMS-04): chỉ khóa thêm, sau quyền truy cập thường
        return lesson;
    }
}
