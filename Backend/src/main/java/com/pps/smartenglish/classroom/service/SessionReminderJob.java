package com.pps.smartenglish.classroom.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Mỗi phút nhắc các buổi học sắp bắt đầu (FR-SES-03). */
@Component
public class SessionReminderJob {

    private static final Logger log = LoggerFactory.getLogger(SessionReminderJob.class);

    private final SessionReminderService reminders;

    SessionReminderJob(SessionReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void run() {
        try {
            int n = reminders.sendDue();
            if (n > 0) {
                log.info("Đã nhắc {} buổi học sắp bắt đầu", n);
            }
        } catch (RuntimeException e) {
            log.warn("Job nhắc lịch học lỗi: {}", e.getMessage());
        }
    }
}
