package com.pps.smartenglish.ai.repository;

import com.pps.smartenglish.ai.domain.UserAiQuota;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAiQuotaRepository extends JpaRepository<UserAiQuota, Long> {

    /** Cộng nguyên tử trong một câu UPDATE (không đọc-rồi-ghi); 0 dòng = chưa có dòng số dư. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserAiQuota q set q.balance = q.balance + :delta where q.userId = :userId")
    int addBalance(@Param("userId") Long userId, @Param("delta") int delta);

    /** Trừ 1 lượt, không bao giờ âm dù nhiều request đồng thời; 0 dòng = hết lượt. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserAiQuota q set q.balance = q.balance - 1 where q.userId = :userId and q.balance >= 1")
    int debitOne(@Param("userId") Long userId);

    @Query("select q.balance from UserAiQuota q where q.userId = :userId")
    Optional<Integer> findBalance(@Param("userId") Long userId);
}
