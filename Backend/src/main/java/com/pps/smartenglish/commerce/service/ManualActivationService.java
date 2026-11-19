package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.commerce.domain.ShopOrder;
import com.pps.smartenglish.commerce.repository.OrderRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.event.OrderPaid;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-SUP-03: nhân viên hỗ trợ kích hoạt thủ công đơn đã trả tiền nhưng webhook lỗi. Cùng đường kích hoạt với webhook
 * (markPaid có điều kiện rồi cấp quyền và phát OrderPaid) nên không thể kích hoạt hai lần. Đơn đã hủy không kích hoạt được.
 */
@Service
public class ManualActivationService {

    private final OrderRepository orders;
    private final ActivationService activation;
    private final ApplicationEventPublisher events;
    private final AuditRecorder audit;
    private final Clock clock;

    ManualActivationService(OrderRepository orders, ActivationService activation, ApplicationEventPublisher events,
                            AuditRecorder audit, Clock clock) {
        this.orders = orders;
        this.activation = activation;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    /** Trả id học viên của đơn. */
    @Transactional
    public long activate(String orderCode, String reason) {
        ShopOrder order = orders.findByCode(orderCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "order_not_found", "Không tìm thấy đơn hàng"));
        String before = order.getStatus();
        if (ShopOrder.CANCELLED.equals(before)) {
            throw new ApiException(HttpStatus.CONFLICT, "order_cancelled", "Đơn đã hủy, không kích hoạt được");
        }
        Instant now = Instant.now(clock);
        long orderId = order.getId();
        long userId = order.getUserId();
        if (orders.markPaid(orderId, now) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "order_already_paid", "Đơn đã được thanh toán và kích hoạt");
        }
        int aiQuota = activation.activate(orderId, userId, now);
        events.publishEvent(new OrderPaid(orderId, userId, aiQuota));
        audit.record("MANUAL_ACTIVATE", "ORDER", orderCode, Map.of("status", before),
                Map.of("status", ShopOrder.PAID, "userId", userId, "reason", reason));
        return userId;
    }
}
