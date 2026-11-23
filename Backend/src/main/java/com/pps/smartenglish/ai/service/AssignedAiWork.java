package com.pps.smartenglish.ai.service;

/**
 * FR-ASG-02, D-130: câu Viết/Nói do giáo viên giao. ai chỉ biết interface này; phân hệ lớp cài đặt,
 * nên ai không phụ thuộc ngược vào lớp.
 */
public interface AssignedAiWork {

    /** Học viên phải là người nhận của bài giao còn hiệu lực và bài phải giao đúng câu này; không thì 404 assignment_not_found. */
    void requireRecipient(long userId, long assignmentId, long questionId);

    /**
     * Khóa dòng bài giao đến hết transaction để hai request của cùng một người không cùng mở hoặc cùng nộp phiên Viết.
     * Gọi sau requireRecipient, trong transaction ghi.
     */
    void lock(long assignmentId);
}
