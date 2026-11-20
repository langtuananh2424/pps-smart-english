package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.auth.service.PermissionService;
import com.pps.smartenglish.classroom.domain.ClassRoom;
import com.pps.smartenglish.classroom.domain.ClassStaff;
import com.pps.smartenglish.classroom.dto.ClassDtos.AssignStaff;
import com.pps.smartenglish.classroom.dto.ClassDtos.StaffView;
import com.pps.smartenglish.classroom.repository.ClassRepository;
import com.pps.smartenglish.classroom.repository.ClassStaffRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.persistence.CmsWrites;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-CLS-04: một giáo viên chính và nhiều trợ giảng cho mỗi lớp, có khoảng ngày phụ trách. Đổi giáo viên chính thì thu hồi
 * người cũ ngay trong cùng transaction (khóa dòng lớp nên không có lúc hai người cùng đang phụ trách).
 */
@Service
public class ClassStaffService {

    private final ClassService classes;
    private final ClassRepository classRepo;
    private final ClassStaffRepository staff;
    private final PermissionService permissions;
    private final CmsWrites writes;
    private final AuditRecorder audit;

    ClassStaffService(ClassService classes, ClassRepository classRepo, ClassStaffRepository staff, PermissionService permissions,
                      CmsWrites writes, AuditRecorder audit) {
        this.classes = classes;
        this.classRepo = classRepo;
        this.staff = staff;
        this.permissions = permissions;
        this.writes = writes;
        this.audit = audit;
    }

    @Transactional
    public StaffView assignTeacher(long classId, AssignStaff req) {
        ClassRoom room = classes.lock(classId);
        ClassService.requireOpen(room);
        LocalDate today = classes.today();
        LocalDate from = req.from() == null ? today : req.from();
        requireRoleAndRange(req, ClassStaff.TEACHER, from, today);
        if (from.isAfter(today)) {
            throw ApiException.validation("from", "Giáo viên chính phải bắt đầu phụ trách từ hôm nay trở về trước");
        }
        staff.revokeTeachers(classId, classes.now());
        var row = writes.save(staff, new ClassStaff(classId, req.userId(), ClassStaff.TEACHER, from, req.to()),
                "staff_conflict", "Không lưu được phân công");
        var view = view(row, today);
        audit.record("CLASS_TEACHER_ASSIGN", "CLASS", classId, null, view);
        return view;
    }

    @Transactional
    public StaffView addAssistant(long classId, AssignStaff req) {
        ClassRoom room = classes.lock(classId);
        ClassService.requireOpen(room);
        LocalDate today = classes.today();
        LocalDate from = req.from() == null ? today : req.from();
        requireRoleAndRange(req, ClassStaff.ASSISTANT, from, today);
        if (staff.existsLive(classId, req.userId(), ClassStaff.ASSISTANT, today)) {
            throw new ApiException(HttpStatus.CONFLICT, "already_assigned", "Người này đã là trợ giảng của lớp");
        }
        var row = writes.save(staff, new ClassStaff(classId, req.userId(), ClassStaff.ASSISTANT, from, req.to()),
                "staff_conflict", "Không lưu được phân công");
        var view = view(row, today);
        audit.record("CLASS_ASSISTANT_ASSIGN", "CLASS", classId, null, view);
        return view;
    }

    /** Thu hồi trợ giảng ngay. Giáo viên chính chỉ đổi bằng cách gán người mới, để lớp không bao giờ thiếu giáo viên chính. */
    @Transactional
    public StaffView revoke(long classId, long assignmentId) {
        classes.lock(classId);
        ClassStaff row = staff.findByIdAndClassId(assignmentId, classId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "assignment_not_found", "Không tìm thấy phân công"));
        if (ClassStaff.TEACHER.equals(row.getRole())) {
            throw new ApiException(HttpStatus.CONFLICT, "teacher_required", "Hãy gán giáo viên chính mới để thay người cũ");
        }
        var before = view(row, classes.today());
        row.revoke(classes.now());
        var after = view(staff.saveAndFlush(row), classes.today());
        audit.record("CLASS_ASSISTANT_REVOKE", "CLASS", classId, before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public List<StaffView> list(long classId) {
        List<ClassStaff> rows = staff.findByClassIdOrderByIdDesc(classId);
        Map<Long, String> names = names(rows.stream().map(ClassStaff::getUserId).distinct().toList());
        LocalDate today = classes.today();
        return rows.stream().map(r -> toView(r, names.get(r.getUserId()), today)).toList();
    }

    private void requireRoleAndRange(AssignStaff req, String role, LocalDate from, LocalDate today) {
        if (!permissions.roleCodes(req.userId()).contains(role)) {
            throw ApiException.validation("userId", "Người này không có vai trò " + (ClassStaff.TEACHER.equals(role) ? "Giáo viên" : "Trợ giảng"));
        }
        if (req.to() != null && (req.to().isBefore(from) || req.to().isBefore(today))) {
            throw ApiException.validation("to", "Ngày kết thúc phụ trách phải từ ngày bắt đầu và từ hôm nay trở đi");
        }
    }

    private StaffView view(ClassStaff row, LocalDate today) {
        return toView(row, names(List.of(row.getUserId())).get(row.getUserId()), today);
    }

    private static StaffView toView(ClassStaff r, String name, LocalDate today) {
        return new StaffView(r.getId(), r.getUserId(), name, r.getRole(), r.getAssignedFrom(), r.getAssignedTo(), r.isActiveOn(today));
    }

    private Map<Long, String> names(List<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            classRepo.people(userIds).forEach(r -> names.put(((Number) r[0]).longValue(), (String) r[1]));
        }
        return names;
    }
}
