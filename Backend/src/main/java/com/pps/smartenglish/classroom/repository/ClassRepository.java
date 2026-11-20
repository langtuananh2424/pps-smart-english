package com.pps.smartenglish.classroom.repository;

import com.pps.smartenglish.classroom.domain.ClassRoom;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassRepository extends JpaRepository<ClassRoom, Long> {

    /** Khóa dòng lớp: tuần tự hóa xếp, chuyển, đổi giáo viên của cùng một lớp (kiểm sĩ số, một giáo viên chính). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClassRoom c where c.id = :id")
    Optional<ClassRoom> lockById(@Param("id") long id);

    boolean existsByCourseId(Long courseId);

    /** courseId = 0 và status rỗng là không lọc. */
    @Query(nativeQuery = true, value = """
            SELECT * FROM classes c
            WHERE c.id < :before AND (:courseId = 0 OR c.course_id = :courseId) AND (:status = '' OR c.status = :status)
            ORDER BY c.id DESC LIMIT :max
            """)
    List<ClassRoom> page(@Param("courseId") long courseId, @Param("status") String status, @Param("before") long before,
                         @Param("max") int max);

    /** Lớp mà người này đang là giáo viên hoặc trợ giảng (đang phụ trách vào ngày today). */
    @Query(nativeQuery = true, value = """
            SELECT * FROM classes c WHERE c.id IN (
              SELECT s.class_id FROM class_staff s WHERE s.user_id = :userId AND s.revoked_at IS NULL
                AND s.assigned_from <= :today AND (s.assigned_to IS NULL OR s.assigned_to >= :today))
            ORDER BY c.id DESC
            """)
    List<ClassRoom> taughtBy(@Param("userId") long userId, @Param("today") LocalDate today);

    /** Lớp học viên đang học hoặc đã hoàn thành. */
    @Query(nativeQuery = true, value = """
            SELECT * FROM classes c WHERE c.id IN (
              SELECT m.class_id FROM class_members m WHERE m.user_id = :userId AND m.status IN ('ACTIVE', 'COMPLETED'))
            ORDER BY c.id DESC
            """)
    List<ClassRoom> attendedBy(@Param("userId") long userId);

    /** [class_id, số học viên ACTIVE]. */
    @Query(nativeQuery = true, value = """
            SELECT m.class_id, COUNT(*) FROM class_members m
            WHERE m.class_id IN (:ids) AND m.status = 'ACTIVE' GROUP BY m.class_id
            """)
    List<Object[]> activeCounts(@Param("ids") Collection<Long> ids);

    /** [class_id, user_id, tên hiển thị] giáo viên chính đang phụ trách. */
    @Query(nativeQuery = true, value = """
            SELECT s.class_id, s.user_id, p.display_name FROM class_staff s LEFT JOIN user_profiles p ON p.user_id = s.user_id
            WHERE s.class_id IN (:ids) AND s.role = 'TEACHER' AND s.revoked_at IS NULL
              AND s.assigned_from <= :today AND (s.assigned_to IS NULL OR s.assigned_to >= :today)
            """)
    List<Object[]> teachers(@Param("ids") Collection<Long> ids, @Param("today") LocalDate today);

    /** [id, tên khóa]. */
    @Query(nativeQuery = true, value = "SELECT c.id, c.title FROM courses c WHERE c.id IN (:ids)")
    List<Object[]> courseTitles(@Param("ids") Collection<Long> ids);

    /** [id, tên hiển thị, email] của các tài khoản. */
    @Query(nativeQuery = true, value = """
            SELECT u.id, p.display_name, u.email FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id WHERE u.id IN (:ids)
            """)
    List<Object[]> people(@Param("ids") Collection<Long> ids);
}
