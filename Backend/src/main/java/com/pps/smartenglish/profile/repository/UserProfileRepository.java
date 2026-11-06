package com.pps.smartenglish.profile.repository;

import com.pps.smartenglish.profile.domain.UserProfile;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    /**
     * Một ngày có học mới: hôm qua có học thì streak + 1, ngược lại về 1; cập nhật best. Điều kiện last_active_date
     * cũ hơn hôm nay làm câu lệnh idempotent trong cùng ngày.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE user_profiles SET
                streak_current = CASE WHEN last_active_date = :yesterday THEN streak_current + 1 ELSE 1 END,
                streak_best = GREATEST(streak_best, CASE WHEN last_active_date = :yesterday THEN streak_current + 1 ELSE 1 END),
                last_active_date = :today,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId AND (last_active_date IS NULL OR last_active_date < :today)
            """)
    int advanceStreak(@Param("userId") Long userId, @Param("today") LocalDate today, @Param("yesterday") LocalDate yesterday);

    /** Cộng nguyên tử, không đọc-rồi-ghi. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserProfile p set p.xp = p.xp + :amount where p.userId = :userId")
    int addXp(@Param("userId") Long userId, @Param("amount") long amount);

    /** Người còn chuỗi đang dang dở: hôm qua có học, hôm nay chưa. Theo lô, sắp theo user_id để lấy trang kế bằng afterId. */
    @Query("select p from UserProfile p where p.streakCurrent > 0 and p.lastActiveDate = :yesterday and p.userId > :afterId "
            + "order by p.userId")
    List<UserProfile> findStreakReminderBatch(@Param("yesterday") LocalDate yesterday, @Param("afterId") Long afterId, Pageable page);
}
