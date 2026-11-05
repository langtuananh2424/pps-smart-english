package com.pps.smartenglish.commerce.controller;

import com.pps.smartenglish.commerce.domain.ShopOrder;
import com.pps.smartenglish.commerce.dto.OrderView;
import com.pps.smartenglish.commerce.payment.FakePaymentGateway;
import com.pps.smartenglish.commerce.repository.OrderRepository;
import com.pps.smartenglish.commerce.service.OrderService;
import com.pps.smartenglish.commerce.service.WebhookService;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.security.CurrentUser;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Giả lập "đã chuyển khoản" cho dev/test; chỉ tồn tại khi cổng giả bật, và đi qua đúng luồng webhook thật. */
@RestController
@RequestMapping("/api/dev/payments")
@ConditionalOnProperty(prefix = "app.payment", name = "fake-enabled", havingValue = "true")
class DevPaymentController {

    private final OrderRepository orders;
    private final OrderService orderService;
    private final FakePaymentGateway gateway;
    private final WebhookService webhooks;

    DevPaymentController(OrderRepository orders, OrderService orderService, FakePaymentGateway gateway, WebhookService webhooks) {
        this.orders = orders;
        this.orderService = orderService;
        this.gateway = gateway;
        this.webhooks = webhooks;
    }

    @PostMapping("/{code}/pay")
    OrderView pay(Authentication auth, @PathVariable String code) {
        orders.findByCodeAndUserId(code, CurrentUser.id(auth))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "order_not_found", "Không tìm thấy đơn hàng"));
        var order = orders.findByCode(code).orElseThrow();
        webhooks.handle(FakePaymentGateway.PROVIDER, gateway.signedPaid(code, order.getTotalVnd(), "DEV-" + UUID.randomUUID()));
        return orderService.view(orders.findByCode(code).orElseThrow());
    }
}
