package com.pps.smartenglish.home.dto;

/** Số câu theo (khóa, dạng): đã nộp/đúng hoặc số câu hiển thị tùy truy vấn. */
public record TypeStatRow(Long courseId, String type, long total, long correct) {}
