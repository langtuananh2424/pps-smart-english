package com.pps.smartenglish.catalog.repository;

import com.pps.smartenglish.catalog.domain.CourseLevel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseLevelRepository extends JpaRepository<CourseLevel, Long> {

    List<CourseLevel> findByCourseIdOrderBySortOrderAscIdAsc(Long courseId);

    /** Đường đọc của học viên: chỉ level đã PUBLISHED. */
    List<CourseLevel> findByCourseIdAndStatusOrderBySortOrderAscIdAsc(Long courseId, String status);

    boolean existsByCourseId(Long courseId);

    boolean existsByCourseIdAndCode(Long courseId, String code);

    boolean existsByCourseIdAndCodeAndIdNot(Long courseId, String code, Long id);
}
