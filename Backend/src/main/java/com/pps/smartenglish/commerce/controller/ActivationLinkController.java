package com.pps.smartenglish.commerce.controller;

import com.pps.smartenglish.commerce.dto.ActivationLinkDtos.Preview;
import com.pps.smartenglish.commerce.dto.ActivationLinkDtos.Redeemed;
import com.pps.smartenglish.commerce.service.ActivationLinkService;
import com.pps.smartenglish.shared.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activation-links")
class ActivationLinkController {

    private final ActivationLinkService service;

    ActivationLinkController(ActivationLinkService service) {
        this.service = service;
    }

    /** Công khai: người nhận link xem trước gói trước khi đăng nhập. */
    @GetMapping("/{token}")
    Preview preview(@PathVariable String token) {
        return service.preview(token);
    }

    @PostMapping("/{token}/redeem")
    Redeemed redeem(Authentication auth, @PathVariable String token) {
        return service.redeem(CurrentUser.id(auth), token);
    }
}
