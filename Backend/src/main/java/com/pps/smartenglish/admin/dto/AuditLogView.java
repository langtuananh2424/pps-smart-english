package com.pps.smartenglish.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** actorEmail/actorName null nếu tài khoản người thao tác không còn hoặc chưa có hồ sơ. */
public record AuditLogView(Long id, Long actorId, String actorEmail, String actorName, String action, String targetType,
                           String targetId, JsonNode before, JsonNode after, String reason, Instant createdAt) {}
