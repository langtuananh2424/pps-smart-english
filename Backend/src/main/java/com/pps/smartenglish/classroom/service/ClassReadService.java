package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.dto.ClassDtos.ClassDetail;
import com.pps.smartenglish.classroom.dto.ClassDtos.ClassView;
import com.pps.smartenglish.classroom.repository.ClassRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Phần đọc dành cho giáo viên, trợ giảng, học viên: chỉ thấy lớp của mình (NFR-SEC-05). */
@Service
public class ClassReadService {

    private final ClassRepository classes;
    private final ClassService classService;
    private final ClassAccess access;
    private final ClassStaffService staff;
    private final ClassMemberService members;

    ClassReadService(ClassRepository classes, ClassService classService, ClassAccess access, ClassStaffService staff,
                     ClassMemberService members) {
        this.classes = classes;
        this.classService = classService;
        this.access = access;
        this.staff = staff;
        this.members = members;
    }

    /** Lớp người này đang phụ trách (giáo viên chính hoặc trợ giảng). */
    @Transactional(readOnly = true)
    public List<ClassView> teaching(long userId) {
        return classService.views(classes.taughtBy(userId, classService.today()));
    }

    /** Lớp học viên đang học hoặc đã hoàn thành. */
    @Transactional(readOnly = true)
    public List<ClassView> attending(long userId) {
        return classService.views(classes.attendedBy(userId));
    }

    /** Học viên của lớp thấy thông tin lớp và nhân sự, không thấy danh sách bạn học. */
    @Transactional(readOnly = true)
    public ClassDetail detail(long userId, long classId) {
        var relation = access.relation(userId, classId);
        var view = classService.view(classService.require(classId));
        var roster = relation == ClassAccess.Relation.STUDENT ? null : members.roster(classId);
        return new ClassDetail(view, staff.list(classId), roster);
    }
}
