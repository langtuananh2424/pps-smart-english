package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.dto.CheckDtos.RetryState;

/**
 * mode (D-151): chế độ của lượt (PRACTICE/EXAM).
 * Trạng thái làm dở: đồng hồ (null nếu không giới hạn) và đáp án đã lưu để khôi phục khi mở lại. Không có đáp án đúng.
 * retry (D-150): chỉ có ở lượt áp dụng thử lại kèm gợi ý; vắng thì web không hiện nút Kiểm tra.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttemptProgress(Long attemptId, Integer timeLimitSec, Long remainingSec, boolean paused, JsonNode answers, String mode,
                              RetryState retry) {}
