package com.pps.smartenglish.lms.service;

import com.pps.smartenglish.lms.dto.PlayableLesson;

/** Strategy quyền xem bài: các bean được OR với nhau, chỉ cần một bean cho phép. */
public interface LessonAccessPolicy {

    /** userId null = khách chưa đăng nhập. */
    boolean allows(Long userId, PlayableLesson lesson);
}
