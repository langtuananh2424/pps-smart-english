package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.auth.service.PermissionService;
import com.pps.smartenglish.classroom.domain.ClassMember;
import com.pps.smartenglish.classroom.domain.ClassRoom;
import com.pps.smartenglish.classroom.repository.ClassMemberRepository;
import com.pps.smartenglish.classroom.repository.ClassStaffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NFR-SEC-05: một chỗ duy nhất quyết định ai được động vào lớp nào, mọi API của phân hệ lớp (kể cả buổi học, điểm danh,
 * giao bài sau này) đi qua đây. Người không có quan hệ với lớp nhận 404 như lớp không tồn tại, để không lộ lớp khác.
 */
@Service
public class ClassAccess {

    public static final String MANAGE = "class.manage";

    public enum Relation { MANAGER, STAFF, STUDENT }

    private final PermissionService permissions;
    private final ClassService classes;
    private final ClassStaffRepository staff;
    private final ClassMemberRepository members;

    ClassAccess(PermissionService permissions, ClassService classes, ClassStaffRepository staff, ClassMemberRepository members) {
        this.permissions = permissions;
        this.classes = classes;
        this.staff = staff;
        this.members = members;
    }

    /** Quan hệ của người gọi với lớp: giáo vụ, nhân sự đang phụ trách, hoặc học viên của lớp. Không có thì 404. */
    @Transactional(readOnly = true)
    public Relation relation(long userId, long classId) {
        classes.require(classId);
        if (permissions.has(userId, MANAGE)) {
            return Relation.MANAGER;
        }
        if (staff.isStaff(classId, userId, classes.today())) {
            return Relation.STAFF;
        }
        boolean student = members.existsByClassIdAndUserIdAndStatus(classId, userId, ClassMember.ACTIVE)
                || members.existsByClassIdAndUserIdAndStatus(classId, userId, ClassMember.COMPLETED);
        if (student) {
            return Relation.STUDENT;
        }
        throw ClassService.notFound();
    }

    /** Chỉ giáo vụ hoặc nhân sự đang phụ trách đúng lớp này; học viên và giáo viên lớp khác bị 404. */
    @Transactional(readOnly = true)
    public ClassRoom requireStaff(long userId, long classId) {
        if (relation(userId, classId) == Relation.STUDENT) {
            throw ClassService.notFound();
        }
        return classes.require(classId);
    }
}
