package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.dto.BankInfo;
import org.springframework.stereotype.Component;

@Component
class TrialBankAccessPolicy implements BankAccessPolicy {

    @Override
    public boolean allows(Long userId, BankInfo bank) {
        // D-35: học thử không được thi, nên bộ EXAM chỉ mở qua enrollment.
        return !QuestionBank.EXAM.equals(bank.mode()) && (bank.trial() || bank.topicFreeTrial());
    }
}
