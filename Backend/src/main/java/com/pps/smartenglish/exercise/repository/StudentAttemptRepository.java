package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.StudentAttempt;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentAttemptRepository extends JpaRepository<StudentAttempt, Long> {

    /** Khóa dòng lượt đến hết transaction: nộp bài và nhận điểm AI muộn tuần tự với nhau (D-145). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StudentAttempt a where a.id = :id")
    Optional<StudentAttempt> lockById(@Param("id") Long id);

    /** Khóa dòng lượt (đã nộp hay chưa) đến hết transaction, không nạp entity. */
    @Query(value = "select id from student_exercise_attempts where id = :id for update", nativeQuery = true)
    List<Long> lockRow(@Param("id") Long id);

    /** Khóa dòng lượt nếu còn mở; rỗng = đã nộp hoặc không có. Đọc scalar nên không dính bản đã cache trong transaction. */
    @Query(value = "select id from student_exercise_attempts where id = :id and submitted_at is null for update", nativeQuery = true)
    List<Long> lockIfOpen(@Param("id") Long id);

    /** Luôn kèm userId: attempt của người khác coi như không tồn tại. */
    Optional<StudentAttempt> findByIdAndUserId(Long id, Long userId);

    /** bankId null = mọi bộ; openOnly = chỉ lượt chưa nộp. */
    @Query("select a from StudentAttempt a where a.userId = :userId and a.id < :before "
            + "and (:bankId is null or a.bankId = :bankId) and (:openOnly = false or a.submittedAt is null) order by a.id desc")
    List<StudentAttempt> findPage(@Param("userId") Long userId, @Param("before") Long before,
                                  @Param("bankId") Long bankId, @Param("openOnly") boolean openOnly, Pageable page);

    /** Nộp bài nguyên tử: chỉ một trong các request đồng thời thấy submitted_at IS NULL; 0 dòng = đã nộp rồi. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StudentAttempt a set a.submittedAt = :now, a.score = :score, a.maxScore = :maxScore "
            + "where a.id = :id and a.submittedAt is null")
    int claimSubmission(@Param("id") Long id, @Param("now") Instant now, @Param("score") BigDecimal score,
                        @Param("maxScore") int maxScore);

    /**
     * Tính lại điểm lượt đã nộp bằng tổng credit các câu (bỏ qua câu chưa chấm), nguyên tử và idempotent: dùng sau khi AI chấm
     * xong và sau khi chấm lại, không cộng dồn delta nên không trôi làm tròn.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StudentAttempt a set a.score = (select coalesce(sum(s.credit), 0) from StudentAnswer s where s.attemptId = a.id) "
            + "where a.id = :id and a.submittedAt is not null")
    int recomputeScore(@Param("id") Long id);

    /** Lượt có giờ đang chạy (chưa tạm dừng, chưa nộp): ứng viên của AttemptTimeoutJob. */
    @Query("select a from StudentAttempt a where a.submittedAt is null and a.timeLimitSec is not null and a.resumedAt is not null")
    List<StudentAttempt> findRunningTimed();

    long countByUserIdAndBankId(Long userId, Long bankId);

    /** Bài được giao (FR-ASG-02): lượt đang làm dở, và đã nộp chưa. */
    Optional<StudentAttempt> findFirstByAssignmentIdAndUserIdAndSubmittedAtIsNullOrderByIdDesc(Long assignmentId, Long userId);

    boolean existsByAssignmentIdAndUserIdAndSubmittedAtIsNotNull(Long assignmentId, Long userId);

    /** Lượt đang làm dở (chưa nộp) mới nhất của người dùng, mọi bộ. */
    Optional<StudentAttempt> findFirstByUserIdAndSubmittedAtIsNullOrderByIdDesc(Long userId);

    /** [bankId, lần nộp gần nhất] của các bộ người dùng đã nộp. */
    @Query("select a.bankId, max(a.submittedAt) from StudentAttempt a where a.userId = :userId and a.bankId in :bankIds "
            + "and a.submittedAt is not null group by a.bankId")
    List<Object[]> lastSubmitted(@Param("userId") Long userId, @Param("bankIds") java.util.Collection<Long> bankIds);

    /** Lượt đang làm dở (chưa nộp) mới nhất của bài thi. */
    Optional<StudentAttempt> findFirstByUserIdAndBankIdAndSubmittedAtIsNullOrderByIdDesc(Long userId, Long bankId);

    Optional<StudentAttempt> findFirstByUserIdAndBankIdAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(Long userId, Long bankId);

    /** Khóa dòng user để hai request start song song của cùng một người chạy tuần tự (đếm lượt thi không bị vượt). */
    @Query(value = "select id from users where id = :userId for update", nativeQuery = true)
    List<Long> lockUser(@Param("userId") Long userId);

    @Query("select coalesce(a.score, 0) from StudentAttempt a where a.id = :id")
    BigDecimal scoreOf(@Param("id") Long id);
}
