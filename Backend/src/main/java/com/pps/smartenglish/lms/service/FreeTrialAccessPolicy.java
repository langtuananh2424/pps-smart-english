package com.pps.smartenglish.lms.service;

import com.pps.smartenglish.lms.dto.PlayableLesson;
import org.springframework.stereotype.Component;

@Component
class FreeTrialAccessPolicy implements LessonAccessPolicy {

    // shortcut: chỉ bài học thử; commerce sẽ thêm policy kiểm tra enrollment
    @Override
    public boolean allows(Long userId, PlayableLesson lesson) {
        return lesson.freeTrial();
    }
}
