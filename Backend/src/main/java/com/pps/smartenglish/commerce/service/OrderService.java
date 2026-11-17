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
import com.pps.smartenglish.commerce.dto.OrderView.CourseRef;
import com.pps.smartenglish.commerce.dto.OrderView.ItemView;
import com.pps.smartenglish.commerce.repository.EnrollmentRepository;
import com.pps.smartenglish.commerce.repository.OrderItemRepository;
import com.pps.smartenglish.commerce.repository.OrderRepository;
import com.pps.smartenglish.commerce.repository.ProductRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.idempotency.IdempotencyService;
import com.pps.smartenglish.shared.idempotency.IdempotencyService.Outcome;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final EnrollmentRepository enrollments;
    private final Clock clock;
    private final Duration ttl;
    private final boolean simulatable;

    OrderService(EmailVerification emailVerification, ProductRepository products, OrderRepository orders, OrderItemRepository items,
                 PaymentGatewayRegistry gateways, IdempotencyService idempotency, OrderCodeGenerator codes,
                 EnrollmentRepository enrollments, Clock clock, @Value("${app.commerce.order-ttl-minutes:30}") long ttlMinutes,
                 @Value("${app.payment.fake-enabled:false}") boolean simulatable) {
        this.emailVerification = emailVerification;
        this.products = products;
        this.orders = orders;
        this.items = items;
        this.gateways = gateways;
        this.idempotency = idempotency;
        this.codes = codes;
        this.enrollments = enrollments;
        this.clock = clock;
        this.simulatable = simulatable;
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

    /**
     * Chạy trong transaction của IdempotencyService: đơn, các dòng và link thanh toán cùng thành công hoặc cùng hủy.
     * Khóa dòng user trước khi tìm đơn chờ để hai request đồng thời (khác Idempotency-Key) không tạo hai đơn (D-77).
     */
    private OrderCreated place(long userId, List<Long> ids, PaymentGateway gateway) {
        enrollments.lockUser(userId);
        var reusable = findReusable(userId, new HashSet<>(ids));
        if (reusable != null) {
            return new OrderCreated(reusable.getCode(), reusable.getStatus(), reusable.getTotalVnd(),
                    new PaymentInfo(reusable.getQrContent(), reusable.getExpiresAt(), simulatable));
        }
        List<Product> chosen = products.findWithLevels(ids);
        if (chosen.size() != ids.size() || chosen.stream().anyMatch(p -> !p.isActive())) {
            throw ApiException.validation("productIds", "Sản phẩm không tồn tại hoặc đã ngừng bán");
        }
        long total = chosen.stream().mapToLong(Product::getPriceVnd).sum();
        // cắt về micro giây như cột DB để lần mở lại đơn trả đúng giá trị cũ
        Instant expiresAt = Instant.now(clock).plus(ttl).truncatedTo(ChronoUnit.MICROS);
        var order = orders.save(new ShopOrder(userId, uniqueCode(), total, expiresAt));
        items.saveAll(chosen.stream().map(p -> new OrderItem(order.getId(), p.getId(), p.getPriceVnd())).toList());
        var link = gateway.createPaymentLink(
                new LinkRequest(order.getCode(), total, transferContent(order.getCode()), expiresAt));
        order.attachPayment(gateway.providerCode(), link.qrContent());
        return new OrderCreated(order.getCode(), order.getStatus(), total, new PaymentInfo(link.qrContent(), expiresAt, simulatable));
    }

    private ShopOrder findReusable(long userId, Set<Long> productIds) {
        for (var o : orders.findLivePending(userId, Instant.now(clock))) {
            if (items.findByOrderId(o.getId()).stream().map(OrderItem::getProductId).collect(Collectors.toSet()).equals(productIds)) {
                return o;
            }
        }
        return null;
    }

    private static String transferContent(String code) {
        return "Thanh toan " + code;
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
        String status = order.effectiveStatus(Instant.now(clock));
        boolean pending = ShopOrder.PENDING.equals(status);
        return new OrderView(order.getCode(), status, order.getTotalVnd(), order.getPaidAt(), order.getExpiresAt(),
                withCourses(items.findViews(order.getId())),
                pending ? new PaymentInfo(order.getQrContent(), order.getExpiresAt(), simulatable) : null,
                pending ? transferContent(order.getCode()) : null);
    }

    /** Gắn khóa học của từng gói (product, level, course); gói lượt AI không có khóa. */
    private List<ItemView> withCourses(List<ItemView> views) {
        Map<Long, CourseRef> byProduct = new HashMap<>();
        for (Object[] r : items.findCourses(views.stream().map(ItemView::productId).toList())) {
            byProduct.putIfAbsent((Long) r[0], new CourseRef((Long) r[1], (String) r[2], (String) r[3]));
        }
        return views.stream().map(v -> v.withCourse(byProduct.get(v.productId()))).toList();
    }
}
