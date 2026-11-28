package com.pps.smartenglish.classroom.repository;

import com.pps.smartenglish.classroom.domain.ClassRoom;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * FR-CLS-05, FR-CLS-07: truy vấn đọc nhiều module (bài giảng, lượt làm bài, điểm danh) cho bảng tiến độ và báo cáo số buổi dạy.
 * Native như các truy vấn báo cáo khác; chỉ trả cột cần hiển thị.
 */
public interface ClassReportRepository extends Repository<ClassRoom, Long> {

    /** Số bài giảng đã xuất bản của khóa (mẫu số của % hoàn thành). */
    @Query(nativeQuery = true, value = """
            SELECT COUNT(*) FROM lessons l JOIN topics t ON t.id = l.topic_id JOIN course_levels lv ON lv.id = t.level_id
            WHERE lv.course_id = :courseId AND lv.status = 'PUBLISHED' AND t.status = 'PUBLISHED' AND l.status = 'PUBLISHED'
            """)
    long lessonTotal(@Param("courseId") long courseId);

    /** [user_id, số bài giảng đã hoàn thành] của các học viên, chỉ bài đã xuất bản của khóa. */
    @Query(nativeQuery = true, value = """
            SELECT p.user_id, COUNT(*) FROM lesson_progress p JOIN lessons l ON l.id = p.lesson_id
            JOIN topics t ON t.id = l.topic_id JOIN course_levels lv ON lv.id = t.level_id
            WHERE lv.course_id = :courseId AND lv.status = 'PUBLISHED' AND t.status = 'PUBLISHED' AND l.status = 'PUBLISHED'
              AND p.completed_at IS NOT NULL AND p.user_id IN (:ids)
            GROUP BY p.user_id
            """)
    List<Object[]> lessonsDone(@Param("courseId") long courseId, @Param("ids") Collection<Long> ids);

    /** [user_id, topic id, điểm, điểm tối đa] (null với lượt Nghe ép, D-142) của lượt kiểm tra chương đã nộp mới nhất của từng học viên ở từng unit. */
    @Query(nativeQuery = true, value = """
            SELECT a.user_id, t.id, CASE WHEN a.result_mode = 'COUNT_ONLY' THEN NULL ELSE a.score END,
                   CASE WHEN a.result_mode = 'COUNT_ONLY' THEN NULL ELSE a.max_score END FROM student_exercise_attempts a
            JOIN topics t ON t.chapter_test_bank_id = a.bank_id JOIN course_levels lv ON lv.id = t.level_id
            WHERE lv.course_id = :courseId AND a.user_id IN (:ids) AND a.submitted_at IS NOT NULL
              AND a.id = (SELECT MAX(a2.id) FROM student_exercise_attempts a2
                          WHERE a2.user_id = a.user_id AND a2.bank_id = a.bank_id AND a2.submitted_at IS NOT NULL)
            ORDER BY t.id
            """)
    List<Object[]> chapterTests(@Param("courseId") long courseId, @Param("ids") Collection<Long> ids);

    /** [user_id, trạng thái điểm danh, số buổi] trong các buổi còn hiệu lực của lớp. */
    @Query(nativeQuery = true, value = """
            SELECT m.user_id, m.status, COUNT(*) FROM attendance_marks m JOIN class_sessions s ON s.id = m.session_id
            WHERE s.class_id = :classId AND s.status = 'SCHEDULED' AND m.user_id IN (:ids)
            GROUP BY m.user_id, m.status
            """)
    List<Object[]> attendance(@Param("classId") long classId, @Param("ids") Collection<Long> ids);

    /** Khóa chứa unit; rỗng nếu unit không tồn tại. */
    @Query(nativeQuery = true, value = "SELECT lv.course_id FROM topics t JOIN course_levels lv ON lv.id = t.level_id WHERE t.id = :topicId")
    List<Long> courseOfTopic(@Param("topicId") long topicId);

    /** [user_id, tên hiển thị] theo id. */
    @Query(nativeQuery = true, value = "SELECT p.user_id, p.display_name FROM user_profiles p WHERE p.user_id IN (:ids)")
    List<Object[]> displayNames(@Param("ids") Collection<Long> ids);

    /** [tên topic] theo id, để hiện unit hiện tại. */
    @Query(nativeQuery = true, value = "SELECT t.id, t.title FROM topics t WHERE t.id IN (:ids)")
    List<Object[]> topicTitles(@Param("ids") Collection<Long> ids);

    /**
     * Buổi đã diễn ra và đã có điểm danh trong [from, to): [id, lớp, tên lớp, loại, người dạy, tên người dạy, bắt đầu, kết thúc].
     * Chỉ đếm buổi đã điểm danh (FR-CLS-07); buổi hủy không đếm.
     */
    @Query(nativeQuery = true, value = """
            SELECT s.id, s.class_id, c.name, s.kind, s.taught_by, p.display_name, s.starts_at, s.ends_at
            FROM class_sessions s JOIN classes c ON c.id = s.class_id LEFT JOIN user_profiles p ON p.user_id = s.taught_by
            WHERE s.status = 'SCHEDULED' AND s.starts_at >= :from AND s.starts_at < :to
              AND EXISTS (SELECT 1 FROM attendance_marks m WHERE m.session_id = s.id)
            ORDER BY s.taught_by, s.starts_at, s.id
            """)
    List<Object[]> markedSessions(@Param("from") Instant from, @Param("to") Instant to);

    /** [session_id, số lần sửa, lần sửa gần nhất] của điểm danh bị sửa sau thời điểm since. */
    @Query(nativeQuery = true, value = """
            SELECT ch.session_id, COUNT(*), MAX(ch.changed_at) FROM attendance_changes ch
            WHERE ch.changed_at > :since GROUP BY ch.session_id
            """)
    List<Object[]> editsSince(@Param("since") Instant since);

    /** Lần xuất báo cáo gần nhất của bất kỳ ai; rỗng nếu chưa từng xuất. */
    @Query(nativeQuery = true, value = "SELECT MAX(e.exported_at) FROM teaching_report_exports e")
    List<Object> lastExportAt();
}
