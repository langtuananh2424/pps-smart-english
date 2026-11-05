package com.pps.smartenglish.lms.dto;

/** Bài đã PUBLISHED (cả khóa) cùng dữ liệu cần để quyết định quyền xem; nội bộ, không trả ra API. */
public record PlayableLesson(Long id, String title, String type, Integer durationSec,
                             String videoSource, String videoRef, boolean freeTrial, Long levelId) {}
