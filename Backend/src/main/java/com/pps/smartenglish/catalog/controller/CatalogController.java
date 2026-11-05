package com.pps.smartenglish.catalog.controller;

import com.pps.smartenglish.catalog.dto.CourseDetail;
import com.pps.smartenglish.catalog.dto.CourseSummary;
import com.pps.smartenglish.catalog.service.CatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-LMS-01: đọc danh mục công khai, không cần token. */
@RestController
@RequestMapping("/api/catalog/courses")
class CatalogController {

    private final CatalogService service;

    CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    List<CourseSummary> list() {
        return service.listCourses();
    }

    @GetMapping("/{slug}")
    CourseDetail detail(@PathVariable String slug) {
        return service.detail(slug);
    }
}
