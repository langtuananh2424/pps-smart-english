package com.pps.smartenglish.lms.repository;

import com.pps.smartenglish.catalog.domain.Topic;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** FR-LMS-04: tiến độ của một unit, cùng định nghĩa "xong" với UnitProgressRepository.unfinishedTopicIds. */
public interface UnitGateRepository extends Repository<Topic, Long> {

    /** Một dòng [tổng bài PUBLISHED, số bài đã hoàn thành]. */
    @Query(nativeQuery = true, value = """
            SELECT COUNT(*), COUNT(p.completed_at)
            FROM lessons l LEFT JOIN lesson_progress p ON p.lesson_id = l.id AND p.user_id = :userId
            WHERE l.topic_id = :topicId AND l.status = 'PUBLISHED'
            """)
    List<Object[]> lectureCounts(@Param("topicId") long topicId, @Param("userId") long userId);

    /** [vị trí xem xa nhất, thời lượng] của bài chưa hoàn thành đầu tiên theo thứ tự; rỗng nếu không còn bài nào. */
    @Query(nativeQuery = true, value = """
            SELECT p.max_position_sec, l.duration_sec
            FROM lessons l LEFT JOIN lesson_progress p ON p.lesson_id = l.id AND p.user_id = :userId
            WHERE l.topic_id = :topicId AND l.status = 'PUBLISHED' AND p.completed_at IS NULL
            ORDER BY l.sort_order, l.id LIMIT 1
            """)
    List<Object[]> firstUnfinishedLesson(@Param("topicId") long topicId, @Param("userId") long userId);

    /** Một dòng [tổng bộ PRACTICE/COURSE có câu hỏi, số bộ đã hoàn thành: nộp một lần, hoặc đủ lượt đạt với bộ video từ nối (D-159)]. */
    @Query(nativeQuery = true, value = """
            SELECT COUNT(*), COUNT(CASE WHEN (
                    (NOT EXISTS (SELECT 1 FROM questions vq WHERE vq.bank_id = b.id AND vq.type = 'VIDEO_CONNECTIVE' AND vq.archived_at IS NULL)
                     AND EXISTS (SELECT 1 FROM student_exercise_attempts a
                                 WHERE a.bank_id = b.id AND a.user_id = :userId AND a.submitted_at IS NOT NULL))
                    OR (EXISTS (SELECT 1 FROM questions vq WHERE vq.bank_id = b.id AND vq.type = 'VIDEO_CONNECTIVE' AND vq.archived_at IS NULL)
                        AND (SELECT COUNT(*) FROM student_exercise_attempts a
                             WHERE a.bank_id = b.id AND a.user_id = :userId AND a.submitted_at IS NOT NULL
                               AND NOT EXISTS (SELECT 1 FROM questions vq WHERE vq.bank_id = b.id AND vq.type = 'VIDEO_CONNECTIVE'
                                               AND vq.archived_at IS NULL
                                               AND NOT EXISTS (SELECT 1 FROM attempt_video_groups v
                                                               WHERE v.attempt_id = a.id AND v.group_id = vq.group_id AND v.passed)))
                            >= :#{@videoPassRequirement.required()})
                ) THEN 1 END)
            FROM question_banks b
            WHERE b.topic_id = :topicId AND b.status = 'PUBLISHED' AND b.mode = 'PRACTICE' AND b.purpose = 'COURSE'
              AND EXISTS (SELECT 1 FROM questions q WHERE q.bank_id = b.id)
            """)
    List<Object[]> exerciseCounts(@Param("topicId") long topicId, @Param("userId") long userId);

    /** Một dòng [mã bộ kiểm tra chương (null nếu chưa gắn), đã nộp 1/0]. */
    @Query(nativeQuery = true, value = """
            SELECT t.chapter_test_bank_id, CASE WHEN EXISTS (SELECT 1 FROM student_exercise_attempts a
                    WHERE a.bank_id = t.chapter_test_bank_id AND a.user_id = :userId AND a.submitted_at IS NOT NULL)
                THEN 1 ELSE 0 END
            FROM topics t WHERE t.id = :topicId
            """)
    List<Object[]> chapterTest(@Param("topicId") long topicId, @Param("userId") long userId);
}
