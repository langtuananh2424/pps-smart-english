package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * mode (D-151): chế độ của lượt (PRACTICE/EXAM).
 * Trạng thái làm dở: đồng hồ (null nếu không giới hạn) và đáp án đã lưu để khôi phục khi mở lại. Không có đáp án đúng.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptProgress(Long attemptId, Integer timeLimitSec, Long remainingSec, boolean paused, JsonNode answers, String mode) {}
