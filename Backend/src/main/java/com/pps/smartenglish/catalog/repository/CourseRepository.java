package com.pps.smartenglish.catalog.repository;

import com.pps.smartenglish.catalog.domain.Course;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByStatusOrderByIdAsc(String status);

    Optional<Course> findBySlugAndStatus(String slug, String status);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    /** CMS: mọi trạng thái, mới nhất trước, phân trang con trỏ theo id. */
    @Query("select c from Course c where c.id < :before order by c.id desc")
    List<Course> findPage(@Param("before") Long before, Pageable page);
}
