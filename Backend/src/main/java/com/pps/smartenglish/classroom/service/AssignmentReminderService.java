package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.domain.Assignment;
import com.pps.smartenglish.classroom.repository.AssignmentRepository;
import com.pps.smartenglish.classroom.repository.ClassSettingsRepository;
import com.pps.smartenglish.retention.service.NotificationService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-ASG-02, D-124: nhắc những người nhận chưa nộp trước hạn theo số giờ admin cài. Mỗi bài được nhận bằng một UPDATE có điều kiện
 * (claimReminder) nên nhiều instance chạy job vẫn nhắc đúng một lần.
 */
@Service
public class AssignmentReminderService {

    private final ClassSettingsRepository settings;
    private final AssignmentRepository assignments;
    private final ClassService classes;
    private final NotificationService notifications;

    AssignmentReminderService(ClassSettingsRepository settings, AssignmentRepository assignments, ClassService classes,
                              NotificationService notifications) {
        this.settings = settings;
        this.assignments = assignments;
        this.classes = classes;
        this.notifications = notifications;
    }

    /** Trả số bài đã nhắc. */
    @Transactional
    public int sendDue() {
        Instant now = classes.now();
        Instant until = now.plus(Duration.ofHours(settings.findById(1).orElseThrow().getAssignmentReminderHours()));
        int sent = 0;
        for (Assignment a : assignments.dueForReminder(now, until)) {
            if (assignments.claimReminder(a.getId(), now) == 1) {
                // r[2] null = chưa có lượt đã nộp
                assignments.progress(a.getId(), a.getClassId()).stream().filter(r -> r[2] == null)
                        .forEach(r -> notifications.assignmentDue(((Number) r[0]).longValue(), a.getClassId(), a.getId()));
                sent++;
            }
        }
        return sent;
    }
}
