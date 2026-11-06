package com.pps.smartenglish.retention.service;

import com.pps.smartenglish.profile.domain.UserProfile;
import com.pps.smartenglish.profile.repository.UserProfileRepository;
import com.pps.smartenglish.retention.domain.Notification;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Nhắc giữ chuỗi (Q-20): người có chuỗi, hôm qua có học, hôm nay chưa. Dedupe STREAK:{ngày} bằng chèn-nếu-chưa-có nên
 * chạy nhiều vòng trong khung 20:00 hay nhiều instance cùng chạy cũng chỉ gửi một lần cho mỗi người.
 */
@Service
public class StreakReminderService {

    static final int BATCH = 500;
    static final Duration PUSH_TTL = Duration.ofHours(4);
    private static final Logger log = LoggerFactory.getLogger(StreakReminderService.class);

    private final UserProfileRepository profiles;
    private final NotificationService notifications;
    private final Today today;

    StreakReminderService(UserProfileRepository profiles, NotificationService notifications, Today today) {
        this.profiles = profiles;
        this.notifications = notifications;
        this.today = today;
    }

    /** Trả số thông báo vừa tạo. Lỗi từng người được bắt và log, không dừng cả lô. */
    public int remindAll() {
        LocalDate d = today.date();
        int created = 0;
        long after = 0;
        List<UserProfile> batch;
        do {
            batch = profiles.findStreakReminderBatch(d.minusDays(1), after, PageRequest.of(0, BATCH));
            for (UserProfile p : batch) {
                after = p.getUserId();
                try {
                    created += remind(p, d) ? 1 : 0;
                } catch (RuntimeException e) {
                    log.warn("Nhắc streak lỗi cho một người dùng: {}", e.getClass().getSimpleName());
                }
            }
        } while (batch.size() == BATCH);
        return created;
    }

    private boolean remind(UserProfile p, LocalDate d) {
        return notifications.notifyOnce(p.getUserId(), Notification.Type.STREAK_REMINDER,
                "Giữ chuỗi " + p.getStreakCurrent() + " ngày nhé!", "Học một bài hôm nay để không mất chuỗi.", "/",
                "STREAK:" + d, PUSH_TTL).isPresent();
    }
}
