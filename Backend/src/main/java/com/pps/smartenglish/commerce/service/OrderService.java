package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.commerce.domain.OrderItem;
import com.pps.smartenglish.commerce.domain.Product;
import com.pps.smartenglish.commerce.domain.ShopOrder;
import com.pps.smartenglish.commerce.dto.CreateOrderRequest;
import com.pps.smartenglish.commerce.dto.OrderCreated;
import com.pps.smartenglish.commerce.dto.OrderCreated.PaymentInfo;
import com.pps.smartenglish.commerce.dto.OrderView;
import com.pps.smartenglish.commerce.payment.PaymentGateway;
import com.pps.smartenglish.commerce.payment.PaymentGateway.LinkRequest;
import com.pps.smartenglish.commerce.payment.PaymentGatewayRegistry;
import com.pps.smartenglish.commerce.repository.OrderItemRepository;
import com.pps.smartenglish.commerce.repository.OrderRepository;
import com.pps.smartenglish.commerce.repository.ProductRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.idempotency.IdempotencyService;
import com.pps.smartenglish.shared.idempotency.IdempotencyService.Outcome;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-COM-01 (tạo đơn, chốt giá) và FR-COM-02 (xem đơn). */
@Service
public class OrderService {

    private final EmailVerification emailVerification;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentGatewayRegistry gateways;
    private final IdempotencyService idempotency;
    private final OrderCodeGenerator codes;
    private final Clock clock;
    private final Duration ttl;

    OrderService(EmailVerification emailVerification, ProductRepository products, OrderRepository orders, OrderItemRepository items,
                 PaymentGatewayRegistry gateways, IdempotencyService idempotency, OrderCodeGenerator codes, Clock clock,
                 @Value("${app.commerce.order-ttl-minutes:30}") long ttlMinutes) {
        this.emailVerification = emailVerification;
        this.products = products;
        this.orders = orders;
        this.items = items;
        this.gateways = gateways;
        this.idempotency = idempotency;
        this.codes = codes;
        this.clock = clock;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public Outcome<OrderCreated> create(long userId, String idempotencyKey, CreateOrderRequest req) {
        emailVerification.require(userId);
        var gateway = gateways.active()
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "payment_unavailable",
                        "Chưa có cổng thanh toán khả dụng"));
        List<Long> ids = req.productIds().stream().sorted().toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw ApiException.validation("productIds", "Sản phẩm bị lặp");
        }
        // Băm theo tập sản phẩm đã sắp xếp: [1,2] và [2,1] là cùng một yêu cầu
        String hash = IdempotencyService.sha256(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
        return idempotency.execute(userId, idempotencyKey, hash, HttpStatus.CREATED.value(), OrderCreated.class,
                () -> place(userId, ids, gateway));
    }

    @Transactional(readOnly = true)
    public OrderView get(long userId, String code) {
        var order = orders.findByCodeAndUserId(code, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "order_not_found", "Không tìm thấy đơn hàng"));
        return view(order);
    }

    /** Chạy trong transaction của IdempotencyService: đơn, các dòng và link thanh toán cùng thành công hoặc cùng hủy. */
    private OrderCreated place(long userId, List<Long> ids, PaymentGateway gateway) {
        List<Product> chosen = products.findWithLevels(ids);
        if (chosen.size() != ids.size() || chosen.stream().anyMatch(p -> !p.isActive())) {
            throw ApiException.validation("productIds", "Sản phẩm không tồn tại hoặc đã ngừng bán");
        }
        long total = chosen.stream().mapToLong(Product::getPriceVnd).sum();
        Instant expiresAt = Instant.now(clock).plus(ttl);
        var order = orders.save(new ShopOrder(userId, uniqueCode(), total, expiresAt));
        items.saveAll(chosen.stream().map(p -> new OrderItem(order.getId(), p.getId(), p.getPriceVnd())).toList());
        var link = gateway.createPaymentLink(
                new LinkRequest(order.getCode(), total, "Thanh toan " + order.getCode(), expiresAt));
        order.attachPayment(gateway.providerCode(), link.qrContent());
        return new OrderCreated(order.getCode(), order.getStatus(), total, new PaymentInfo(link.qrContent(), expiresAt));
    }

    /** Nhờ unique(orders.code) làm lưới an toàn cuối; vòng lặp này chỉ để khỏi vấp va chạm hiếm. */
    private String uniqueCode() {
        String code;
        do {
            code = codes.next();
        } while (orders.existsByCode(code));
        return code;
    }

    /** Dùng chung cho GET /orders/{code} và endpoint giả lập thanh toán. */
    @Transactional(readOnly = true)
    public OrderView view(ShopOrder order) {
        boolean pending = ShopOrder.PENDING.equals(order.getStatus());
        return new OrderView(order.getCode(), order.getStatus(), order.getTotalVnd(), order.getPaidAt(), order.getExpiresAt(),
                items.findViews(order.getId()), pending ? new PaymentInfo(order.getQrContent(), order.getExpiresAt()) : null);
    }
}
