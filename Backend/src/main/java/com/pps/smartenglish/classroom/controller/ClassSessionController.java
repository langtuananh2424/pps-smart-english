package com.pps.smartenglish.classroom.controller;

import com.pps.smartenglish.classroom.dto.SessionDtos.AttendanceRow;
import com.pps.smartenglish.classroom.dto.SessionDtos.ChangeView;
import com.pps.smartenglish.classroom.dto.SessionDtos.CreateSession;
import com.pps.smartenglish.classroom.dto.SessionDtos.MarkRequest;
import com.pps.smartenglish.classroom.dto.SessionDtos.PatchSession;
import com.pps.smartenglish.classroom.dto.SessionDtos.SessionView;
import com.pps.smartenglish.classroom.service.AttendanceService;
import com.pps.smartenglish.classroom.service.ClassSessionService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-SES-01, 03, 04: lịch và điểm danh của một lớp. Quyền theo lớp kiểm trong service qua ClassAccess (NFR-SEC-05). */
@RestController
@RequestMapping("/api/classes/{classId}/sessions")
class ClassSessionController {

    private static final String CLASS_NF = "class_not_found";
    private static final String CLASS_NF_MSG = "Không tìm thấy lớp";
    private static final String SESSION_NF = "session_not_found";
    private static final String SESSION_NF_MSG = "Không tìm thấy buổi học";

    private final ClassSessionService sessions;
    private final AttendanceService attendance;

    ClassSessionController(ClassSessionService sessions, AttendanceService attendance) {
        this.sessions = sessions;
        this.attendance = attendance;
    }

    @GetMapping
    List<SessionView> list(Authentication auth, @PathVariable String classId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return sessions.list(CurrentUser.id(auth), PathId.parse(classId, CLASS_NF, CLASS_NF_MSG), from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    List<SessionView> create(Authentication auth, @PathVariable String classId, @Valid @RequestBody CreateSession req) {
        return sessions.createGroup(CurrentUser.id(auth), PathId.parse(classId, CLASS_NF, CLASS_NF_MSG), req);
    }

    @PatchMapping("/{sessionId}")
    SessionView update(Authentication auth, @PathVariable String classId, @PathVariable String sessionId,
                       @Valid @RequestBody PatchSession req) {
        return sessions.update(CurrentUser.id(auth), PathId.parse(classId, CLASS_NF, CLASS_NF_MSG),
                PathId.parse(sessionId, SESSION_NF, SESSION_NF_MSG), req);
    }

    @PostMapping("/{sessionId}/cancel")
    SessionView cancel(Authentication auth, @PathVariable String classId, @PathVariable String sessionId) {
        return sessions.cancel(CurrentUser.id(auth), PathId.parse(classId, CLASS_NF, CLASS_NF_MSG),
                PathId.parse(sessionId, SESSION_NF, SESSION_NF_MSG));
    }

    @GetMapping("/{sessionId}/attendance")
    List<AttendanceRow> attendance(Authentication auth, @PathVariable String classId, @PathVariable String sessionId) {
        return attendance.get(CurrentUser.id(auth), PathId.parse(classId, CLASS_NF, CLASS_NF_MSG),
                PathId.parse(sessionId, SESSION_NF, SESSION_NF_MSG));
    }

    @PutMapping("/{sessionId}/attendance")
    List<AttendanceRow> mark(Authentication auth, @PathVariable String classId, @PathVariable String sessionId,
                             @Valid @RequestBody MarkRequest req) {
        return attendance.mark(CurrentUser.id(auth), PathId.parse(classId, CLASS_NF, CLASS_NF_MSG),
                PathId.parse(sessionId, SESSION_NF, SESSION_NF_MSG), req.marks());
    }

    @GetMapping("/{sessionId}/attendance/history")
    List<ChangeView> history(Authentication auth, @PathVariable String classId, @PathVariable String sessionId) {
        return attendance.history(CurrentUser.id(auth), PathId.parse(classId, CLASS_NF, CLASS_NF_MSG),
                PathId.parse(sessionId, SESSION_NF, SESSION_NF_MSG));
    }
}
