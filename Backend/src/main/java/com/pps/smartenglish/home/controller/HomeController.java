package com.pps.smartenglish.home.controller;

import com.pps.smartenglish.home.dto.HomeDtos.CamMessages;
import com.pps.smartenglish.home.dto.HomeDtos.MyCourse;
import com.pps.smartenglish.home.dto.HomeDtos.Roadmap;
import com.pps.smartenglish.home.dto.HomeDtos.Today;
import com.pps.smartenglish.home.service.CamService;
import com.pps.smartenglish.home.service.MyCourseService;
import com.pps.smartenglish.home.service.RoadmapService;
import com.pps.smartenglish.home.service.TodayService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Các API đọc của Trang chủ học viên (issue #52); chỉ trả dữ liệu của người đang đăng nhập. */
@RestController
@RequestMapping("/api/me")
class HomeController {

    private static final String NO_COURSE = "course_not_found";
    private static final String NO_COURSE_MSG = "Không tìm thấy khóa học";

    private final MyCourseService courses;
    private final RoadmapService roadmaps;
    private final TodayService today;
    private final CamService cam;

    HomeController(MyCourseService courses, RoadmapService roadmaps, TodayService today, CamService cam) {
        this.courses = courses;
        this.roadmaps = roadmaps;
        this.today = today;
        this.cam = cam;
    }

    @GetMapping("/courses")
    List<MyCourse> courses(Authentication auth) {
        return courses.list(CurrentUser.id(auth));
    }

    @GetMapping("/courses/{courseId}/roadmap")
    Roadmap roadmap(@PathVariable String courseId, Authentication auth) {
        return roadmaps.of(CurrentUser.id(auth), PathId.parse(courseId, NO_COURSE, NO_COURSE_MSG));
    }

    @GetMapping("/today")
    Today today(@RequestParam(required = false) String courseId, Authentication auth) {
        Long id = courseId == null ? null : PathId.parse(courseId, NO_COURSE, NO_COURSE_MSG);
        return today.today(CurrentUser.id(auth), id);
    }

    @GetMapping("/cam-messages")
    CamMessages camMessages(Authentication auth) {
        return cam.messages(CurrentUser.id(auth));
    }
}
