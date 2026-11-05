package com.pps.smartenglish.commerce.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.commerce.service.ProcessOutcome;
import com.pps.smartenglish.commerce.service.WebhookService;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-COM-02: công khai, xác thực bằng chữ ký của cổng thanh toán. */
@RestController
@RequestMapping("/api/payments/webhook")
class WebhookController {

    private final WebhookService webhooks;

    WebhookController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping("/{provider}")
    Map<String, ProcessOutcome> receive(@PathVariable String provider, @RequestBody JsonNode body) {
        return Map.of("result", webhooks.handle(provider, body));
    }
}
