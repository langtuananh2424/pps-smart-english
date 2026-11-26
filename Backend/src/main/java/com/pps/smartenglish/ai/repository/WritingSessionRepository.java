package com.pps.smartenglish.ai.repository;

import com.pps.smartenglish.ai.domain.WritingSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WritingSessionRepository extends JpaRepository<WritingSession, Long> {

    /** Luôn kèm userId: session của người khác coi như không tồn tại. */
    Optional<WritingSession> findByIdAndUserId(Long id, Long userId);

    List<WritingSession> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long before, Pageable page);

    /** Phiên gắn một bài giao của chính học viên (D-130), tách khỏi phiên tự luyện cùng câu. */
    List<WritingSession> findByUserIdAndAssignmentIdAndIdLessThanOrderByIdDesc(Long userId, Long assignmentId, Long before, Pageable page);

    List<WritingSession> findByUserIdAndQuestionIdAndIdLessThanOrderByIdDesc(Long userId, Long questionId, Long before, Pageable page);

    /** [questionId, lần nộp gần nhất] của các câu người dùng đã nộp (updated_at được đặt lúc nộp). */
    @Query("select s.questionId, max(s.updatedAt) from WritingSession s where s.userId = :userId and s.status = 'SUBMITTED' "
            + "and s.questionId in :questionIds group by s.questionId")
    java.util.List<Object[]> lastSubmitted(@Param("userId") Long userId,
                                           @Param("questionIds") java.util.Collection<Long> questionIds);

    /** Phiên đang làm dở của bài giao: dùng lại thay vì mở thêm phiên (mỗi bài giao nộp một lần). */
    Optional<WritingSession> findFirstByAssignmentIdAndUserIdAndStatusInOrderByIdDesc(Long assignmentId, Long userId,
                                                                                      java.util.Collection<String> statuses);

    /** Phiên gắn với lượt làm bài, cũ trước mới sau (D-145: câu nhiều phiên thì lấy phiên mới nhất). */
    List<WritingSession> findByAttemptIdOrderByIdAsc(Long attemptId);

    Optional<WritingSession> findFirstByAttemptIdAndQuestionIdOrderByIdDesc(Long attemptId, Long questionId);

    List<WritingSession> findByAssignmentIdAndStatus(Long assignmentId, String status);

    List<WritingSession> findByAssignmentIdAndUserIdAndStatus(Long assignmentId, Long userId, String status);

    /** Đã nộp bài giao này mà chấm không lỗi (job lỗi hẳn thì cho nộp lại). */
    @Query("select count(s) > 0 from WritingSession s where s.assignmentId = :assignmentId and s.userId = :userId "
            + "and s.status = 'SUBMITTED' and not exists (select 1 from AiJob j where j.id = s.gradeJobId and j.status = 'FAILED')")
    boolean hasSubmittedAssignment(@Param("assignmentId") Long assignmentId, @Param("userId") Long userId);

    /** Nộp từ ACTIVE hoặc READY đúng một lần; 0 dòng = đã nộp (nộp đồng thời bằng hai khóa khác nhau). */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WritingSession s set s.status = 'SUBMITTED', s.gradeJobId = :jobId, s.updatedAt = :now, s.submittedAt = :now "
            + "where s.id = :id and s.status in ('ACTIVE', 'READY')")
    int claimSubmit(@Param("id") Long id, @Param("jobId") Long jobId, @Param("now") Instant now);
}
