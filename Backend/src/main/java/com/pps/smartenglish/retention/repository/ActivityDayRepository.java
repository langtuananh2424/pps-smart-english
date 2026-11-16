package com.pps.smartenglish.retention.repository;

import com.pps.smartenglish.retention.domain.ActivityDay;
import java.time.LocalDate;
import java.util.List;
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

    /** Các ngày có học trong [from, to], tăng dần. */
    @Query("select a.activityDate from ActivityDay a where a.userId = :userId and a.activityDate between :from and :to "
            + "order by a.activityDate")
    List<LocalDate> findDays(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
