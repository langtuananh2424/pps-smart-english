package com.pps.smartenglish.exercise.controller;

import com.pps.smartenglish.exercise.dto.LearningDtos.Profile;
import com.pps.smartenglish.exercise.service.LearningStatsService;
import com.pps.smartenglish.shared.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-EX-09: hồ sơ năng lực của chính học viên. */
@RestController
class LearningProfileController {

    private final LearningStatsService stats;

    LearningProfileController(LearningStatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/api/me/learning-profile")
    Profile mine(Authentication auth) {
        return stats.profile(CurrentUser.id(auth));
    }
}
