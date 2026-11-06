package com.pps.smartenglish.profile.controller;

import com.pps.smartenglish.profile.dto.GoalResponse;
import com.pps.smartenglish.profile.dto.MeResponse;
import com.pps.smartenglish.profile.dto.ReplaceGoalsRequest;
import com.pps.smartenglish.profile.dto.UpdateMeRequest;
import com.pps.smartenglish.profile.service.ProfileService;
import com.pps.smartenglish.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
class ProfileController {

    private final ProfileService service;

    ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    MeResponse me(Authentication auth) {
        return service.me(CurrentUser.id(auth));
    }

    @PatchMapping
    MeResponse update(Authentication auth, @Valid @RequestBody UpdateMeRequest req) {
        return service.update(CurrentUser.id(auth), req);
    }

    @GetMapping("/goals")
    List<GoalResponse> goals(Authentication auth) {
        return service.goals(CurrentUser.id(auth));
    }

    @PutMapping("/goals")
    List<GoalResponse> replaceGoals(Authentication auth, @Valid @RequestBody ReplaceGoalsRequest req) {
        return service.replaceGoals(CurrentUser.id(auth), req.goals());
    }
}
