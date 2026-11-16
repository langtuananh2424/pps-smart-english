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

    /** Trừ n lượt, không bao giờ âm dù nhiều request đồng thời; 0 dòng = không đủ. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserAiQuota q set q.balance = q.balance - :n where q.userId = :userId and q.balance >= :n")
    int debit(@Param("userId") Long userId, @Param("n") int n);

    @Query("select q.balance from UserAiQuota q where q.userId = :userId")
    Optional<Integer> findBalance(@Param("userId") Long userId);
}
