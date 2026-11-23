package com.pps.smartenglish.classroom.controller;

import com.pps.smartenglish.classroom.service.ClassLearningProfileService;
import com.pps.smartenglish.exercise.dto.LearningDtos.Profile;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** FR-EX-09: hồ sơ năng lực học viên cho nhân sự lớp (ClassAccess, NFR-SEC-05). */
@RestController
class ClassLearningProfileController {

    private final ClassLearningProfileService profiles;

    ClassLearningProfileController(ClassLearningProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/classes/{classId}/students/{userId}/learning-profile")
    Profile profile(Authentication auth, @PathVariable String classId, @PathVariable String userId) {
        return profiles.of(CurrentUser.id(auth), PathId.parse(classId, "class_not_found", "Không tìm thấy lớp"),
                PathId.parse(userId, "class_not_found", "Không tìm thấy lớp"));
    }
}
