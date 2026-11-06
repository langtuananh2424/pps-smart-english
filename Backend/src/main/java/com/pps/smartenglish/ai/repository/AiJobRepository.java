package com.pps.smartenglish.ai.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.ai.domain.AiJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AiJobRepository extends JpaRepository<AiJob, Long> {

    Optional<AiJob> findByIdAndUserId(Long id, Long userId);

    long countByStatus(String status);

    /** Job chưa xong của một lượt làm bài (cho pendingJobs). */
    @Query("select j.id from AiJob j where j.attemptId = :attemptId and j.status in ('PENDING', 'RUNNING') order by j.id")
    List<Long> findPendingIdsByAttempt(@Param("attemptId") Long attemptId);

    /** Session đang chờ AI: không cho gửi thêm bước nào cho tới khi job xong. */
    @Query("select count(j) > 0 from AiJob j where j.sessionId = :sessionId and j.status in ('PENDING', 'RUNNING') "
            + "and j.kind in ('SCAFFOLD_FOLLOWUP', 'SCAFFOLD_DRAFT')")
    boolean existsScaffoldInFlight(@Param("sessionId") Long sessionId);

    /**
     * Khóa các job đến hạn mà không chờ worker khác (SKIP LOCKED). Gồm cả job RUNNING quá locked_until
     * (worker trước chết giữa chừng): giao hàng "ít nhất một lần".
     */
    @Query(value = "select id from ai_jobs where (status = 'PENDING' and run_after <= :now) "
            + "or (status = 'RUNNING' and locked_until < :now) order by run_after, id limit :limit for update skip locked",
            nativeQuery = true)
    List<Long> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AiJob j set j.status = 'RUNNING', j.attempts = j.attempts + 1, j.lockedUntil = :until, j.updatedAt = :now "
            + "where j.id = :id")
    int markRunning(@Param("id") Long id, @Param("until") Instant until, @Param("now") Instant now);

    /** Mọi ghi kết quả đều kèm attempts: worker cũ (lease đã bị lấy lại) ghi muộn thì 0 dòng và bị bỏ qua. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AiJob j set j.status = 'DONE', j.output = :output, j.error = null, j.lockedUntil = null, j.updatedAt = :now "
            + "where j.id = :id and j.status = 'RUNNING' and j.attempts = :attempts")
    int markDone(@Param("id") Long id, @Param("attempts") int attempts, @Param("output") JsonNode output,
                 @Param("now") Instant now);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AiJob j set j.status = 'PENDING', j.runAfter = :runAfter, j.error = :error, j.lockedUntil = null, "
            + "j.updatedAt = :now where j.id = :id and j.status = 'RUNNING' and j.attempts = :attempts")
    int markRetry(@Param("id") Long id, @Param("attempts") int attempts, @Param("runAfter") Instant runAfter,
                  @Param("error") String error, @Param("now") Instant now);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AiJob j set j.status = 'FAILED', j.error = :error, j.lockedUntil = null, j.updatedAt = :now "
            + "where j.id = :id and j.status = 'RUNNING' and j.attempts = :attempts")
    int markFailed(@Param("id") Long id, @Param("attempts") int attempts, @Param("error") String error,
                   @Param("now") Instant now);
}
