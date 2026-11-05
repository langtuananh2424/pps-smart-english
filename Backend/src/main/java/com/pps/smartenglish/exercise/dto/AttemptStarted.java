package com.pps.smartenglish.exercise.dto;

import java.time.Instant;

public record AttemptStarted(Long id, Long bankId, Instant startedAt) {}
