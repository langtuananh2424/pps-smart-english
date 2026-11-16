package com.pps.smartenglish.exercise.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AttemptSummary(Long id, Long bankId, Instant startedAt, Instant submittedAt, BigDecimal score, Integer maxScore) {}
