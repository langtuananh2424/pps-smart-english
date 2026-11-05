package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.dto.BankInfo;
import org.springframework.stereotype.Component;

@Component
class TrialBankAccessPolicy implements BankAccessPolicy {

    @Override
    public boolean allows(Long userId, BankInfo bank) {
        return bank.trial() || bank.topicFreeTrial();
    }
}
