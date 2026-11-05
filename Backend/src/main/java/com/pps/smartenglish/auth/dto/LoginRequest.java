package com.pps.smartenglish.auth.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

/** identifier là email (số điện thoại chưa hỗ trợ ở MVP). */
public record LoginRequest(
        @NotBlank(message = "Email là bắt buộc") String identifier,
        @NotBlank(message = "Mật khẩu là bắt buộc") String password) {

    public LoginRequest {
        identifier = identifier == null ? null : identifier.trim().toLowerCase(Locale.ROOT);
    }
}
