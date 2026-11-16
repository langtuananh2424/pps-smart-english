package com.pps.smartenglish.ai.repository;

import com.pps.smartenglish.ai.domain.WritingSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WritingSessionRepository extends JpaRepository<WritingSession, Long> {

    /** Luôn kèm userId: session của người khác coi như không tồn tại. */
    Optional<WritingSession> findByIdAndUserId(Long id, Long userId);

    /** [questionId, lần nộp gần nhất] của các câu người dùng đã nộp (updated_at được đặt lúc nộp). */
    @Query("select s.questionId, max(s.updatedAt) from WritingSession s where s.userId = :userId and s.status = 'SUBMITTED' "
            + "and s.questionId in :questionIds group by s.questionId")
    java.util.List<Object[]> lastSubmitted(@Param("userId") Long userId,
                                           @Param("questionIds") java.util.Collection<Long> questionIds);

    /** Nộp từ ACTIVE hoặc READY đúng một lần; 0 dòng = đã nộp (nộp đồng thời bằng hai khóa khác nhau). */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WritingSession s set s.status = 'SUBMITTED', s.gradeJobId = :jobId, s.updatedAt = :now "
            + "where s.id = :id and s.status in ('ACTIVE', 'READY')")
    int claimSubmit(@Param("id") Long id, @Param("jobId") Long jobId, @Param("now") Instant now);
}
