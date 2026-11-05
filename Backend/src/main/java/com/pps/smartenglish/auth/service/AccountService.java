package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.auth.dto.AccountInfo;
import com.pps.smartenglish.auth.repository.UserRepository;
import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Cửa vào dữ liệu tài khoản cho module khác (không đụng repository auth). */
@Service
public class AccountService {

    private final UserRepository users;

    AccountService(UserRepository users) {
        this.users = users;
    }

    public AccountInfo get(long userId) {
        return users.findById(userId)
                .map(u -> new AccountInfo(u.getId(), u.getEmail(), u.getEmailVerifiedAt() != null, u.getRole().name()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Không tìm thấy tài khoản"));
    }
}
