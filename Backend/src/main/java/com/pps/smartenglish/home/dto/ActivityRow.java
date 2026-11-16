package com.pps.smartenglish.home.dto;

import java.time.Instant;

/** Hoạt động gần nhất của một khóa (kết quả truy vấn nhóm theo khóa). */
public record ActivityRow(Long courseId, Instant at) {}
