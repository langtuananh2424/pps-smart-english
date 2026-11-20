package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.domain.ClassSession;
import com.pps.smartenglish.classroom.dto.SessionDtos.SettingsView;
import com.pps.smartenglish.classroom.dto.SessionDtos.UpdateSettings;
import com.pps.smartenglish.classroom.repository.ClassSessionRepository;
import com.pps.smartenglish.classroom.repository.ClassSettingsRepository;
import com.pps.smartenglish.retention.service.NotificationService;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-SES-03, D-124: nhắc học viên trước giờ học theo số phút admin cài. Mỗi buổi được nhận bằng một UPDATE có điều kiện
 * (claimReminder) nên nhiều instance chạy job cùng lúc vẫn nhắc đúng một lần; đổi giờ thì buổi được nhắc lại.
 */
@Service
public class SessionReminderService {

    private final ClassSettingsRepository settings;
    private final ClassSessionRepository sessions;
    private final ClassSessionService sessionService;
    private final ClassService classes;
    private final NotificationService notifications;
    private final AuditRecorder audit;

    SessionReminderService(ClassSettingsRepository settings, ClassSessionRepository sessions, ClassSessionService sessionService,
                           ClassService classes, NotificationService notifications, AuditRecorder audit) {
        this.settings = settings;
        this.sessions = sessions;
        this.sessionService = sessionService;
        this.classes = classes;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public SettingsView settings() {
        var row = settings.findById(1).orElseThrow();
        return new SettingsView(row.getReminderMinutes(), row.getAssignmentReminderHours());
    }

    @Transactional
    public SettingsView update(UpdateSettings req) {
        var row = settings.findById(1).orElseThrow();
        var before = new SettingsView(row.getReminderMinutes(), row.getAssignmentReminderHours());
        row.setReminderMinutes(req.reminderMinutes());
        if (req.assignmentReminderHours() != null) {
            row.setAssignmentReminderHours(req.assignmentReminderHours());
        }
        settings.saveAndFlush(row);
        var after = new SettingsView(row.getReminderMinutes(), row.getAssignmentReminderHours());
        audit.record("CLASS_SETTINGS_UPDATE", "CLASS_SETTINGS", 1, before, after);
        return after;
    }

    /** Gửi nhắc cho các buổi bắt đầu trong khoảng mốc nhắc. Trả số buổi đã nhắc. */
    @Transactional
    public int sendDue() {
        Instant now = classes.now();
        Instant until = now.plus(Duration.ofMinutes(settings.findById(1).orElseThrow().getReminderMinutes()));
        int sent = 0;
        for (ClassSession s : sessions.dueForReminder(now, until)) {
            if (sessions.claimReminder(s.getId(), now) == 1) {
                sessionService.participants(s).forEach(uid ->
                        notifications.sessionReminder(uid, s.getClassId(), s.getId(), s.getStartsAt().toEpochMilli()));
                sent++;
            }
        }
        return sent;
    }
}
