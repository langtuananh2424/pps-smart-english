package com.pps.smartenglish.classroom.service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một bài nộp của bài giao: lượt làm bài, phiên viết hoặc lượt nói (id theo loại bài giao). score là điểm AI, null khi AI chưa chấm xong;
 * max là thang điểm (số câu của bộ, 100 cho bài Viết, 9 band cho bài Nói).
 */
record AssignmentSubmission(long userId, long id, Instant submittedAt, BigDecimal score, Integer max) {}
