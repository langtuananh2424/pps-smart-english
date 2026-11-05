package com.pps.smartenglish.exercise.controller;

import com.pps.smartenglish.exercise.dto.AttemptStarted;
import com.pps.smartenglish.exercise.dto.BankResponse;
import com.pps.smartenglish.exercise.service.AttemptService;
import com.pps.smartenglish.exercise.service.BankService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-EX-01, FR-EX-02: học viên đăng nhập xem đề và bắt đầu làm. */
@RestController
@RequestMapping("/api/banks")
class BankController {

    private final BankService banks;
    private final AttemptService attempts;

    BankController(BankService banks, AttemptService attempts) {
        this.banks = banks;
        this.attempts = attempts;
    }

    @GetMapping("/{id}")
    BankResponse get(@PathVariable String id, Authentication auth) {
        return banks.get(bankId(id), CurrentUser.id(auth));
    }

    @PostMapping("/{id}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    AttemptStarted start(@PathVariable String id, Authentication auth) {
        return attempts.start(bankId(id), CurrentUser.id(auth));
    }

    private static Long bankId(String raw) {
        return PathId.parse(raw, "bank_not_found", "Không tìm thấy bộ câu hỏi");
    }
}
