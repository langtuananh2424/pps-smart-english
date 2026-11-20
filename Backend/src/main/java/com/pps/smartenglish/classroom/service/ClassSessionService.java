package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.domain.AttendanceMark;
import com.pps.smartenglish.classroom.domain.ClassMember;
import com.pps.smartenglish.classroom.domain.ClassRoom;
import com.pps.smartenglish.classroom.domain.ClassSession;
import com.pps.smartenglish.classroom.domain.ClassStaff;
import com.pps.smartenglish.classroom.dto.SessionDtos.CreateSession;
import com.pps.smartenglish.classroom.dto.SessionDtos.CreateTutoring;
import com.pps.smartenglish.classroom.dto.SessionDtos.PatchSession;
import com.pps.smartenglish.classroom.dto.SessionDtos.SessionView;
import com.pps.smartenglish.classroom.repository.AttendanceRepository;
import com.pps.smartenglish.classroom.repository.ClassMemberRepository;
import com.pps.smartenglish.classroom.repository.ClassRepository;
import com.pps.smartenglish.classroom.repository.ClassSessionRepository;
import com.pps.smartenglish.classroom.repository.ClassStaffRepository;
import com.pps.smartenglish.retention.service.NotificationService;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-SES-01..03: lịch buổi học nhóm (có lặp tuần), buổi kèm 1-1, đổi và hủy buổi. Quyền theo lớp qua ClassAccess (NFR-SEC-05).
 * Buổi đã điểm danh thì khóa đổi và hủy; điểm danh là sự thật về buổi đã diễn ra.
 */
@Service
public class ClassSessionService {

    public static final int MAX_OCCURRENCES = 100;
    public static final int MAX_RANGE_DAYS = 366;
    private static final int DEFAULT_PAST_DAYS = 7;
    private static final int DEFAULT_AHEAD_DAYS = 60;

    private final ClassSessionRepository sessions;
    private final AttendanceRepository attendance;
    private final ClassAccess access;
    private final ClassService classes;
    private final ClassRepository classRepo;
    private final ClassStaffRepository staff;
    private final ClassMemberRepository members;
    private final NotificationService notifications;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    ClassSessionService(ClassSessionRepository sessions, AttendanceRepository attendance, ClassAccess access, ClassService classes,
                        ClassRepository classRepo, ClassStaffRepository staff, ClassMemberRepository members,
                        NotificationService notifications, CmsWrites writes, AuditRecorder audit) {
        this.sessions = sessions;
        this.attendance = attendance;
        this.access = access;
        this.classes = classes;
        this.classRepo = classRepo;
        this.staff = staff;
        this.members = members;
        this.notifications = notifications;
        this.writes = writes;
        this.audit = audit;
    }

    /** Buổi nhóm, lặp hằng tuần đến hết repeatUntil nếu có. Tất cả cùng giờ trong ngày, cùng link. */
    @Transactional
    public List<SessionView> createGroup(long callerId, long classId, CreateSession req) {
        ClassRoom room = access.requireStaff(callerId, classId);
        ClassService.requireOpen(room);
        long teacher = req.taughtBy() == null ? callerId : req.taughtBy();
        if (!staff.isStaff(classId, teacher, classes.today())) {
            throw ApiException.validation("taughtBy", "Người dạy phải đang phụ trách lớp");
        }
        String url = meetingUrl(req.mode(), req.meetingUrl());
        LocalDate first = day(req.startsAt());
        LocalDate until = req.repeatUntil() == null ? first : req.repeatUntil();
        if (req.repeatUntil() != null && (until.isBefore(first) || until.isAfter(room.getEndsOn()))) {
            throw ApiException.validation("repeatUntil", "Ngày kết thúc lặp phải từ buổi đầu đến hết ngày của lớp");
        }
        var created = new ArrayList<ClassSession>();
        Instant s = req.startsAt();
        Instant e = req.endsAt();
        while (!day(s).isAfter(until)) {
            if (created.size() >= MAX_OCCURRENCES) {
                throw ApiException.validation("repeatUntil", "Tạo tối đa " + MAX_OCCURRENCES + " buổi một lần");
            }
            requireTimes(room, s, e);
            created.add(new ClassSession(classId, ClassSession.GROUP, null, req.mode(), s, e, url, teacher, callerId));
            s = s.plus(7, ChronoUnit.DAYS); // giờ Việt Nam không có giờ mùa hè nên cộng 7 ngày giữ nguyên giờ trong ngày
            e = e.plus(7, ChronoUnit.DAYS);
        }
        var saved = sessions.saveAllAndFlush(created);
        audit.record("SESSION_CREATE", "CLASS", classId, null, Map.of("count", saved.size(), "firstStartsAt", req.startsAt().toString()));
        return views(saved, 0);
    }

    /** Buổi kèm 1-1 (giáo vụ xếp): học viên đang học lớp, trợ giảng đang phụ trách lớp. */
    @Transactional
    public SessionView createTutoring(long callerId, long classId, CreateTutoring req) {
        ClassRoom room = access.requireStaff(callerId, classId);
        ClassService.requireOpen(room);
        if (!members.existsByClassIdAndUserIdAndStatus(classId, req.studentId(), ClassMember.ACTIVE)) {
            throw ApiException.validation("studentId", "Học viên không đang học lớp này");
        }
        if (!staff.existsLive(classId, req.assistantId(), ClassStaff.ASSISTANT, classes.today())) {
            throw ApiException.validation("assistantId", "Người này không phải trợ giảng của lớp");
        }
        requireTimes(room, req.startsAt(), req.endsAt());
        var s = writes.save(sessions, new ClassSession(classId, ClassSession.TUTORING, req.studentId(), req.mode(), req.startsAt(),
                req.endsAt(), meetingUrl(req.mode(), req.meetingUrl()), req.assistantId(), callerId), "session_conflict", "Không lưu được buổi học");
        var view = views(List.of(s), 0).get(0);
        audit.record("SESSION_CREATE", "CLASS", classId, null, view);
        return view;
    }

    @Transactional
    public SessionView update(long callerId, long classId, long sessionId, PatchSession req) {
        ClassRoom room = access.requireStaff(callerId, classId);
        ClassSession s = editable(room, sessionId);
        var before = views(List.of(s), 0).get(0);
        String mode = req.mode() != null ? req.mode() : s.getMode();
        String urlIn = req.meetingUrl() != null ? req.meetingUrl() : s.getMeetingUrl();
        Instant start = req.startsAt() != null ? req.startsAt() : s.getStartsAt();
        Instant end = req.endsAt() != null ? req.endsAt() : s.getEndsAt();
        requireTimes(room, start, end);
        s.change(start, end, mode, meetingUrl(mode, urlIn));
        sessions.saveAndFlush(s);
        var after = views(List.of(s), 0).get(0);
        notifyParticipants(s, false);
        audit.record("SESSION_UPDATE", "CLASS", classId, before, after);
        return after;
    }

    @Transactional
    public SessionView cancel(long callerId, long classId, long sessionId) {
        ClassRoom room = access.requireStaff(callerId, classId);
        ClassSession s = editable(room, sessionId);
        var before = views(List.of(s), 0).get(0);
        s.cancel();
        sessions.saveAndFlush(s);
        notifyParticipants(s, true);
        var after = views(List.of(s), 0).get(0);
        audit.record("SESSION_CANCEL", "CLASS", classId, before, after);
        return after;
    }

    /** Buổi bắt đầu trong [from, to] (ngày giờ Việt Nam). Học viên chỉ thấy buổi nhóm và buổi kèm của mình, kèm điểm danh của mình. */
    @Transactional(readOnly = true)
    public List<SessionView> list(long callerId, long classId, LocalDate from, LocalDate to) {
        var relation = access.relation(callerId, classId);
        LocalDate today = classes.today();
        LocalDate f = from == null ? today.minusDays(DEFAULT_PAST_DAYS) : from;
        LocalDate t = to == null ? f.plusDays(DEFAULT_PAST_DAYS + DEFAULT_AHEAD_DAYS) : to;
        if (t.isBefore(f) || ChronoUnit.DAYS.between(f, t) > MAX_RANGE_DAYS) {
            throw ApiException.validation("to", "Khoảng ngày phải từ ngày bắt đầu và tối đa " + MAX_RANGE_DAYS + " ngày");
        }
        boolean student = relation == ClassAccess.Relation.STUDENT;
        var rows = sessions.range(classId, f.atStartOfDay(ClassService.VN).toInstant(), t.plusDays(1).atStartOfDay(ClassService.VN).toInstant(),
                student ? callerId : 0);
        return views(rows, student ? callerId : 0);
    }

    /** Buổi thuộc lớp; sai lớp hoặc không có thì 404 (không lộ buổi của lớp khác). */
    ClassSession require(long classId, long sessionId) {
        return sessions.findByIdAndClassId(sessionId, classId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "session_not_found", "Không tìm thấy buổi học"));
    }

    /** Người tham gia buổi: nhóm = học viên đang học lớp; kèm 1-1 = đúng học viên của buổi. */
    List<Long> participants(ClassSession s) {
        if (s.isTutoring()) {
            return List.of(s.getStudentId());
        }
        return members.findByClassIdAndStatus(s.getClassId(), ClassMember.ACTIVE).stream().map(ClassMember::getUserId).toList();
    }

    private ClassSession editable(ClassRoom room, long sessionId) {
        ClassService.requireOpen(room);
        ClassSession s = require(room.getId(), sessionId);
        if (s.isCancelled()) {
            throw new ApiException(HttpStatus.CONFLICT, "session_cancelled", "Buổi học đã hủy");
        }
        if (attendance.existsBySessionId(s.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "session_attended", "Buổi đã điểm danh, không đổi hoặc hủy được");
        }
        return s;
    }

    private void notifyParticipants(ClassSession s, boolean cancelled) {
        participants(s).forEach(uid -> notifications.sessionChanged(uid, s.getClassId(), s.getId(), cancelled));
    }

    /** Giờ kết thúc sau giờ bắt đầu; cả hai nằm trong ngày của lớp (giờ Việt Nam). */
    private static void requireTimes(ClassRoom room, Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw ApiException.validation("endsAt", "Giờ kết thúc phải sau giờ bắt đầu");
        }
        LocalDate d1 = day(start);
        LocalDate d2 = day(end);
        if (d1.isBefore(room.getStartsOn()) || d2.isAfter(room.getEndsOn())) {
            throw ApiException.validation("startsAt", "Buổi học nằm ngoài ngày của lớp");
        }
    }

    /** Online bắt buộc link http(s); offline bỏ link. Chặn scheme khác (javascript:, file:) vì link được hiển thị cho học viên. */
    private static String meetingUrl(String mode, String raw) {
        if (!ClassSession.ONLINE.equals(mode)) {
            return null;
        }
        if (raw == null || raw.isBlank()) {
            throw ApiException.validation("meetingUrl", "Buổi online cần link Meet hoặc Zoom");
        }
        String url = raw.strip();
        try {
            URI u = new URI(url);
            if (u.getHost() == null || !("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw ApiException.validation("meetingUrl", "Link phải bắt đầu bằng http:// hoặc https://");
        }
        return url;
    }

    private static LocalDate day(Instant i) {
        return i.atZone(ClassService.VN).toLocalDate();
    }

    /** viewerId > 0: điền myStatus theo điểm danh của người đó. */
    private List<SessionView> views(List<ClassSession> rows, long viewerId) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(ClassSession::getId).toList();
        Map<Long, String> mine = new HashMap<>();
        if (viewerId > 0) {
            attendance.ofUser(viewerId, ids).forEach(m -> mine.put(m.getSessionId(), m.getStatus()));
        }
        Map<Long, Boolean> marked = new HashMap<>();
        if (viewerId == 0) {
            attendance.findBySessionIdIn(ids).forEach(m -> marked.put(m.getSessionId(), true));
        }
        Map<Long, String> names = new HashMap<>();
        classRepo.people(rows.stream().map(ClassSession::getTaughtBy).distinct().toList()).forEach(r -> names.put(((Number) r[0]).longValue(), (String) r[1]));
        return rows.stream().map(s -> new SessionView(s.getId(), s.getClassId(), s.getKind(), s.getStudentId(), s.getMode(), s.getStartsAt(),
                s.getEndsAt(), s.getMeetingUrl(), s.getTaughtBy(), names.get(s.getTaughtBy()), s.getStatus(),
                marked.containsKey(s.getId()) || mine.containsKey(s.getId()), mine.get(s.getId()))).toList();
    }
}
