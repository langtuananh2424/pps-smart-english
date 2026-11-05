package com.pps.smartenglish.auth.controller;

import com.pps.smartenglish.auth.dto.AuthResponse;
import com.pps.smartenglish.auth.service.LoginResult;
import com.pps.smartenglish.auth.service.SessionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

/** Dựng cookie refresh và phản hồi đăng nhập ở một chỗ để mọi endpoint phát phiên giống nhau. */
final class RefreshCookies {

    static final String NAME = "refresh_token";

    private RefreshCookies() {}

    static ResponseEntity<AuthResponse> session(HttpStatus status, LoginResult r) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, build(r.refreshToken(), SessionService.REFRESH_TTL.toSeconds()).toString())
                .body(r.body());
    }

    static ResponseCookie cleared() {
        return build("", 0);
    }

    private static ResponseCookie build(String value, long maxAgeSeconds) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true).secure(true).sameSite("Strict").path("/api/auth").maxAge(maxAgeSeconds).build();
    }
}
