package com.pps.smartenglish.commerce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.commerce.domain.PaymentTransaction;
import com.pps.smartenglish.commerce.domain.PaymentTransaction.Status;
import com.pps.smartenglish.commerce.domain.ShopOrder;
import com.pps.smartenglish.commerce.payment.PaymentGateway.PaidEvent;
import com.pps.smartenglish.commerce.repository.OrderRepository;
import com.pps.smartenglish.commerce.repository.PaymentTransactionRepository;
import com.pps.smartenglish.shared.event.OrderPaid;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xử lý một giao dịch thành công trong MỘT transaction (FR-COM-02: kích hoạt đồng bộ trong request webhook).
 * Trùng provider_ref ném DataIntegrityViolationException khi ghi nhật ký; transaction rollback và WebhookService
 * coi đó là webhook trùng.
 */
@Component
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final OrderRepository orders;
    private final PaymentTransactionRepository transactions;
    private final ActivationService activation;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PaymentProcessor(OrderRepository orders, PaymentTransactionRepository transactions, ActivationService activation,
                     ApplicationEventPublisher events, Clock clock) {
        this.orders = orders;
        this.transactions = transactions;
        this.activation = activation;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ProcessOutcome process(String provider, PaidEvent paid, JsonNode raw) {
        if (transactions.existsByProviderAndProviderRef(provider, paid.reference())) {
            return ProcessOutcome.DUPLICATE;
        }
        Instant now = Instant.now(clock);
        var order = orders.findByCode(paid.orderCode()).orElse(null);
        if (order == null) {
            log.warn("Webhook {} ref {}: không tìm thấy đơn {}", provider, paid.reference(), paid.orderCode());
            return record(null, provider, paid, Status.ORDER_NOT_FOUND, raw, now, ProcessOutcome.ORDER_NOT_FOUND);
        }
        if (paid.amountVnd() != order.getTotalVnd()) {
            log.warn("Webhook {} ref {}: số tiền lệch cho đơn {} (nhận {}, cần {}), cần Admin xử lý",
                    provider, paid.reference(), order.getCode(), paid.amountVnd(), order.getTotalVnd());
            return record(order.getId(), provider, paid, Status.MISMATCH, raw, now, ProcessOutcome.MISMATCH);
        }
        if (ShopOrder.CANCELLED.equals(order.getStatus())) {
            log.warn("Webhook {} ref {}: đơn {} đã hủy nhưng tiền đã về, cần Admin xử lý",
                    provider, paid.reference(), order.getCode());
            return record(order.getId(), provider, paid, Status.ORDER_CANCELLED, raw, now, ProcessOutcome.ORDER_CANCELLED);
        }
        long orderId = order.getId();
        long userId = order.getUserId();
        if (orders.markPaid(orderId, now) == 0) {
            log.warn("Webhook {} ref {}: đơn {} đã được thanh toán trước đó, giao dịch này là thừa",
                    provider, paid.reference(), order.getCode());
            return record(orderId, provider, paid, Status.ORDER_ALREADY_PAID, raw, now, ProcessOutcome.ORDER_ALREADY_PAID);
        }
        int aiQuota = activation.activate(orderId, userId, now);
        events.publishEvent(new OrderPaid(orderId, userId, aiQuota));
        return record(orderId, provider, paid, Status.APPLIED, raw, now, ProcessOutcome.APPLIED);
    }

    private ProcessOutcome record(Long orderId, String provider, PaidEvent paid, Status status, JsonNode raw, Instant now,
                                  ProcessOutcome outcome) {
        transactions.save(new PaymentTransaction(orderId, provider, paid.reference(), paid.amountVnd(), status, raw, now));
        return outcome;
    }
}
