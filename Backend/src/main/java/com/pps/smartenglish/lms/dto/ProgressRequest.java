package com.pps.smartenglish.lms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProgressRequest(
        @NotNull(message = "Bắt buộc") @PositiveOrZero(message = "Không được âm") Integer positionSec,
        @NotNull(message = "Bắt buộc") @PositiveOrZero(message = "Không được âm") Integer watchedDeltaSec) {}
