package com.pps.smartenglish.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** refreshToken chỉ có khi client gửi X-Client: mobile (D-94); web nhận qua cookie nên trường này vắng. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(String accessToken, long expiresIn, UserSummary user, String refreshToken) {

    public AuthResponse(String accessToken, long expiresIn, UserSummary user) {
        this(accessToken, expiresIn, user, null);
    }

    public AuthResponse withRefreshToken(String token) {
        return new AuthResponse(accessToken, expiresIn, user, token);
    }

    public record UserSummary(Long id, String displayName, String role) {}
}
