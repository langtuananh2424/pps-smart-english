package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.repository.UnlockOverrideRepository;
import com.pps.smartenglish.lms.service.EarlyUnlocks;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** FR-CLS-06: cài đặt EarlyUnlocks cho lms: unit giáo viên đã mở sớm cho học viên qua lớp đang học. */
@Component
class EarlyUnlocksImpl implements EarlyUnlocks {

    private final UnlockOverrideRepository unlocks;

    EarlyUnlocksImpl(UnlockOverrideRepository unlocks) {
        this.unlocks = unlocks;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> openedTopics(long userId, long courseId) {
        return new HashSet<>(unlocks.openedTopics(userId, courseId));
    }
}
