package com.pps.smartenglish.shared.event;

/** Phát khi một job AI gắn lượt làm bài kết thúc hẳn (DONE hoặc FAILED đã hoàn lượt). D-149: retention tự kiểm lượt đã xong hết chưa. */
public record AttemptGradingSettled(long attemptId, long userId) {}
