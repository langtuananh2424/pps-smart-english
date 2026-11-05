package com.pps.smartenglish.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "token là bắt buộc") String token,
        @NotBlank(message = "Mật khẩu là bắt buộc") @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự") String newPassword) {}
