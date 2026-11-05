package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.dto.BankInfo;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Cổng chung: 404 nếu bank không tồn tại, 403 nếu không policy nào cho làm. */
@Component
class BankGate {

    private final QuestionBankRepository banks;
    private final List<BankAccessPolicy> policies;

    BankGate(QuestionBankRepository banks, List<BankAccessPolicy> policies) {
        this.banks = banks;
        this.policies = policies;
    }

    BankInfo require(Long bankId, Long userId) {
        var bank = banks.findInfo(bankId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy bộ câu hỏi"));
        if (policies.stream().noneMatch(p -> p.allows(userId, bank))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "bank_locked", "Bạn chưa có quyền làm bộ câu hỏi này");
        }
        return bank;
    }
}
