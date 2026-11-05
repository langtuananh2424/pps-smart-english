package com.pps.smartenglish.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record RegisterRequest(
        @NotBlank(message = "Email là bắt buộc") @Email(message = "Email không hợp lệ")
        @Size(max = 255, message = "Email tối đa 255 ký tự") String email,
        @NotBlank(message = "Mật khẩu là bắt buộc") @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự") String password,
        @NotBlank(message = "Tên hiển thị là bắt buộc") @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự") String displayName) {

    public RegisterRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        displayName = displayName == null ? null : displayName.trim();
    }
}
