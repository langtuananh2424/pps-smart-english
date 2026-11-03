package com.pps.smartenglish.course;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    public record CourseDto(Long id, String title, String description, String levelLabel) {
    }

    private final CourseRepository courses;

    public CourseController(CourseRepository courses) {
        this.courses = courses;
    }

    @GetMapping
    public List<CourseDto> list() {
        return courses.findAllByOrderByIdAsc().stream()
                .map(c -> new CourseDto(c.getId(), c.getTitle(), c.getDescription(), c.getLevelLabel()))
                .toList();
    }
}
