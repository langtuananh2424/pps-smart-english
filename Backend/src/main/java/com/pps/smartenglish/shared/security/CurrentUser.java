package com.pps.smartenglish.shared.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

public final class CurrentUser {

    private CurrentUser() {}

    /** JWT sub = userId; endpoint đã qua bộ lọc xác thực nên luôn có giá trị. */
    public static long id(Authentication auth) {
        return Long.parseLong(auth.getName());
    }

    /** Endpoint công khai: null khi khách chưa đăng nhập (token ẩn danh). */
    public static Long idOrNull(Authentication auth) {
        return auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated() ? null : id(auth);
    }
}
