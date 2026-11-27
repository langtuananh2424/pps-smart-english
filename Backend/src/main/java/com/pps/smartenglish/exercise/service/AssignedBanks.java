package com.pps.smartenglish.exercise.service;

/**
 * FR-ASG-01: bộ câu hỏi đang được giáo viên giao cho học viên thì mở riêng bộ đó dù unit chưa mở (học tuần tự). exercise chỉ biết
 * interface này; phân hệ lớp cài đặt, nên exercise không phụ thuộc ngược vào lớp.
 */
public interface AssignedBanks {

    boolean isAssigned(long userId, long bankId);

    /** D-153: người nhận bài giao bộ này ở mọi trạng thái (kể cả đã hủy): chỉ dùng cho đường đọc. */
    boolean isAssignedAnyStatus(long userId, long bankId);

    /** D-155: học viên là người nhận bài giao còn hiệu lực của bộ này với chế độ Thi (chỉ để ẩn gợi ý ở GET bộ). */
    boolean isExamAssigned(long userId, long bankId);

    /** D-153: lượt của bài giao đã hủy không lưu nháp, kiểm câu hay nộp được nữa: 409 assignment_cancelled. null = lượt tự học. */
    void requireActive(Long assignmentId);
}
