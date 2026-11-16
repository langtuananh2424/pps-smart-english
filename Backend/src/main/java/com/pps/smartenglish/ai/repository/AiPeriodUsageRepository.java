package com.pps.smartenglish.ai.repository;

import com.pps.smartenglish.ai.domain.AiPeriodUsage;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiPeriodUsageRepository extends JpaRepository<AiPeriodUsage, Long> {

    @Query("select u.used from AiPeriodUsage u where u.userId = :userId and u.courseId = :courseId and u.periodStart = :start")
    Optional<Integer> findUsed(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("start") LocalDate start);

    /** Tăng used khi còn đủ chỗ (used + n <= quota), không bao giờ vượt quota dù đồng thời; 0 dòng = không đủ hoặc chưa có dòng. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AiPeriodUsage u set u.used = u.used + :n where u.userId = :userId and u.courseId = :courseId "
            + "and u.periodStart = :start and u.used + :n <= :quota")
    int take(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("start") LocalDate start,
             @Param("n") int n, @Param("quota") int quota);

    /** Trả lượt về đúng kỳ đã trừ, không xuống dưới 0. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AiPeriodUsage u set u.used = case when u.used > :n then u.used - :n else 0 end "
            + "where u.userId = :userId and u.courseId = :courseId and u.periodStart = :start")
    int give(@Param("userId") Long userId, @Param("courseId") Long courseId, @Param("start") LocalDate start, @Param("n") int n);
}
