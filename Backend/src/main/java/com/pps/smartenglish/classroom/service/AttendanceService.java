package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.domain.AttendanceChange;
import com.pps.smartenglish.classroom.domain.AttendanceMark;
import com.pps.smartenglish.classroom.domain.ClassSession;
import com.pps.smartenglish.classroom.dto.SessionDtos.AttendanceRow;
import com.pps.smartenglish.classroom.dto.SessionDtos.ChangeView;
import com.pps.smartenglish.classroom.dto.SessionDtos.MarkInput;
import com.pps.smartenglish.classroom.repository.AttendanceChangeRepository;
import com.pps.smartenglish.classroom.repository.AttendanceRepository;
import com.pps.smartenglish.classroom.repository.ClassRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-SES-04: điểm danh tay do giáo viên, trợ giảng (hoặc giáo vụ) của lớp làm; học viên không tự điểm danh. Chỉ điểm danh buổi đã
 * bắt đầu. Sửa không giới hạn thời gian nhưng mỗi lần đổi giá trị đều ghi giá trị cũ, người sửa, thời điểm (D-124).
 */
@Service
public class AttendanceService {

    private final ClassSessionService sessions;
    private final ClassAccess access;
    private final ClassService classes;
    private final ClassRepository classRepo;
    private final AttendanceRepository marks;
    private final AttendanceChangeRepository changes;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    AttendanceService(ClassSessionService sessions, ClassAccess access, ClassService classes, ClassRepository classRepo,
                      AttendanceRepository marks, AttendanceChangeRepository changes, CmsWrites writes, AuditRecorder audit) {
        this.sessions = sessions;
        this.access = access;
        this.classes = classes;
        this.classRepo = classRepo;
        this.marks = marks;
        this.changes = changes;
        this.writes = writes;
        this.audit = audit;
    }

    @Transactional
    public List<AttendanceRow> mark(long callerId, long classId, long sessionId, List<MarkInput> input) {
        access.requireStaff(callerId, classId);
        ClassSession s = sessions.require(classId, sessionId);
        if (s.isCancelled()) {
            throw new ApiException(HttpStatus.CONFLICT, "session_cancelled", "Buổi học đã hủy");
        }
        Instant now = classes.now();
        if (s.getStartsAt().isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "session_not_started", "Buổi học chưa diễn ra, chưa điểm danh được");
        }
        Set<Long> allowed = new HashSet<>(sessions.participants(s));
        Set<Long> seen = new HashSet<>();
        for (MarkInput m : input) {
            if (!seen.add(m.userId())) {
                throw ApiException.validation("marks", "Một người chỉ điểm danh một lần trong mỗi yêu cầu");
            }
            if (!allowed.contains(m.userId())) {
                throw ApiException.validation("marks", "Có người không thuộc buổi học này");
            }
        }
        Map<Long, AttendanceMark> existing = new HashMap<>();
        marks.findBySessionId(sessionId).forEach(m -> existing.put(m.getUserId(), m));
        int edited = 0;
        for (MarkInput m : input) {
            AttendanceMark old = existing.get(m.userId());
            if (old == null) {
                writes.save(marks, new AttendanceMark(sessionId, m.userId(), m.status(), callerId, now),
                        "attendance_conflict", "Có người khác vừa điểm danh buổi này, hãy tải lại");
            } else if (!old.getStatus().equals(m.status())) {
                changes.save(new AttendanceChange(sessionId, m.userId(), old.getStatus(), m.status(), callerId, now));
                old.edit(m.status(), callerId, now);
                marks.save(old);
                edited++;
            }
        }
        marks.flush();
        audit.record("ATTENDANCE_MARK", "CLASS_SESSION", sessionId, null, Map.of("marked", input.size(), "edited", edited));
        return rows(s);
    }

    /** Người tham gia buổi kèm trạng thái điểm danh (null = chưa điểm danh). Chỉ nhân sự của lớp. */
    @Transactional(readOnly = true)
    public List<AttendanceRow> get(long callerId, long classId, long sessionId) {
        access.requireStaff(callerId, classId);
        return rows(sessions.require(classId, sessionId));
    }

    @Transactional(readOnly = true)
    public List<ChangeView> history(long callerId, long classId, long sessionId) {
        access.requireStaff(callerId, classId);
        sessions.require(classId, sessionId);
        return changes.findBySessionIdOrderByIdAsc(sessionId).stream()
                .map(c -> new ChangeView(c.getUserId(), c.getOldStatus(), c.getNewStatus(), c.getChangedBy(), c.getChangedAt())).toList();
    }

    /** Người đang tham gia cộng người đã có điểm danh (kể cả người sau đó rời lớp). */
    private List<AttendanceRow> rows(ClassSession s) {
        Map<Long, AttendanceMark> byUser = new HashMap<>();
        marks.findBySessionId(s.getId()).forEach(m -> byUser.put(m.getUserId(), m));
        Set<Long> ids = new LinkedHashSet<>(sessions.participants(s));
        ids.addAll(byUser.keySet());
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            classRepo.people(ids).forEach(r -> names.put(((Number) r[0]).longValue(), (String) r[1]));
        }
        return ids.stream().map(id -> {
            AttendanceMark m = byUser.get(id);
            return new AttendanceRow(id, names.get(id), m == null ? null : m.getStatus(), m == null ? null : m.getMarkedBy(),
                    m == null ? null : m.getMarkedAt());
        }).toList();
    }
}
