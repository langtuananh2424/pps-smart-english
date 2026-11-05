package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.auth.repository.UserRepository;
import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Mua hàng và kích hoạt link đều yêu cầu email đã xác thực (system-design 5.1). */
@Component
class EmailVerification {

    private final UserRepository users;

    EmailVerification(UserRepository users) {
        this.users = users;
    }

    void require(long userId) {
        boolean verified = users.findById(userId).map(u -> u.getEmailVerifiedAt() != null).orElse(false);
        if (!verified) {
            throw new ApiException(HttpStatus.FORBIDDEN, "email_not_verified", "Hãy xác thực email trước khi mua hàng hoặc kích hoạt");
        }
    }
}
