package com.pps.smartenglish.commerce.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateActivationLinkRequest(
        @NotNull(message = "Bắt buộc") Long productId,
        @NotNull(message = "Bắt buộc") @Min(value = 1, message = "Tối thiểu 1") Integer maxUses,
        @NotNull(message = "Bắt buộc") @Future(message = "Phải ở tương lai") Instant expiresAt,
        @Size(max = 500, message = "Tối đa 500 ký tự") String note) {}
