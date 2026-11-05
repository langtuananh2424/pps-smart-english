package com.pps.smartenglish.ai.repository;

import com.pps.smartenglish.ai.domain.UserAiQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAiQuotaRepository extends JpaRepository<UserAiQuota, Long> {

    /** Cộng nguyên tử trong một câu UPDATE (không đọc-rồi-ghi); 0 dòng = chưa có dòng số dư. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserAiQuota q set q.balance = q.balance + :delta where q.userId = :userId")
    int addBalance(@Param("userId") Long userId, @Param("delta") int delta);
}
