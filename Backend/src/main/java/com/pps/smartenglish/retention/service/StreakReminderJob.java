package com.pps.smartenglish.retention.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chạy mỗi 5 phút trong khung 20:00-20:55 giờ Việt Nam (Q-20); nhiều vòng để khởi động lại giữa khung vẫn gửi được.
 * Tắt bằng app.retention.jobs-enabled=false.
 */
@Component
@ConditionalOnProperty(prefix = "app.retention", name = "jobs-enabled", havingValue = "true", matchIfMissing = true)
class StreakReminderJob {

    private static final Logger log = LoggerFactory.getLogger(StreakReminderJob.class);

    private final StreakReminderService reminders;

    StreakReminderJob(StreakReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(cron = "0 */5 20 * * *", zone = "Asia/Ho_Chi_Minh")
    void run() {
        try {
            int n = reminders.remindAll();
            if (n > 0) {
                log.info("Đã tạo {} nhắc giữ chuỗi", n);
            }
        } catch (RuntimeException e) {
            log.warn("Job nhắc streak lỗi: {}", e.toString());
        }
    }
}
