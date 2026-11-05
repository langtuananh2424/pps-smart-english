package com.pps.smartenglish.profile.dto;

import com.pps.smartenglish.profile.domain.SelfLevel;

public record MeResponse(Long id, String email, boolean emailVerified, String role, String displayName,
                         SelfLevel selfLevel, String timezone) {}
