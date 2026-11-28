package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.classroom.domain.ClassMember;
import com.pps.smartenglish.classroom.domain.ClassRoom;
import com.pps.smartenglish.classroom.domain.UnlockOverride;
import com.pps.smartenglish.classroom.dto.ProgressDtos.CreateUnlock;
import com.pps.smartenglish.classroom.dto.ProgressDtos.UnlockView;
import com.pps.smartenglish.classroom.repository.ClassMemberRepository;
import com.pps.smartenglish.classroom.repository.ClassReportRepository;
import com.pps.smartenglish.classroom.repository.UnlockOverrideRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-CLS-06, D-122: giáo viên, trợ giảng (hoặc giáo vụ) của lớp mở sớm một unit cho cả lớp hoặc từng học viên, bỏ qua điều kiện
 * học tuần tự của unit đó. Ghi người mở và thời điểm; không áp dụng cho khóa tắt học tuần tự. Thu hồi giữ lại dòng cũ.
 */
@Service
public class EarlyUnlockService {

    private final ClassAccess access;
    private final ClassService classes;
    private final CourseRepository courses;
    private final ClassReportRepository reports;
    private final ClassMemberRepository members;
    private final UnlockOverrideRepository unlocks;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    EarlyUnlockService(ClassAccess access, ClassService classes, CourseRepository courses, ClassReportRepository reports,
                       ClassMemberRepository members, UnlockOverrideRepository unlocks, CmsWrites writes, AuditRecorder audit) {
        this.access = access;
        this.classes = classes;
        this.courses = courses;
        this.reports = reports;
        this.members = members;
        this.unlocks = unlocks;
        this.writes = writes;
        this.audit = audit;
    }

    @Transactional
    public List<UnlockView> create(long callerId, long classId, CreateUnlock req) {
        ClassRoom room = access.requireStaff(callerId, classId);
        ClassService.requireOpen(room);
        var course = courses.findById(room.getCourseId()).orElseThrow();
        if (!course.isSequential()) {
            throw new ApiException(HttpStatus.CONFLICT, "not_sequential", "Khóa này không học tuần tự nên không cần mở sớm");
        }
        if (!reports.courseOfTopic(req.topicId()).contains(course.getId())) {
            throw ApiException.validation("topicId", "Unit không thuộc khóa của lớp");
        }
        Set<Long> chosen = req.studentIds() == null ? Set.of() : new HashSet<>(req.studentIds());
        Set<Long> active = new HashSet<>();
        members.findByClassIdAndStatus(classId, ClassMember.ACTIVE).forEach(m -> active.add(m.getUserId()));
        if (!active.containsAll(chosen)) {
            throw ApiException.validation("studentIds", "Có học viên không thuộc lớp");
        }
        List<Long> targets = new ArrayList<>(chosen);
        if (targets.isEmpty()) {
            targets.add(null); // cả lớp
        }
        List<UnlockView> out = new ArrayList<>();
        var callerNames = names(callerId);
        for (Long target : targets) {
            var saved = writes.save(unlocks, new UnlockOverride(classId, req.topicId(), target, callerId, classes.now()), "unlock_duplicate",
                    "Unit này đã được mở sớm cho đối tượng đã chọn");
            out.add(view(saved, callerNames));
        }
        audit.record("UNIT_UNLOCK", "CLASS", classId, null, out);
        return out;
    }

    /** Thu hồi (idempotent): học viên bị khóa lại nếu chưa xong unit trước. */
    @Transactional
    public UnlockView revoke(long callerId, long classId, long unlockId) {
        access.requireStaff(callerId, classId);
        UnlockOverride o = unlocks.findByIdAndClassId(unlockId, classId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "unlock_not_found", "Không tìm thấy lần mở sớm"));
        if (!o.isRevoked()) {
            o.revoke(classes.now());
            unlocks.saveAndFlush(o);
            audit.record("UNIT_UNLOCK_REVOKE", "CLASS", classId, null, view(o, names(o.getGrantedBy())));
        }
        return view(o, names(o.getGrantedBy()));
    }

    @Transactional(readOnly = true)
    public List<UnlockView> list(long callerId, long classId) {
        access.requireStaff(callerId, classId);
        var rows = unlocks.findByClassIdOrderByIdDesc(classId);
        var names = names(rows.stream().map(UnlockOverride::getGrantedBy).filter(java.util.Objects::nonNull).distinct().toArray(Long[]::new));
        return rows.stream().map(o -> view(o, names)).toList();
    }

    /** Tên người mở theo id, một truy vấn. */
    private Map<Long, String> names(Long... ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids.length > 0) {
            reports.displayNames(List.of(ids)).forEach(r -> out.put(((Number) r[0]).longValue(), (String) r[1]));
        }
        return out;
    }

    private static UnlockView view(UnlockOverride o, Map<Long, String> names) {
        return new UnlockView(o.getId(), o.getClassId(), o.getTopicId(), o.getUserId(), o.getGrantedBy(), names.get(o.getGrantedBy()), o.getGrantedAt(), o.getRevokedAt());
    }
}
