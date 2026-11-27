package com.pps.smartenglish.classroom.repository;

import com.pps.smartenglish.classroom.domain.Assignment;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    Optional<Assignment> findByIdAndClassId(Long id, Long classId);

    /** SELECT ... FOR UPDATE: tuần tự hóa các request cùng bài giao (chạy cả H2 và PostgreSQL). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Assignment a where a.id = :id")
    Optional<Assignment> lockById(@Param("id") Long id);

    List<Assignment> findByClassIdOrderByIdDesc(Long classId, Pageable page);

    /** Bài còn hiệu lực của lớp mà học viên là người nhận (cả lớp, hoặc có tên trong danh sách riêng). */
    @Query("select a from Assignment a where a.classId = :classId and a.status = 'ACTIVE' "
            + "and (not exists (select 1 from AssignmentTarget t where t.assignmentId = a.id) "
            + "or exists (select 1 from AssignmentTarget t where t.assignmentId = a.id and t.userId = :userId)) order by a.id desc")
    List<Assignment> forStudentInClass(@Param("classId") Long classId, @Param("userId") Long userId, Pageable page);

    /** Mọi bài còn hiệu lực của các lớp học viên đang học. */
    @Query("select a from Assignment a where a.status = 'ACTIVE' "
            + "and a.classId in (select m.classId from ClassMember m where m.userId = :userId and m.status = 'ACTIVE') "
            + "and (not exists (select 1 from AssignmentTarget t where t.assignmentId = a.id) "
            + "or exists (select 1 from AssignmentTarget t where t.assignmentId = a.id and t.userId = :userId)) order by a.dueAt desc, a.id desc")
    List<Assignment> forStudent(@Param("userId") Long userId, Pageable page);

    /** Học viên đang học lớp của bài và là người nhận của bài còn hiệu lực. */
    @Query("select count(a) > 0 from Assignment a where a.id = :id and a.status = 'ACTIVE' "
            + "and a.classId in (select m.classId from ClassMember m where m.userId = :userId and m.status = 'ACTIVE') "
            + "and (not exists (select 1 from AssignmentTarget t where t.assignmentId = a.id) "
            + "or exists (select 1 from AssignmentTarget t where t.assignmentId = a.id and t.userId = :userId))")
    boolean isRecipient(@Param("id") Long id, @Param("userId") Long userId);

    /** Như isRecipient nhưng không đòi bài còn hiệu lực: người nhận vẫn xem được bài đã hủy (D-153). */
    @Query("select count(a) > 0 from Assignment a where a.id = :id "
            + "and a.classId in (select m.classId from ClassMember m where m.userId = :userId and m.status = 'ACTIVE') "
            + "and (not exists (select 1 from AssignmentTarget t where t.assignmentId = a.id) "
            + "or exists (select 1 from AssignmentTarget t where t.assignmentId = a.id and t.userId = :userId))")
    boolean isRecipientAnyStatus(@Param("id") Long id, @Param("userId") Long userId);

    /** Như isRecipientOfQuestion nhưng mọi trạng thái: xem đề của bài đã hủy (D-153). */
    @Query("select count(a) > 0 from Assignment a where a.id = :id and a.questionId = :questionId "
            + "and a.classId in (select m.classId from ClassMember m where m.userId = :userId and m.status = 'ACTIVE') "
            + "and (not exists (select 1 from AssignmentTarget t where t.assignmentId = a.id) "
            + "or exists (select 1 from AssignmentTarget t where t.assignmentId = a.id and t.userId = :userId))")
    boolean isRecipientOfQuestionAnyStatus(@Param("id") Long id, @Param("userId") Long userId, @Param("questionId") Long questionId);

    /** Như isRecipient, và bài phải giao đúng câu Viết/Nói này (D-130). */
    @Query("select count(a) > 0 from Assignment a where a.id = :id and a.questionId = :questionId and a.status = 'ACTIVE' "
            + "and a.classId in (select m.classId from ClassMember m where m.userId = :userId and m.status = 'ACTIVE') "
            + "and (not exists (select 1 from AssignmentTarget t where t.assignmentId = a.id) "
            + "or exists (select 1 from AssignmentTarget t where t.assignmentId = a.id and t.userId = :userId))")
    boolean isRecipientOfQuestion(@Param("id") Long id, @Param("userId") Long userId, @Param("questionId") Long questionId);

    /** Học viên có bài giao cả bộ còn hiệu lực cho bộ này (mở riêng bài đó dù unit chưa mở, FR-ASG-01). Bài giao một câu không mở cả bộ. */
    @Query("select count(a) > 0 from Assignment a where a.bankId = :bankId and a.questionId is null and a.status = 'ACTIVE' "
            + "and a.classId in (select m.classId from ClassMember m where m.userId = :userId and m.status = 'ACTIVE') "
            + "and (not exists (select 1 from AssignmentTarget t where t.assignmentId = a.id) "
            + "or exists (select 1 from AssignmentTarget t where t.assignmentId = a.id and t.userId = :userId))")
    boolean isBankAssigned(@Param("bankId") Long bankId, @Param("userId") Long userId);

    /** D-155, D-162: như isBankAssigned nhưng chỉ bài giao có chế độ hiệu lực Thi (của bài, hoặc của bộ khi bài không đặt) mà học viên chưa nộp. */
    @Query("select count(a) > 0 from Assignment a where a.bankId = :bankId and a.questionId is null and a.status = 'ACTIVE' "
            + "and (a.mode = 'EXAM' or (a.mode is null and exists (select 1 from QuestionBank b where b.id = a.bankId and b.mode = 'EXAM'))) "
            + "and not exists (select 1 from StudentAttempt s where s.assignmentId = a.id and s.userId = :userId and s.submittedAt is not null) "
            + "and a.classId in (select m.classId from ClassMember m where m.userId = :userId and m.status = 'ACTIVE') "
            + "and (not exists (select 1 from AssignmentTarget t where t.assignmentId = a.id) "
            + "or exists (select 1 from AssignmentTarget t where t.assignmentId = a.id and t.userId = :userId))")
    boolean isExamAssigned(@Param("bankId") Long bankId, @Param("userId") Long userId);

    /** D-155: đã có học viên bắt đầu lượt của bài giao này (khóa đổi chế độ). */
    @Query("select count(s) > 0 from StudentAttempt s where s.assignmentId = :id")
    boolean hasAttempts(@Param("id") Long id);

    /** Như isBankAssigned nhưng mọi trạng thái: chỉ để xem lại bộ đã giao (D-153). */
    @Query("select count(a) > 0 from Assignment a where a.bankId = :bankId and a.questionId is null "
            + "and a.classId in (select m.classId from ClassMember m where m.userId = :userId and m.status = 'ACTIVE') "
            + "and (not exists (select 1 from AssignmentTarget t where t.assignmentId = a.id) "
            + "or exists (select 1 from AssignmentTarget t where t.assignmentId = a.id and t.userId = :userId))")
    boolean isBankAssignedAnyStatus(@Param("bankId") Long bankId, @Param("userId") Long userId);

    @Query("select a from Assignment a where a.status = 'ACTIVE' and a.remindedAt is null and a.dueAt > :now and a.dueAt <= :until")
    List<Assignment> dueForReminder(@Param("now") Instant now, @Param("until") Instant until);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Assignment a set a.remindedAt = :now where a.id = :id and a.remindedAt is null and a.status = 'ACTIVE'")
    int claimReminder(@Param("id") Long id, @Param("now") Instant now);

    /** [id, tên bộ câu hỏi, chế độ của bộ]. */
    @Query(nativeQuery = true, value = "SELECT b.id, b.title, b.mode FROM question_banks b WHERE b.id IN (:ids)")
    List<Object[]> bankTitles(@Param("ids") Collection<Long> ids);

    /** Người nhận của bài: danh sách riêng nếu có, không thì học viên đang học lớp. */
    @Query(nativeQuery = true, value = """
            SELECT t.user_id FROM assignment_targets t WHERE t.assignment_id = :id
            UNION
            SELECT m.user_id FROM class_members m WHERE m.class_id = :classId AND m.status = 'ACTIVE'
              AND NOT EXISTS (SELECT 1 FROM assignment_targets t2 WHERE t2.assignment_id = :id)
            """)
    List<Long> recipientIds(@Param("id") Long id, @Param("classId") Long classId);

    /**
     * [user_id, tên hiển thị, attempt id, nộp lúc, điểm AI, điểm tối đa, result_mode của lượt] của từng người nhận; lượt đã nộp ĐẦU TIÊN (bài thường chỉ có một; bài Nghe ép làm lại được, D-143), null nếu chưa nộp.
     * Người nhận tính như recipientIds.
     */
    @Query(nativeQuery = true, value = """
            SELECT u.user_id, p.display_name, x.id, x.submitted_at, x.score, x.max_score, x.result_mode FROM (
              SELECT t.user_id FROM assignment_targets t WHERE t.assignment_id = :id
              UNION
              SELECT m.user_id FROM class_members m WHERE m.class_id = :classId AND m.status = 'ACTIVE'
                AND NOT EXISTS (SELECT 1 FROM assignment_targets t2 WHERE t2.assignment_id = :id)
            ) u LEFT JOIN user_profiles p ON p.user_id = u.user_id
            LEFT JOIN student_exercise_attempts x ON x.id = (SELECT a.id FROM student_exercise_attempts a
                WHERE a.assignment_id = :id AND a.user_id = u.user_id AND a.submitted_at IS NOT NULL
                ORDER BY a.submitted_at ASC, a.id ASC LIMIT 1)
            ORDER BY u.user_id
            """)
    List<Object[]> progress(@Param("id") Long id, @Param("classId") Long classId);

    /** [attempt id, user id, điểm AI, điểm tối đa, nộp lúc]: lượt đã nộp của bài này. */
    @Query(nativeQuery = true, value = """
            SELECT a.id, a.user_id, a.score, a.max_score, a.submitted_at FROM student_exercise_attempts a
            WHERE a.id = :attemptId AND a.assignment_id = :assignmentId AND a.submitted_at IS NOT NULL
            """)
    List<Object[]> submittedAttempt(@Param("attemptId") Long attemptId, @Param("assignmentId") Long assignmentId);

    /** result_mode của lượt (rỗng nếu không có): Nghe ép (COUNT_ONLY) không đưa điểm cho học viên. */
    @Query(nativeQuery = true, value = "SELECT a.result_mode FROM student_exercise_attempts a WHERE a.id = :attemptId")
    List<String> resultModeOf(@Param("attemptId") Long attemptId);

    /** [attempt id, nộp lúc, điểm AI, điểm tối đa] lượt đã nộp ĐẦU TIÊN của một học viên cho bài này (D-143: trạng thái nộp theo lượt đầu). */
    @Query(nativeQuery = true, value = """
            SELECT a.id, a.submitted_at, a.score, a.max_score FROM student_exercise_attempts a
            WHERE a.assignment_id = :id AND a.user_id = :userId AND a.submitted_at IS NOT NULL ORDER BY a.submitted_at ASC, a.id ASC LIMIT 1
            """)
    List<Object[]> latestSubmitted(@Param("id") Long id, @Param("userId") Long userId);
}
