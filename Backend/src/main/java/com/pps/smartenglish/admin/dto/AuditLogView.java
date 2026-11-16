package com.pps.smartenglish.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record AuditLogView(Long id, Long actorId, String action, String targetType, String targetId, JsonNode before,
                           JsonNode after, String reason, Instant createdAt) {}
