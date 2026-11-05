package com.pps.smartenglish.commerce.controller;

import com.pps.smartenglish.commerce.dto.CreateOrderRequest;
import com.pps.smartenglish.commerce.dto.OrderCreated;
import com.pps.smartenglish.commerce.dto.OrderView;
import com.pps.smartenglish.commerce.service.OrderService;
import com.pps.smartenglish.shared.idempotency.IdempotencyService;
import com.pps.smartenglish.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    ResponseEntity<OrderCreated> create(Authentication auth,
                                        @RequestHeader(value = IdempotencyService.HEADER, required = false) String key,
                                        @Valid @RequestBody CreateOrderRequest req) {
        var outcome = orders.create(CurrentUser.id(auth), key, req);
        return ResponseEntity.status(outcome.status()).body(outcome.body());
    }

    @GetMapping("/{code}")
    OrderView get(Authentication auth, @PathVariable String code) {
        return orders.get(CurrentUser.id(auth), code);
    }
}
