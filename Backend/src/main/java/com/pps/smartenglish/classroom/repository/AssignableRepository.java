package com.pps.smartenglish.classroom.repository;

import com.pps.smartenglish.classroom.domain.ClassRoom;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * FR-ASG-01: nội dung giao được của khóa, cùng luật với AssignmentService.create (bộ xuất bản cả chuỗi, bài giảng xuất bản,
 * bộ có câu; câu Viết/Nói chưa lưu trữ và không thuộc bộ thi). Mỗi truy vấn cho cả khóa, không N+1.
 */
public interface AssignableRepository extends Repository<ClassRoom, Long> {

    /** [topic id, tên, vị trí] theo thứ tự level rồi unit. */
    @Query(nativeQuery = true, value = """
            SELECT t.id, t.title, t.sort_order FROM topics t JOIN course_levels lv ON lv.id = t.level_id
            JOIN courses c ON c.id = lv.course_id
            WHERE lv.course_id = :courseId AND c.status = 'PUBLISHED' AND lv.status = 'PUBLISHED' AND t.status = 'PUBLISHED'
            ORDER BY lv.sort_order, lv.id, t.sort_order, t.id
            """)
    List<Object[]> topics(@Param("courseId") long courseId);

    /** [bank id, topic id, tên, mode, số câu còn hiệu lực]. */
    @Query(nativeQuery = true, value = """
            SELECT b.id, b.topic_id, b.title, b.mode, qc.n FROM question_banks b
            JOIN topics t ON t.id = b.topic_id JOIN course_levels lv ON lv.id = t.level_id JOIN courses c ON c.id = lv.course_id
            JOIN (SELECT q.bank_id, COUNT(*) AS n FROM questions q WHERE q.archived_at IS NULL GROUP BY q.bank_id) qc ON qc.bank_id = b.id
            WHERE lv.course_id = :courseId AND c.status = 'PUBLISHED' AND lv.status = 'PUBLISHED' AND t.status = 'PUBLISHED' AND b.status = 'PUBLISHED'
              AND (b.lesson_id IS NULL OR EXISTS (SELECT 1 FROM lessons l WHERE l.id = b.lesson_id AND l.status = 'PUBLISHED'))
            ORDER BY b.id
            """)
    List<Object[]> banks(@Param("courseId") long courseId);

    /** [question id, topic id, loại, đề bài] của câu Viết/Nói giao riêng được. */
    @Query(nativeQuery = true, value = """
            SELECT q.id, b.topic_id, q.type, q.prompt FROM questions q JOIN question_banks b ON b.id = q.bank_id
            JOIN topics t ON t.id = b.topic_id JOIN course_levels lv ON lv.id = t.level_id JOIN courses c ON c.id = lv.course_id
            WHERE lv.course_id = :courseId AND c.status = 'PUBLISHED' AND lv.status = 'PUBLISHED' AND t.status = 'PUBLISHED' AND b.status = 'PUBLISHED'
              AND (b.lesson_id IS NULL OR EXISTS (SELECT 1 FROM lessons l WHERE l.id = b.lesson_id AND l.status = 'PUBLISHED'))
              AND q.archived_at IS NULL AND q.type IN ('WRITING', 'SPEAKING') AND b.mode <> 'EXAM'
            ORDER BY q.id
            """)
    List<Object[]> aiQuestions(@Param("courseId") long courseId);
}
