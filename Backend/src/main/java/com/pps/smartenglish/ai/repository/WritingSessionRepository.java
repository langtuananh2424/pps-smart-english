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

    /** Nộp từ ACTIVE hoặc READY đúng một lần; 0 dòng = đã nộp (nộp đồng thời bằng hai khóa khác nhau). */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WritingSession s set s.status = 'SUBMITTED', s.gradeJobId = :jobId, s.updatedAt = :now "
            + "where s.id = :id and s.status in ('ACTIVE', 'READY')")
    int claimSubmit(@Param("id") Long id, @Param("jobId") Long jobId, @Param("now") Instant now);
}
