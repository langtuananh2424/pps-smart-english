package com.pps.smartenglish.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SpeakingSubmissionRepository extends JpaRepository<SpeakingSubmission, Long> {

    /** Luôn kèm userId: lượt của người khác coi như không tồn tại. */
    Optional<SpeakingSubmission> findByIdAndUserId(Long id, Long userId);

    List<SpeakingSubmission> findByUserIdAndIdLessThanOrderByIdDesc(Long userId, Long before, Pageable page);

    List<SpeakingSubmission> findByUserIdAndQuestionIdAndIdLessThanOrderByIdDesc(Long userId, Long questionId, Long before, Pageable page);

    List<SpeakingSubmission> findByAssignmentIdAndStatusIn(Long assignmentId, java.util.Collection<String> statuses);

    List<SpeakingSubmission> findByAssignmentIdAndUserIdAndStatusIn(Long assignmentId, Long userId, java.util.Collection<String> statuses);

    /** Đã nộp bài giao này (trừ lượt `exceptId`). */
    boolean existsByAssignmentIdAndUserIdAndStatusInAndIdNot(Long assignmentId, Long userId, java.util.Collection<String> statuses, Long id);

    /** Xác nhận đúng một lần: 0 dòng nghĩa là có request khác đã xác nhận trước. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SpeakingSubmission s set s.status = 'GRADING', s.jobId = :jobId, s.sizeBytes = :size, s.durationSec = :duration, "
            + "s.updatedAt = :now, s.submittedAt = :now where s.id = :id and s.status = 'AWAITING_UPLOAD'")
    int claimUpload(@Param("id") Long id, @Param("jobId") Long jobId, @Param("size") long size,
                    @Param("duration") BigDecimal duration, @Param("now") Instant now);

    /** Ghi kết quả khi job xong; chỉ khi còn GRADING. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SpeakingSubmission s set s.status = :status, s.result = :result, s.updatedAt = :now "
            + "where s.id = :id and s.status = 'GRADING'")
    int finish(@Param("id") Long id, @Param("status") String status, @Param("result") JsonNode result, @Param("now") Instant now);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SpeakingSubmission s set s.status = 'FAILED', s.updatedAt = :now where s.jobId = :jobId and s.status = 'GRADING'")
    int failByJob(@Param("jobId") Long jobId, @Param("now") Instant now);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SpeakingSubmission s set s.status = 'EXPIRED', s.updatedAt = :now where s.id = :id and s.status = 'AWAITING_UPLOAD'")
    int expire(@Param("id") Long id, @Param("now") Instant now);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SpeakingSubmission s set s.audioDeletedAt = :now, s.updatedAt = :now where s.id = :id and s.audioDeletedAt is null")
    int markAudioDeleted(@Param("id") Long id, @Param("now") Instant now);

    @Query("select s from SpeakingSubmission s where s.status = 'AWAITING_UPLOAD' and s.uploadExpiresAt < :cutoff order by s.id")
    List<SpeakingSubmission> findStaleUploads(@Param("cutoff") Instant cutoff, Pageable page);

    @Query("select s from SpeakingSubmission s where s.status in ('GRADED', 'UNCLEAR', 'FAILED') and s.audioDeletedAt is null "
            + "and s.createdAt < :cutoff order by s.id")
    List<SpeakingSubmission> findAudioDue(@Param("cutoff") Instant cutoff, Pageable page);
}
