package com.pps.smartenglish.ai.controller;

import com.pps.smartenglish.ai.service.AiQuotaService;
import com.pps.smartenglish.shared.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/quota")
class QuotaController {

    record QuotaResponse(int balance) {}

    private final AiQuotaService quota;

    QuotaController(AiQuotaService quota) {
        this.quota = quota;
    }

    @GetMapping
    QuotaResponse get(Authentication auth) {
        return new QuotaResponse(quota.balance(CurrentUser.id(auth)));
    }
}
