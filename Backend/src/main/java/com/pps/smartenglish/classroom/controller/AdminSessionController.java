package com.pps.smartenglish.classroom.controller;

import com.pps.smartenglish.classroom.dto.SessionDtos.CreateTutoring;
import com.pps.smartenglish.classroom.dto.SessionDtos.SessionView;
import com.pps.smartenglish.classroom.dto.SessionDtos.SettingsView;
import com.pps.smartenglish.classroom.dto.SessionDtos.UpdateSettings;
import com.pps.smartenglish.classroom.service.ClassSessionService;
import com.pps.smartenglish.classroom.service.SessionReminderService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-SES-02 (giáo vụ xếp buổi kèm 1-1) và mốc nhắc lịch (D-124); nhóm route cần quyền class.manage (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/classes")
class AdminSessionController {

    private final ClassSessionService sessions;
    private final SessionReminderService reminders;

    AdminSessionController(ClassSessionService sessions, SessionReminderService reminders) {
        this.sessions = sessions;
        this.reminders = reminders;
    }

    @PostMapping("/{id}/tutoring")
    @ResponseStatus(HttpStatus.CREATED)
    SessionView tutoring(Authentication auth, @PathVariable String id, @Valid @RequestBody CreateTutoring req) {
        return sessions.createTutoring(CurrentUser.id(auth), PathId.parse(id, "class_not_found", "Không tìm thấy lớp"), req);
    }

    @GetMapping("/settings")
    SettingsView settings() {
        return reminders.settings();
    }

    @PutMapping("/settings")
    SettingsView updateSettings(@Valid @RequestBody UpdateSettings req) {
        return reminders.update(req);
    }
}
