package com.pps.smartenglish.retention.repository;

import com.pps.smartenglish.retention.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByUserIdAndReadAtIsNull(Long userId);

    boolean existsByUserIdAndDedupeKey(Long userId, String dedupeKey);

    /** Luôn kèm userId: thông báo của người khác coi như không tồn tại. */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Query("select n from Notification n where n.userId = :userId and n.id < :before order by n.id desc")
    List<Notification> findPage(@Param("userId") Long userId, @Param("before") Long before, Pageable page);

    /** Đánh dấu đã đọc một lần; gọi lại giữ nguyên thời điểm đọc đầu tiên. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.id = :id and n.userId = :userId and n.readAt is null")
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("now") Instant now);

    /** Đánh dấu mọi thông báo chưa đọc của một người (D-93); trả số dòng đổi. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    /** Câu hỏi của phiên viết đã nộp bằng job này; web mở /viet/:questionId để xem kết quả. */
    @Query(value = "select question_id from writing_sessions where grade_job_id = :jobId", nativeQuery = true)
    Optional<Long> findWritingQuestionId(@Param("jobId") Long jobId);

    /** D-149: bộ bài của lượt của userId khi lượt đã nộp và không còn job chấm lần đầu nào chờ; rỗng nếu chưa xong. */
    @Query(value = "select b.id as \"bankId\", b.title as \"title\", a.extra as \"extra\" from student_exercise_attempts a "
            + "join question_banks b on b.id = a.bank_id where a.id = :attemptId and a.user_id = :userId and a.submitted_at is not null "
            + "and not exists (select 1 from ai_jobs j where j.attempt_id = a.id and j.regrade_item_id is null "
            + "and j.status in ('PENDING', 'RUNNING'))", nativeQuery = true)
    Optional<SettledAttempt> findSettledAttempt(@Param("attemptId") Long attemptId, @Param("userId") Long userId);

    interface SettledAttempt {
        Long getBankId();

        Boolean getExtra();

        String getTitle();
    }

    /** Ghi sent_at khi có ít nhất một lần push SENT; giữ lần đầu. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.sentAt = :now where n.id = :id and n.sentAt is null")
    int markSent(@Param("id") Long id, @Param("now") Instant now);
}
