package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.commerce.service.EnrollmentAccess;
import com.pps.smartenglish.exercise.dto.BankInfo;
import org.springframework.stereotype.Component;

/** Bộ câu hỏi trả phí mở khi học viên có enrollment còn hạn của Level chứa bộ câu hỏi. */
@Component
class EnrollmentBankAccessPolicy implements BankAccessPolicy {

    private final EnrollmentAccess enrollments;

    EnrollmentBankAccessPolicy(EnrollmentAccess enrollments) {
        this.enrollments = enrollments;
    }

    @Override
    public boolean allows(Long userId, BankInfo bank) {
        return enrollments.hasAccess(userId, bank.levelId());
    }
}
