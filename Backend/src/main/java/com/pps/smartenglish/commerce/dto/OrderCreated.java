package com.pps.smartenglish.commerce.dto;

import java.time.Instant;

/** Phản hồi 201 của POST /orders; cũng là nội dung được lưu để phát lại theo Idempotency-Key. */
public record OrderCreated(String code, String status, long totalVnd, PaymentInfo payment) {

    /** simulatable: cổng giả đang bật, web hiện nút giả lập thanh toán (D-79). */
    public record PaymentInfo(String qrContent, Instant expiresAt, boolean simulatable) {}
}
