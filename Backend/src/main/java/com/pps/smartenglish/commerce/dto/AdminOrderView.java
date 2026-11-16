package com.pps.smartenglish.commerce.dto;

import java.time.Instant;

/** Một dòng danh sách đơn cho nhân sự (GET /api/admin/orders). paidAt null khi chưa thanh toán. */
public record AdminOrderView(long id, String code, String status, long totalVnd, Instant paidAt, Instant createdAt,
                             String email, String displayName, String provider) {}
