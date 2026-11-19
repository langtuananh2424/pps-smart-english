package com.pps.smartenglish.support.repository;

import com.pps.smartenglish.support.domain.SupportTicket;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Mọi đổi trạng thái là một UPDATE có điều kiện: hai nhân viên bấm nhận cùng lúc chỉ một người thắng. */
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    /** Luôn kèm userId: ticket của người khác coi như không tồn tại. */
    Optional<SupportTicket> findByIdAndUserId(Long id, Long userId);

    Optional<SupportTicket> findFirstByUserIdAndStatusNotOrderByIdDesc(Long userId, String status);

    List<SupportTicket> findByUserIdOrderByIdDesc(Long userId, Pageable page);

    /** Nhận xử lý: ticket mở, hoặc đang xử lý bởi chính người này (gọi lại không lỗi). 0 dòng = đã có người khác nhận hoặc đã đóng. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SupportTicket t set t.status = 'IN_PROGRESS', t.assignedTo = :staff, t.updatedAt = :now "
            + "where t.id = :id and (t.status = 'OPEN' or (t.status = 'IN_PROGRESS' and t.assignedTo = :staff))")
    int claim(@Param("id") Long id, @Param("staff") Long staff, @Param("now") Instant now);

    /** Học viên nhắn tiếp vào ticket đã đóng thì mở lại, ai cũng nhận được. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SupportTicket t set t.status = 'OPEN', t.assignedTo = null, t.closedAt = null, t.updatedAt = :now "
            + "where t.id = :id and t.status = 'CLOSED'")
    int reopen(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SupportTicket t set t.status = 'CLOSED', t.closedAt = :now, t.updatedAt = :now "
            + "where t.id = :id and t.status <> 'CLOSED'")
    int close(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SupportTicket t set t.lastMessageAt = :now, t.updatedAt = :now where t.id = :id")
    int touch(@Param("id") Long id, @Param("now") Instant now);

    /**
     * Danh sách cho nhân viên: [id, tiêu đề, trạng thái, mã học viên, email, tên hiển thị, người nhận, tin nhắn cuối, tạo lúc].
     * status '' = mọi trạng thái (không dùng NULL vì PostgreSQL không suy ra kiểu tham số null). Con trỏ theo id giảm dần.
     */
    @Query(nativeQuery = true, value = """
            SELECT t.id, t.subject, t.status, t.user_id, u.email, p.display_name, t.assigned_to, t.last_message_at, t.created_at
            FROM support_tickets t JOIN users u ON u.id = t.user_id LEFT JOIN user_profiles p ON p.user_id = u.id
            WHERE t.id < :before AND (:status = '' OR t.status = :status)
            ORDER BY t.id DESC LIMIT :max
            """)
    List<Object[]> staffPage(@Param("status") String status, @Param("before") long before, @Param("max") int max);

    /** Cùng cột với staffPage cho một ticket. */
    @Query(nativeQuery = true, value = """
            SELECT t.id, t.subject, t.status, t.user_id, u.email, p.display_name, t.assigned_to, t.last_message_at, t.created_at
            FROM support_tickets t JOIN users u ON u.id = t.user_id LEFT JOIN user_profiles p ON p.user_id = u.id
            WHERE t.id = :id
            """)
    List<Object[]> staffRow(@Param("id") long id);
}
