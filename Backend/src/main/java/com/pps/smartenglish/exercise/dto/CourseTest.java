package com.pps.smartenglish.exercise.dto;

import com.pps.smartenglish.exercise.dto.ExamStatus.LastResult;

/**
 * Một bài kiểm tra của khóa (D-118): kind CHAPTER (kiểm tra chương, gắn topicId) hoặc FINAL (cuối khóa, topicId null).
 * reason null khi unlocked; nếu không: ENROLLMENT_REQUIRED, UNIT_LOCKED, PROGRESS, ATTEMPTS_EXHAUSTED, NOT_CONFIGURED.
 */
public record CourseTest(String kind, Long topicId, String title, Long bankId, boolean unlocked, String reason,
                         long attemptsUsed, int attemptsAllowed, Long inProgressAttemptId, LastResult lastResult) {}
