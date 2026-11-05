package com.pps.smartenglish.exercise.dto;

import java.time.Instant;

public record AttemptSummary(Long id, Long bankId, Instant startedAt, Instant submittedAt, Integer score, Integer maxScore) {}
