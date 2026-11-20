package com.pps.smartenglish.lms.service;

import java.util.Set;

/**
 * FR-CLS-06, D-122: unit giáo viên, trợ giảng đã mở sớm cho học viên. lms chỉ biết interface này; phân hệ lớp cài đặt, nên lms
 * không phụ thuộc ngược vào lớp.
 */
public interface EarlyUnlocks {

    /** Các topic của khóa đang được mở sớm cho học viên (qua lớp đang học, cả lớp hoặc riêng học viên). */
    Set<Long> openedTopics(long userId, long courseId);
}
