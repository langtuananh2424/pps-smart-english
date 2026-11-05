package com.pps.smartenglish.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(@NotBlank(message = "token là bắt buộc") String token) {}
