package com.pps.smartenglish.commerce.repository;

import com.pps.smartenglish.commerce.domain.ShopOrder;
import java.time.Instant;
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
}
