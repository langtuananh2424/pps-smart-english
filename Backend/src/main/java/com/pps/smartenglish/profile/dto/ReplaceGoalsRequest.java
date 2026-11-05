package com.pps.smartenglish.profile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReplaceGoalsRequest(
        @NotNull(message = "Danh sách mục tiêu là bắt buộc")
        @Size(max = 20, message = "Tối đa 20 mục tiêu")
        List<@Valid @NotNull(message = "Mục tiêu không được rỗng") GoalInput> goals) {}
