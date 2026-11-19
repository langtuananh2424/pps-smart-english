package com.pps.smartenglish.auth.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** Ba trường sau chỉ cần khi Google tạo tài khoản mới (FR-AUTH-01, D-114). */
public record GoogleLoginRequest(@NotBlank(message = "idToken là bắt buộc") String idToken,
                                 LocalDate dateOfBirth, Boolean termsAccepted, Boolean guardianConsent) {

    public ConsentInput consent() {
        return new ConsentInput(dateOfBirth, termsAccepted, guardianConsent);
    }
}
