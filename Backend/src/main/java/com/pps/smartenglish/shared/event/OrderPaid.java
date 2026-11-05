package com.pps.smartenglish.shared.event;

/**
 * Phát trong transaction kích hoạt đơn; ai lắng nghe để cộng lượt AI (aiQuota = tổng lượt các sản phẩm trong đơn)
 * nên commerce không phải biết cách ai ghi số dư.
 */
public record OrderPaid(long orderId, long userId, int aiQuota) {}
