package com.pps.smartenglish.commerce.dto;

import java.time.Instant;
import java.util.List;

/** Chi tiết đơn cho nhân sự (GET /api/admin/orders/{id}). Cố ý không có raw_payload của cổng thanh toán. */
public record AdminOrderDetail(long id, String code, String status, long totalVnd, String provider, Instant createdAt,
                               Instant expiresAt, Instant paidAt, Buyer buyer, List<Item> items, List<Transaction> transactions) {

    public record Buyer(long id, String email, String displayName) {}

    /** courseTitle null khi sản phẩm không gắn khóa học (gói lượt AI). */
    public record Item(String productName, String productType, String courseTitle, long priceVnd) {}

    public record Transaction(Instant receivedAt, String provider, String providerRef, long amountVnd, String status) {}
}
