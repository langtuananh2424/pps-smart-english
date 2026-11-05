package com.pps.smartenglish.auth.repository;

import com.pps.smartenglish.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("select t from RefreshToken t where t.userId = :userId and t.revokedAt is null and t.expiresAt > :now order by t.createdAt desc")
    List<RefreshToken> findActive(@Param("userId") Long userId, @Param("now") Instant now);

    /** UPDATE có điều kiện: trả 0 nghĩa là token đã bị thu hồi/dùng rồi. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
    int revokeIfActive(@Param("id") Long id, @Param("now") Instant now);

    /** Xoay vòng: thu hồi và ghi thời điểm thay thế để phân biệt với hủy chủ động. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.replacedAt = :now where t.id = :id and t.revokedAt is null")
    int rotate(@Param("id") Long id, @Param("now") Instant now);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.userId = :userId and t.revokedAt is null")
    int revokeOwned(@Param("id") Long id, @Param("userId") Long userId, @Param("now") Instant now);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAll(@Param("userId") Long userId, @Param("now") Instant now);
}
