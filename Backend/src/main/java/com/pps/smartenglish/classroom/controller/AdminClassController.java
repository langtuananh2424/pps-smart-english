package com.pps.smartenglish.classroom.controller;

import com.pps.smartenglish.classroom.dto.ClassDtos.AssignMember;
import com.pps.smartenglish.classroom.dto.ClassDtos.AssignStaff;
import com.pps.smartenglish.classroom.dto.ClassDtos.ClassView;
import com.pps.smartenglish.classroom.dto.ClassDtos.CreateClass;
import com.pps.smartenglish.classroom.dto.ClassDtos.HistoryRow;
import com.pps.smartenglish.classroom.dto.ClassDtos.MemberView;
import com.pps.smartenglish.classroom.dto.ClassDtos.PatchClass;
import com.pps.smartenglish.classroom.dto.ClassDtos.StaffView;
import com.pps.smartenglish.classroom.dto.ClassDtos.TransferMember;
import com.pps.smartenglish.classroom.dto.ClassDtos.WaitingRow;
import com.pps.smartenglish.classroom.service.ClassMemberService;
import com.pps.smartenglish.classroom.service.ClassService;
import com.pps.smartenglish.classroom.service.ClassStaffService;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/** FR-CLS-01..04 phía giáo vụ; cả nhóm route cần quyền class.manage (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/classes")
class AdminClassController {

    private static final String CLASS_NF = "class_not_found";
    private static final String CLASS_NF_MSG = "Không tìm thấy lớp";
    private static final String STUDENT_NF = "student_not_found";
    private static final String STUDENT_NF_MSG = "Không tìm thấy học viên";

    private final ClassService classes;
    private final ClassMemberService members;
    private final ClassStaffService staff;

    AdminClassController(ClassService classes, ClassMemberService members, ClassStaffService staff) {
        this.classes = classes;
        this.members = members;
        this.staff = staff;
    }

    @GetMapping
    CursorPage<ClassView> list(@RequestParam(required = false) Long courseId, @RequestParam(required = false) String status,
                               @RequestParam(required = false) String limit, @RequestParam(required = false) String cursor) {
        return classes.list(courseId, status, limit, cursor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ClassView create(@Valid @RequestBody CreateClass req) {
        return classes.create(req);
    }

    @PatchMapping("/{id}")
    ClassView update(@PathVariable String id, @Valid @RequestBody PatchClass req) {
        return classes.update(PathId.parse(id, CLASS_NF, CLASS_NF_MSG), req);
    }

    @PostMapping("/{id}/close")
    ClassView close(@PathVariable String id) {
        return classes.close(PathId.parse(id, CLASS_NF, CLASS_NF_MSG));
    }

    @GetMapping("/waiting")
    CursorPage<WaitingRow> waiting(@RequestParam(required = false) Long courseId, @RequestParam(required = false) String limit,
                                   @RequestParam(required = false) String cursor) {
        return members.waiting(courseId, limit, cursor);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    MemberView assign(@PathVariable String id, @Valid @RequestBody AssignMember req) {
        return members.assign(PathId.parse(id, CLASS_NF, CLASS_NF_MSG), req.userId());
    }

    @PostMapping("/{id}/members/{userId}/transfer")
    MemberView transfer(@PathVariable String id, @PathVariable String userId, @Valid @RequestBody TransferMember req) {
        return members.transfer(PathId.parse(id, CLASS_NF, CLASS_NF_MSG), PathId.parse(userId, STUDENT_NF, STUDENT_NF_MSG),
                req.toClassId());
    }

    @DeleteMapping("/{id}/members/{userId}")
    MemberView withdraw(@PathVariable String id, @PathVariable String userId) {
        return members.withdraw(PathId.parse(id, CLASS_NF, CLASS_NF_MSG), PathId.parse(userId, STUDENT_NF, STUDENT_NF_MSG));
    }

    @GetMapping("/students/{userId}/history")
    List<HistoryRow> history(@PathVariable String userId) {
        return members.history(PathId.parse(userId, STUDENT_NF, STUDENT_NF_MSG));
    }

    @PutMapping("/{id}/teacher")
    StaffView assignTeacher(@PathVariable String id, @Valid @RequestBody AssignStaff req) {
        return staff.assignTeacher(PathId.parse(id, CLASS_NF, CLASS_NF_MSG), req);
    }

    @PostMapping("/{id}/assistants")
    @ResponseStatus(HttpStatus.CREATED)
    StaffView addAssistant(@PathVariable String id, @Valid @RequestBody AssignStaff req) {
        return staff.addAssistant(PathId.parse(id, CLASS_NF, CLASS_NF_MSG), req);
    }

    @DeleteMapping("/{id}/staff/{assignmentId}")
    StaffView revoke(@PathVariable String id, @PathVariable String assignmentId) {
        return staff.revoke(PathId.parse(id, CLASS_NF, CLASS_NF_MSG),
                PathId.parse(assignmentId, "assignment_not_found", "Không tìm thấy phân công"));
    }
}
