package com.pps.smartenglish.exercise.service;

/**
 * FR-ASG-01: bộ câu hỏi đang được giáo viên giao cho học viên thì mở riêng bộ đó dù unit chưa mở (học tuần tự). exercise chỉ biết
 * interface này; phân hệ lớp cài đặt, nên exercise không phụ thuộc ngược vào lớp.
 */
public interface AssignedBanks {

    boolean isAssigned(long userId, long bankId);
}
