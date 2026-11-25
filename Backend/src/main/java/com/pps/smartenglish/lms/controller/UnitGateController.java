package com.pps.smartenglish.lms.controller;

import com.pps.smartenglish.lms.dto.UnitGate;
import com.pps.smartenglish.lms.service.UnitGateService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** FR-LMS-04, D-122, D-124: trạng thái khóa của một unit với chính người gọi. */
@RestController
class UnitGateController {

    private final UnitGateService gate;

    UnitGateController(UnitGateService gate) {
        this.gate = gate;
    }

    @GetMapping("/api/me/courses/{courseId}/units/{topicId}/gate")
    UnitGate get(@PathVariable String courseId, @PathVariable String topicId, Authentication auth) {
        return gate.of(CurrentUser.id(auth), PathId.parse(courseId, "course_not_found", "Không tìm thấy khóa học"),
                PathId.parse(topicId, "unit_not_found", "Không tìm thấy unit"));
    }
}
