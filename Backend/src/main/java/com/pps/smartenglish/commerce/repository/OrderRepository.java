package com.pps.smartenglish.commerce.repository;

import com.pps.smartenglish.commerce.domain.ShopOrder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OrderRepository extends JpaRepository<ShopOrder, Long> {

    Optional<ShopOrder> findByCode(String code);

    /** Luôn kèm userId: đơn của người khác coi như không tồn tại. */
    Optional<ShopOrder> findByCodeAndUserId(String code, Long userId);

    boolean existsByCode(String code);

    /**
     * Kích hoạt nguyên tử theo trạng thái cũ: webhook trùng hoặc đồng thời chỉ một lần thấy 1 dòng bị sửa.
     * EXPIRED vẫn nhận vì tiền đã về (khách đã trả).
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ShopOrder o set o.status = 'PAID', o.paidAt = :now, o.updatedAt = :now "
            + "where o.id = :id and o.status in ('PENDING', 'EXPIRED')")
    int markPaid(@Param("id") Long id, @Param("now") Instant now);

    /** Một câu UPDATE nên nhiều instance chạy job cùng lúc vẫn an toàn. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ShopOrder o set o.status = 'EXPIRED', o.updatedAt = :now "
            + "where o.status = 'PENDING' and o.expiresAt < :now")
    int expirePending(@Param("now") Instant now);

    /**
     * Doanh thu theo sản phẩm: [id, tên, loại, số lượng bán, tổng giá lúc mua], giảm dần theo doanh thu. Chỉ đơn PAID có
     * paid_at trong kỳ (đơn từng EXPIRED rồi về PAID vẫn tính vì trạng thái hiện tại là PAID).
     */
    @Query(nativeQuery = true, value = """
            SELECT p.id, p.name, p.type, COUNT(i.id), SUM(i.price_vnd)
            FROM order_items i JOIN orders o ON o.id = i.order_id JOIN products p ON p.id = i.product_id
            WHERE o.status = 'PAID' AND o.paid_at >= :start AND o.paid_at < :end
            GROUP BY p.id, p.name, p.type ORDER BY SUM(i.price_vnd) DESC, p.id
            """)
    List<Object[]> revenueByProduct(@Param("start") Instant start, @Param("end") Instant end);

    /** Doanh thu theo ngày paid_at của múi giờ zone: [ngày, tổng]. Truy vấn con vì lý do như UserRepository.registrationsByDay. */
    @Query(nativeQuery = true, value = """
            SELECT d, SUM(price) FROM (
                SELECT CAST(o.paid_at AT TIME ZONE CAST(:zone AS varchar(64)) AS date) AS d, i.price_vnd AS price
                FROM orders o JOIN order_items i ON i.order_id = o.id
                WHERE o.status = 'PAID' AND o.paid_at >= :start AND o.paid_at < :end) t
            GROUP BY d ORDER BY d
            """)
    List<Object[]> revenueByDay(@Param("zone") String zone, @Param("start") Instant start, @Param("end") Instant end);

    /**
     * Chuyển đổi của cohort học viên đăng ký trong kỳ: [cohort, có đơn PAID, chỉ có enrollment từ link]. Buộc phải JOIN
     * bảng users của auth vì cohort được định nghĩa bằng users.created_at; đây là ngoại lệ có chủ đích của quy tắc
     * "không đụng bảng module khác", chỉ đọc và chỉ ở truy vấn báo cáo.
     */
    @Query(nativeQuery = true, value = """
            SELECT COUNT(*),
                   SUM(CASE WHEN EXISTS (SELECT 1 FROM orders o WHERE o.user_id = u.id AND o.status = 'PAID') THEN 1 ELSE 0 END),
                   SUM(CASE WHEN NOT EXISTS (SELECT 1 FROM orders o WHERE o.user_id = u.id AND o.status = 'PAID')
                             AND EXISTS (SELECT 1 FROM user_course_enrollments e WHERE e.user_id = u.id AND e.order_id IS NULL)
                            THEN 1 ELSE 0 END)
            FROM users u WHERE u.role = 'STUDENT' AND u.created_at >= :start AND u.created_at < :end
            """)
    List<Object[]> conversionCounts(@Param("start") Instant start, @Param("end") Instant end);
}
