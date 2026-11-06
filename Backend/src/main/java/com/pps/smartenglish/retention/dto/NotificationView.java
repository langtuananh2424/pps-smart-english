package com.pps.smartenglish.retention.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** payload là {title, body, url}. readAt null = chưa đọc. */
public record NotificationView(Long id, String type, JsonNode payload, Instant createdAt, Instant readAt) {}
