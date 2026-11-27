package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.VideoProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoProgressRepository extends JpaRepository<VideoProgress, Long> {

    List<VideoProgress> findByAttemptId(Long attemptId);

    Optional<VideoProgress> findByAttemptIdAndGroupId(Long attemptId, Long groupId);

    /** Số thứ tự lượt của học viên trên bộ (lượt đầu = 1). */
    @Query("select count(a) from StudentAttempt a where a.userId = :userId and a.bankId = :bankId and a.id <= :attemptId")
    long attemptNo(@Param("userId") long userId, @Param("bankId") long bankId, @Param("attemptId") long attemptId);

    /** Số lượt đã nộp mà mọi nhóm video của bộ đều đạt (cùng điều kiện với UnitProgressRepository và UnitGateRepository). */
    @Query(nativeQuery = true, value = """
            SELECT COUNT(*) FROM student_exercise_attempts a
            WHERE a.user_id = :userId AND a.bank_id = :bankId AND a.submitted_at IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM questions q WHERE q.bank_id = a.bank_id AND q.type = 'VIDEO_CONNECTIVE'
                              AND q.archived_at IS NULL
                              AND NOT EXISTS (SELECT 1 FROM attempt_video_groups v
                                              WHERE v.attempt_id = a.id AND v.group_id = q.group_id AND v.passed))
            """)
    long passedAttempts(@Param("userId") long userId, @Param("bankId") long bankId);
}
