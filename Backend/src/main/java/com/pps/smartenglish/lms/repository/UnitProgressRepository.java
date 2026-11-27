package com.pps.smartenglish.lms.repository;

import com.pps.smartenglish.catalog.domain.Topic;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** FR-LMS-04: dữ liệu để quyết định unit nào đã xong. Native vì đọc bài giảng, bài tập, lượt làm của nhiều module. */
public interface UnitProgressRepository extends Repository<Topic, Long> {

    /** [mã khóa, khóa bật tuần tự, topic học thử] của topic; rỗng nếu topic không tồn tại. */
    @Query(nativeQuery = true, value = """
            SELECT lv.course_id, c.sequential, t.is_free_trial
            FROM topics t JOIN course_levels lv ON lv.id = t.level_id JOIN courses c ON c.id = lv.course_id
            WHERE t.id = :topicId
            """)
    List<Object[]> courseOf(@Param("topicId") long topicId);

    /** Mã topic đã xuất bản của khóa theo thứ tự học (level rồi topic). */
    @Query(nativeQuery = true, value = """
            SELECT t.id FROM topics t JOIN course_levels lv ON lv.id = t.level_id
            WHERE lv.course_id = :courseId AND lv.status = 'PUBLISHED' AND t.status = 'PUBLISHED'
            ORDER BY lv.sort_order, lv.id, t.sort_order, t.id
            """)
    List<Long> orderedTopicIds(@Param("courseId") long courseId);

    /**
     * Topic CHƯA xong của học viên (D-122): còn bài giảng chưa hoàn thành, hoặc còn bộ bài tập (có câu hỏi) chưa nộp lần nào (bộ video từ nối: chưa đủ lượt đạt, D-159),
     * hoặc chưa nộp kiểm tra chương. Kiểm tra chương chỉ cần nộp, không cần đạt.
     */
    @Query(nativeQuery = true, value = """
            SELECT t.id FROM topics t JOIN course_levels lv ON lv.id = t.level_id
            WHERE lv.course_id = :courseId AND lv.status = 'PUBLISHED' AND t.status = 'PUBLISHED' AND (
                EXISTS (SELECT 1 FROM lessons l WHERE l.topic_id = t.id AND l.status = 'PUBLISHED'
                        AND NOT EXISTS (SELECT 1 FROM lesson_progress p
                                        WHERE p.lesson_id = l.id AND p.user_id = :userId AND p.completed_at IS NOT NULL))
                OR EXISTS (SELECT 1 FROM question_banks b WHERE b.topic_id = t.id AND b.status = 'PUBLISHED'
                           AND b.mode = 'PRACTICE' AND b.purpose = 'COURSE'
                           AND EXISTS (SELECT 1 FROM questions q WHERE q.bank_id = b.id)
                           AND NOT (
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
                ))
                OR (t.chapter_test_bank_id IS NOT NULL
                    AND NOT EXISTS (SELECT 1 FROM student_exercise_attempts a
                                    WHERE a.bank_id = t.chapter_test_bank_id AND a.user_id = :userId AND a.submitted_at IS NOT NULL)))
            """)
    List<Long> unfinishedTopicIds(@Param("courseId") long courseId, @Param("userId") long userId);
}
