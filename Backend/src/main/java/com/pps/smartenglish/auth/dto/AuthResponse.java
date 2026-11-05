package com.pps.smartenglish.auth.dto;

public record AuthResponse(String accessToken, long expiresIn, UserSummary user) {

    public record UserSummary(Long id, String displayName, String role) {}
}
