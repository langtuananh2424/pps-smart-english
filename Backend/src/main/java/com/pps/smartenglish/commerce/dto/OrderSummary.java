package com.pps.smartenglish.commerce.dto;

import java.time.Instant;
import java.util.List;

/** Một dòng của GET /me/orders (D-75); status là trạng thái hiệu lực. */
public record OrderSummary(String code, String status, long totalVnd, Instant createdAt, Instant paidAt, Instant expiresAt,
                           List<Item> items) {

    public record Item(Long productId, String name) {}
}
