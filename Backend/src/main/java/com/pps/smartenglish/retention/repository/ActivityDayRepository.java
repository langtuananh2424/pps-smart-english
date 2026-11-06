package com.pps.smartenglish.retention.repository;

import com.pps.smartenglish.retention.domain.ActivityDay;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityDayRepository extends JpaRepository<ActivityDay, ActivityDay.Key> {

    /**
     * Không dùng ON CONFLICT vì H2 (test) không hỗ trợ. Chèn đồng thời: PK chặn trùng, bên thua ném
     * DataIntegrityViolationException và transaction của nó rollback (nên không cập nhật streak lần hai).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO activity_days (user_id, activity_date)
            SELECT :userId, :day
            WHERE NOT EXISTS (SELECT 1 FROM activity_days WHERE user_id = :userId AND activity_date = :day)
            """)
    int insertIfAbsent(@Param("userId") Long userId, @Param("day") LocalDate day);
}
