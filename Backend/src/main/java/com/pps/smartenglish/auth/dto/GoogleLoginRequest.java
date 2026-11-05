package com.pps.smartenglish.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(@NotBlank(message = "idToken là bắt buộc") String idToken) {}
