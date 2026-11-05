package com.pps.smartenglish.catalog.repository;

import com.pps.smartenglish.catalog.domain.Course;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByStatusOrderByIdAsc(String status);

    Optional<Course> findBySlugAndStatus(String slug, String status);
}
