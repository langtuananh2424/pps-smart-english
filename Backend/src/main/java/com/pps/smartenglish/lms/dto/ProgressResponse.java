package com.pps.smartenglish.lms.dto;

import java.time.Instant;

public record ProgressResponse(Long lessonId, int watchedSec, int maxPositionSec, boolean completed, Instant completedAt) {}
