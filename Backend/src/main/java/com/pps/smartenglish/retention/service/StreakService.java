package com.pps.smartenglish.retention.service;

import com.pps.smartenglish.profile.repository.UserProfileRepository;
import com.pps.smartenglish.retention.repository.ActivityDayRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** FR-RET-01: ghi ngày có học và cập nhật chuỗi. Transaction riêng để lỗi ở đây không kéo theo nơi phát sự kiện. */
@Service
public class StreakService {

    private final ActivityDayRepository days;
    private final UserProfileRepository profiles;
    private final Today today;

    StreakService(ActivityDayRepository days, UserProfileRepository profiles, Today today) {
        this.days = days;
        this.profiles = profiles;
        this.today = today;
    }

    /**
     * Chèn activity_days nếu chưa có; chỉ khi chèn được mới cập nhật chuỗi, nên nhiều sự kiện cùng ngày chỉ tính một.
     * Hai request đồng thời: bên thua ném DataIntegrityViolationException và rollback, người gọi bỏ qua.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordActivity(long userId) {
        LocalDate d = today.date();
        if (days.insertIfAbsent(userId, d) == 0) {
            return false;
        }
        profiles.advanceStreak(userId, d, d.minusDays(1));
        return true;
    }
}
