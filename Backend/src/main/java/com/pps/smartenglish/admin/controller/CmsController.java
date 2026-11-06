package com.pps.smartenglish.admin.controller;

import com.pps.smartenglish.catalog.dto.CmsDtos.CourseTree;
import com.pps.smartenglish.catalog.dto.CmsDtos.CourseView;
import com.pps.smartenglish.catalog.dto.CmsDtos.CreateCourse;
import com.pps.smartenglish.catalog.dto.CmsDtos.CreateLesson;
import com.pps.smartenglish.catalog.dto.CmsDtos.CreateLevel;
import com.pps.smartenglish.catalog.dto.CmsDtos.CreateTopic;
import com.pps.smartenglish.catalog.dto.CmsDtos.LessonView;
import com.pps.smartenglish.catalog.dto.CmsDtos.LevelView;
import com.pps.smartenglish.catalog.dto.CmsDtos.PatchCourse;
import com.pps.smartenglish.catalog.dto.CmsDtos.PatchLesson;
import com.pps.smartenglish.catalog.dto.CmsDtos.PatchLevel;
import com.pps.smartenglish.catalog.dto.CmsDtos.PatchTopic;
import com.pps.smartenglish.catalog.dto.CmsDtos.TopicView;
import com.pps.smartenglish.catalog.service.CmsCourseService;
import com.pps.smartenglish.catalog.service.CmsLessonService;
import com.pps.smartenglish.catalog.service.CmsLevelService;
import com.pps.smartenglish.catalog.service.CmsTopicService;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-ADM-01: soạn Course > Level > Topic > Lesson. Cho ADMIN và EDITOR (SecurityConfig). Ghi qua service của module
 * catalog; controller này không chạm repository.
 */
@RestController
@RequestMapping("/api/admin/cms")
class CmsController {

    private final CmsCourseService courses;
    private final CmsLevelService levels;
    private final CmsTopicService topics;
    private final CmsLessonService lessons;

    CmsController(CmsCourseService courses, CmsLevelService levels, CmsTopicService topics, CmsLessonService lessons) {
        this.courses = courses;
        this.levels = levels;
        this.topics = topics;
        this.lessons = lessons;
    }

    // ---- courses ----

    @GetMapping("/courses")
    CursorPage<CourseView> listCourses(@RequestParam(required = false) String limit, @RequestParam(required = false) String cursor) {
        return courses.list(limit, cursor);
    }

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    CourseView createCourse(@Valid @RequestBody CreateCourse req) {
        return courses.create(req);
    }

    @GetMapping("/courses/{id}")
    CourseTree course(@PathVariable String id) {
        return courses.tree(id(id, "course_not_found"));
    }

    @PatchMapping("/courses/{id}")
    CourseView patchCourse(@PathVariable String id, @Valid @RequestBody PatchCourse req) {
        return courses.patch(id(id, "course_not_found"), req);
    }

    @DeleteMapping("/courses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCourse(@PathVariable String id) {
        courses.delete(id(id, "course_not_found"));
    }

    // ---- levels ----

    @PostMapping("/courses/{id}/levels")
    @ResponseStatus(HttpStatus.CREATED)
    LevelView createLevel(@PathVariable String id, @Valid @RequestBody CreateLevel req) {
        return levels.create(id(id, "course_not_found"), req);
    }

    @PatchMapping("/levels/{id}")
    LevelView patchLevel(@PathVariable String id, @Valid @RequestBody PatchLevel req) {
        return levels.patch(id(id, "level_not_found"), req);
    }

    @DeleteMapping("/levels/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteLevel(@PathVariable String id) {
        levels.delete(id(id, "level_not_found"));
    }

    // ---- topics ----

    @PostMapping("/levels/{id}/topics")
    @ResponseStatus(HttpStatus.CREATED)
    TopicView createTopic(@PathVariable String id, @Valid @RequestBody CreateTopic req) {
        return topics.create(id(id, "level_not_found"), req);
    }

    @PatchMapping("/topics/{id}")
    TopicView patchTopic(@PathVariable String id, @Valid @RequestBody PatchTopic req) {
        return topics.patch(id(id, "topic_not_found"), req);
    }

    @DeleteMapping("/topics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTopic(@PathVariable String id) {
        topics.delete(id(id, "topic_not_found"));
    }

    // ---- lessons ----

    @PostMapping("/topics/{id}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    LessonView createLesson(@PathVariable String id, @Valid @RequestBody CreateLesson req) {
        return lessons.create(id(id, "topic_not_found"), req);
    }

    @PatchMapping("/lessons/{id}")
    LessonView patchLesson(@PathVariable String id, @Valid @RequestBody PatchLesson req) {
        return lessons.patch(id(id, "lesson_not_found"), req);
    }

    @DeleteMapping("/lessons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteLesson(@PathVariable String id) {
        lessons.delete(id(id, "lesson_not_found"));
    }

    // ---- xuất bản (idempotent) ----

    @PostMapping("/{kind}/{id}/publish")
    Object publish(@PathVariable String kind, @PathVariable String id) {
        return setPublished(kind, id, true);
    }

    @PostMapping("/{kind}/{id}/unpublish")
    Object unpublish(@PathVariable String kind, @PathVariable String id) {
        return setPublished(kind, id, false);
    }

    private Object setPublished(String kind, String rawId, boolean published) {
        return switch (kind) {
            case "courses" -> courses.setPublished(id(rawId, "course_not_found"), published);
            case "levels" -> levels.setPublished(id(rawId, "level_not_found"), published);
            case "topics" -> topics.setPublished(id(rawId, "topic_not_found"), published);
            case "lessons" -> lessons.setPublished(id(rawId, "lesson_not_found"), published);
            default -> throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Không tìm thấy");
        };
    }

    private static long id(String raw, String notFoundCode) {
        return PathId.parse(raw, notFoundCode, "Không tìm thấy");
    }
}
