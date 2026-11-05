package com.pps.smartenglish.catalog.repository;

import com.pps.smartenglish.catalog.domain.CourseLevel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseLevelRepository extends JpaRepository<CourseLevel, Long> {

    List<CourseLevel> findByCourseIdOrderBySortOrderAscIdAsc(Long courseId);
}
