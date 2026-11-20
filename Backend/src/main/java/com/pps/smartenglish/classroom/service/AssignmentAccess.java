package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.repository.AssignmentRepository;
import com.pps.smartenglish.exercise.service.AssignedBanks;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** FR-ASG-01: cài đặt AssignedBanks cho exercise: bộ câu hỏi đang được giao cho học viên thì mở riêng dù unit chưa mở. */
@Component
class AssignmentAccess implements AssignedBanks {

    private final AssignmentRepository assignments;

    AssignmentAccess(AssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAssigned(long userId, long bankId) {
        return assignments.isBankAssigned(bankId, userId);
    }
}
