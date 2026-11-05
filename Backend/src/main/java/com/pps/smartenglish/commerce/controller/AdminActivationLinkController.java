package com.pps.smartenglish.commerce.controller;

import com.pps.smartenglish.commerce.dto.ActivationLinkDtos.Created;
import com.pps.smartenglish.commerce.dto.ActivationLinkDtos.Revoked;
import com.pps.smartenglish.commerce.dto.ActivationLinkView;
import com.pps.smartenglish.commerce.dto.CreateActivationLinkRequest;
import com.pps.smartenglish.commerce.service.ActivationLinkAdminService;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-COM-04: chỉ ADMIN (SecurityConfig chặn /api/admin/** theo role). */
@RestController
@RequestMapping("/api/admin/activation-links")
class AdminActivationLinkController {

    private final ActivationLinkAdminService service;

    AdminActivationLinkController(ActivationLinkAdminService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Created create(Authentication auth, @Valid @RequestBody CreateActivationLinkRequest req) {
        return service.create(CurrentUser.id(auth), req);
    }

    @GetMapping
    CursorPage<ActivationLinkView> list(@RequestParam(required = false) String limit,
                                        @RequestParam(required = false) String cursor) {
        return service.list(Cursors.size(limit, ActivationLinkAdminService.DEFAULT_LIMIT, ActivationLinkAdminService.MAX_LIMIT), cursor);
    }

    @PostMapping("/{id}/revoke")
    Revoked revoke(@PathVariable String id) {
        return service.revoke(PathId.parse(id, "activation_link_not_found", "Không tìm thấy link kích hoạt"));
    }
}
