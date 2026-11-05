package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.auth.dto.AuthResponse;

/** Body trả cho client; refresh token đi riêng vào cookie. */
public record LoginResult(AuthResponse body, String refreshToken) {}
