package com.pps.smartenglish.classroom.repository;

import com.pps.smartenglish.classroom.domain.ClassMember;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassMemberRepository extends JpaRepository<ClassMember, Long> {

    long countByClassIdAndStatus(Long classId, String status);

    boolean existsByClassIdAndUserIdAndStatus(Long classId, Long userId, String status);

    Optional<ClassMember> findByClassIdAndUserIdAndStatus(Long classId, Long userId, String status);

    List<ClassMember> findByClassIdAndStatus(Long classId, String status);

    List<ClassMember> findByClassIdOrderByIdAsc(Long classId);

    List<ClassMember> findByUserIdOrderByIdDesc(Long userId);

    /** [enrollment id, user id, tên hiển thị, email, course id, tên khóa, ngày mua]: đã mua khóa có lớp, còn hạn, chưa có lớp (FR-CLS-02). */
    @Query(nativeQuery = true, value = """
            SELECT e.id, e.user_id, p.display_name, u.email, e.course_id, c.title, e.created_at
            FROM user_course_enrollments e
            JOIN courses c ON c.id = e.course_id AND c.delivery_mode = 'CLASS_BASED'
            JOIN users u ON u.id = e.user_id LEFT JOIN user_profiles p ON p.user_id = e.user_id
            WHERE e.status = 'ACTIVE' AND (e.expires_at IS NULL OR e.expires_at > :now) AND e.id < :before
              AND (:courseId = 0 OR e.course_id = :courseId)
              AND NOT EXISTS (SELECT 1 FROM class_members m WHERE m.user_id = e.user_id AND m.course_id = e.course_id
                              AND m.status IN ('ACTIVE', 'COMPLETED'))
            ORDER BY e.id DESC LIMIT :max
            """)
    List<Object[]> waiting(@Param("courseId") long courseId, @Param("now") Instant now, @Param("before") long before,
                           @Param("max") int max);
}
