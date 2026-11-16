package com.pps.smartenglish.ai.controller;

import com.pps.smartenglish.ai.service.AiQuotaService;
import com.pps.smartenglish.ai.service.AiQuotaService.Overview;
import com.pps.smartenglish.shared.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-AI-04: pool mua thêm và lượt định kỳ từng khóa còn hạn. */
@RestController
@RequestMapping("/api/me/quota")
class QuotaController {

    private final AiQuotaService quota;

    QuotaController(AiQuotaService quota) {
        this.quota = quota;
    }

    @GetMapping
    Overview get(Authentication auth) {
        return quota.overview(CurrentUser.id(auth));
    }
}
