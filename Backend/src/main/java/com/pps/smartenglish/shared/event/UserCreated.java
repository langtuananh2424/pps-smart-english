package com.pps.smartenglish.shared.event;

/**
 * Phát trong transaction tạo tài khoản; profile và ai lắng nghe để tạo dữ liệu riêng,
 * nên auth không phải biết các module đó khi tạo user.
 */
public record UserCreated(long userId, String displayName) {}
