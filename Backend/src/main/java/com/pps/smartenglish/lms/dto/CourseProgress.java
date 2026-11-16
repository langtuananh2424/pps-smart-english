package com.pps.smartenglish.lms.dto;

/** Tiến độ học của một khóa (D-46): bài hoàn thành / bài đã xuất bản; percent làm tròn xuống. */
public record CourseProgress(long completedLessons, long totalLessons, int percent) {}
