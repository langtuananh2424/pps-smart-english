package com.pps.smartenglish.exercise.dto;

/** Bộ câu hỏi cùng cờ học thử của bank và topic cha; nội bộ, dùng để quyết định quyền. */
public record BankInfo(Long id, String title, Long lessonId, boolean trial, boolean topicFreeTrial) {}
