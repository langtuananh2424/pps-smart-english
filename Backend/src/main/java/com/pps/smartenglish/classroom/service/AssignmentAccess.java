package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.repository.AssignmentRepository;
import com.pps.smartenglish.ai.service.AssignedAiWork;
import com.pps.smartenglish.exercise.service.AssignedBanks;
import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-ASG-01, FR-ASG-02: cài đặt AssignedBanks cho exercise (bộ câu hỏi đang được giao thì mở riêng dù unit chưa mở) và AssignedAiWork
 * cho ai (câu Viết/Nói giao cho học viên). NFR-SEC-05: người nhận kiểm theo lớp đang học.
 */
@Component
class AssignmentAccess implements AssignedBanks, AssignedAiWork {

    private final AssignmentRepository assignments;

    AssignmentAccess(AssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAssigned(long userId, long bankId) {
        return assignments.isBankAssigned(bankId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAssignedAnyStatus(long userId, long bankId) {
        return assignments.isBankAssignedAnyStatus(bankId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireActive(Long assignmentId) {
        if (assignmentId != null && assignments.findById(assignmentId).filter(a -> !a.isActive()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "assignment_cancelled", "Bài này đã được giáo viên hủy");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireViewer(long userId, long assignmentId, long questionId) {
        if (!assignments.isRecipientOfQuestionAnyStatus(assignmentId, userId, questionId)) {
            throw AssignmentService.notFound();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireRecipient(long userId, long assignmentId, long questionId) {
        if (!assignments.isRecipientOfQuestion(assignmentId, userId, questionId)) {
            throw AssignmentService.notFound();
        }
    }

    @Override
    public void lock(long assignmentId) {
        assignments.lockById(assignmentId); // dòng bị xóa thì không có gì để khóa; requireRecipient đã chặn trước đó
    }
}
