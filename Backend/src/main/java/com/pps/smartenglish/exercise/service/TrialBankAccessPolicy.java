package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.dto.BankInfo;
import org.springframework.stereotype.Component;

@Component
class TrialBankAccessPolicy implements BankAccessPolicy {

    // shortcut: chỉ bank thử hoặc topic học thử; commerce sẽ thêm policy kiểm tra enrollment
    @Override
    public boolean allows(Long userId, BankInfo bank) {
        return bank.trial() || bank.topicFreeTrial();
    }
}
