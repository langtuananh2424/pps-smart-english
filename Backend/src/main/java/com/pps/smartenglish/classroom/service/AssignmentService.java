package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.domain.Assignment;
import com.pps.smartenglish.classroom.domain.AssignmentTarget;
import com.pps.smartenglish.classroom.domain.ClassMember;
import com.pps.smartenglish.classroom.domain.ClassRoom;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.AssignmentView;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.CreateAssignment;
import com.pps.smartenglish.classroom.repository.AssignmentRepository;
import com.pps.smartenglish.classroom.repository.AssignmentTargetRepository;
import com.pps.smartenglish.classroom.repository.ClassMemberRepository;
import com.pps.smartenglish.exercise.dto.AttemptStarted;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.service.AttemptService;
import com.pps.smartenglish.retention.service.NotificationService;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import com.pps.smartenglish.shared.persistence.SqlTimes;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-ASG-01, FR-ASG-02: giáo viên, trợ giảng giao một bộ câu hỏi đã xuất bản của khóa cho cả lớp hoặc một số học viên, kèm hạn.
 * Không giao trùng (cùng bài, lớp, hạn) nhờ unique ở DB. Học viên làm qua luồng lượt làm bài thường; bài giao không trừ lượt AI
 * (D-130) và được mở riêng dù unit chưa mở (BankGate hỏi AssignedBanks).
 */
@Service
public class AssignmentService {

    public static final int LIST_LIMIT = 50;

    private final AssignmentRepository assignments;
    private final AssignmentTargetRepository targets;
    private final ClassAccess access;
    private final ClassService classes;
    private final ClassMemberRepository members;
    private final QuestionBankRepository banks;
    private final AttemptService attempts;
    private final NotificationService notifications;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    AssignmentService(AssignmentRepository assignments, AssignmentTargetRepository targets, ClassAccess access, ClassService classes,
                      ClassMemberRepository members, QuestionBankRepository banks, AttemptService attempts,
                      NotificationService notifications, CmsWrites writes, AuditRecorder audit) {
        this.assignments = assignments;
        this.targets = targets;
        this.access = access;
        this.classes = classes;
        this.members = members;
        this.banks = banks;
        this.attempts = attempts;
        this.notifications = notifications;
        this.writes = writes;
        this.audit = audit;
    }

    @Transactional
    public AssignmentView create(long callerId, long classId, CreateAssignment req) {
        ClassRoom room = access.requireStaff(callerId, classId);
        ClassService.requireOpen(room);
        if (!req.dueAt().isAfter(classes.now())) {
            throw ApiException.validation("dueAt", "Hạn nộp phải ở tương lai");
        }
        // findInfo chỉ trả bộ câu hỏi mà cả chuỗi bài, unit, level, khóa đều đã xuất bản
        banks.findInfo(req.bankId()).orElseThrow(() -> ApiException.validation("bankId", "Bộ câu hỏi không tồn tại hoặc chưa xuất bản"));
        if (!room.getCourseId().equals(banks.findCourseId(req.bankId()).orElse(null))) {
            throw ApiException.validation("bankId", "Bộ câu hỏi không thuộc khóa của lớp");
        }
        Set<Long> chosen = req.studentIds() == null ? Set.of() : new HashSet<>(req.studentIds());
        Set<Long> active = new HashSet<>();
        members.findByClassIdAndStatus(classId, ClassMember.ACTIVE).forEach(m -> active.add(m.getUserId()));
        if (!active.containsAll(chosen)) {
            throw ApiException.validation("studentIds", "Có học viên không thuộc lớp");
        }
        var a = writes.save(assignments, new Assignment(classId, req.bankId(), req.dueAt(), callerId), "assignment_duplicate",
                "Bài này đã được giao cho lớp với cùng hạn nộp");
        targets.saveAll(chosen.stream().map(u -> new AssignmentTarget(a.getId(), u)).toList());
        (chosen.isEmpty() ? active : chosen).forEach(u -> notifications.assignmentNew(u, classId, a.getId()));
        var view = views(List.of(a), 0).get(0);
        audit.record("ASSIGNMENT_CREATE", "CLASS", classId, null, view);
        return view;
    }

    /** Hủy bài giao (idempotent): lượt đã nộp vẫn còn, bài không hiện cho học viên nữa. */
    @Transactional
    public AssignmentView cancel(long callerId, long classId, long assignmentId) {
        access.requireStaff(callerId, classId);
        Assignment a = require(classId, assignmentId);
        if (a.isActive()) {
            a.cancel();
            assignments.saveAndFlush(a);
            audit.record("ASSIGNMENT_CANCEL", "CLASS", classId, null, Map.of("assignmentId", assignmentId));
        }
        return views(List.of(a), 0).get(0);
    }

    /** Nhân sự thấy mọi bài của lớp (kể cả đã hủy); học viên chỉ thấy bài còn hiệu lực dành cho mình, kèm trạng thái của mình. */
    @Transactional(readOnly = true)
    public List<AssignmentView> list(long callerId, long classId) {
        var relation = access.relation(callerId, classId);
        if (relation == ClassAccess.Relation.STUDENT) {
            return views(assignments.forStudentInClass(classId, callerId, PageRequest.of(0, LIST_LIMIT)), callerId);
        }
        return views(assignments.findByClassIdOrderByIdDesc(classId, PageRequest.of(0, LIST_LIMIT)), 0);
    }

    /** Mọi bài còn hiệu lực của các lớp học viên đang học, hạn gần nhất trước. */
    @Transactional(readOnly = true)
    public List<AssignmentView> mine(long userId) {
        return views(assignments.forStudent(userId, PageRequest.of(0, LIST_LIMIT)), userId);
    }

    /** Bắt đầu hoặc làm tiếp bài được giao; chỉ người nhận. Trả lượt làm bài, nộp qua /api/attempts/{id}/submit như bài thường. */
    @Transactional
    public AttemptStarted start(long userId, long classId, long assignmentId) {
        Assignment a = require(classId, assignmentId);
        if (!assignments.isRecipient(assignmentId, userId)) {
            throw notFound();
        }
        return attempts.startAssigned(a.getBankId(), userId, assignmentId);
    }

    Assignment require(long classId, long assignmentId) {
        return assignments.findByIdAndClassId(assignmentId, classId).orElseThrow(AssignmentService::notFound);
    }

    static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "assignment_not_found", "Không tìm thấy bài được giao");
    }

    /** viewerId > 0: điền trạng thái của học viên đó. */
    private List<AssignmentView> views(List<Assignment> rows, long viewerId) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(Assignment::getId).toList();
        Map<Long, String> titles = new HashMap<>();
        assignments.bankTitles(rows.stream().map(Assignment::getBankId).distinct().toList())
                .forEach(r -> titles.put(((Number) r[0]).longValue(), (String) r[1]));
        Set<Long> withTargets = new HashSet<>();
        targets.findByAssignmentIdIn(ids).forEach(t -> withTargets.add(t.getAssignmentId()));
        Instant now = classes.now();
        return rows.stream().map(a -> new AssignmentView(a.getId(), a.getClassId(), a.getBankId(), titles.get(a.getBankId()), a.getDueAt(),
                a.getStatus(), !withTargets.contains(a.getId()), viewerId > 0 ? myStatus(a, viewerId, now) : null)).toList();
    }

    private String myStatus(Assignment a, long userId, Instant now) {
        var row = assignments.latestSubmitted(a.getId(), userId);
        Instant submittedAt = row.isEmpty() ? null : SqlTimes.instant(row.get(0)[1]);
        return AssignmentProgressService.status(a.getDueAt(), submittedAt, now);
    }
}
