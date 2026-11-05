package com.pps.smartenglish.exercise.dto;

/** Bộ câu hỏi cùng cờ học thử của bank, topic cha và Level chứa nó; nội bộ, dùng để quyết định quyền. */
public record BankInfo(Long id, String title, Long lessonId, boolean trial, boolean topicFreeTrial, Long levelId) {}
