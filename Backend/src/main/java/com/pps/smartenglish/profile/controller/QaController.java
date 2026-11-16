package com.pps.smartenglish.profile.controller;

import com.pps.smartenglish.profile.dto.QaDtos.QaRequest;
import com.pps.smartenglish.profile.dto.QaDtos.QaView;
import com.pps.smartenglish.profile.service.QaService;
import com.pps.smartenglish.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-GOAL-01, D-55. */
@RestController
@RequestMapping("/api/me/qa")
class QaController {

    private final QaService service;

    QaController(QaService service) {
        this.service = service;
    }

    @PutMapping
    QaView save(Authentication auth, @Valid @RequestBody QaRequest req) {
        return service.save(CurrentUser.id(auth), req);
    }

    /** 204 khi chưa làm Q&A. */
    @GetMapping
    ResponseEntity<QaView> get(Authentication auth) {
        return service.find(CurrentUser.id(auth)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
