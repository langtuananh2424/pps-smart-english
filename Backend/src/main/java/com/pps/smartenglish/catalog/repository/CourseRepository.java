package com.pps.smartenglish.catalog.repository;

import com.pps.smartenglish.catalog.domain.Course;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByStatusOrderByIdAsc(String status);

    Optional<Course> findBySlugAndStatus(String slug, String status);

    /** Khóa dùng bộ câu hỏi này làm bài thi (FR-EXAM-02). */
    Optional<Course> findByExamBankId(Long examBankId);

    /** Khóa đã xuất bản có dải band, để gợi ý khóa theo level (FR-GOAL-03). */
    List<Course> findByStatusAndBandFromIsNotNullOrderByBandFromAscIdAsc(String status);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    /** CMS: mọi trạng thái, mới nhất trước, phân trang con trỏ theo id. */
    @Query("select c from Course c where c.id < :before order by c.id desc")
    List<Course> findPage(@Param("before") Long before, Pageable page);

    /** Khóa học viên đang học còn hạn: nguồn của lượt AI định kỳ (D-47). Rỗng = không có quyền còn hạn (ví dụ học thử). */
    @Query("select c from Course c where c.id = :courseId and exists (select 1 from Enrollment e where e.courseId = c.id "
            + "and e.userId = :userId and e.status = 'ACTIVE' and (e.expiresAt is null or e.expiresAt > :now))")
    Optional<Course> findActive(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("now") Instant now);

    /** Mọi khóa còn hạn của học viên, cho /api/me/quota. */
    @Query("select c from Course c where exists (select 1 from Enrollment e where e.courseId = c.id "
            + "and e.userId = :userId and e.status = 'ACTIVE' and (e.expiresAt is null or e.expiresAt > :now)) order by c.id")
    List<Course> findActiveCourses(@Param("userId") Long userId, @Param("now") Instant now);
}
