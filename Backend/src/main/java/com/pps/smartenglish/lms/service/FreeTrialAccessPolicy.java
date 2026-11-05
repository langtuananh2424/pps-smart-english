package com.pps.smartenglish.lms.service;

import com.pps.smartenglish.lms.dto.PlayableLesson;
import org.springframework.stereotype.Component;

@Component
class FreeTrialAccessPolicy implements LessonAccessPolicy {

    @Override
    public boolean allows(Long userId, PlayableLesson lesson) {
        return lesson.freeTrial();
    }
}
