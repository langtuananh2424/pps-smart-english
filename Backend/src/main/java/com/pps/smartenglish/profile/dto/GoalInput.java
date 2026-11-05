package com.pps.smartenglish.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record GoalInput(
        Long courseId,
        @NotBlank(message = "Loại mục tiêu là bắt buộc") @Size(max = 100, message = "Tối đa 100 ký tự") String targetType,
        @NotBlank(message = "Giá trị mục tiêu là bắt buộc") @Size(max = 100, message = "Tối đa 100 ký tự") String targetValue,
        LocalDate targetDate) {}
