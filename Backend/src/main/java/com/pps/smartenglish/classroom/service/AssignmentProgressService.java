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
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final AiAssignmentSubmissions aiSubmissions;
    private final ClassAccess access;
    private final ClassService classes;
    private final com.pps.smartenglish.classroom.repository.ClassRepository classRepo;
    private final NotificationService notifications;
    private final AuditRecorder audit;
    private final com.pps.smartenglish.exercise.listening.ForcedAssignmentProgress forced;

    AssignmentProgressService(AssignmentService assignmentService, AssignmentRepository assignments, GradeOverrideRepository overrides,
                              AiAssignmentSubmissions aiSubmissions, ClassAccess access, ClassService classes, com.pps.smartenglish.classroom.repository.ClassRepository classRepo,
                              NotificationService notifications, AuditRecorder audit,
                              com.pps.smartenglish.exercise.listening.ForcedAssignmentProgress forced) {
        this.forced = forced;
        this.assignmentService = assignmentService;
        this.assignments = assignments;
        this.overrides = overrides;
        this.aiSubmissions = aiSubmissions;
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
        return rows(assignmentService.require(classId, assignmentId));
    }

    /** Tiến độ từng người nhận của một bài, không kiểm quyền: người gọi (bảng tiến độ lớp) đã kiểm. */
    List<ProgressRow> rows(Assignment a) {
        var rows = assignments.progress(a.getId(), a.getClassId());
        // bài giao Viết/Nói: query trên không có lượt làm bài nào, bài nộp lấy từ phiên viết và lượt nói
        Map<Long, AssignmentSubmission> ai = a.isAiQuestion() ? aiSubmissions.latestByUser(a) : Map.of();
        List<AssignmentSubmission> subs = rows.stream().map(r -> a.isAiQuestion() ? ai.get(((Number) r[0]).longValue()) : attempt(r)).toList();
        Map<Long, Latest> latest = latest(a, subs.stream().filter(Objects::nonNull).map(AssignmentSubmission::id).toList());
        Instant now = classes.now();
        // D-143: tiến độ Nghe ép chỉ dựng cho bài giao đã chốt Nghe ép lúc giao; D-142: ẩn điểm theo result_mode của từng lượt
        var forcedByUser = a.isForcedListening() ? forced.byUser(a.getBankId(), a.getId()) : Map.<Long, com.pps.smartenglish.exercise.listening.ListeningDtos.ForcedProgress>of();
        List<ProgressRow> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            AssignmentSubmission s = subs.get(i);
            Latest l = s == null ? null : latest.get(s.id());
            boolean noScore = !a.isAiQuestion() && "COUNT_ONLY".equals(r[6]);
            BigDecimal fin = l != null && l.score != null ? l.score : s == null ? null : s.score();
            out.add(new ProgressRow(((Number) r[0]).longValue(), (String) r[1], status(a.getDueAt(), s == null ? null : s.submittedAt(), now),
                    s == null ? null : s.id(), s == null ? null : s.submittedAt(), noScore || s == null ? null : s.score(),
                    noScore || s == null ? null : s.max(), noScore ? null : fin, l == null ? null : l.comment,
                    forcedByUser.get(((Number) r[0]).longValue())));
        }
        return out;
    }

    /** Dòng [user, tên, attempt id, nộp lúc, điểm AI, điểm tối đa] của AssignmentRepository.progress; null nếu chưa nộp. */
    private static AssignmentSubmission attempt(Object[] r) {
        return r[2] == null ? null : new AssignmentSubmission(((Number) r[0]).longValue(), ((Number) r[2]).longValue(),
                SqlTimes.instant(r[3]), (BigDecimal) r[4], r[5] == null ? null : ((Number) r[5]).intValue());
    }

    /** Kết quả của chính học viên: điểm AI hiện ngay, điểm và nhận xét của giáo viên khi có. */
    @Transactional(readOnly = true)
    public MyResult mine(long userId, long classId, long assignmentId) {
        Assignment a = assignmentService.require(classId, assignmentId);
        if (!assignments.isRecipient(assignmentId, userId)) {
            throw AssignmentService.notFound();
        }
        var sub = latestOf(a, userId);
        if (sub.isEmpty()) {
            return new MyResult(assignmentId, status(a.getDueAt(), null, classes.now()), null, null, null, null, null, null);
        }
        AssignmentSubmission r = sub.get();
        Latest l = latest(a, List.of(r.id())).get(r.id());
        // D-142: lượt Nghe ép chỉ biết số câu đúng, không có điểm hệ thống; điểm giáo viên tự chấm (nếu có) vẫn hiện
        boolean noScore = !a.isAiQuestion() && assignments.resultModeOf(r.id()).contains("COUNT_ONLY");
        BigDecimal aiScore = noScore ? null : r.score();
        return new MyResult(assignmentId, status(a.getDueAt(), r.submittedAt(), classes.now()), r.id(), r.submittedAt(), aiScore,
                noScore ? null : r.max(), l != null && l.score != null ? l.score : aiScore, l == null ? null : l.comment);
    }

    /** Bài nộp mới nhất của một học viên cho bài giao. */
    Optional<AssignmentSubmission> latestOf(Assignment a, long userId) {
        if (a.isAiQuestion()) {
            return aiSubmissions.latestOf(a, userId);
        }
        return assignments.latestSubmitted(a.getId(), userId).stream().findFirst().map(r -> new AssignmentSubmission(userId,
                ((Number) r[0]).longValue(), SqlTimes.instant(r[1]), (BigDecimal) r[2], r[3] == null ? null : ((Number) r[3]).intValue()));
    }

    /** Bài nộp có id này của bài giao (lượt đã nộp, phiên viết hoặc lượt nói đã nộp). */
    private Optional<AssignmentSubmission> submission(Assignment a, long id) {
        if (a.isAiQuestion()) {
            return aiSubmissions.byId(a, id);
        }
        return assignments.submittedAttempt(id, a.getId()).stream().findFirst().map(r -> new AssignmentSubmission(((Number) r[1]).longValue(),
                ((Number) r[0]).longValue(), SqlTimes.instant(r[4]), (BigDecimal) r[2], r[3] == null ? null : ((Number) r[3]).intValue()));
    }

    /** Sửa điểm hoặc nhận xét của một lượt đã nộp: ghi dòng mới, giữ lịch sử, báo học viên. Điểm không vượt điểm tối đa của bài. */
    @Transactional
    public GradeView grade(long callerId, long classId, long assignmentId, long attemptId, GradeRequest req) {
        access.requireStaff(callerId, classId);
        Assignment a = assignmentService.require(classId, assignmentId);
        if (!a.isAiQuestion() && assignments.resultModeOf(attemptId).contains("COUNT_ONLY")) { // D-142: lượt Nghe ép không có điểm để sửa
            throw new ApiException(HttpStatus.CONFLICT, "grade_not_supported", "Bài giao Nghe ép không có điểm nên không sửa điểm được");
        }
        String comment = req.comment() == null || req.comment().isBlank() ? null : req.comment().strip();
        if (req.score() == null && comment == null) {
            throw ApiException.validation("score", "Cần điểm hoặc nhận xét");
        }
        AssignmentSubmission sub = submission(a, attemptId).orElseThrow(AssignmentProgressService::attemptNotFound);
        if (req.score() != null && sub.max() != null && req.score().compareTo(BigDecimal.valueOf(sub.max())) > 0) {
            throw ApiException.validation("score", "Điểm vượt điểm tối đa của bài");
        }
        var row = overrides.saveAndFlush(GradeOverride.of(a.getQuestionType(), attemptId, req.score(), comment, callerId, classes.now()));
        notifications.assignmentGraded(sub.userId(), classId, assignmentId, row.getId());
        var view = view(row);
        audit.record("ASSIGNMENT_GRADE", "CLASS", classId, null, view);
        return view;
    }

    private static ApiException attemptNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt đã nộp của bài này");
    }

    @Transactional(readOnly = true)
    public List<GradeView> history(long callerId, long classId, long assignmentId, long attemptId) {
        access.requireStaff(callerId, classId);
        Assignment a = assignmentService.require(classId, assignmentId);
        submission(a, attemptId).orElseThrow(AssignmentProgressService::attemptNotFound);
        return overridesOf(a, List.of(attemptId)).stream().map(AssignmentProgressService::view).toList();
    }

    /** Các lần sửa của những bài nộp, cũ đến mới. */
    private List<GradeOverride> overridesOf(Assignment a, List<Long> ids) {
        if (a.isAiQuestion()) {
            return Assignment.WRITING.equals(a.getQuestionType()) ? overrides.findByWritingSessionIdInOrderByIdAsc(ids)
                    : overrides.findBySpeakingSubmissionIdInOrderByIdAsc(ids);
        }
        return overrides.findByAttemptIdInOrderByIdAsc(ids);
    }

    private record Latest(BigDecimal score, String comment) {}

    /** Điểm cuối và nhận xét cuối của từng lượt: dòng mới nhất có giá trị tương ứng. */
    private Map<Long, Latest> latest(Assignment a, List<Long> attemptIds) {
        Map<Long, Latest> out = new HashMap<>();
        if (attemptIds.isEmpty()) {
            return out;
        }
        for (GradeOverride o : overridesOf(a, attemptIds)) { // cũ đến mới nên dòng sau ghi đè
            Latest old = out.get(o.getSubmissionId());
            out.put(o.getSubmissionId(), new Latest(o.getScore() != null ? o.getScore() : old == null ? null : old.score,
                    o.getComment() != null ? o.getComment() : old == null ? null : old.comment));
        }
        return out;
    }

    private static GradeView view(GradeOverride o) {
        return new GradeView(o.getId(), o.getSubmissionId(), o.getScore(), o.getComment(), o.getGradedBy(), o.getGradedAt());
    }
}
