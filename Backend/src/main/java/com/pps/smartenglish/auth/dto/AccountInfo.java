package com.pps.smartenglish.auth.dto;

/** Phần tài khoản (không phải hồ sơ) mà module khác được phép thấy. */
public record AccountInfo(Long id, String email, boolean emailVerified, String role) {}
