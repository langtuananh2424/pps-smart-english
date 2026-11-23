package com.pps.smartenglish.auth.dto;

/** Body tùy chọn của refresh/logout cho mobile (không có cookie). */
public record RefreshRequest(String refreshToken) {}
