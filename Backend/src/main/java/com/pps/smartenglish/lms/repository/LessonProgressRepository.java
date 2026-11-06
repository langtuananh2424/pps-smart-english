package com.pps.smartenglish.lms.repository;

import com.pps.smartenglish.lms.domain.LessonProgress;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    Optional<LessonProgress> findByUserIdAndLessonId(Long userId, Long lessonId);

    /**
     * Không dùng ON CONFLICT vì H2 (test) không hỗ trợ. Hai request đầu tiên đồng thời: unique (user, lesson) chặn trùng,
     * bên thua ném DataIntegrityViolationException; REQUIRES_NEW để lỗi đó không làm hỏng transaction của người gọi.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO lesson_progress (user_id, lesson_id)
            SELECT :userId, :lessonId
            WHERE NOT EXISTS (SELECT 1 FROM lesson_progress WHERE user_id = :userId AND lesson_id = :lessonId)
            """)
    int createIfAbsent(@Param("userId") Long userId, @Param("lessonId") Long lessonId);

    /**
     * Cộng dồn nguyên tử trong một UPDATE (khóa dòng): không đọc-rồi-ghi, nên không mất delta khi đồng thời.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE lesson_progress SET
                watched_sec = watched_sec + :delta,
                max_position_sec = GREATEST(max_position_sec, :pos),
                updated_at = CAST(:now AS timestamp with time zone)
            WHERE user_id = :userId AND lesson_id = :lessonId
            """)
    int addProgress(@Param("userId") Long userId, @Param("lessonId") Long lessonId, @Param("delta") int delta,
                    @Param("pos") int pos, @Param("now") Instant now);

    /**
     * Đặt completed_at đúng một lần: trả 1 chỉ ở lần chuyển sang hoàn thành đầu tiên (completed_at IS NULL),
     * nên dùng làm điều kiện phát LessonCompleted. duration 0 = chưa biết thời lượng, không bao giờ hoàn thành.
     * Ngưỡng 80% tính số nguyên: watched*10 >= duration*8.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE lesson_progress SET completed_at = CAST(:now AS timestamp with time zone)
            WHERE user_id = :userId AND lesson_id = :lessonId AND completed_at IS NULL
              AND :duration > 0 AND watched_sec * 10 >= :duration * 8
            """)
    int markCompleted(@Param("userId") Long userId, @Param("lessonId") Long lessonId, @Param("duration") int duration,
                      @Param("now") Instant now);
}
