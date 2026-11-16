package com.pps.smartenglish.commerce.repository;

import com.pps.smartenglish.commerce.domain.Enrollment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByUserIdAndCourseId(Long userId, Long courseId);

    /** Level thuộc khóa nào thì quyền theo khóa đó (D-25). */
    @Query("select count(e) > 0 from Enrollment e, CourseLevel cl where cl.id = :levelId and e.courseId = cl.courseId "
            + "and e.userId = :userId and e.status = 'ACTIVE' and (e.expiresAt is null or e.expiresAt > :now)")
    boolean existsActive(@Param("userId") Long userId, @Param("levelId") Long levelId, @Param("now") Instant now);

    /** Khóa dòng user để các đơn của cùng một người kích hoạt tuần tự (tránh trùng unique user+level khi gia hạn). */
    @Query(value = "select id from users where id = :userId for update", nativeQuery = true)
    List<Long> lockUser(@Param("userId") Long userId);
}
