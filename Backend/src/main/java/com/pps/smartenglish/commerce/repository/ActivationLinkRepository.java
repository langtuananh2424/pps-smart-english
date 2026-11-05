package com.pps.smartenglish.commerce.repository;

import com.pps.smartenglish.commerce.domain.ActivationLink;
import com.pps.smartenglish.commerce.dto.ActivationLinkView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ActivationLinkRepository extends JpaRepository<ActivationLink, Long> {

    Optional<ActivationLink> findByTokenHash(String tokenHash);

    /** Không có token: danh sách admin chỉ thấy hash gián tiếp qua id. */
    @Query("select new com.pps.smartenglish.commerce.dto.ActivationLinkView(l.id, l.productId, p.name, l.maxUses, "
            + "l.usedCount, l.expiresAt, l.revokedAt, l.note) "
            + "from ActivationLink l, Product p where p.id = l.productId and l.id < :before order by l.id desc")
    List<ActivationLinkView> findPage(@Param("before") Long before, Pageable page);

    /**
     * Giữ một lượt dùng: một UPDATE có điều kiện nên nhiều người đồng thời không vượt max_uses.
     * 0 dòng = hết lượt, đã hết hạn hoặc đã thu hồi.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ActivationLink l set l.usedCount = l.usedCount + 1, l.updatedAt = :now "
            + "where l.id = :id and l.usedCount < l.maxUses and l.revokedAt is null and l.expiresAt > :now")
    int claimUse(@Param("id") Long id, @Param("now") Instant now);

    /** Chỉ đặt lần đầu nên gọi lại không đổi thời điểm thu hồi cũ. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ActivationLink l set l.revokedAt = :now, l.updatedAt = :now where l.id = :id and l.revokedAt is null")
    int revoke(@Param("id") Long id, @Param("now") Instant now);
}
