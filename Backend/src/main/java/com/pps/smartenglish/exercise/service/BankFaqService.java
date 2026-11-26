package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.dto.CamDtos.FaqItem;
import com.pps.smartenglish.exercise.repository.BankFaqRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-AI-07: Cam hỏi đáp cho học viên; cùng quyền truy cập như xem đề, không có ở bộ EXAM. */
@Service
public class BankFaqService {

    private final BankGate gate;
    private final BankFaqRepository faqs;
    private final StudentAttemptRepository attempts;
    private final AttemptModes modes;

    BankFaqService(BankGate gate, BankFaqRepository faqs, StudentAttemptRepository attempts, AttemptModes modes) {
        this.gate = gate;
        this.faqs = faqs;
        this.attempts = attempts;
        this.modes = modes;
    }

    @Transactional(readOnly = true)
    public List<FaqItem> list(Long bankId, Long userId) {
        var bank = gate.require(bankId, userId);
        // Giữ mode của bộ: FAQ theo bộ, không gắn lượt (D-151: bộ IELTS luôn PRACTICE; web ẩn Cam ở lượt Mô phỏng thi)
        if (QuestionBank.EXAM.equals(bank.mode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "assistant_unavailable", "Cam không có trong bài kiểm tra/thi");
        }
        // D-151: Mô phỏng thi không có Cam, kể cả FAQ theo bộ
        if (attempts.findFirstByUserIdAndBankIdAndSubmittedAtIsNullOrderByIdDesc(userId, bankId)
                .filter(a -> QuestionBank.EXAM.equals(modes.of(a))).isPresent()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "hints_unavailable", "Cam không có khi đang mô phỏng thi");
        }
        return faqs.findByBankIdOrderBySortOrderAscIdAsc(bankId).stream()
                .map(f -> new FaqItem(f.getId(), f.getQuestion(), f.getAnswer())).toList();
    }
}
