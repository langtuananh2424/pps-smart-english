package com.pps.smartenglish.commerce.controller;

import com.pps.smartenglish.commerce.dto.OrderSummary;
import com.pps.smartenglish.commerce.service.MyOrderService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.CursorPage;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/orders")
class MyOrderController {

    private final MyOrderService service;

    MyOrderController(MyOrderService service) {
        this.service = service;
    }

    @GetMapping
    CursorPage<OrderSummary> list(Authentication auth, @RequestParam(required = false) String limit,
                                  @RequestParam(required = false) String cursor) {
        return service.list(CurrentUser.id(auth), limit, cursor);
    }
}
