package com.pps.smartenglish.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email là bắt buộc") @Email(message = "Email không hợp lệ") String email) {

    public ForgotPasswordRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
