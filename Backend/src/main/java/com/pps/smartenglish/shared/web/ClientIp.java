package com.pps.smartenglish.shared.web;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {

    private ClientIp() {}

    /** nginx đặt X-Real-IP từ CF-Connecting-IP; không có thì lấy địa chỉ kết nối. */
    public static String of(HttpServletRequest req) {
        String ip = req.getHeader("X-Real-IP");
        return ip == null || ip.isBlank() ? req.getRemoteAddr() : ip.trim();
    }
}
