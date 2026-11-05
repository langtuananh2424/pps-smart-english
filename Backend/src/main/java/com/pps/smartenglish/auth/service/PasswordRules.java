package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.shared.error.ApiException;
import java.nio.charset.StandardCharsets;

final class PasswordRules {

    static final int BCRYPT_MAX_BYTES = 72;

    private PasswordRules() {}

    static boolean tooLong(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }

    /** BCrypt bỏ qua byte từ 73 trở đi; từ chối thay vì lặng lẽ cắt phần đuôi. */
    static void check(String password, String field) {
        if (tooLong(password)) {
            throw ApiException.validation(field, "Mật khẩu tối đa 72 byte");
        }
    }
}
