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

    /** Ghi sent_at khi có ít nhất một lần push SENT; giữ lần đầu. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.sentAt = :now where n.id = :id and n.sentAt is null")
    int markSent(@Param("id") Long id, @Param("now") Instant now);
}
