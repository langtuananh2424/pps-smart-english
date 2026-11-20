package com.pps.smartenglish.classroom.repository;

import com.pps.smartenglish.classroom.domain.UnlockOverride;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnlockOverrideRepository extends JpaRepository<UnlockOverride, Long> {

    Optional<UnlockOverride> findByIdAndClassId(Long id, Long classId);

    List<UnlockOverride> findByClassIdOrderByIdDesc(Long classId);

    /** Topic của khóa đang mở sớm cho học viên qua lớp mà họ đang học (cả lớp hoặc riêng họ), chưa thu hồi. */
    @Query(nativeQuery = true, value = """
            SELECT DISTINCT o.topic_id FROM unlock_overrides o
            JOIN class_members m ON m.class_id = o.class_id AND m.user_id = :userId AND m.status = 'ACTIVE'
            WHERE o.revoked_at IS NULL AND (o.user_id IS NULL OR o.user_id = :userId) AND m.course_id = :courseId
            """)
    List<Long> openedTopics(@Param("userId") long userId, @Param("courseId") long courseId);
}
