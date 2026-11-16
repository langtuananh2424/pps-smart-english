package com.pps.smartenglish.shared.event;

/** Phát khi job AI chuyển FAILED hẳn (đã hoàn lượt). kind là tên AiJobKind. */
public record AiJobFailed(long jobId, String kind) {}
