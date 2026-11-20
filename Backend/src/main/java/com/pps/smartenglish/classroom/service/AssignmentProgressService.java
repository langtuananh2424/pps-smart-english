package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.domain.Assignment;
import com.pps.smartenglish.classroom.domain.GradeOverride;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.GradeRequest;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.GradeView;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.MyResult;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.ProgressRow;
import com.pps.smartenglish.classroom.repository.AssignmentRepository;
import com.pps.smartenglish.classroom.repository.GradeOverrideRepository;
import com.pps.smartenglish.retention.service.NotificationService;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.SqlTimes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-ASG-03, FR-ASG-04: theo dõi từng bài (đã nộp, chưa nộp, nộp trễ, điểm) và điểm, nhận xét của giáo viên. Điểm AI hiện ngay
 * (D-113); mỗi lần giáo viên sửa là một dòng grade_overrides mới, điểm cuối là dòng mới nhất có điểm.
 */
@Service
public class AssignmentProgressService {

    public static final String NOT_SUBMITTED = "NOT_SUBMITTED";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String LATE = "LATE";
    public static final String OVERDUE = "OVERDUE";

    private final AssignmentService assignmentService;
    private final AssignmentRepository assignments;
    private final GradeOverrideRepository overrides;
    private final ClassAccess access;
    private final ClassService classes;
    private final com.pps.smartenglish.classroom.repository.ClassRepository classRepo;
    private final NotificationService notifications;
    private final AuditRecorder audit;

    AssignmentProgressService(AssignmentService assignmentService, AssignmentRepository assignments, GradeOverrideRepository overrides,
                              ClassAccess access, ClassService classes, com.pps.smartenglish.classroom.repository.ClassRepository classRepo,
                              NotificationService notifications, AuditRecorder audit) {
        this.assignmentService = assignmentService;
        this.assignments = assignments;
        this.overrides = overrides;
        this.access = access;
        this.classes = classes;
        this.classRepo = classRepo;
        this.notifications = notifications;
        this.audit = audit;
    }

    /** Trạng thái theo hạn: đã nộp thì SUBMITTED hoặc LATE (nộp sau hạn, D-121); chưa nộp thì NOT_SUBMITTED hoặc OVERDUE. */
    static String status(Instant due, Instant submittedAt, Instant now) {
        if (submittedAt != null) {
            return submittedAt.isAfter(due) ? LATE : SUBMITTED;
        }
        return now.isAfter(due) ? OVERDUE : NOT_SUBMITTED;
    }

    @Transactional(readOnly = true)
    public List<ProgressRow> progress(long callerId, long classId, long assignmentId) {
        access.requireStaff(callerId, classId);
        Assignment a = assignmentService.require(classId, assignmentId);
        var rows = assignments.progress(assignmentId, classId);
        Map<Long, Latest> latest = latest(rows.stream().filter(r -> r[2] != null).map(r -> ((Number) r[2]).longValue()).toList());
        Instant now = classes.now();
        return rows.stream().map(r -> {
            Long attemptId = r[2] == null ? null : ((Number) r[2]).longValue();
            Instant submittedAt = SqlTimes.instant(r[3]);
            BigDecimal ai = (BigDecimal) r[4];
            Latest l = attemptId == null ? null : latest.get(attemptId);
            BigDecimal fin = l != null && l.score != null ? l.score : ai;
            return new ProgressRow(((Number) r[0]).longValue(), (String) r[1], status(a.getDueAt(), submittedAt, now), attemptId, submittedAt, ai,
                    r[5] == null ? null : ((Number) r[5]).intValue(), fin, l == null ? null : l.comment);
        }).toList();
    }

    /** Kết quả của chính học viên: điểm AI hiện ngay, điểm và nhận xét của giáo viên khi có. */
    @Transactional(readOnly = true)
    public MyResult mine(long userId, long classId, long assignmentId) {
        Assignment a = assignmentService.require(classId, assignmentId);
        if (!assignments.isRecipient(assignmentId, userId)) {
            throw AssignmentService.notFound();
        }
        var row = assignments.latestSubmitted(assignmentId, userId);
        if (row.isEmpty()) {
            return new MyResult(assignmentId, status(a.getDueAt(), null, classes.now()), null, null, null, null, null, null);
        }
        Object[] r = row.get(0);
        long attemptId = ((Number) r[0]).longValue();
        Instant submittedAt = SqlTimes.instant(r[1]);
        BigDecimal ai = (BigDecimal) r[2];
        Latest l = latest(List.of(attemptId)).get(attemptId);
        return new MyResult(assignmentId, status(a.getDueAt(), submittedAt, classes.now()), attemptId, submittedAt, ai,
                r[3] == null ? null : ((Number) r[3]).intValue(), l != null && l.score != null ? l.score : ai, l == null ? null : l.comment);
    }

    /** Sửa điểm hoặc nhận xét của một lượt đã nộp: ghi dòng mới, giữ lịch sử, báo học viên. Điểm không vượt điểm tối đa của bài. */
    @Transactional
    public GradeView grade(long callerId, long classId, long assignmentId, long attemptId, GradeRequest req) {
        access.requireStaff(callerId, classId);
        assignmentService.require(classId, assignmentId);
        String comment = req.comment() == null || req.comment().isBlank() ? null : req.comment().strip();
        if (req.score() == null && comment == null) {
            throw ApiException.validation("score", "Cần điểm hoặc nhận xét");
        }
        var rows = assignments.submittedAttempt(attemptId, assignmentId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt đã nộp của bài này");
        }
        Object[] r = rows.get(0);
        if (req.score() != null && r[3] != null && req.score().compareTo(BigDecimal.valueOf(((Number) r[3]).longValue())) > 0) {
            throw ApiException.validation("score", "Điểm vượt điểm tối đa của bài");
        }
        var row = overrides.saveAndFlush(new GradeOverride(attemptId, req.score(), comment, callerId, classes.now()));
        notifications.assignmentGraded(((Number) r[1]).longValue(), classId, assignmentId, row.getId());
        var view = view(row);
        audit.record("ASSIGNMENT_GRADE", "CLASS", classId, null, view);
        return view;
    }

    @Transactional(readOnly = true)
    public List<GradeView> history(long callerId, long classId, long assignmentId, long attemptId) {
        access.requireStaff(callerId, classId);
        assignmentService.require(classId, assignmentId);
        if (assignments.submittedAttempt(attemptId, assignmentId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt đã nộp của bài này");
        }
        return overrides.findByAttemptIdOrderByIdAsc(attemptId).stream().map(AssignmentProgressService::view).toList();
    }

    private record Latest(BigDecimal score, String comment) {}

    /** Điểm cuối và nhận xét cuối của từng lượt: dòng mới nhất có giá trị tương ứng. */
    private Map<Long, Latest> latest(List<Long> attemptIds) {
        Map<Long, Latest> out = new HashMap<>();
        if (attemptIds.isEmpty()) {
            return out;
        }
        for (GradeOverride o : overrides.findByAttemptIdInOrderByIdAsc(attemptIds)) { // cũ đến mới nên dòng sau ghi đè
            Latest old = out.get(o.getAttemptId());
            out.put(o.getAttemptId(), new Latest(o.getScore() != null ? o.getScore() : old == null ? null : old.score,
                    o.getComment() != null ? o.getComment() : old == null ? null : old.comment));
        }
        return out;
    }

    private static GradeView view(GradeOverride o) {
        return new GradeView(o.getId(), o.getAttemptId(), o.getScore(), o.getComment(), o.getGradedBy(), o.getGradedAt());
    }
}
