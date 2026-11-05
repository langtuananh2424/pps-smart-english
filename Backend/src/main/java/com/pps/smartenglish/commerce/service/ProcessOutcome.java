package com.pps.smartenglish.commerce.service;

/** Kết quả xử lý một webhook; mọi giá trị đều trả 200 cho cổng (trừ sai chữ ký). */
public enum ProcessOutcome {
    APPLIED,
    /** Payload hợp lệ nhưng không phải thanh toán thành công. */
    IGNORED,
    DUPLICATE,
    MISMATCH,
    ORDER_NOT_FOUND,
    ORDER_CANCELLED,
    ORDER_ALREADY_PAID
}
