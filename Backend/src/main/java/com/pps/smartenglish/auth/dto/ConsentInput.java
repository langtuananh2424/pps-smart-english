package com.pps.smartenglish.auth.dto;

import java.time.LocalDate;

/** Phần chấp thuận khi tạo tài khoản; dưới 18 tuổi thì guardianConsent là ô tick của phụ huynh (D-129). */
public record ConsentInput(LocalDate dateOfBirth, Boolean termsAccepted, Boolean guardianConsent) {}
