package com.pps.smartenglish.suggestion.controller;

import com.pps.smartenglish.profile.dto.QaDtos.QaRequest;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.ClientIp;
import com.pps.smartenglish.suggestion.dto.SuggestionDtos.CourseSuggestion;
import com.pps.smartenglish.suggestion.service.CourseSuggestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** FR-GOAL-03, D-56. */
@RestController
class SuggestionController {

    private final CourseSuggestionService service;

    SuggestionController(CourseSuggestionService service) {
        this.service = service;
    }

    @GetMapping("/api/me/course-suggestions")
    CourseSuggestion mine(Authentication auth) {
        return service.forUser(CurrentUser.id(auth));
    }

    /** Công khai: khách chưa đăng nhập làm Q&A xem gợi ý, không lưu gì. */
    @PostMapping("/api/catalog/course-suggestions")
    CourseSuggestion guest(@Valid @RequestBody QaRequest req, HttpServletRequest http) {
        return service.forGuest(req, ClientIp.of(http));
    }
}
