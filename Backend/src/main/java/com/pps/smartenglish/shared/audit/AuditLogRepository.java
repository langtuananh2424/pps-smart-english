package com.pps.smartenglish.shared.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Lọc theo người, đối tượng, khoảng thời gian [from, to); null thì bỏ điều kiện. Mới nhất trước, phân trang theo id. */
    @Query("select a from AuditLog a where a.id < :before and (:actorId is null or a.actorId = :actorId) "
            + "and (:targetType is null or a.targetType = :targetType) and (:targetId is null or a.targetId = :targetId) "
            + "and a.createdAt >= :from and a.createdAt < :to order by a.id desc")
    List<AuditLog> search(@Param("before") Long before, @Param("actorId") Long actorId,
                          @Param("targetType") String targetType, @Param("targetId") String targetId,
                          @Param("from") Instant from, @Param("to") Instant to, Pageable page);

    /** [user_id, email, tên hiển thị] của người thao tác, một query cho cả trang. */
    @Query(value = "select u.id, u.email, p.display_name from users u left join user_profiles p on p.user_id = u.id where u.id in (:ids)",
            nativeQuery = true)
    List<Object[]> actorsOf(@Param("ids") java.util.Collection<Long> ids);
}
