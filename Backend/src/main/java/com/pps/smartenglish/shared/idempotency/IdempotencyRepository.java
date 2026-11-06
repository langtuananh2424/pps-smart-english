package com.pps.smartenglish.shared.idempotency;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, Long> {

    @Query("select r from IdempotencyRecord r where r.userId = :userId and r.idemKey = :key")
    Optional<IdempotencyRecord> find(@Param("userId") Long userId, @Param("key") String key);

    /**
     * Điền kết quả bằng UPDATE theo id thay vì sửa entity: action có thể đã xóa persistence context (UPDATE trừ lượt dùng
     * clearAutomatically), khi đó thay đổi trên entity cũ sẽ mất và dòng giữ chỗ còn trống.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("update IdempotencyRecord r set r.responseStatus = :status, r.responseBody = :body where r.id = :id")
    int complete(@Param("id") Long id, @Param("status") int status, @Param("body") String body);

    @Transactional
    @Modifying
    @Query("delete from IdempotencyRecord r where r.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
