package com.pps.smartenglish.shared.event;

/** Phát khi job AI chuyển DONE. kind là tên AiJobKind (chuỗi để shared không phụ thuộc module ai). */
public record AiGradingFinished(long jobId, long userId, String kind) {}
