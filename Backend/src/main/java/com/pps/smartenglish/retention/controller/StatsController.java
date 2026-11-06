package com.pps.smartenglish.retention.controller;

import com.pps.smartenglish.profile.dto.StatsResponse;
import com.pps.smartenglish.retention.service.StatsService;
import com.pps.smartenglish.shared.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-RET-01, 02: giữ URL /api/me/stats của profile, giờ có giá trị thật. */
@RestController
@RequestMapping("/api/me/stats")
class StatsController {

    private final StatsService stats;

    StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping
    StatsResponse get(Authentication auth) {
        return stats.stats(CurrentUser.id(auth));
    }
}
