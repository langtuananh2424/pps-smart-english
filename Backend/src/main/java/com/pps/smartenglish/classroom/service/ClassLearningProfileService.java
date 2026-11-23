package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.domain.ClassMember;
import com.pps.smartenglish.classroom.repository.ClassMemberRepository;
import com.pps.smartenglish.exercise.dto.LearningDtos.Profile;
import com.pps.smartenglish.exercise.service.LearningStatsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-EX-09, NFR-SEC-05: nhân sự lớp (hoặc giáo vụ) xem hồ sơ năng lực của học viên trong lớp. Lớp lạ, học viên không thuộc
 * lớp, hay người gọi là học viên đều 404 như không tồn tại.
 */
@Service
public class ClassLearningProfileService {

    private final ClassAccess access;
    private final ClassMemberRepository members;
    private final LearningStatsService stats;

    ClassLearningProfileService(ClassAccess access, ClassMemberRepository members, LearningStatsService stats) {
        this.access = access;
        this.members = members;
        this.stats = stats;
    }

    @Transactional(readOnly = true)
    public Profile of(long callerId, long classId, long studentId) {
        access.requireStaff(callerId, classId);
        boolean inClass = members.existsByClassIdAndUserIdAndStatus(classId, studentId, ClassMember.ACTIVE)
                || members.existsByClassIdAndUserIdAndStatus(classId, studentId, ClassMember.COMPLETED);
        if (!inClass) {
            throw ClassService.notFound();
        }
        return stats.profile(studentId);
    }
}
