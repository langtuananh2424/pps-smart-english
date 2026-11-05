package com.pps.smartenglish.commerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pps.smartenglish.commerce.dto.OrderCreated.PaymentInfo;
import java.time.Instant;
import java.util.List;

/** GET /orders/{code}; payment chỉ có khi đơn còn PENDING, paidAt chỉ có khi đã PAID. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderView(String code, String status, long totalVnd, Instant paidAt, Instant expiresAt,
                        List<ItemView> items, PaymentInfo payment) {

    public record ItemView(Long productId, String name, long priceVnd) {}
}
