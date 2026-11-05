package com.pps.smartenglish.auth.dto;

import java.time.Instant;

public record SessionResponse(Long id, String deviceLabel, Instant createdAt, Instant expiresAt) {}
